package com.contentgrid.appserver.query.engine.api;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.domain.values.EntityRequest;
import com.contentgrid.appserver.domain.values.ItemCount;
import com.contentgrid.appserver.query.engine.api.data.EntityCreateData;
import com.contentgrid.appserver.query.engine.api.data.EntityData;
import com.contentgrid.appserver.query.engine.api.data.QueryPageData;
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
     * @param sortData sorting parameters for the query
     * @param page pagination parameters for the query
     * @return a slice of entities matching the criteria
     * @throws QueryEngineException if an error occurs during the query operation
     */
    SliceData findAll(@NonNull Application application, @NonNull Entity entity, @NonNull ThunkExpression<Boolean> expression,
            SortData sortData, @NonNull QueryPageData page) throws QueryEngineException;

    /**
     * Finds an entity that matches the requested identity
     *
     * @param application the application context
     * @param entityRequest the identity of the entity to query
     * @param permitReadPredicate predicate that has to pass for the entity to be allowed to be read
     * @return an Optional containing the entity data if found, empty otherwise
     */
    Optional<EntityData> findById(@NonNull Application application, @NonNull EntityRequest entityRequest,
            @NonNull ThunkExpression<Boolean> permitReadPredicate)
            throws QueryEngineException;

    /**
     * Creates an entity with the given data and relations.
     *
     * @param application the application context
     * @param data the data for the new entity
     * @param permitCreatePredicate predicate that has to pass for the entity to be allowed to be created
     * @return The entity data that was inserted
     * @throws QueryEngineException if an error occurs during the create operation
     */
    EntityData create(@NonNull Application application, @NonNull EntityCreateData data,
            @NonNull ThunkExpression<Boolean> permitCreatePredicate) throws QueryEngineException;

    /**
     * Updates an entity with the given data.
     *
     * @param application the application context
     * @param data the updated data for the entity, must include the entity's id
     * @param permitUpdatePredicate predicate that has to pass for the entity to be allowed to be updated
     * @return Result of the update, including old and new entity data objects
     * @throws QueryEngineException if an error occurs during the update operation
     */
    UpdateResult update(@NonNull Application application, @NonNull EntityData data,
            @NonNull ThunkExpression<Boolean> permitUpdatePredicate) throws QueryEngineException;

    /**
     * Deletes the entity that matches the given identity
     *
     * @param application the application context
     * @param entityRequest the identity of the entity to delete
     * @param permitDeletePredicate predicate that has to pass for the entity to be allowed to be updated
     * @return The entity data that was deleted, if any was deleted
     * @throws QueryEngineException if an error occurs during the delete operation
     */
    Optional<EntityData> delete(@NonNull Application application, @NonNull EntityRequest entityRequest,
            @NonNull ThunkExpression<Boolean> permitDeletePredicate)
            throws QueryEngineException;

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
     * @param permitReadPredicate predicate that has to pass for the relation to be allowed to be read
     * @return true if the entities are linked, false otherwise
     * @throws QueryEngineException if an error occurs during the check operation
     */
    boolean isLinked(@NonNull Application application, @NonNull Relation relation, @NonNull EntityId sourceId, @NonNull EntityId targetId, @NonNull ThunkExpression<Boolean> permitReadPredicate) throws QueryEngineException;

    /**
     * Returns the target entity id that is linked with the entity having the given id.
     * This operation can only be used for many-to-one or one-to-one relationships.
     *
     * @param application the application context
     * @param relation the *-to-one relation to query
     * @param id the primary key of the source entity
     * @param permitReadPredicate predicate that has to pass for the relation to be allowed to be read
     * @return optional with the linked target entity, empty otherwise
     * @throws QueryEngineException if an error occurs during the query operation
     */
    Optional<EntityId> findTarget(@NonNull Application application, @NonNull Relation relation, @NonNull EntityId id, @NonNull ThunkExpression<Boolean> permitReadPredicate) throws QueryEngineException;

    /**
     * Link the target entity id provided in data with the given source id.
     * This operation can only be used for many-to-one or one-to-one relationships.
     *
     * @param application the application context
     * @param relation the *-to-one relation for which to set the link
     * @param id the primary key of the source entity
     * @param targetId the primary key of the target entity
     * @param permitUpdatePredicate predicate that has to pass for the relation to be allowed to be updated
     * @throws QueryEngineException if an error occurs during the set operation
     */
    void setLink(@NonNull Application application, @NonNull Relation relation, @NonNull EntityId id, @NonNull EntityId targetId, @NonNull ThunkExpression<Boolean> permitUpdatePredicate) throws QueryEngineException;

    /**
     * Removes all links from the entity with the given id for the specified relation.
     *
     * @param application the application context
     * @param relation the relation type for which to remove links
     * @param id the primary key of the source entity
     * @param permitUpdatePredicate predicate that has to pass for the relation to be allowed to be updated
     * @throws QueryEngineException if an error occurs during the unset operation
     */
    void unsetLink(@NonNull Application application, @NonNull Relation relation, @NonNull EntityId id, @NonNull ThunkExpression<Boolean> permitUpdatePredicate) throws QueryEngineException;

    /**
     * Adds the links provided in data to the entity with the given id.
     * This operation can only be used for many-to-many or one-to-many relationships.
     *
     * @param application the application context
     * @param relation the *-to-many relation to add links to
     * @param id the primary key of the source entity
     * @param targetIds the primary keys of the target entities
     * @param permitUpdatePredicate predicate that has to pass for the relation to be allowed to be updated
     * @throws QueryEngineException if an error occurs during the add operation
     */
    void addLinks(@NonNull Application application, @NonNull Relation relation, @NonNull EntityId id, @NonNull Set<EntityId> targetIds, @NonNull ThunkExpression<Boolean> permitUpdatePredicate) throws QueryEngineException;

    /**
     * Removes the links provided in data from the entity with the given id.
     * This operation can only be used for many-to-many or one-to-many relationships.
     *
     * @param application the application context
     * @param relation the *-to-many relation to remove links from
     * @param id the primary key of the source entity
     * @param targetIds the primary keys of the target entities
     * @param permitUpdatePredicate predicate that has to pass for the relation to be allowed to be updated
     * @throws QueryEngineException if an error occurs during the remove operation
     */
    void removeLinks(@NonNull Application application, @NonNull Relation relation, @NonNull EntityId id, @NonNull Set<EntityId> targetIds, @NonNull ThunkExpression<Boolean> permitUpdatePredicate) throws QueryEngineException;

    /**
     * Counts how many entities exist that match the given expression.
     *
     * @param application the application context
     * @param entity the entity type to query
     * @param expression the predicate expression to filter entities
     * @return the amount of entities matching the criteria
     * @throws QueryEngineException if an error occurs during the query operation
     */
    ItemCount count(@NonNull Application application, @NonNull Entity entity, @NonNull ThunkExpression<Boolean> expression) throws QueryEngineException;
}
