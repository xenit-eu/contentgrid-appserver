package com.contentgrid.appserver.query.engine.api;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.query.engine.api.data.EntityCreateData;
import com.contentgrid.appserver.query.engine.api.data.EntityData;
import com.contentgrid.appserver.query.engine.api.data.EntityId;
import com.contentgrid.appserver.query.engine.api.data.PageData;
import com.contentgrid.appserver.query.engine.api.data.SliceData;
import com.contentgrid.appserver.query.engine.api.data.SortData;
import com.contentgrid.appserver.query.engine.api.exception.QueryEngineException;
import com.contentgrid.thunx.predicates.model.ThunkExpression;
import java.util.Optional;
import java.util.Set;
import lombok.NonNull;

/**
 * The QueryEngine interface defines operations for querying and manipulating entities
 * and their relationships within a ContentGrid application.
 * <p>
 * This interface provides methods for:
 * <ul>
 *   <li>Finding and retrieving entities</li>
 *   <li>Creating, updating, and deleting entities</li>
 *   <li>Managing relationships between entities</li>
 * </ul>
 * <p>
 * Implementations of this interface handle the underlying database operations
 * and data conversions necessary to support these operations.
 */
public interface QueryEngine {

    /**
     * Finds all entities that match the given expression.
     *
     * @param application the application context
     * @param entity the entity type to query
     * @param expression the predicate expression to filter entities
     * @param pageData pagination parameters for the query
     * @return a slice of entities matching the criteria
     * @throws QueryEngineException if an error occurs during the query operation
     */
    SliceData findAll(@NonNull Application application, @NonNull Entity entity, @NonNull ThunkExpression<Boolean> expression,
            SortData sortData, PageData pageData) throws QueryEngineException;

    /**
     * Finds an entity that matches the given id.
     *
     * @param application the application context
     * @param entity the entity type to query
     * @param id the primary key value of the entity to find
     * @return an Optional containing the entity data if found, empty otherwise
     */
    Optional<EntityData> findById(@NonNull Application application, @NonNull Entity entity, @NonNull EntityId id);

    /**
     * Creates an entity with the given data and relations.
     *
     * @param application the application context
     * @param data the data for the new entity
     * @return the value of the primary key for the newly created entity
     * @throws QueryEngineException if an error occurs during the create operation
     */
    EntityId create(@NonNull Application application, @NonNull EntityCreateData data) throws QueryEngineException;

    /**
     * Updates an entity with the given data.
     *
     * @param application the application context
     * @param data the updated data for the entity, must include the entity's id
     * @throws QueryEngineException if an error occurs during the update operation
     */
    void update(@NonNull Application application, @NonNull EntityData data) throws QueryEngineException;

    /**
     * Deletes the entity that matches the given id.
     *
     * @param application the application context
     * @param entity the entity type to delete from
     * @param id the primary key value of the entity to delete
     * @throws QueryEngineException if an error occurs during the delete operation
     */
    void delete(@NonNull Application application, @NonNull Entity entity, @NonNull EntityId id) throws QueryEngineException;

    /**
     * Deletes all entities of the specified type.
     *
     * @param application the application context
     * @param entity the entity type for which all records should be deleted
     * @throws QueryEngineException if an error occurs during the delete operation
     */
    void deleteAll(@NonNull Application application, @NonNull Entity entity) throws QueryEngineException;

    /**
     * Determines whether the given source and target entities are linked by the specified relation.
     *
     * @param application the application context
     * @param relation the relation type to check
     * @param sourceId the primary key of the source entity
     * @param targetId the primary key of the target entity
     * @return true if the entities are linked, false otherwise
     * @throws QueryEngineException if an error occurs during the check operation
     */
    boolean isLinked(@NonNull Application application, @NonNull Relation relation, @NonNull EntityId sourceId, @NonNull EntityId targetId) throws QueryEngineException;

    /**
     * Returns the target entity id that is linked with the entity having the given id.
     * This operation can only be used for many-to-one or one-to-one relationships.
     *
     * @param application the application context
     * @param relation the *-to-one relation to query
     * @param id the primary key of the source entity
     * @return optional with the linked target entity, empty otherwise
     * @throws QueryEngineException if an error occurs during the query operation
     */
    Optional<EntityId> findTarget(@NonNull Application application, @NonNull Relation relation, @NonNull EntityId id) throws QueryEngineException;

    /**
     * Link the target entity id provided in data with the given source id.
     * This operation can only be used for many-to-one or one-to-one relationships.
     *
     * @param application the application context
     * @param relation the *-to-one relation for which to set the link
     * @param id the primary key of the source entity
     * @param targetId the primary key of the target entity
     * @throws QueryEngineException if an error occurs during the set operation
     */
    void setLink(@NonNull Application application, @NonNull Relation relation, @NonNull EntityId id, @NonNull EntityId targetId) throws QueryEngineException;

    /**
     * Removes all links from the entity with the given id for the specified relation.
     *
     * @param application the application context
     * @param relation the relation type for which to remove links
     * @param id the primary key of the source entity
     * @throws QueryEngineException if an error occurs during the unset operation
     */
    void unsetLink(@NonNull Application application, @NonNull Relation relation, @NonNull EntityId id) throws QueryEngineException;

    /**
     * Adds the links provided in data to the entity with the given id.
     * This operation can only be used for many-to-many or one-to-many relationships.
     *
     * @param application the application context
     * @param relation the *-to-many relation to add links to
     * @param id the primary key of the source entity
     * @param targetIds the primary keys of the target entities
     * @throws QueryEngineException if an error occurs during the add operation
     */
    void addLinks(@NonNull Application application, @NonNull Relation relation, @NonNull EntityId id, @NonNull Set<EntityId> targetIds) throws QueryEngineException;

    /**
     * Removes the links provided in data from the entity with the given id.
     * This operation can only be used for many-to-many or one-to-many relationships.
     *
     * @param application the application context
     * @param relation the *-to-many relation to remove links from
     * @param id the primary key of the source entity
     * @param targetIds the primary keys of the target entities
     * @throws QueryEngineException if an error occurs during the remove operation
     */
    void removeLinks(@NonNull Application application, @NonNull Relation relation, @NonNull EntityId id, @NonNull Set<EntityId> targetIds) throws QueryEngineException;

}
