package com.contentgrid.appserver.domain;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.exceptions.EntityNotFoundException;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.domain.data.DataEntry;
import com.contentgrid.appserver.domain.data.RequestInputData;
import com.contentgrid.appserver.domain.data.mapper.AttributeAndRelationMapper;
import com.contentgrid.appserver.domain.data.mapper.DataEntryToQueryEngineMapper;
import com.contentgrid.appserver.domain.data.mapper.OptionalFlatMapAdaptingMapper;
import com.contentgrid.appserver.domain.data.mapper.RequestInputDataMapper;
import com.contentgrid.appserver.domain.data.mapper.RequestInputDataToDataEntryMapper;
import com.contentgrid.appserver.domain.data.mapper.TransformingDataEntryMapper;
import com.contentgrid.appserver.domain.data.transformers.FilterDataEntryTransformer;
import com.contentgrid.appserver.domain.data.transformers.InvalidPropertyDataException;
import com.contentgrid.appserver.exception.InvalidSortParameterException;
import com.contentgrid.appserver.query.engine.api.QueryEngine;
import com.contentgrid.appserver.query.engine.api.data.EntityCreateData;
import com.contentgrid.appserver.query.engine.api.data.EntityData;
import com.contentgrid.appserver.query.engine.api.data.EntityId;
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

@RequiredArgsConstructor
public class DatamodelApiImpl implements DatamodelApi {
    private final QueryEngine queryEngine;

    private RequestInputDataMapper createInputDataMapper(
            @NonNull Application application,
            @NonNull EntityName entityName,
            @NonNull AttributeAndRelationMapper<DataEntry, Optional<DataEntry>, DataEntry, Optional<DataEntry>> mapper
    ) {
        var entity = application.getRequiredEntityByName(entityName);
        var relations = application.getRelationsForSourceEntity(entity);

        var inputMapper = AttributeAndRelationMapper.from(new RequestInputDataToDataEntryMapper());
        var queryEngineMapper = new OptionalFlatMapAdaptingMapper<>(AttributeAndRelationMapper.from(new DataEntryToQueryEngineMapper()));

        var combinedMapper = inputMapper.andThen(mapper)
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
    public Optional<EntityData> findById(@NonNull Application application,
            @NonNull Entity entity, @NonNull EntityId id)
            throws EntityNotFoundException {
        return queryEngine.findById(application, entity, id);
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
                new TransformingDataEntryMapper<>(FilterDataEntryTransformer.missingAsNull())
        );

        var createData = EntityCreateData.builder()
                .entityName(entityName)
                .attributes(inputMapper.mapAttributes(requestData))
                .relations(inputMapper.mapRelations(requestData))
                .build();

        return queryEngine.create(application, createData);
    }

    @Override
    public EntityData update(@NonNull Application application,
            @NonNull EntityName entityName, @NonNull EntityId id, @NonNull RequestInputData data)
            throws QueryEngineException, InvalidPropertyDataException {
        var inputMapper = createInputDataMapper(
                application,
                entityName,
                // All missing fields are regarded as null
                new TransformingDataEntryMapper<>(FilterDataEntryTransformer.missingAsNull())
        );

        var entityData = EntityData.builder()
                .name(entityName)
                .id(id)
                .attributes(inputMapper.mapAttributes(data))
                .build();

        var updateData = queryEngine.update(application, entityData);

        return updateData.getUpdated();
    }

    @Override
    public EntityData updatePartial(@NonNull Application application,
            @NonNull EntityName entityName, @NonNull EntityId id, @NonNull RequestInputData data)
            throws QueryEngineException, InvalidPropertyDataException {
        var inputMapper = createInputDataMapper(
                application,
                entityName,
                // Missing fields are omitted, so they are not update
                new TransformingDataEntryMapper<>(FilterDataEntryTransformer.omitMissing())
        );

        var entityData = EntityData.builder()
                .name(entityName)
                .id(id)
                .attributes(inputMapper.mapAttributes(data))
                .build();

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
