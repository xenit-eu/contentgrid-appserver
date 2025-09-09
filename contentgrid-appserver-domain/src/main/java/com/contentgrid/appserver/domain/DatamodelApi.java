package com.contentgrid.appserver.domain;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.domain.authorization.PermissionPredicate;
import com.contentgrid.appserver.domain.data.InvalidPropertyDataException;
import com.contentgrid.appserver.domain.data.RequestInputData;
import com.contentgrid.appserver.domain.paging.ResultSlice;
import com.contentgrid.appserver.domain.paging.cursor.CursorCodec.CursorDecodeException;
import com.contentgrid.appserver.domain.paging.cursor.EncodedCursorPagination;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.domain.values.EntityRequest;
import com.contentgrid.appserver.query.engine.api.data.EntityData;
import com.contentgrid.appserver.query.engine.api.exception.EntityIdNotFoundException;
import com.contentgrid.appserver.query.engine.api.exception.InvalidThunkExpressionException;
import com.contentgrid.appserver.query.engine.api.exception.QueryEngineException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
     * @param pagination pagination parameters for the query
     * @return a slice of entities matching the criteria
     * @throws QueryEngineException if an error occurs during the query operation
     */
    ResultSlice findAll(@NonNull Application application, @NonNull Entity entity, @NonNull Map<String, List<String>> params,
            @NonNull EncodedCursorPagination pagination,
            @NonNull PermissionPredicate permissionPredicate)
            throws InvalidThunkExpressionException, CursorDecodeException;

    /**
     * Finds an entity that matches the given id.
     *
     * @param application the application context
     * @param entityRequest the identity of the entity to query
     * @return an Optional containing the entity data if found, empty otherwise
     */
    Optional<EntityData> findById(@NonNull Application application, @NonNull EntityRequest entityRequest, @NonNull PermissionPredicate permissionPredicate);

    /**
     * Creates an entity with the given data and relations.
     *
     * @param application the application context
     * @param entityName the name of the entity to update
     * @param data the data for the new entity
     * @return the value of the primary key for the newly created entity
     * @throws QueryEngineException if an error occurs during the create operation
     * @throws InvalidPropertyDataException when any part of the {@code data} is not valid
     */
    EntityData create(@NonNull Application application, @NonNull EntityName entityName, @NonNull RequestInputData data,
            @NonNull PermissionPredicate permissionPredicate
    )
            throws QueryEngineException, InvalidPropertyDataException;

    /**
     * Updates an entity with the given data. Fully replaces the existing data in the entity with the given data.
     *
     * @param application the application context
     * @param entityRequest the identity of the entity to query
     * @param data the updated data for the entity
     * @throws QueryEngineException if an error occurs during the update operation
     * @throws InvalidPropertyDataException when any part of the {@code data} is not valid
     */
    default EntityData update(@NonNull Application application, @NonNull EntityRequest entityRequest,
            @NonNull RequestInputData data,
            @NonNull PermissionPredicate permissionPredicate
    )
            throws QueryEngineException, InvalidPropertyDataException {
        var original = findById(application, entityRequest, permissionPredicate)
                .orElseThrow(() -> new EntityIdNotFoundException(entityRequest));
        return update(application, original, data, permissionPredicate);
    }

    /**
     * Updates an entity with the given data. Fully replaces the existing data in the entity with the given data.
     *
     * @param application the application context
     * @param original the original data of the entity to update
     * @param data the updated data for the entity
     * @throws QueryEngineException if an error occurs during the update operation
     * @throws InvalidPropertyDataException when any part of the {@code data} is not valid
     */
    EntityData update(@NonNull Application application, @NonNull EntityData original,
            @NonNull RequestInputData data,
            @NonNull PermissionPredicate permissionPredicate
    )
            throws QueryEngineException, InvalidPropertyDataException;

    /**
     * Updates an entity with the given data. Replaces only the attributes present in the given data, other attributes
     * keep their previous value.
     *
     * @param application the application context
     * @param entityRequest the identity of the entity to query
     * @param data the updated data for the entity
     * @throws QueryEngineException if an error occurs during the update operation
     * @throws InvalidPropertyDataException when any part of the {@code data} is not valid
     */
    default EntityData updatePartial(@NonNull Application application, @NonNull EntityRequest entityRequest,
            @NonNull RequestInputData data,
            @NonNull PermissionPredicate permissionPredicate
    )
            throws QueryEngineException, InvalidPropertyDataException {
        var original = findById(application, entityRequest, permissionPredicate)
                .orElseThrow(() -> new EntityIdNotFoundException(entityRequest));
        return updatePartial(application, original, data, permissionPredicate);
    }

    /**
     * Updates an entity with the given data. Replaces only the attributes present in the given data, other attributes
     * keep their previous value.
     *
     * @param application the application context
     * @param original the original data of the entity to update
     * @param data the updated data for the entity
     * @throws QueryEngineException if an error occurs during the update operation
     * @throws InvalidPropertyDataException when any part of the {@code data} is not valid
     */
    EntityData updatePartial(@NonNull Application application, @NonNull EntityData original,
            @NonNull RequestInputData data,
            @NonNull PermissionPredicate permissionPredicate
    )
            throws QueryEngineException, InvalidPropertyDataException;

    /**
     * Deletes the entity with the given id.
     *
     * @param application the application context
     * @param entityRequest the id of the entity to query
     * @return the entity data that was deleted
     * @throws EntityIdNotFoundException if there's no id with this entity
     */
    EntityData deleteEntity(@NonNull Application application, @NonNull EntityRequest entityRequest, @NonNull PermissionPredicate permissionPredicate) throws EntityIdNotFoundException;

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
    boolean hasRelationTarget(@NonNull Application application, @NonNull Relation relation, @NonNull EntityId sourceId, @NonNull EntityId targetId, @NonNull PermissionPredicate permissionPredicate) throws QueryEngineException;

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
    Optional<EntityId> findRelationTarget(@NonNull Application application, @NonNull Relation relation, @NonNull EntityId id, @NonNull PermissionPredicate permissionPredicate) throws QueryEngineException;

    /**
     * Link the target entity id with the given source id.
     * This operation can only be used for many-to-one or one-to-one relationships.
     *
     * @param application the application context
     * @param relation the relation to set
     * @param id the primary key of the source entity
     * @param targetId the primary key of the target entity to link
     * @throws QueryEngineException if an error occurs during the set operation
     */
    void setRelation(@NonNull Application application, @NonNull Relation relation, @NonNull EntityId id, @NonNull EntityId targetId, @NonNull PermissionPredicate permissionPredicate) throws QueryEngineException;

    /**
     * Removes all links from the entity with the given id for the specified relation.
     *
     * @param application the application context
     * @param relation the relation type for which to remove links
     * @param id the primary key of the source entity
     * @throws QueryEngineException if an error occurs during the unset operation
     */
    void deleteRelation(@NonNull Application application, @NonNull Relation relation, @NonNull EntityId id, @NonNull PermissionPredicate permissionPredicate) throws QueryEngineException;

    /**
     * Adds the target entity links to the entity with the given id.
     * This operation can only be used for many-to-many or one-to-many relationships.
     *
     * @param application the application context
     * @param relation the relation to add links to
     * @param id the primary key of the source entity
     * @param targetIds the primary keys of the target entities to link
     * @throws QueryEngineException if an error occurs during the add operation
     */
    void addRelationItems(@NonNull Application application, @NonNull Relation relation, @NonNull EntityId id, @NonNull Set<EntityId> targetIds, @NonNull PermissionPredicate permissionPredicate) throws QueryEngineException;

    /**
     * Removes the target entity links from the entity with the given id.
     * This operation can only be used for many-to-many or one-to-many relationships.
     *
     * @param application the application context
     * @param relation the relation to remove links from
     * @param id the primary key of the source entity
     * @param targetIds the primary keys of the target entities to unlink
     * @throws QueryEngineException if an error occurs during the remove operation
     */
    void removeRelationItems(@NonNull Application application, @NonNull Relation relation, @NonNull EntityId id, @NonNull Set<EntityId> targetIds, @NonNull PermissionPredicate permissionPredicate) throws QueryEngineException;

    /**
     * Unlink the given target id from the given source id of the given relation.
     * This operation can only be used for many-to-many or one-to-many relationships.
     *
     * @param application the application context
     * @param relation the relation to remove an item from
     * @param sourceId the primary key of the source entity
     * @param targetId the primary key of the target entity
     * @throws QueryEngineException if an error occurs during the remove operation
     */
    default void removeRelationItem(@NonNull Application application, @NonNull Relation relation, @NonNull EntityId sourceId, @NonNull EntityId targetId, @NonNull PermissionPredicate permissionPredicate) throws QueryEngineException {
        removeRelationItems(application, relation, sourceId, Set.of(targetId), permissionPredicate);
    }

}
