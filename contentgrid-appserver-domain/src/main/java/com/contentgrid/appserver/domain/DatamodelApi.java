package com.contentgrid.appserver.domain;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.exceptions.EntityNotFoundException;
import com.contentgrid.appserver.query.engine.api.data.EntityData;
import com.contentgrid.appserver.query.engine.api.data.EntityId;
import com.contentgrid.appserver.query.engine.api.data.PageData;
import com.contentgrid.appserver.query.engine.api.data.RelationData;
import com.contentgrid.appserver.query.engine.api.data.SliceData;
import com.contentgrid.appserver.query.engine.api.exception.InvalidThunkExpressionException;
import com.contentgrid.appserver.query.engine.api.exception.QueryEngineException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.NonNull;

/**
 * The Datamodel API is the primary entrypoint for interacting with the data that lives in the application.
 * Primarily it relays operations to the QueryEngine, with arguments slightly transformed; as well as to the Content
 * service.
 */
public interface DatamodelApi {

    /**
     * Finds all entities that match the given params.
     *
     * @param application the application context
     * @param entity the entity type to query
     * @param params the parameters to filter entities
     * @param pageData pagination parameters for the query
     * @return a slice of entities matching the criteria
     * @throws QueryEngineException if an error occurs during the query operation
     */
    SliceData findAll(@NonNull Application application, @NonNull Entity entity, @NonNull Map<String, String> params,
            PageData pageData) throws EntityNotFoundException, InvalidThunkExpressionException;

    /**
     * Finds an entity that matches the given id.
     *
     * @param application the application context
     * @param entity the entity type to query
     * @param id the primary key value of the entity to find
     * @return an Optional containing the entity data if found, empty otherwise
     */
    Optional<EntityData> findById(@NonNull Application application, @NonNull Entity entity, @NonNull EntityId id)
            throws EntityNotFoundException;

    /**
     * Creates an entity with the given data and relations.
     *
     * @param application the application context
     * @param data the data for the new entity
     * @param relations the relationships to establish for the new entity
     * @return the value of the primary key for the newly created entity
     * @throws QueryEngineException if an error occurs during the create operation
     */
    EntityId create(@NonNull Application application, @NonNull EntityData data, @NonNull List<RelationData> relations)
            throws QueryEngineException;

    /**
     * Updates an entity with the given data.
     *
     * @param application the application context
     * @param id the id of the entity to update
     * @param data the updated data for the entity
     * @throws QueryEngineException if an error occurs during the update operation
     */
    void update(@NonNull Application application, @NonNull EntityId id, @NonNull EntityData data)
            throws QueryEngineException;
}
