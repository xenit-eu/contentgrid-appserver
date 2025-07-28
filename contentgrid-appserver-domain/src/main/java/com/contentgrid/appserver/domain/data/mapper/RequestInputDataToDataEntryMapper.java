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
import com.contentgrid.appserver.domain.data.DataEntry.PlainDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.RelationDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.StringDataEntry;
import com.contentgrid.appserver.domain.data.RequestInputData;
import com.contentgrid.appserver.domain.data.RequestInputData.DataResult;
import com.contentgrid.appserver.domain.data.RequestInputData.MissingResult;
import com.contentgrid.appserver.domain.data.RequestInputData.NullResult;
import com.contentgrid.appserver.domain.data.transformers.AsTypeDataEntryTransformer;
import com.contentgrid.appserver.domain.data.InvalidDataException;
import com.contentgrid.appserver.domain.data.InvalidDataTypeException;
import com.contentgrid.appserver.domain.data.InvalidPropertyDataException;
import com.contentgrid.appserver.domain.data.type.DataType;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;

/**
 * Basic mapper performing the conversion of {@link RequestInputData} to {@link DataEntry} (without any validation)
 */
@RequiredArgsConstructor
public class RequestInputDataToDataEntryMapper implements AttributeMapper<RequestInputData, DataEntry>, RelationMapper<RequestInputData, DataEntry> {

    @Override
    public DataEntry mapAttribute(Attribute attribute, RequestInputData inputData)
            throws InvalidPropertyDataException {
        return switch (attribute) {
            case SimpleAttribute simpleAttribute -> mapSimpleAttribute(simpleAttribute, inputData);
            case CompositeAttribute compositeAttribute -> mapCompositeAttribute(compositeAttribute, inputData);
        };
    }

    private DataEntry mapSimpleAttribute(SimpleAttribute simpleAttribute,
            RequestInputData inputData) throws InvalidPropertyDataException{
        var attributeName = simpleAttribute.getName();
        var entryType = switch (simpleAttribute.getType()) {
            case LONG -> LongDataEntry.class;
            case DOUBLE -> DecimalDataEntry.class;
            case BOOLEAN -> BooleanDataEntry.class;
            case TEXT, UUID -> StringDataEntry.class;
            case DATETIME -> InstantDataEntry.class;
        };
        try {
            return inputData.get(attributeName.getValue(), entryType);
        } catch (InvalidDataException e) {
            throw e.withinProperty(attributeName);
        }
    }

    private DataEntry mapCompositeAttribute(CompositeAttribute compositeAttribute,
            RequestInputData inputData) throws InvalidPropertyDataException {
        var attributeName = compositeAttribute.getName();
        try {
            var nestedInputData = inputData.nested(attributeName.getValue());
            return switch (nestedInputData) {
                case MissingResult<RequestInputData> ignored -> MissingDataEntry.INSTANCE;
                case NullResult<RequestInputData> ignored -> NullDataEntry.INSTANCE;
                case DataResult<RequestInputData> data -> {
                    var builder = MapDataEntry.builder();
                    for (var attr : compositeAttribute.getAttributes()) {
                        builder.item(attr.getName().getValue(), (PlainDataEntry) mapAttribute(attr, data.get()));
                    }

                    yield builder.build();
                }
            };
        } catch (InvalidDataException e) {
            throw e.withinProperty(attributeName);
        }
    }

    @Override
    public DataEntry mapRelation(Relation relation, RequestInputData inputData)
            throws InvalidPropertyDataException {
        return switch (relation) {
            case OneToOneRelation ignored -> mapToOneRelation(relation, inputData);
            case ManyToOneRelation ignored -> mapToOneRelation(relation, inputData);
            case OneToManyRelation ignored -> mapToManyRelation(relation, inputData);
            case ManyToManyRelation ignored -> mapToManyRelation(relation, inputData);
        };
    }

    private DataEntry mapToOneRelation(Relation relation, RequestInputData inputData)
            throws InvalidPropertyDataException {
        var relationName = relation.getSourceEndPoint().getName();
        try {
            var entry = inputData.get(relationName.getValue(), RelationDataEntry.class);
            if(entry instanceof RelationDataEntry e) {
                if(!Objects.equals(relation.getTargetEndPoint().getEntity().getName(), e.getTargetEntity())) {
                    throw new InvalidDataTypeException(DataType.of(relation), DataType.of(e));
                }
            }
            return entry;
        } catch (InvalidDataException e) {
            throw e.withinProperty(relationName);
        }
    }

    private DataEntry mapToManyRelation(Relation relation, RequestInputData inputData)
            throws InvalidPropertyDataException {
        var relationName = relation.getSourceEndPoint().getName();

        try {
            var listResult = inputData.getList(relationName.getValue(), RelationDataEntry.class);
            return switch (listResult) {
                case DataResult<List<? extends DataEntry>> v -> {
                    var targetEntity = relation.getTargetEndPoint().getEntity().getName();
                    var builder = MultipleRelationDataEntry.builder()
                            .targetEntity(targetEntity);

                    for (var item : v.get()) {
                        item.map(new AsTypeDataEntryTransformer<>(RelationDataEntry.class))
                                .validate(entry -> {
                                    if(!Objects.equals(targetEntity, entry.getTargetEntity())) {
                                        throw new InvalidDataTypeException(
                                                DataType.of(relation),
                                                // We need to create a MultipleRelationDataEntry, so the the exception correctly reports this to be a multiple relations entry
                                                DataType.of(MultipleRelationDataEntry.builder().targetEntity(entry.getTargetEntity()).build())
                                        );
                                    }
                                })
                                .map(RelationDataEntry::getTargetId)
                                .ifPresent(builder::targetId);
                    }
                    yield builder.build();
                }
                case MissingResult<List<? extends DataEntry>> v -> MissingDataEntry.INSTANCE;
                case NullResult<List<? extends DataEntry>> v -> NullDataEntry.INSTANCE;
            };
        } catch (InvalidDataException e) {
            throw e.withinProperty(relationName);
        }

    }

}
