package com.contentgrid.appserver.domain.data.mapper;

import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.CompositeAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.relations.ManyToManyRelation;
import com.contentgrid.appserver.application.model.relations.ManyToOneRelation;
import com.contentgrid.appserver.application.model.relations.OneToManyRelation;
import com.contentgrid.appserver.application.model.relations.OneToOneRelation;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.domain.data.DataEntry;
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
import com.contentgrid.appserver.domain.data.InvalidDataTypeException;
import com.contentgrid.appserver.domain.data.InvalidDataException;
import com.contentgrid.appserver.domain.data.InvalidPropertyDataException;
import com.contentgrid.appserver.domain.data.type.DataType;
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
        if(inputData instanceof MissingDataEntry) {
            return Optional.empty();
        }
        try {
            return switch (attribute) {
                case SimpleAttribute simpleAttribute -> mapSimpleAttribute(simpleAttribute, inputData)
                        .map(entry -> new SimpleAttributeData<>(attribute.getName(), entry.getValue()));
                case CompositeAttribute compositeAttribute -> mapCompositeAttribute(compositeAttribute, inputData);
            };
        } catch (InvalidDataException e) {
            throw e.withinProperty(attribute.getName());
        }
    }

    private Optional<AttributeData> mapCompositeAttribute(CompositeAttribute attribute, DataEntry inputData)
            throws InvalidDataException {
        if(inputData instanceof NullDataEntry) {
            var builder = MapDataEntry.builder();
            for (var nestedAttr : attribute.getAttributes()) {
                // All values are set to null, so a proper composite attribute is built,
                // but with all attributes set to null
                builder.item(nestedAttr.getName().getValue(), NullDataEntry.INSTANCE);
            }
            inputData = builder.build();
        }
        if(inputData instanceof MapDataEntry mapDataEntry) {
            var builder = CompositeAttributeData.builder()
                    .name(attribute.getName());
            for (var nestedAttr : attribute.getAttributes()) {
                mapAttribute(nestedAttr, mapDataEntry.get(nestedAttr.getName().getValue()))
                        .ifPresent(builder::attribute);
            }

            return Optional.of(builder.build());
        }
        throw new InvalidDataTypeException(DataType.of(attribute), DataType.of(inputData));
    }

    private Optional<ScalarDataEntry> mapSimpleAttribute(SimpleAttribute attribute, DataEntry inputData)
            throws InvalidDataTypeException {
        if(inputData instanceof NullDataEntry nullDataEntry) {
            return Optional.of(nullDataEntry);
        }
        var expectedType = getTypeForAttribute(attribute.getType());
        if(expectedType.isInstance(inputData)) {
            return Optional.of((ScalarDataEntry) inputData);
        }
        throw new InvalidDataTypeException(DataType.of(expectedType), DataType.of(inputData));
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
        if(inputData instanceof MissingDataEntry) {
            return Optional.empty();
        }
        if(inputData instanceof NullDataEntry) {
            // TODO: If relations can ever be used for entity updates; don't discard null values
            // Instead, there should be a complete split between to-one and to-many relations.
            // The null value would be valid to clear a to-one relation, but would be invalid to clear a to-many relation
            // (that would require an empty list)
            return Optional.empty();
        }

        try {
            return switch (relation) {
                case OneToOneRelation ignored -> mapToOneRelation(relation, inputData);
                case ManyToOneRelation ignored -> mapToOneRelation(relation, inputData);
                case OneToManyRelation ignored -> mapToManyRelation(relation, inputData);
                case ManyToManyRelation ignored -> mapToManyRelation(relation, inputData);
            };
        } catch (InvalidDataException e) {
            throw e.withinProperty(relation.getSourceEndPoint().getName());
        }

    }

    private Optional<RelationData> mapToOneRelation(Relation relation, DataEntry inputData) throws InvalidDataException {
        if(inputData instanceof RelationDataEntry relationDataEntry) {
            return Optional.of(XToOneRelationData.builder()
                    .name(relation.getSourceEndPoint().getName())
                    .ref(relationDataEntry.getTargetId())
                    .build());
        }
        throw new InvalidDataTypeException(DataType.of(relation), DataType.of(inputData));

    }

    private Optional<RelationData> mapToManyRelation(Relation relation, DataEntry inputData) throws InvalidDataException {
        if(inputData instanceof MultipleRelationDataEntry multipleRelationDataEntry) {
            return Optional.of(XToManyRelationData.builder()
                    .name(relation.getSourceEndPoint().getName())
                    .refs(multipleRelationDataEntry.getTargetIds())
                    .build());
        }

        throw new InvalidDataTypeException(DataType.of(relation), DataType.of(inputData));
    }

}
