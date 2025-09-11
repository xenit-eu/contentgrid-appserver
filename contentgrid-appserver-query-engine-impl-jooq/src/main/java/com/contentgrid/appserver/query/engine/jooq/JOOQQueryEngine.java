package com.contentgrid.appserver.query.engine.jooq;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.exceptions.EntityDefinitionNotFoundException;
import com.contentgrid.appserver.application.model.relations.ManyToOneRelation;
import com.contentgrid.appserver.application.model.relations.OneToOneRelation;
import com.contentgrid.appserver.application.model.values.AttributePath;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.RelationName;
import com.contentgrid.appserver.domain.values.EntityRequest;
import com.contentgrid.appserver.domain.values.ItemCount;
import com.contentgrid.appserver.domain.values.RelationRequest;
import com.contentgrid.appserver.domain.values.version.NonExistingVersion;
import com.contentgrid.appserver.domain.values.version.Version;
import com.contentgrid.appserver.domain.values.version.ExactlyVersion;
import com.contentgrid.appserver.domain.values.version.UnspecifiedVersion;
import com.contentgrid.appserver.query.engine.api.EntityIdAndVersion;
import com.contentgrid.appserver.query.engine.api.QueryEngine;
import com.contentgrid.appserver.query.engine.api.UpdateResult;
import com.contentgrid.appserver.query.engine.api.data.EntityCreateData;
import com.contentgrid.appserver.query.engine.api.data.EntityData;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.query.engine.api.data.OffsetData;
import com.contentgrid.appserver.query.engine.api.data.QueryPageData;
import com.contentgrid.appserver.query.engine.api.data.RelationData;
import com.contentgrid.appserver.query.engine.api.data.SliceData;
import com.contentgrid.appserver.query.engine.api.data.SortData;
import com.contentgrid.appserver.query.engine.api.data.SortData.FieldSort;
import com.contentgrid.appserver.query.engine.api.data.XToManyRelationData;
import com.contentgrid.appserver.query.engine.api.data.XToOneRelationData;
import com.contentgrid.appserver.query.engine.api.exception.ConstraintViolationException;
import com.contentgrid.appserver.query.engine.api.exception.EntityIdNotFoundException;
import com.contentgrid.appserver.query.engine.api.exception.InvalidDataException;
import com.contentgrid.appserver.query.engine.api.exception.PermissionDeniedException;
import com.contentgrid.appserver.query.engine.api.exception.QueryEngineException;
import com.contentgrid.appserver.query.engine.api.exception.UnsatisfiedVersionException;
import com.contentgrid.appserver.query.engine.jooq.JOOQThunkExpressionVisitor.JOOQContext;
import com.contentgrid.appserver.query.engine.jooq.count.JOOQCountStrategy;
import com.contentgrid.appserver.query.engine.jooq.resolver.DSLContextResolver;
import com.contentgrid.appserver.query.engine.jooq.strategy.HasSourceTableColumnRef;
import com.contentgrid.appserver.query.engine.jooq.strategy.JOOQRelationStrategyFactory;
import com.contentgrid.thunx.predicates.model.Scalar;
import com.contentgrid.thunx.predicates.model.ThunkExpression;
import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochRandomGenerator;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.Record;
import org.jooq.SortField;
import org.jooq.exception.IntegrityConstraintViolationException;
import org.jooq.impl.DSL;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

@RequiredArgsConstructor
public class JOOQQueryEngine implements QueryEngine {

    @NonNull
    private final DSLContextResolver resolver;
    private static final JOOQThunkExpressionVisitor visitor = new JOOQThunkExpressionVisitor();

    private static final TimeBasedEpochRandomGenerator uuidGenerator = Generators.timeBasedEpochRandomGenerator(); // uuid v7 generator

    @NonNull
    private final JOOQCountStrategy countStrategy;

    private static final long VERSION_MODULUS = 1L << 32;

    private static final SecureRandom secureRandom = new SecureRandom();

    private static Condition createCondition(JOOQContext context, ThunkExpression<Boolean> expression) {
        return DSL.condition((Field<Boolean>) expression.accept(visitor, context));
    }

    @Override
    public SliceData findAll(@NonNull Application application, @NonNull Entity entity,
            @NonNull ThunkExpression<Boolean> expression, SortData sortData, @NonNull QueryPageData page) throws QueryEngineException {
        var dslContext = resolver.resolve(application);
        var context = new JOOQContext(application, entity);
        var alias = context.getRootAlias();
        var table = JOOQUtils.resolveTable(entity, alias);
        var orderBy = sortData != null
                ? sortData.getSortedFields().stream().map(field -> convert(entity, field)).toList()
                : List.<OrderField<?>>of();

        var offsetAndLimit = convertPageData(page);

        var condition = createCondition(context, expression);
        var results = dslContext.selectFrom(table)
                .where(condition)
                .orderBy(orderBy)
                .offset(offsetAndLimit.offset())
                .limit(offsetAndLimit.limit())
                .fetch()
                .intoMaps();

        return SliceData.builder()
                .entities(results.stream()
                        .map(result -> EntityDataMapper.from(entity, result))
                        .toList())
                .build();
    }

    private record OffsetAndLimit(long offset, int limit) {}

    private static OffsetAndLimit convertPageData(@NonNull QueryPageData data)  {
        return switch (data) {
            case OffsetData offsetData -> new OffsetAndLimit(offsetData.getOffset(), offsetData.getLimit());
        };
    }

    private static SortField<Object> convert(Entity entity, FieldSort field) {
        var path = entity.getSortableFieldByName(field.getName()).orElseThrow().getPropertyPath();
        if (!(path instanceof AttributePath attrPath)) {
            throw new IllegalArgumentException("Sorting by complex property paths is not supported.");
        }
        var attr = entity.resolveAttributePath(attrPath);
        var dslField = DSL.field(attr.getColumn().getValue());
        return switch (field.getDirection()) {
            case ASC -> dslField.asc();
            case DESC -> dslField.desc();
        };
    }

    @Override
    public Optional<EntityData> findById(@NonNull Application application, @NonNull EntityRequest entityRequest,
            @NonNull ThunkExpression<Boolean> permitReadPredicate) {
        var dslContext = resolver.resolve(application);
        var entity = application.getRequiredEntityByName(entityRequest.getEntityName());
        var context = new JOOQContext(application, entity);
        var alias = context.getRootAlias();
        var table = JOOQUtils.resolveTable(entity, alias);
        var primaryKey = JOOQUtils.resolvePrimaryKey(alias, entity);

        var fields = new ArrayList<>(Arrays.asList(JOOQUtils.resolveAttributeFields(entity)));
        var condition = createCondition(context, permitReadPredicate);

        fields.add(DSL.field(condition).as("_allow_read"));

        return dslContext
                .select(fields)
                .from(table)
                .where(primaryKey.eq(entityRequest.getEntityId().getValue()))
                .fetchOptional()
                .map(Record::intoMap)
                .map(result -> {
                    var entityData = EntityDataMapper.from(entity, result);
                    if(result.get("_allow_read") != Boolean.TRUE) {
                        throw new PermissionDeniedException(entityData.getIdentity());
                    }
                    return entityData;
                })
                .map(checkVersionSatisfied(entityRequest));
    }

    private static @NotNull Function<EntityData, EntityData> checkVersionSatisfied(@NotNull EntityRequest entityRequest) {
        return entityData -> {
            if (!entityRequest.getVersionConstraint().isSatisfiedBy(entityData.getIdentity().getVersion())) {
                throw new UnsatisfiedVersionException(
                        entityData.getIdentity().getVersion(),
                        entityRequest.getVersionConstraint()
                );
            }
            return entityData;
        };
    }

    @Override
    public EntityData create(@NonNull Application application, @NonNull EntityCreateData data,
            @NonNull ThunkExpression<Boolean> permitCreatePredicate) throws QueryEngineException {
        var dslContext = resolver.resolve(application);
        var entity = getRequiredEntity(application, data.getEntityName());
        var table = JOOQUtils.resolveTable(entity);
        var primaryKey = JOOQUtils.resolvePrimaryKey(entity);
        var id = generateId(entity);

        var step = dslContext.insertInto(table)
                .set(primaryKey, id.getValue());

        var maybeVersionField = JOOQUtils.resolveVersionField(entity);

        if(maybeVersionField.isPresent()) {
            // Set version field to initial, random value
            step = step.set(maybeVersionField.get(), secureRandom.nextLong(1, VERSION_MODULUS));
        }

        var entityData = EntityData.builder()
                .name(data.getEntityName())
                .id(id)
                .attributes(data.getAttributes())
                .build();

        var list = EntityDataConverter.convert(entityData, entity);

        for (var entry : list) {
            step = step.set(entry.field(), entry.value());
        }

        // add owning relations to step and keep track of relations owned by other entities
        var nonOwningRelations = new ArrayList<RelationData>();
        var processedRelations = new HashSet<RelationName>();

        for (var relationData : data.getRelations()) {
            if (!processedRelations.add(relationData.getName())) {
                throw new InvalidDataException("Multiple RelationData instances provided for relation '%s'"
                        .formatted(relationData.getName()));
            }
            var relation = application.getRelationForEntity(entity, relationData.getName())
                    .orElseThrow(() -> new InvalidDataException("Relation '%s' does not exist on entity '%s'".formatted(relationData.getName(), entity.getName())));

            if(relationData instanceof XToOneRelationData toOneRelationData) {
                var strategy = JOOQRelationStrategyFactory.forToOneRelation(relation);
                if(strategy instanceof HasSourceTableColumnRef hasSourceTableColumnRef) {
                    step = step.set(hasSourceTableColumnRef.getSourceTableColumnRef(application, relation), toOneRelationData.getRef().getValue());
                } else {
                    nonOwningRelations.add(relationData);
                }
            } else {
                nonOwningRelations.add(relationData);
            }
        }

        EntityData insertedData;
        try {
            var insertedRecord = step
                    .returning(JOOQUtils.resolveAttributeFields(entity))
                    .fetchSingleMap();
            insertedData = EntityDataMapper.from(entity, insertedRecord);
        } catch (DuplicateKeyException e) {
            throw new ConstraintViolationException("Provided value for unique field already exists. " + e.getMessage(), e);
        } catch (DataIntegrityViolationException | IntegrityConstraintViolationException e) {
            throw new ConstraintViolationException(e.getMessage(), e); // null for required field or foreign key does not exist
        }

        // add relations owned by other entities
        for (var relationData : nonOwningRelations) {
            var relation = application.getRelationForEntity(entity, relationData.getName())
                    .orElseThrow(() -> new InvalidDataException("Relation '%s' does not exist on entity '%s'".formatted(relationData.getName(), entity.getName())));
            var relationRequest = RelationRequest.forRelation(
                    relation.getSourceEndPoint().getEntity(),
                    id,
                    relation.getSourceEndPoint().getName()
            );
            switch (relationData) {
                case XToOneRelationData xToOneRelationData -> setLink(
                        application,
                        relationRequest,
                        xToOneRelationData.getRef(),
                        Scalar.of(true)
                );
                case XToManyRelationData xToManyRelationData -> addLinks(
                        application,
                        relationRequest,
                        xToManyRelationData.getRefs(),
                        Scalar.of(true)
                );
            }
        }

        assertPermission(application, insertedData.getIdentity().toRequest(), permitCreatePredicate);

        return insertedData;
    }

    /**
     * Check if a predicate matches (using a find)
     * <p>
     * This is done after operations that manipulate an object, but before the transaction commits
     * @throws PermissionDeniedException when the predicate does not allow access
     */
    private void assertPermission(
            @NonNull Application application,
            @NonNull EntityRequest request,
            @NonNull ThunkExpression<Boolean> predicate
    ) throws PermissionDeniedException {
        findById(application, request, predicate)
                .orElseThrow(() -> new EntityIdNotFoundException(request.getEntityName(), request.getEntityId()));
    }

    private Entity getRequiredEntity(Application application, EntityName entityName) throws InvalidDataException {
        try {
            return application.getRequiredEntityByName(entityName);
        } catch (EntityDefinitionNotFoundException e) {
            throw new InvalidDataException(e.getMessage(), e);
        }
    }

    private EntityId generateId(Entity entity) throws InvalidDataException {
        if (!Type.UUID.equals(entity.getPrimaryKey().getType())) {
            throw new InvalidDataException("Primary key with type %s not supported".formatted(entity.getPrimaryKey().getType()));
        }
        return EntityId.of(uuidGenerator.generate());
    }

    @Override
    public UpdateResult update(@NonNull Application application, @NonNull EntityData data,
            @NonNull ThunkExpression<Boolean> permitUpdatePredicate) throws QueryEngineException {
        var dslContext = resolver.resolve(application);
        var entity = getRequiredEntity(application, data.getName());
        var table = JOOQUtils.resolveTable(entity);
        var primaryKey = JOOQUtils.resolvePrimaryKey(entity);
        var id = data.getId();

        var attributeFields = JOOQUtils.resolveAttributeFields(entity);
        var updatedFields = dslContext.newRecord(attributeFields);

        for (var pair : EntityDataConverter.convert(data, entity)) {
            updatedFields.set(pair.field(), pair.value());
        }

        if(!updatedFields.changed()) {
            // Check that at least one field is updated
            throw new InvalidDataException("Provided data is empty");
        }

        var update = dslContext.update(table)
                .set(updatedFields);

        // Increment version
        var maybeVersionField = JOOQUtils.resolveVersionField(entity);
        // Randomize the increase a bit, so its clear for consumers that it is not a number or monotonically increasing field to be dependent on
        // Instead, due to the large possibility of version increments, it will wrap around very soon and very often
        var versionIncrement = secureRandom.nextLong(1, VERSION_MODULUS >> 1);
        if(maybeVersionField.isPresent()) {
            update = update.set(maybeVersionField.get(), maybeVersionField.get().plus(versionIncrement).modulo(VERSION_MODULUS));
        }

        try {
            // If previous value was not found with an update, the user does not have permission to update the object
            // so we act as if it does not exist at all
            var oldValue = findById(application, data.getIdentity().toRequest(), permitUpdatePredicate)
                    .orElseThrow(() -> new EntityIdNotFoundException(entity.getName(), data.getId()));

            var newValue = update
                    .where(primaryKey.eq(id.getValue()))
                    .returning(attributeFields)
                    .fetchOptionalMap()
                    .map(result -> EntityDataMapper.from(entity, result))
                    .orElseThrow(() -> new EntityIdNotFoundException(entity.getName(), data.getId()));

            // When the update is done properly, the value of the new version field will be one higher
            // than the previous value, so restore it back to the previous value to check against the requested version
            var previousVersion = previousVersion(newValue.getIdentity().getVersion(), versionIncrement);

            // If the update was done, and it has violated the version requirement, throw an exception.
            // Throwing the exception will both signal a failure, and will result in the transaction being rolled back,
            // so the update will not actually be committed
            if(!data.getIdentity().getVersion().isSatisfiedBy(previousVersion)) {
                throw new UnsatisfiedVersionException(
                        data.getIdentity().getVersion(),
                        previousVersion
                );
            }

            assertPermission(application, newValue.getIdentity().toRequest(), permitUpdatePredicate);

            return new UpdateResult(
                    oldValue,
                    newValue
            );
        } catch (DuplicateKeyException e) {
            throw new ConstraintViolationException("Provided value for unique field already exists" + e.getMessage(), e);
        } catch (DataIntegrityViolationException | IntegrityConstraintViolationException e) {
            throw new ConstraintViolationException(e.getMessage(), e);
        }
    }

    private Version previousVersion(@NonNull Version version, long versionIncrement) {
        return switch (version) {
            case UnspecifiedVersion unspecifiedVersion -> unspecifiedVersion;
            case NonExistingVersion nonExistingVersion -> nonExistingVersion;
            case ExactlyVersion exactlyVersion -> {
                var current = Long.parseLong(exactlyVersion.getVersion(), Character.MAX_RADIX);
                // Note: needs floorMod, so we always have a positive modulus, even when current is small and versionIncrement is large
                var previousVersion = Math.floorMod(current - versionIncrement, VERSION_MODULUS);
                yield Version.exactly(Long.toString(previousVersion, Character.MAX_RADIX));
            }
        };
    }

    @Override
    public Optional<EntityData> delete(@NonNull Application application, @NonNull EntityRequest entityRequest,
            @NonNull ThunkExpression<Boolean> permitDeletePredicate)
            throws QueryEngineException {
        var dslContext = resolver.resolve(application);
        var entity = application.getRequiredEntityByName(entityRequest.getEntityName());
        var table = JOOQUtils.resolveTable(entity);
        var primaryKey = JOOQUtils.resolvePrimaryKey(entity);

        findById(application, entityRequest, permitDeletePredicate)
                .orElseThrow(() -> new EntityIdNotFoundException(entityRequest.getEntityName(), entityRequest.getEntityId()));

        try {
            // Remove relations that reference this entity
            for (var relation : application.getRelationsForSourceEntity(entity)) {
                var strategy = JOOQRelationStrategyFactory.forRelation(relation);
                // If data is not stored in the table of this entity, cascade-delete it
                // Do not delete relations that are stored in this entity, as the row will be deleted anyway,
                // and we might run into relations that are required on this side (and thus can't be cleared)
                if(!(strategy instanceof HasSourceTableColumnRef<?>)) {
                    strategy.delete(dslContext, application, relation, entityRequest.getEntityId());
                }
            }
            return dslContext.deleteFrom(table)
                    .where(primaryKey.eq(entityRequest.getEntityId().getValue()))
                    .returning(JOOQUtils.resolveAttributeFields(entity))
                    .fetchOptionalMap()
                    .map(result -> EntityDataMapper.from(entity, result))
                    .map(checkVersionSatisfied(entityRequest));

        } catch (DataIntegrityViolationException | IntegrityConstraintViolationException e) {
            throw new ConstraintViolationException(e.getMessage(), e);
        }
    }

    @Override
    public void deleteAll(@NonNull Application application, @NonNull Entity entity) throws QueryEngineException {
        var dslContext = resolver.resolve(application);
        var table = JOOQUtils.resolveTable(entity);

        try {
            dslContext.deleteFrom(table).execute();
        } catch (DataIntegrityViolationException | IntegrityConstraintViolationException e) {
            throw new ConstraintViolationException(e.getMessage(), e);
        }
    }

    /**
     * Converts the entity that a relation points to into a version.
     * <p>
     * The version hash includes entity & relation name as well as the IDs on both sides
     * of the relation to ensure that version hashes can not be reused for a different relation
     */
    private static Version getRelationVersion(RelationRequest relationRequest, Optional<EntityId> maybeEntityId) {
        return maybeEntityId.map(entityId -> Version.exactly(hash(
                        relationRequest.getEntityName().getValue(),
                        relationRequest.getEntityId().getValue().toString(),
                        relationRequest.getRelationName().getValue(),
                        entityId.getValue().toString()
                )))
                .orElseGet(Version::nonExisting);
    }

    @SneakyThrows(NoSuchAlgorithmException.class)
    private static String hash(String... inputs) {
        var md = MessageDigest.getInstance("SHA256");
        for (var input : inputs) {
            md.update(input.getBytes(StandardCharsets.UTF_8));
            md.update((byte) 0); // NUL-byte as separator for fields
        }
        var digest = md.digest();
        // An always-positive bigint, limited to 16 bytes (truncated sha-256 hash), to reduce the size of the version
        // This reduces the size of the version from 50 characters to a more sensible 25 characters
        return new BigInteger(1, digest, 0, 16).toString(Character.MAX_RADIX);
    }

    @Override
    public boolean isLinked(@NonNull Application application, @NonNull RelationRequest relationRequest,
            @NonNull EntityId targetId, @NonNull ThunkExpression<Boolean> permitReadPredicate) throws QueryEngineException {
        assertPermission(application, EntityRequest.forEntity(relationRequest.getEntityName(), relationRequest.getEntityId()), permitReadPredicate);
        var dslContext = resolver.resolve(application);
        var relation = application.getRequiredRelationForEntity(relationRequest.getEntityName(), relationRequest.getRelationName());
        var strategy = JOOQRelationStrategyFactory.forRelation(relation);
        return strategy.isLinked(dslContext, application, relation, relationRequest.getEntityId(), targetId);
    }

    @Override
    public Optional<EntityIdAndVersion> findTarget(@NonNull Application application, @NonNull RelationRequest relationRequest,
            @NonNull ThunkExpression<Boolean> permitReadPredicate) throws QueryEngineException {
        assertPermission(application, EntityRequest.forEntity(relationRequest.getEntityName(), relationRequest.getEntityId()), permitReadPredicate);
        var dslContext = resolver.resolve(application);
        var relation = application.getRequiredRelationForEntity(relationRequest.getEntityName(), relationRequest.getRelationName());
        var strategy = JOOQRelationStrategyFactory.forToOneRelation(relation);
        var maybeEntityId = strategy.findTarget(dslContext, application, relation, relationRequest.getEntityId());
        var version = getRelationVersion(relationRequest, maybeEntityId);
        if(!relationRequest.getVersionConstraint().isSatisfiedBy(version)) {
            throw new UnsatisfiedVersionException(version, relationRequest.getVersionConstraint());
        }

        return maybeEntityId.map(entityId -> new EntityIdAndVersion(entityId, version));
    }

    @Override
    public void setLink(@NonNull Application application, @NonNull RelationRequest relationRequest, @NonNull EntityId targetId,
            @NonNull ThunkExpression<Boolean> permitUpdatePredicate) throws QueryEngineException {
        findTarget(application, relationRequest, permitUpdatePredicate); // implicit permission check + version check

        var dslContext = resolver.resolve(application);
        var relation = application.getRequiredRelationForEntity(relationRequest.getEntityName(), relationRequest.getRelationName());
        var strategy = JOOQRelationStrategyFactory.forToOneRelation(relation);
        strategy.create(dslContext, application, relation, relationRequest.getEntityId(), targetId);
        assertPermission(application, EntityRequest.forEntity(relationRequest.getEntityName(), relationRequest.getEntityId()), permitUpdatePredicate);
    }

    @Override
    public void unsetLink(@NonNull Application application, @NonNull RelationRequest relationRequest,
            @NonNull ThunkExpression<Boolean> permitUpdatePredicate) throws QueryEngineException {
        var dslContext = resolver.resolve(application);
        var relation = application.getRequiredRelationForEntity(relationRequest.getEntityName(), relationRequest.getRelationName());
        if(relation instanceof OneToOneRelation || relation instanceof ManyToOneRelation) {
            // implicit permission check + version check
            findTarget(application, relationRequest, permitUpdatePredicate);
        } else {
            assertPermission(application, EntityRequest.forEntity(relationRequest.getEntityName(), relationRequest.getEntityId()), permitUpdatePredicate);
        }

        JOOQRelationStrategyFactory.forRelation(relation)
                .delete(dslContext, application, relation, relationRequest.getEntityId());

        assertPermission(application, EntityRequest.forEntity(relationRequest.getEntityName(), relationRequest.getEntityId()), permitUpdatePredicate);
    }

    @Override
    public void addLinks(@NonNull Application application, @NonNull RelationRequest relationRequest, @NonNull Set<EntityId> targetIds,
            @NonNull ThunkExpression<Boolean> permitUpdatePredicate) throws QueryEngineException {
        assertPermission(application, EntityRequest.forEntity(relationRequest.getEntityName(), relationRequest.getEntityId()), permitUpdatePredicate);
        var dslContext = resolver.resolve(application);
        var relation = application.getRequiredRelationForEntity(relationRequest.getEntityName(), relationRequest.getRelationName());
        var strategy = JOOQRelationStrategyFactory.forToManyRelation(relation);
        strategy.add(dslContext, application, relation, relationRequest.getEntityId(), targetIds);
        assertPermission(application, EntityRequest.forEntity(relationRequest.getEntityName(), relationRequest.getEntityId()), permitUpdatePredicate);
    }

    @Override
    public void removeLinks(@NonNull Application application, @NonNull RelationRequest relationRequest, @NonNull Set<EntityId> targetIds,
            @NonNull ThunkExpression<Boolean> permitUpdatePredicate) throws QueryEngineException {
        assertPermission(application, EntityRequest.forEntity(relationRequest.getEntityName(), relationRequest.getEntityId()), permitUpdatePredicate);
        var dslContext = resolver.resolve(application);
        var relation = application.getRequiredRelationForEntity(relationRequest.getEntityName(), relationRequest.getRelationName());
        var strategy = JOOQRelationStrategyFactory.forToManyRelation(relation);
        strategy.remove(dslContext, application, relation, relationRequest.getEntityId(), targetIds);
        assertPermission(application, EntityRequest.forEntity(relationRequest.getEntityName(), relationRequest.getEntityId()), permitUpdatePredicate);
    }

    @Override
    public ItemCount count(@NonNull Application application, @NonNull Entity entity,
            @NonNull ThunkExpression<Boolean> expression) throws QueryEngineException {
        var dslContext = resolver.resolve(application);
        var context = new JOOQContext(application, entity);
        var alias = context.getRootAlias();
        var table = JOOQUtils.resolveTable(entity, alias);

        var condition = DSL.condition((Field<Boolean>) expression.accept(visitor, context));
        return countStrategy.count(dslContext, DSL.selectFrom(table).where(condition));
    }
}
