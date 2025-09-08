package com.contentgrid.appserver.domain;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.CompositeAttribute;
import com.contentgrid.appserver.application.model.attributes.flags.AttributeFlag;
import com.contentgrid.appserver.application.model.attributes.flags.CreatedDateFlag;
import com.contentgrid.appserver.application.model.attributes.flags.CreatorFlag;
import com.contentgrid.appserver.application.model.attributes.flags.ModifiedDateFlag;
import com.contentgrid.appserver.application.model.attributes.flags.ModifierFlag;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.AttributePath;
import com.contentgrid.appserver.application.model.values.CompositeAttributePath;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.SimpleAttributePath;
import com.contentgrid.appserver.contentstore.api.ContentStore;
import com.contentgrid.appserver.domain.authorization.PermissionPredicate;
import com.contentgrid.appserver.domain.data.DataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.PlainDataEntry;
import com.contentgrid.appserver.domain.data.EntityInstance;
import com.contentgrid.appserver.domain.data.InvalidPropertyDataException;
import com.contentgrid.appserver.domain.data.RelationTarget;
import com.contentgrid.appserver.domain.data.RequestInputData;
import com.contentgrid.appserver.domain.data.UsageTrackingRequestInputData;
import com.contentgrid.appserver.domain.data.mapper.AttributeAndRelationMapper;
import com.contentgrid.appserver.domain.data.mapper.AttributeDataToDataEntryMapper;
import com.contentgrid.appserver.domain.data.mapper.AttributeMapper;
import com.contentgrid.appserver.domain.data.mapper.AuditAttributeMapper;
import com.contentgrid.appserver.domain.data.mapper.ContentUploadAttributeMapper;
import com.contentgrid.appserver.domain.data.mapper.DataEntryToQueryEngineMapper;
import com.contentgrid.appserver.domain.data.mapper.FilterDataEntryMapper;
import com.contentgrid.appserver.domain.data.mapper.OptionalFlatMapAdaptingMapper;
import com.contentgrid.appserver.domain.data.mapper.RequestInputDataMapper;
import com.contentgrid.appserver.domain.data.mapper.RequestInputDataToDataEntryMapper;
import com.contentgrid.appserver.domain.data.validation.AllowedValuesConstraintValidator;
import com.contentgrid.appserver.domain.data.validation.AttributeValidationDataMapper;
import com.contentgrid.appserver.domain.data.validation.ContentAttributeModificationValidator;
import com.contentgrid.appserver.domain.data.validation.RelationRequiredValidationDataMapper;
import com.contentgrid.appserver.domain.data.validation.RequiredAttributeConstraintValidator;
import com.contentgrid.appserver.domain.data.validation.ValidationExceptionCollector;
import com.contentgrid.appserver.domain.values.EntityIdentity;
import com.contentgrid.appserver.domain.values.ItemCount;
import com.contentgrid.appserver.domain.paging.PageBasedPagination;
import com.contentgrid.appserver.domain.paging.ResultSlice;
import com.contentgrid.appserver.domain.paging.cursor.CursorCodec;
import com.contentgrid.appserver.domain.paging.cursor.EncodedCursorPagination;
import com.contentgrid.appserver.domain.paging.cursor.EncodedCursorSupport;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.domain.values.EntityRequest;
import com.contentgrid.appserver.domain.values.RelationIdentity;
import com.contentgrid.appserver.domain.values.RelationRequest;
import com.contentgrid.appserver.domain.values.version.Version;
import com.contentgrid.appserver.domain.values.User;
import com.contentgrid.appserver.exception.InvalidSortParameterException;
import com.contentgrid.appserver.query.engine.api.QueryEngine;
import com.contentgrid.appserver.query.engine.api.data.AttributeData;
import com.contentgrid.appserver.query.engine.api.data.CompositeAttributeData;
import com.contentgrid.appserver.query.engine.api.data.EntityCreateData;
import com.contentgrid.appserver.query.engine.api.data.EntityData;
import com.contentgrid.appserver.query.engine.api.data.OffsetData;
import com.contentgrid.appserver.query.engine.api.data.SliceData;
import com.contentgrid.appserver.query.engine.api.data.SimpleAttributeData;
import com.contentgrid.appserver.query.engine.api.data.SliceData;
import com.contentgrid.appserver.query.engine.api.data.SortData;
import com.contentgrid.appserver.query.engine.api.data.SortData.FieldSort;
import com.contentgrid.appserver.query.engine.api.exception.EntityIdNotFoundException;
import com.contentgrid.appserver.query.engine.api.exception.InvalidThunkExpressionException;
import com.contentgrid.appserver.query.engine.api.exception.QueryEngineException;
import com.contentgrid.hateoas.pagination.api.PaginationControls;
import com.contentgrid.thunx.predicates.model.LogicalOperation;
import com.contentgrid.thunx.predicates.model.ThunkExpression;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public class DatamodelApiImpl implements DatamodelApi {

    private final QueryEngine queryEngine;
    private final ContentStore contentStore;
    private final CursorCodec cursorCodec;

    private RequestInputDataMapper createInputDataMapper(
            @NonNull Application application,
            @NonNull EntityName entityName,
            @NonNull AttributeAndRelationMapper<DataEntry, Optional<DataEntry>, DataEntry, Optional<DataEntry>> mapper,
            @NonNull AttributeMapper<Optional<DataEntry>, Optional<DataEntry>> auditMapper
    ) {
        var entity = application.getRequiredEntityByName(entityName);
        var relations = application.getRelationsForSourceEntity(entity);

        var inputMapper = AttributeAndRelationMapper.from(new RequestInputDataToDataEntryMapper());
        var queryEngineMapper = new OptionalFlatMapAdaptingMapper<>(AttributeAndRelationMapper.from(new DataEntryToQueryEngineMapper()));

        var combinedMapper = inputMapper
                .andThen(new OptionalFlatMapAdaptingMapper<>(mapper))
                .andThen(auditMapper)
                // Validate constraints
                .andThen(new OptionalFlatMapAdaptingMapper<>(
                        AttributeAndRelationMapper.from(
                                new AttributeValidationDataMapper(
                                        new RequiredAttributeConstraintValidator(),
                                        new AllowedValuesConstraintValidator()
                                ),
                                new RelationRequiredValidationDataMapper()
                        )
                ))
                .andThen(new OptionalFlatMapAdaptingMapper<>(
                        AttributeAndRelationMapper.from(
                                new ContentUploadAttributeMapper(contentStore),
                                (rel, value) -> Optional.of(value)
                        )
                ))
                .andThen(queryEngineMapper);

        return new RequestInputDataMapper(
                entity.getAttributes(),
                relations,
                combinedMapper,
                combinedMapper
        );

    }

    private ResponseOutputDataMapper createOutputDataMapper(
            @NonNull Application application,
            @NonNull EntityName entityName
    ) {
        var entity = application.getRequiredEntityByName(entityName);
        return new ResponseOutputDataMapper(
                entity.getAttributes(),
                new AttributeDataToDataEntryMapper()
        );
    }

    @Override
    public ResultSlice findAll(@NonNull Application application, @NonNull Entity entity,
            @NonNull Map<String, List<String>> params, @NonNull EncodedCursorPagination pagination,
            @NonNull PermissionPredicate permissionPredicate
    )
            throws InvalidThunkExpressionException {

        var sort = pagination.getSort();
        ThunkExpression<Boolean> filter = ThunkExpressionGenerator.from(application, entity, params);
        var fullFilter = LogicalOperation.conjunction(
                filter,
                permissionPredicate.predicate()
        );
        validateSortData(entity, sort);

        var offsetData = convertPaginationToOffset(pagination, entity.getName(), params);

        // Request one extra row, so we can see if it's present → there is a next page
        var page = new OffsetData(offsetData.getLimit() + 1, offsetData.getOffset());
        var result = queryEngine.findAll(application, entity, fullFilter, sort, page);
        var hasNext = result.getEntities().size() > offsetData.getLimit();

        PaginationControls controls = EncodedCursorSupport.makeControls(cursorCodec, pagination, entity.getName(),
                params, hasNext);

        // Get a total count of how many items match these params
        var count = calculateCount(() -> queryEngine.count(application, entity, fullFilter),
                offsetData, result.getEntities().size(), hasNext);

        var outputMapper = createOutputDataMapper(application, entity.getName());

        var entities = result.getEntities()
                .subList(0, Math.min(offsetData.getLimit(), result.getEntities().size()))
                .stream()
                .map(outputMapper::mapAttributes)
                .toList();

        return new ResultSlice(entities, controls, count);

    }

    private OffsetData convertPaginationToOffset(@NonNull EncodedCursorPagination encodedPagination, EntityName entityName, Map<String, List<String>> params) {
        var pagination = (PageBasedPagination) cursorCodec.decodeCursor(encodedPagination.getCursorContext(), entityName, params);
        return new OffsetData(pagination.getSize(), pagination.getPage() * pagination.getSize());
    }

    private void validateSortData(Entity entity, SortData sortData) {
        for (FieldSort field : sortData.getSortedFields()) {
            var name = field.getName();
            entity.getSortableFieldByName(name).orElseThrow(() ->
                    InvalidSortParameterException.invalidField(name.getValue(), entity.getName().getValue()));
        }
    }

    private ItemCount calculateCount(Supplier<ItemCount> countSupplier, OffsetData offsetData, long size, boolean hasNext) {
        var hasPrevious = offsetData.getOffset() > 0;

        if (!hasNext && !(hasPrevious && size == 0L)) {
            // If this is exactly the last page with results: we know the exact size, no need for counting
            return ItemCount.exact(offsetData.getOffset() + size);
        }

        var result = countSupplier.get();

        if (hasNext) {
            // There has to be a next page, adjust count to have at least one item on the next page
            return result.orMinimally(offsetData.getOffset() + offsetData.getLimit() + 1L);
        } else {
            // There is no next page and there are also no results on this page (otherwise we returned exact result),
            // adjust count to have at most the amount on the previous page
            return result.orMaximally(offsetData.getOffset());
        }
    }

    @Override
    public Optional<InternalEntityInstance> findById(
            @NonNull Application application,
            @NonNull EntityRequest entityRequest,
            @NonNull PermissionPredicate permissionPredicate
    ) {
        var outputMapper = createOutputDataMapper(application, entityRequest.getEntityName());
        return queryEngine.findById(application, entityRequest, permissionPredicate.predicate())
                .map(outputMapper::mapAttributes);
    }

    @Override
    public InternalEntityInstance create(
            @NonNull Application application,
            @NonNull EntityName entityName,
            @NonNull RequestInputData requestData,
            @NonNull PermissionPredicate permissionPredicate,
            @NonNull Optional<User> user
    ) throws QueryEngineException, InvalidPropertyDataException {
        var inputMapper = createInputDataMapper(
                application,
                entityName,
                // All missing fields are regarded as null
                FilterDataEntryMapper.missingAsNull()
                        // Validate that content attribute is not partially set
                        .andThen(new OptionalFlatMapAdaptingMapper<>(
                                AttributeAndRelationMapper.from(
                                        new AttributeValidationDataMapper(new ContentAttributeModificationValidator(null)),
                                        (rel, data) -> Optional.of(data)
                                )
                        ))
                , AuditAttributeMapper.forCreate(user.orElse(null))
        );

        var usageTrackingRequestData = new UsageTrackingRequestInputData(requestData);

        var exceptionCollector = new ValidationExceptionCollector<>(InvalidPropertyDataException.class);
        var attributes = exceptionCollector.use(() -> inputMapper.mapAttributes(usageTrackingRequestData));
        var relations = exceptionCollector.use(() -> inputMapper.mapRelations(usageTrackingRequestData));
        exceptionCollector.rethrow();

//        attributes.addAll(creationAuditFields(application, entityName));

        var unusedKeys = usageTrackingRequestData.getUnusedKeys();
        if(!unusedKeys.isEmpty()) {
            log.warn("Unused request keys: {}", unusedKeys);
        }

        var createData = EntityCreateData.builder()
                .entityName(entityName)
                .attributes(attributes)
                .relations(relations)
                .build();

        var outputMapper = createOutputDataMapper(application, entityName);

        return outputMapper.mapAttributes(queryEngine.create(application, createData, permissionPredicate.predicate()));
    }

    private List<AttributeData> creationAuditFields(@NonNull Application application, @NonNull EntityName entityName) {
        var entity = application.getRequiredEntityByName(entityName);
        return AuditHelper2.contributeAuditMetadata(entity);
//        return findAuditFieldsForFlags(entity.getAttributes(), CreatorFlag.class, CreatedDateFlag.class);
    }

//    private List<AttributeData> modificationAuditFields(@NonNull Application application, @NonNull EntityName entityName) {
//        var entity = application.getRequiredEntityByName(entityName);
//        return findAuditFieldsForFlags(entity.getAttributes(), ModifierFlag.class, ModifiedDateFlag.class);
//    }


    @Override
    public InternalEntityInstance update(@NonNull Application application,
            @NonNull EntityInstance existingEntity, @NonNull RequestInputData data,
            @NonNull PermissionPredicate permissionPredicate
    )
            throws QueryEngineException, InvalidPropertyDataException {
        var inputMapper = createInputDataMapper(
                application,
                existingEntity.getIdentity().getEntityName(),
                // All missing fields are regarded as null
                FilterDataEntryMapper.missingAsNull()
                        // Validate that content attribute is not partially set
                        .andThen(new OptionalFlatMapAdaptingMapper<>(
                                AttributeAndRelationMapper.from(
                                        new AttributeValidationDataMapper(new ContentAttributeModificationValidator(existingEntity)),
                                        (rel, d) -> Optional.of(d)
                                )
                        )),
                AuditAttributeMapper.forCreate(null)
        );

        var usageTrackingRequestData = new UsageTrackingRequestInputData(data);

        var entityData = new EntityData(existingEntity.getIdentity(),
                inputMapper.mapAttributes(usageTrackingRequestData));

        var unusedKeys = usageTrackingRequestData.getUnusedKeys();
        if(!unusedKeys.isEmpty()) {
            log.warn("Unused request keys: {}", unusedKeys);
        }

        var updateData = queryEngine.update(application, entityData, permissionPredicate.predicate());

        var outputMapper = createOutputDataMapper(application, existingEntity.getIdentity().getEntityName());
        return outputMapper.mapAttributes(updateData.getUpdated());
    }

    @Override
    public InternalEntityInstance updatePartial(@NonNull Application application,
            @NonNull EntityInstance existingEntity,
            @NonNull RequestInputData data,
            @NonNull PermissionPredicate permissionPredicate
    ) throws QueryEngineException, InvalidPropertyDataException {
        var inputMapper = createInputDataMapper(
                application,
                existingEntity.getIdentity().getEntityName(),
                // Missing fields are omitted, so they are not updated
                FilterDataEntryMapper.omitMissing()
                        // Validate that content attribute is not partially set
                        .andThen(new OptionalFlatMapAdaptingMapper<>(
                                AttributeAndRelationMapper.from(
                                        new AttributeValidationDataMapper(new ContentAttributeModificationValidator(existingEntity)),
                                        (rel, d) -> Optional.of(d)
                                )
                        )),
                AuditAttributeMapper.forCreate(null)
        );

        var usageTrackingRequestData = new UsageTrackingRequestInputData(data);

        var entityData = new EntityData(existingEntity.getIdentity(),
                inputMapper.mapAttributes(usageTrackingRequestData));

        var unusedKeys = usageTrackingRequestData.getUnusedKeys();
        if(!unusedKeys.isEmpty()) {
            log.warn("Unused request keys: {}", unusedKeys);
        }

        var updateData = queryEngine.update(application, entityData, permissionPredicate.predicate());

        var outputMapper = createOutputDataMapper(application, existingEntity.getIdentity().getEntityName());
        return outputMapper.mapAttributes(updateData.getUpdated());
    }

    @Override
    public InternalEntityInstance deleteEntity(@NonNull Application application, @NonNull EntityRequest entityRequest, @NonNull PermissionPredicate permissionPredicate)
            throws EntityIdNotFoundException {
        var outputMapper = createOutputDataMapper(application, entityRequest.getEntityName());

        var deleted =  queryEngine.delete(application, entityRequest, permissionPredicate.predicate())
                .orElseThrow(() -> new EntityIdNotFoundException(entityRequest));

        return outputMapper.mapAttributes(deleted);
    }

    @Override
    public boolean hasRelationTarget(@NonNull Application application, @NonNull RelationRequest relationRequest,
            @NonNull EntityId targetId, @NonNull PermissionPredicate permissionPredicate) throws QueryEngineException {
        return queryEngine.isLinked(application, relationRequest, targetId, permissionPredicate.predicate());
    }

    @Override
    public Optional<RelationTarget> findRelationTarget(@NonNull Application application, @NonNull RelationRequest relationRequest,
             @NonNull PermissionPredicate permissionPredicate) throws QueryEngineException {
        var relation = application.getRequiredRelationForEntity(relationRequest.getEntityName(), relationRequest.getRelationName());
        return queryEngine.findTarget(application, relationRequest, permissionPredicate.predicate())
                .map(entityIdAndVersion -> new RelationTarget(
                        RelationIdentity.forRelation(relationRequest.getEntityName(), relationRequest.getEntityId(), relationRequest.getRelationName())
                                .withVersion(entityIdAndVersion.version()),
                        EntityIdentity.forEntity(relation.getTargetEndPoint().getEntity(), entityIdAndVersion.entityId())
                ));
    }

    @Override
    public void setRelation(@NonNull Application application, @NonNull RelationRequest relationRequest, @NonNull EntityId targetId, @NonNull PermissionPredicate permissionPredicate)
            throws QueryEngineException {
        queryEngine.setLink(application, relationRequest, targetId, permissionPredicate.predicate());
    }

    @Override
    public void deleteRelation(@NonNull Application application, @NonNull RelationRequest relationRequest, @NonNull PermissionPredicate permissionPredicate)
            throws QueryEngineException {
        queryEngine.unsetLink(application, relationRequest, permissionPredicate.predicate());
    }

    @Override
    public void addRelationItems(@NonNull Application application, @NonNull RelationRequest relation, @NonNull Set<EntityId> targetIds, @NonNull PermissionPredicate permissionPredicate)
            throws QueryEngineException {
        queryEngine.addLinks(
                application,
                relation,
                targetIds,
                permissionPredicate.predicate()
        );
    }

    @Override
    public void removeRelationItems(@NonNull Application application, @NonNull RelationRequest relation, @NonNull Set<EntityId> targetIds, @NonNull PermissionPredicate permissionPredicate)
            throws QueryEngineException {
        queryEngine.removeLinks(
                application,
                relation,
                targetIds,
                permissionPredicate.predicate()
        );
    }

    @RequiredArgsConstructor
    public static class ResponseOutputDataMapper {
        private final List<Attribute> attributes;
        private final AttributeMapper<Optional<AttributeData>, PlainDataEntry> attributeMapper;

        public InternalEntityInstance mapAttributes(@NonNull EntityData entityData) {
            var data = LinkedHashMap.<String, PlainDataEntry>newLinkedHashMap(attributes.size());
            for (var attribute : attributes) {
                try {
                    data.put(
                            attribute.getName().getValue(),
                            attributeMapper.mapAttribute(attribute, entityData.getAttributeByName(attribute.getName()))
                    );
                } catch (InvalidPropertyDataException e) {
                    throw new IllegalStateException(
                            "Invalid data from storage for %s (attribute %s)".formatted(
                                    entityData.getIdentity(),
                                    attribute.getName()
                            ),
                            e
                    );
                }
            }

            return new InternalEntityInstance(
                    entityData.getIdentity(),
                    Collections.unmodifiableSequencedMap(data),
                    entityData.getAttributes()
            );
        }
    }
}
