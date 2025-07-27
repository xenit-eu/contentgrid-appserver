package com.contentgrid.appserver.domain.data.mapper;

import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.CompositeAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.relations.ManyToManyRelation;
import com.contentgrid.appserver.application.model.relations.ManyToOneRelation;
import com.contentgrid.appserver.application.model.relations.OneToManyRelation;
import com.contentgrid.appserver.application.model.relations.OneToOneRelation;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.domain.data.AnyRelationDataEntryTransformer;
import com.contentgrid.appserver.domain.data.DataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.AnyRelationDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.BooleanDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.DecimalDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.InstantDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.LongDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.MapDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.MissingDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.MultipleRelationDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.NullDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.RelationDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.ScalarDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.StringDataEntry;
import com.contentgrid.appserver.domain.data.transformers.AsTypeDataEntryTransformer;
import com.contentgrid.appserver.domain.data.transformers.InvalidDataException;
import com.contentgrid.appserver.domain.data.transformers.InvalidPropertyDataException;
import com.contentgrid.appserver.domain.data.transformers.result.Result;
import com.contentgrid.appserver.query.engine.api.data.AttributeData;
import com.contentgrid.appserver.query.engine.api.data.CompositeAttributeData;
import com.contentgrid.appserver.query.engine.api.data.RelationData;
import com.contentgrid.appserver.query.engine.api.data.SimpleAttributeData;
import com.contentgrid.appserver.query.engine.api.data.XToManyRelationData;
import com.contentgrid.appserver.query.engine.api.data.XToOneRelationData;
import java.util.Optional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Basic mapper that converts {@link DataEntry} to {@link AttributeData} or {@link RelationData} (without any validation)
 */
@RequiredArgsConstructor
public class DataEntryToQueryEngineMapper implements AttributeMapper<DataEntry, Optional<AttributeData>>, RelationMapper<DataEntry, Optional<RelationData>> {

    @Override
    public Optional<AttributeData> mapAttribute(Attribute attribute, DataEntry inputData) throws InvalidPropertyDataException {
        try {
            return switch (attribute) {
                case SimpleAttribute simpleAttribute -> inputData
                        .map(new AsTypeDataEntryTransformer<>(getTypeForAttribute(simpleAttribute.getType())) {
                            @Override
                            public Result<ScalarDataEntry> transform(MissingDataEntry missingDataEntry) {
                                return Result.empty();
                            }

                            @Override
                            public Result<ScalarDataEntry> transform(NullDataEntry nullDataEntry) {
                                return Result.of(nullDataEntry);
                            }
                        })
                        .asOptional()
                        .map(entry -> new SimpleAttributeData<>(attribute.getName(), entry.getValue()));
                case CompositeAttribute compositeAttribute -> inputData
                        .map(new AsTypeDataEntryTransformer<>(MapDataEntry.class) {
                            @Override
                            public Result<MapDataEntry> transform(MissingDataEntry missingDataEntry) {
                                return Result.empty();
                            }

                            @Override
                            public Result<MapDataEntry> transform(NullDataEntry nullDataEntry) {
                                var builder = MapDataEntry.builder();
                                for (var nestedAttr : compositeAttribute.getAttributes()) {
                                    // All values are set to null, so a proper composite attribute is built,
                                    // but with all attributes set to null
                                    builder.item(nestedAttr.getName().getValue(), NullDataEntry.INSTANCE);
                                }
                                return Result.of(builder.build());
                            }
                        })
                        .map(dataEntry -> {
                            var builder = CompositeAttributeData.builder()
                                    .name(attribute.getName());
                            for (var nestedAttr : compositeAttribute.getAttributes()) {
                                mapAttribute(nestedAttr, dataEntry.get(nestedAttr.getName().getValue()))
                                        .ifPresent(builder::attribute);
                            }

                            return (AttributeData) builder.build();
                        })
                        .asOptional();
            };
        } catch (InvalidDataException e) {
            throw e.withinProperty(attribute.getName());
        }
    }

    private Class<ScalarDataEntry> getTypeForAttribute(@NonNull SimpleAttribute.Type type) {
        return (Class<ScalarDataEntry>) switch (type) {
            case LONG -> LongDataEntry.class;
            case DOUBLE -> DecimalDataEntry.class;
            case BOOLEAN -> BooleanDataEntry.class;
            case TEXT, UUID -> StringDataEntry.class;
            case DATETIME -> InstantDataEntry.class;
        };
    }

    @Override
    public Optional<RelationData> mapRelation(Relation relation, DataEntry inputData)
            throws InvalidPropertyDataException {
        try {
            return inputData
                    .map(new AsTypeDataEntryTransformer<>(getTypeForRelation(relation)) {
                        @Override
                        public Result<AnyRelationDataEntry> transform(NullDataEntry nullDataEntry) {
                            // TODO: If relations can ever be used for entity updates; don't discard null values
                            // Instead, there should be a complete split between to-one and to-many relations.
                            // The null value would be valid to clear a to-one relation, but would be invalid to clear a to-many relation
                            // (that would require an empty list)
                            return Result.empty();
                        }
                    })
                    .map(dataEntry -> dataEntry.map(
                            new AnyRelationDataEntryTransformer<RelationData>() {
                                @Override
                                public RelationData transform(RelationDataEntry relationDataEntry) {
                                    return XToOneRelationData.builder()
                                            .name(relation.getSourceEndPoint().getName())
                                            .ref(relationDataEntry.getTargetId())
                                            .build();
                                }

                                @Override
                                public RelationData transform(
                                        MultipleRelationDataEntry multipleRelationDataEntry) {
                                    return XToManyRelationData.builder()
                                            .name(relation.getSourceEndPoint().getName())
                                            .refs(multipleRelationDataEntry.getTargetIds())
                                            .build();
                                }
                            }))
                    .asOptional();
        } catch (InvalidDataException e) {
            throw e.withinProperty(relation.getSourceEndPoint().getName());
        }

    }

    private Class<AnyRelationDataEntry> getTypeForRelation(Relation relation) {
        return (Class<AnyRelationDataEntry>) switch (relation) {
            case OneToOneRelation ignored -> RelationDataEntry.class;
            case ManyToOneRelation ignored -> RelationDataEntry.class;
            case OneToManyRelation ignored -> MultipleRelationDataEntry.class;
            case ManyToManyRelation ignored -> MultipleRelationDataEntry.class;
        };
    }
}
