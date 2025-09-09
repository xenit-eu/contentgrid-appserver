package com.contentgrid.appserver.domain.data.mapper;

import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.CompositeAttribute;
import com.contentgrid.appserver.application.model.attributes.ContentAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.relations.ManyToManyRelation;
import com.contentgrid.appserver.application.model.relations.ManyToOneRelation;
import com.contentgrid.appserver.application.model.relations.OneToManyRelation;
import com.contentgrid.appserver.application.model.relations.OneToOneRelation;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.domain.data.DataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.BooleanDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.DecimalDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.FileDataEntry;
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
import com.contentgrid.appserver.domain.data.InvalidDataException;
import com.contentgrid.appserver.domain.data.InvalidDataTypeException;
import com.contentgrid.appserver.domain.data.InvalidPropertyDataException;
import com.contentgrid.appserver.domain.data.type.DataType;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

/**
 * Basic mapper performing the conversion of {@link RequestInputData} to {@link DataEntry} (without any validation)
 */
@RequiredArgsConstructor
public class RequestInputDataToDataEntryMapper implements AttributeMapper<RequestInputData, Optional<DataEntry>>, RelationMapper<RequestInputData, Optional<DataEntry>> {

    @Override
    public Optional<DataEntry> mapAttribute(Attribute attribute, RequestInputData inputData)
            throws InvalidPropertyDataException {
        if(attribute.isReadOnly() || attribute.isIgnored()) {
            // Completely skip readonly/ignored attributes
            return Optional.empty();
        }
        return switch (attribute) {
            case SimpleAttribute simpleAttribute -> mapSimpleAttribute(simpleAttribute, inputData);
            case ContentAttribute contentAttribute -> mapContentAttribute(contentAttribute, inputData);
            case CompositeAttribute compositeAttribute -> mapCompositeAttribute(compositeAttribute, inputData);
        };
    }

    private Optional<DataEntry> mapContentAttribute(ContentAttribute contentAttribute, RequestInputData inputData) throws InvalidPropertyDataException{
        var attributeName = contentAttribute.getName();
        try {
            var dataEntry = inputData.get(attributeName.getValue(), FileDataEntry.class);
            if(dataEntry instanceof FileDataEntry fileDataEntry) {
                return Optional.of(fileDataEntry);
            }
        } catch (InvalidDataTypeException e) {
            // The input data is not a File type, map to composite attribute
            // Fallthrough to mapping a composite attribute
        } catch (InvalidDataException e) {
            throw e.withinProperty(attributeName);
        }
        return mapCompositeAttribute(contentAttribute, inputData);
    }

    private Optional<DataEntry> mapSimpleAttribute(SimpleAttribute simpleAttribute,
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
            return Optional.of(inputData.get(attributeName.getValue(), entryType));
        } catch (InvalidDataException e) {
            throw e.withinProperty(attributeName);
        }
    }

    private Optional<DataEntry> mapCompositeAttribute(CompositeAttribute compositeAttribute,
            RequestInputData inputData) throws InvalidPropertyDataException {
        var attributeName = compositeAttribute.getName();
        try {
            var nestedInputData = inputData.nested(attributeName.getValue());
            return switch (nestedInputData) {
                case MissingResult<RequestInputData> ignored -> Optional.of(MissingDataEntry.INSTANCE);
                case NullResult<RequestInputData> ignored -> Optional.of(NullDataEntry.INSTANCE);
                case DataResult<RequestInputData> data -> {
                    var builder = MapDataEntry.builder();
                    for (var attr : compositeAttribute.getAttributes()) {
                        mapAttribute(attr, data.get())
                                .ifPresent(attrData -> builder.item(attr.getName().getValue(), (PlainDataEntry)attrData));
                    }

                    yield Optional.of(builder.build());
                }
            };
        } catch (InvalidDataException e) {
            throw e.withinProperty(attributeName);
        }
    }

    @Override
    public Optional<DataEntry> mapRelation(Relation relation, RequestInputData inputData)
            throws InvalidPropertyDataException {
        return switch (relation) {
            case OneToOneRelation ignored -> mapToOneRelation(relation, inputData);
            case ManyToOneRelation ignored -> mapToOneRelation(relation, inputData);
            case OneToManyRelation ignored -> mapToManyRelation(relation, inputData);
            case ManyToManyRelation ignored -> mapToManyRelation(relation, inputData);
        };
    }

    private Optional<DataEntry> mapToOneRelation(Relation relation, RequestInputData inputData)
            throws InvalidPropertyDataException {
        var relationName = relation.getSourceEndPoint().getName();
        try {
            var entry = inputData.get(relationName.getValue(), RelationDataEntry.class);
            if(entry instanceof RelationDataEntry e) {
                if(!Objects.equals(relation.getTargetEndPoint().getEntity(), e.getTargetEntity())) {
                    throw new InvalidDataTypeException(DataType.of(relation), DataType.of(e));
                }
            }
            return Optional.of(entry);
        } catch (InvalidDataException e) {
            throw e.withinProperty(relationName);
        }
    }

    private Optional<DataEntry> mapToManyRelation(Relation relation, RequestInputData inputData)
            throws InvalidPropertyDataException {
        var relationName = relation.getSourceEndPoint().getName();

        try {
            var listResult = inputData.getList(relationName.getValue(), RelationDataEntry.class);
            return switch (listResult) {
                case DataResult<List<? extends DataEntry>> v -> {
                    var targetEntity = relation.getTargetEndPoint().getEntity();
                    var builder = MultipleRelationDataEntry.builder()
                            .targetEntity(targetEntity);

                    for (var item : v.get()) {
                        if(item instanceof RelationDataEntry entry) {
                            if(!Objects.equals(targetEntity, entry.getTargetEntity())) {
                                throw new InvalidDataTypeException(
                                        DataType.of(relation),
                                        // We need to create a MultipleRelationDataEntry, so the exception correctly reports this to be a multiple relations entry
                                        DataType.of(MultipleRelationDataEntry.builder().targetEntity(entry.getTargetEntity()).build())
                                );
                            }
                            builder.targetId(entry.getTargetId());
                        } else {
                            throw new InvalidDataTypeException(
                                    DataType.of(relation),
                                    DataType.of(item)
                            );
                        }
                    }
                    yield Optional.of(builder.build());
                }
                case MissingResult<List<? extends DataEntry>> v -> Optional.of(MissingDataEntry.INSTANCE);
                case NullResult<List<? extends DataEntry>> v -> Optional.of(NullDataEntry.INSTANCE);
            };
        } catch (InvalidDataException e) {
            throw e.withinProperty(relationName);
        }

    }

}
