package com.contentgrid.appserver.rest;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.query.engine.api.QueryEngine;
import com.contentgrid.appserver.query.engine.api.data.EntityData;
import com.contentgrid.appserver.query.engine.api.data.EntityId;
import com.contentgrid.appserver.query.engine.api.data.PageData;
import com.contentgrid.appserver.query.engine.api.data.RelationData;
import com.contentgrid.appserver.query.engine.api.data.SliceData;
import com.contentgrid.appserver.query.engine.api.data.SliceData.PageInfo;
import com.contentgrid.appserver.query.engine.api.data.XToManyRelationData;
import com.contentgrid.appserver.query.engine.api.exception.QueryEngineException;
import com.contentgrid.thunx.predicates.model.ThunkExpression;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.NonNull;


public class DummyQueryEngine implements QueryEngine {
    protected final Map<String, List<EntityData>> entityInstances = new ConcurrentHashMap<>();

    @Override
    public SliceData findAll(@NonNull Application application, @NonNull Entity entity,
            @NonNull ThunkExpression<Boolean> expression, PageData pageData)
            throws QueryEngineException {
        var entities = entityInstances.get(entity.getName().getValue());
        return SliceData.builder()
                .entities(entities)
                .pageInfo(PageInfo.builder()
                        .start(0L)
                        .size((long) entities.size())
                        .exactCount((long) entities.size())
                        .estimatedCount((long) entities.size())
                        .build())
                .build();
    }

    @Override
    public Optional<EntityData> findById(@NonNull Application application, @NonNull Entity entity, @NonNull EntityId id) {
        return entityInstances.getOrDefault(entity.getName().getValue(), List.of()).stream()
                .filter(e -> e.getId().equals(id))
                .findAny();
    }

    @Override
    public EntityId create(@NonNull Application application, @NonNull EntityData data,
            @NonNull List<RelationData> relations) throws QueryEngineException {
        var instances = entityInstances.getOrDefault(data.getName().getValue(), new ArrayList<>());
        var entity = application.getEntityByName(data.getName());
        if (entity.isEmpty()) {
            return null;
        }
        var dataWithId = EntityData.builder()
                .name(data.getName())
                .id(EntityId.of(UUID.randomUUID()))
                .attributes(data.getAttributes())
                .build();
        instances.add(dataWithId);
        entityInstances.put(data.getName().getValue(), instances);
        return dataWithId.getId();
    }

    @Override
    public void update(@NonNull Application application, @NonNull EntityData data) throws QueryEngineException {

    }

    @Override
    public void delete(@NonNull Application application, @NonNull Entity entity, @NonNull EntityId id)
            throws QueryEngineException {

    }

    @Override
    public void deleteAll(@NonNull Application application, @NonNull Entity entity) throws QueryEngineException {

    }

    @Override
    public boolean isLinked(@NonNull Application application, @NonNull Relation relation, @NonNull EntityId sourceId,
            @NonNull EntityId targetId) throws QueryEngineException {
        return false;
    }

    @Override
    public RelationData findLink(@NonNull Application application, @NonNull Relation relation, @NonNull EntityId id)
            throws QueryEngineException {
        return null;
    }

    @Override
    public void setLink(@NonNull Application application, @NonNull RelationData data, @NonNull EntityId id)
            throws QueryEngineException {

    }

    @Override
    public void unsetLink(@NonNull Application application, @NonNull Relation relation, @NonNull EntityId id)
            throws QueryEngineException {

    }

    @Override
    public void addLinks(@NonNull Application application, @NonNull XToManyRelationData data, @NonNull EntityId id)
            throws QueryEngineException {

    }

    @Override
    public void removeLinks(@NonNull Application application, @NonNull XToManyRelationData data, @NonNull EntityId id)
            throws QueryEngineException {

    }
}
