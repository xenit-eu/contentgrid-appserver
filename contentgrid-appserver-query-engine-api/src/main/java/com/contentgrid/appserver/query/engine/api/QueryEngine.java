package com.contentgrid.appserver.query.engine.api;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.query.engine.api.data.EntityData;
import com.contentgrid.appserver.query.engine.api.data.EntityId;
import com.contentgrid.appserver.query.engine.api.data.PageData;
import com.contentgrid.appserver.query.engine.api.data.RelationData;
import com.contentgrid.appserver.query.engine.api.data.SliceData;
import com.contentgrid.appserver.query.engine.api.data.XToManyRelationData;
import com.contentgrid.appserver.query.engine.api.exception.QueryEngineException;
import com.contentgrid.thunx.predicates.model.ThunkExpression;
import java.util.List;
import java.util.Optional;
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
            PageData pageData) throws QueryEngineException;

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
     * @param relations the relationships to establish for the new entity
     * @return the value of the primary key for the newly created entity
     * @throws QueryEngineException if an error occurs during the create operation
     */
    EntityId create(@NonNull Application application, @NonNull EntityData data, @NonNull List<RelationData> relations) throws QueryEngineException;

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
     * Returns the target entity or entities that are linked with the entity having the given id.
     *
     * @param application the application context
     * @param relation the relation type to query
     * @param id the primary key of the source entity
     * @return data representing the linked target entity or entities
     * @throws QueryEngineException if an error occurs during the query operation
     */
    RelationData findLink(@NonNull Application application, @NonNull Relation relation, @NonNull EntityId id) throws QueryEngineException;

    /**
     * Overwrites the link(s) from the entity with the given id with the link(s) provided in data.
     * Any existing links not included in the data will be removed.
     *
     * @param application the application context
     * @param data the relation data containing the links to set
     * @param id the primary key of the source entity
     * @throws QueryEngineException if an error occurs during the set operation
     */
    void setLink(@NonNull Application application, @NonNull RelationData data, @NonNull EntityId id) throws QueryEngineException;

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
     * This operation is typically used for many-to-many or one-to-many relationships.
     *
     * @param application the application context
     * @param data the relation data containing the links to add
     * @param id the primary key of the source entity
     * @throws QueryEngineException if an error occurs during the add operation
     */
    void addLinks(@NonNull Application application, @NonNull XToManyRelationData data, @NonNull EntityId id) throws QueryEngineException;

    /**
     * Removes the links provided in data from the entity with the given id.
     * This operation is typically used for many-to-many or one-to-many relationships.
     *
     * @param application the application context
     * @param data the relation data containing the links to remove
     * @param id the primary key of the source entity
     * @throws QueryEngineException if an error occurs during the remove operation
     */
    void removeLinks(@NonNull Application application, @NonNull XToManyRelationData data, @NonNull EntityId id) throws QueryEngineException;

}
