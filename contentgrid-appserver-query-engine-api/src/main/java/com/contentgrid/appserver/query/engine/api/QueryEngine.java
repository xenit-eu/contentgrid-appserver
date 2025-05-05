package com.contentgrid.appserver.query.engine.api;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.query.engine.api.data.EntityData;
import com.contentgrid.appserver.query.engine.api.data.PageData;
import com.contentgrid.appserver.query.engine.api.data.RelationData;
import com.contentgrid.appserver.query.engine.api.data.SliceData;
import com.contentgrid.appserver.query.engine.api.exception.QueryEngineException;
import com.contentgrid.thunx.predicates.model.ThunkExpression;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.NonNull;

public interface QueryEngine {

    /**
     * Find all entities that match the given expression.
     */
    SliceData findAll(@NonNull Application application, @NonNull Entity entity, @NonNull ThunkExpression<Boolean> expression,
            PageData pageData) throws QueryEngineException;

    /**
     * Find an entity that matches the given id.
     */
    Optional<EntityData> findById(@NonNull Application application, @NonNull Entity entity, @NonNull Object id);

    /**
     * Create an entity with the given data and relations, and returns the value for its primary key.
     */
    Object create(@NonNull Application application, @NonNull EntityData data, @NonNull List<RelationData> relations) throws QueryEngineException;

    /**
     * Update an entity with the given data.
     */
    void update(@NonNull Application application, @NonNull EntityData data) throws QueryEngineException;

    /**
     * Delete the entity that matches the given id.
     */
    void delete(@NonNull Application application, @NonNull Entity entity, @NonNull Object id) throws QueryEngineException;

    /**
     * Delete all entities in the table.
     */
    void deleteAll(@NonNull Application application, @NonNull Entity entity) throws QueryEngineException;

    /**
     * Overwrite the link(s) from the entity with the given id with the link(s) provided in data.
     */
    void setLink(@NonNull Application application, @NonNull Entity entity, @NonNull Object id, @NonNull RelationData data) throws QueryEngineException;

    /**
     * Remove all link(s) from the entity with the given id.
     */
    void unsetLink(@NonNull Application application, @NonNull Entity entity, @NonNull Object id, @NonNull Relation relation) throws QueryEngineException;

    /**
     * Add the link(s) provided in data to the entity with the given id.
     */
    void addLink(@NonNull Application application, @NonNull Entity entity, @NonNull Object id, @NonNull RelationData data) throws QueryEngineException;

    /**
     * Remove the link(s) provided in data from the entity with the given id.
     */
    void removeLink(@NonNull Application application, @NonNull Entity entity, @NonNull Object id, @NonNull RelationData data) throws QueryEngineException;

}
