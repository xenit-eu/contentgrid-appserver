package com.contentgrid.appserver.domain;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.exceptions.EntityNotFoundException;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.content.api.ContentStore;
import com.contentgrid.appserver.domain.data.DataEntry;
import com.contentgrid.appserver.domain.data.RequestInputData;
import com.contentgrid.appserver.domain.data.UsageTrackingRequestInputData;
import com.contentgrid.appserver.domain.data.mapper.AttributeAndRelationMapper;
import com.contentgrid.appserver.domain.data.mapper.ContentUploadAttributeMapper;
import com.contentgrid.appserver.domain.data.mapper.DataEntryToQueryEngineMapper;
import com.contentgrid.appserver.domain.data.mapper.OptionalFlatMapAdaptingMapper;
import com.contentgrid.appserver.domain.data.mapper.RequestInputDataMapper;
import com.contentgrid.appserver.domain.data.mapper.RequestInputDataToDataEntryMapper;
import com.contentgrid.appserver.domain.data.mapper.FilterDataEntryMapper;
import com.contentgrid.appserver.domain.data.InvalidPropertyDataException;
import com.contentgrid.appserver.domain.data.validation.AttributeValidationDataMapper;
import com.contentgrid.appserver.domain.data.validation.ContentAttributeModificationValidator;
import com.contentgrid.appserver.domain.data.validation.RequiredAttributeConstraintValidator;
import com.contentgrid.appserver.domain.data.validation.RelationRequiredValidationDataMapper;
import com.contentgrid.appserver.domain.data.validation.ValidationExceptionCollector;
import com.contentgrid.appserver.domain.values.EntityIdentity;
import com.contentgrid.appserver.exception.InvalidSortParameterException;
import com.contentgrid.appserver.query.engine.api.QueryEngine;
import com.contentgrid.appserver.query.engine.api.data.EntityCreateData;
import com.contentgrid.appserver.query.engine.api.data.EntityData;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.query.engine.api.data.PageData;
import com.contentgrid.appserver.query.engine.api.data.SliceData;
import com.contentgrid.appserver.query.engine.api.data.SortData;
import com.contentgrid.appserver.query.engine.api.data.SortData.FieldSort;
import com.contentgrid.appserver.query.engine.api.exception.InvalidThunkExpressionException;
import com.contentgrid.appserver.query.engine.api.exception.QueryEngineException;
import com.contentgrid.thunx.predicates.model.ThunkExpression;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public class DatamodelApiImpl implements DatamodelApi {
    private final QueryEngine queryEngine;
    private final ContentStore contentStore;

    private RequestInputDataMapper createInputDataMapper(
            @NonNull Application application,
            @NonNull EntityName entityName,
            @NonNull AttributeAndRelationMapper<DataEntry, Optional<DataEntry>, DataEntry, Optional<DataEntry>> mapper
    ) {
        var entity = application.getRequiredEntityByName(entityName);
        var relations = application.getRelationsForSourceEntity(entity);

        var inputMapper = AttributeAndRelationMapper.from(new RequestInputDataToDataEntryMapper());
        var queryEngineMapper = new OptionalFlatMapAdaptingMapper<>(AttributeAndRelationMapper.from(new DataEntryToQueryEngineMapper()));

        var combinedMapper = inputMapper.andThen(new OptionalFlatMapAdaptingMapper<>(mapper))
                // Validate that required attributes and relations are present
                .andThen(new OptionalFlatMapAdaptingMapper<>(
                        AttributeAndRelationMapper.from(
                                new AttributeValidationDataMapper(new RequiredAttributeConstraintValidator()),
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

    @Override
    public SliceData findAll(@NonNull Application application,
            @NonNull Entity entity,
            @NonNull Map<String, String> params, SortData sort, PageData pageData)
            throws EntityNotFoundException, InvalidThunkExpressionException {
        ThunkExpression<Boolean> filter = ThunkExpressionGenerator.from(application, entity, params);
        validateSortData(entity, sort);
        return queryEngine.findAll(application, entity, filter, sort, pageData);
    }

    private void validateSortData(Entity entity, SortData sortData) {
        for (FieldSort field : sortData.getSortedFields()) {
            var name = field.getName();
            entity.getSortableFieldByName(name).orElseThrow(() ->
                    InvalidSortParameterException.invalidField(name.getValue(), entity.getName().getValue()));
        }
    }

    @Override
    public Optional<EntityData> findById(
            @NonNull Application application,
            @NonNull EntityIdentity identity
    ) throws EntityNotFoundException {
        return queryEngine.findById(application, identity);
    }

    @Override
    public EntityData create(
            @NonNull Application application,
            @NonNull EntityName entityName,
            @NonNull RequestInputData requestData
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
        );

        var usageTrackingRequestData = new UsageTrackingRequestInputData(requestData);

        var exceptionCollector = new ValidationExceptionCollector<>(InvalidPropertyDataException.class);
        var attributes = exceptionCollector.use(() -> inputMapper.mapAttributes(usageTrackingRequestData));
        var relations = exceptionCollector.use(() -> inputMapper.mapRelations(usageTrackingRequestData));
        exceptionCollector.rethrow();

        var unusedKeys = usageTrackingRequestData.getUnusedKeys();
        if(!unusedKeys.isEmpty()) {
            log.warn("Unused request keys: {}", unusedKeys);
        }

        var createData = EntityCreateData.builder()
                .entityName(entityName)
                .attributes(attributes)
                .relations(relations)
                .build();

        return queryEngine.create(application, createData);
    }

    @Override
    public EntityData update(@NonNull Application application,
            @NonNull EntityIdentity identity, @NonNull RequestInputData data)
            throws QueryEngineException, InvalidPropertyDataException {
        var existingEntity = queryEngine.findById(application, identity).orElse(null);
        var inputMapper = createInputDataMapper(
                application,
                identity.getEntityName(),
                // All missing fields are regarded as null
                FilterDataEntryMapper.missingAsNull()
                        // Validate that content attribute is not partially set
                        .andThen(new OptionalFlatMapAdaptingMapper<>(
                                AttributeAndRelationMapper.from(
                                        new AttributeValidationDataMapper(new ContentAttributeModificationValidator(existingEntity)),
                                        (rel, d) -> Optional.of(d)
                                )
                        ))
        );

        var usageTrackingRequestData = new UsageTrackingRequestInputData(data);

        var entityData = new EntityData(identity, inputMapper.mapAttributes(usageTrackingRequestData));

        var unusedKeys = usageTrackingRequestData.getUnusedKeys();
        if(!unusedKeys.isEmpty()) {
            log.warn("Unused request keys: {}", unusedKeys);
        }

        var updateData = queryEngine.update(application, entityData);

        return updateData.getUpdated();
    }

    @Override
    public EntityData updatePartial(@NonNull Application application,
            @NonNull EntityIdentity identity, @NonNull RequestInputData data)
            throws QueryEngineException, InvalidPropertyDataException {
        var existingEntity = queryEngine.findById(application, identity).orElse(null);
        var inputMapper = createInputDataMapper(
                application,
                identity.getEntityName(),
                // Missing fields are omitted, so they are not updated
                FilterDataEntryMapper.omitMissing()
                        // Validate that content attribute is not partially set
                        .andThen(new OptionalFlatMapAdaptingMapper<>(
                                AttributeAndRelationMapper.from(
                                        new AttributeValidationDataMapper(new ContentAttributeModificationValidator(existingEntity)),
                                        (rel, d) -> Optional.of(d)
                                )
                        ))
        );

        var usageTrackingRequestData = new UsageTrackingRequestInputData(data);

        var entityData = new EntityData(identity, inputMapper.mapAttributes(usageTrackingRequestData));

        var unusedKeys = usageTrackingRequestData.getUnusedKeys();
        if(!unusedKeys.isEmpty()) {
            log.warn("Unused request keys: {}", unusedKeys);
        }

        var updateData = queryEngine.update(application, entityData);

        return updateData.getUpdated();
    }

    @Override
    public boolean hasRelationTarget(@NonNull Application application, @NonNull Relation relation, @NonNull EntityId sourceId,
            @NonNull EntityId targetId) throws QueryEngineException {
        return queryEngine.isLinked(application, relation, sourceId, targetId);
    }

    @Override
    public Optional<EntityId> findRelationTarget(@NonNull Application application, @NonNull Relation relation,
            @NonNull EntityId id) throws QueryEngineException {
        return queryEngine.findTarget(application, relation, id);
    }

    @Override
    public void setRelation(@NonNull Application application, @NonNull Relation relation, @NonNull EntityId id, @NonNull EntityId targetId)
            throws QueryEngineException {
        queryEngine.setLink(application, relation, id, targetId);
    }

    @Override
    public void deleteRelation(@NonNull Application application, @NonNull Relation relation, @NonNull EntityId id)
            throws QueryEngineException {
        queryEngine.unsetLink(application, relation, id);
    }

    @Override
    public void addRelationItems(@NonNull Application application, @NonNull Relation relation, @NonNull EntityId id, @NonNull Set<EntityId> targetIds)
            throws QueryEngineException {
        queryEngine.addLinks(application, relation, id, targetIds);
    }

    @Override
    public void removeRelationItems(@NonNull Application application, @NonNull Relation relation, @NonNull EntityId id, @NonNull Set<EntityId> targetIds)
            throws QueryEngineException {
        queryEngine.removeLinks(application, relation, id, targetIds);
    }
}
