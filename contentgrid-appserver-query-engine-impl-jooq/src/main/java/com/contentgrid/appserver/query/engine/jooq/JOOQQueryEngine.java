package com.contentgrid.appserver.query.engine.jooq;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Constraint.RequiredConstraint;
import com.contentgrid.appserver.application.model.Constraint.UniqueConstraint;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.HasAttributes.Entry;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.relations.ManyToOneRelation;
import com.contentgrid.appserver.application.model.relations.OneToOneRelation;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.application.model.values.AttributePath;
import com.contentgrid.appserver.application.model.values.PropertyPath;
import com.contentgrid.appserver.application.model.values.RelationName;
import com.contentgrid.appserver.application.model.values.RelationPath;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.domain.values.EntityIdentity;
import com.contentgrid.appserver.domain.values.EntityRequest;
import com.contentgrid.appserver.domain.values.ItemCount;
import com.contentgrid.appserver.domain.values.RelationRequest;
import com.contentgrid.appserver.domain.values.version.ExactlyVersion;
import com.contentgrid.appserver.domain.values.version.NonExistingVersion;
import com.contentgrid.appserver.domain.values.version.UnspecifiedVersion;
import com.contentgrid.appserver.domain.values.version.Version;
import com.contentgrid.appserver.query.engine.api.CreateEventConsumer;
import com.contentgrid.appserver.query.engine.api.DeleteEventConsumer;
import com.contentgrid.appserver.query.engine.api.EntityIdAndVersion;
import com.contentgrid.appserver.query.engine.api.LinkEventConsumer;
import com.contentgrid.appserver.query.engine.api.QueryEngine;
import com.contentgrid.appserver.query.engine.api.UnlinkEventConsumer;
import com.contentgrid.appserver.query.engine.api.UpdateEventConsumer;
import com.contentgrid.appserver.query.engine.api.UpdateResult;
import com.contentgrid.appserver.query.engine.api.data.EntityCreateData;
import com.contentgrid.appserver.query.engine.api.data.EntityData;
import com.contentgrid.appserver.query.engine.api.data.OffsetData;
import com.contentgrid.appserver.query.engine.api.data.QueryPageData;
import com.contentgrid.appserver.query.engine.api.data.RelationData;
import com.contentgrid.appserver.query.engine.api.data.SliceData;
import com.contentgrid.appserver.query.engine.api.data.SortData;
import com.contentgrid.appserver.query.engine.api.data.SortData.FieldSort;
import com.contentgrid.appserver.query.engine.api.data.XToManyRelationData;
import com.contentgrid.appserver.query.engine.api.data.XToOneRelationData;
import com.contentgrid.appserver.query.engine.api.exception.ConcurrencyFailureException;
import com.contentgrid.appserver.query.engine.api.exception.EntityIdNotFoundException;
import com.contentgrid.appserver.query.engine.api.exception.IllegalInputDataException;
import com.contentgrid.appserver.query.engine.api.exception.PermissionDeniedException;
import com.contentgrid.appserver.query.engine.api.exception.QueryEngineException;
import com.contentgrid.appserver.query.engine.api.exception.RequiredConstraintViolationException;
import com.contentgrid.appserver.query.engine.api.exception.UniqueConstraintViolationException;
import com.contentgrid.appserver.query.engine.api.exception.UnsatisfiedVersionException;
import com.contentgrid.appserver.query.engine.jooq.JOOQThunkExpressionVisitor.JOOQContext;
import com.contentgrid.appserver.query.engine.jooq.count.JOOQCountStrategy;
import com.contentgrid.appserver.query.engine.jooq.resolver.DSLContextResolver;
import com.contentgrid.appserver.query.engine.jooq.strategy.ExpectedId;
import com.contentgrid.appserver.query.engine.jooq.strategy.ExpectedIdMismatchException;
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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.Record3;
import org.jooq.SelectUnionStep;
import org.jooq.SortField;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;

@RequiredArgsConstructor
public class JOOQQueryEngine implements QueryEngine {

    @NonNull
    private final DSLContextResolver resolver;

    @NonNull
    private final JOOQCountStrategy countStrategy;

    private static final JOOQThunkExpressionVisitor visitor = new JOOQThunkExpressionVisitor();

    private static final TimeBasedEpochRandomGenerator uuidGenerator = Generators.timeBasedEpochRandomGenerator(); // uuid v7 generator


    private static final long VERSION_MODULUS = 1L << 32;

    private static final SecureRandom secureRandom = new SecureRandom();

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

        var condition = visitor.createCondition(expression, context);
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
        var dslField = DSL.field(DSL.name(attr.getColumn().getValue()));
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
        var condition = visitor.createCondition(permitReadPredicate, context);

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
            @NonNull ThunkExpression<Boolean> permitCreatePredicate,
            @NonNull CreateEventConsumer createEventConsumer) throws QueryEngineException {
        var dslContext = resolver.resolve(application);
        var entity = application.getRequiredEntityByName(data.getEntityName());
        var primaryKey = JOOQUtils.resolvePrimaryKey(entity);
        var id = generateId(entity);

        var createFields = dslContext.newRecord(JOOQUtils.resolveAttributeAndRelationFields(application, entity));
        createFields.set(primaryKey, id.getValue());

        var maybeVersionField = JOOQUtils.resolveVersionField(entity);

        if(maybeVersionField.isPresent()) {
            // Set version field to initial, random value
            createFields.set(maybeVersionField.get(), secureRandom.nextLong(1, VERSION_MODULUS));
        }

        var entityData = EntityData.builder()
                .name(data.getEntityName())
                .id(id)
                .attributes(data.getAttributes())
                .build();

        var list = EntityDataConverter.convert(entityData, entity);

        for (var entry : list) {
            createFields.set(entry.field(), entry.value());
        }

        // add owning relations to step and keep track of relations owned by other entities
        var nonOwningRelations = new ArrayList<RelationData>();
        var processedRelations = new HashSet<RelationName>();

        for (var relationData : data.getRelations()) {
            if (!processedRelations.add(relationData.getName())) {
                throw new IllegalInputDataException("Multiple RelationData instances provided for relation '%s'"
                        .formatted(relationData.getName()));
            }
            var relation = application.getRelationForEntity(entity, relationData.getName())
                    .orElseThrow(() -> new IllegalInputDataException("Relation '%s' does not exist on entity '%s'".formatted(relationData.getName(), entity.getName())));

            if(relationData instanceof XToOneRelationData toOneRelationData) {
                var strategy = JOOQRelationStrategyFactory.forToOneRelation(relation);
                if(strategy instanceof HasSourceTableColumnRef hasSourceTableColumnRef) {
                    createFields.set(hasSourceTableColumnRef.getSourceTableColumnRef(application, relation), toOneRelationData.getRef().getValue());
                } else {
                    nonOwningRelations.add(relationData);
                }
            } else {
                nonOwningRelations.add(relationData);
            }
        }

        EntityData insertedData;
        try {
            var insertedRecord = DslContextUtils.executeInSavepoint(dslContext, () -> dslContext
                    .insertInto(JOOQUtils.resolveTable(entity))
                    .set(createFields)
                    .returning(JOOQUtils.resolveAttributeFields(entity))
                    .fetchSingleMap());
            insertedData = EntityDataMapper.from(entity, insertedRecord);
        } catch (DataAccessException e) {
            throw ExceptionUtils.handleException(e, () -> switch (PostgresqlErrorType.from(e)) {
                case UNIQUE_CONSTRAINT_VIOLATION -> handleUniqueConstraintViolation(application, entityData.getIdentity(), createFields,
                        dslContext);
                case NOT_NULL_CONSTRAINT_VIOLATION -> handleNotNullConstraintViolation(application, entityData.getIdentity(), createFields);
                case FOREIGN_KEY_CONSTRAINT_VIOLATION -> handleForeignKeyViolation(application, entityData.getIdentity(), createFields,
                        dslContext);
                default -> null;
            });
        }

        // add relations owned by other entities
        for (var relationData : nonOwningRelations) {
            var relation = application.getRelationForEntity(entity, relationData.getName())
                    .orElseThrow(() -> new IllegalInputDataException("Relation '%s' does not exist on entity '%s'".formatted(relationData.getName(), entity.getName())));
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
                        Scalar.of(true),
                        (app, oldData, newData) -> {} // no-op: create event will be dispatched
                );
                case XToManyRelationData xToManyRelationData -> addLinks(
                        application,
                        relationRequest,
                        xToManyRelationData.getRefs(),
                        Scalar.of(true),
                        (app, oldData, newData) -> {} // no-op: create event will be dispatched
                );
            }
        }

        assertPermission(application, insertedData.getIdentity().toRequest(), permitCreatePredicate);

        createEventConsumer.onEntityCreate(application, insertedData);

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
        getByIdRequired(application, request, predicate);
    }

    private EntityId generateId(Entity entity) throws IllegalInputDataException {
        if (!Type.UUID.equals(entity.getPrimaryKey().getType())) {
            throw new IllegalInputDataException("Primary key with type %s not supported".formatted(entity.getPrimaryKey().getType()));
        }
        return EntityId.of(uuidGenerator.generate());
    }

    @Override
    public UpdateResult update(@NonNull Application application, @NonNull EntityData data,
            @NonNull ThunkExpression<Boolean> permitUpdatePredicate,
            @NonNull UpdateEventConsumer updateEventConsumer) throws QueryEngineException {
        var dslContext = resolver.resolve(application);
        var entity = application.getRequiredEntityByName(data.getName());
        var table = JOOQUtils.resolveTable(entity);
        var primaryKey = JOOQUtils.resolvePrimaryKey(entity);
        var id = data.getId();

        var attributeFields = JOOQUtils.resolveAttributeFields(entity);
        var updatedFields = dslContext.newRecord(attributeFields);

        for (var pair : EntityDataConverter.convert(data, entity)) {
            updatedFields.set(pair.field(), pair.value());
        }

        var update = dslContext.update(table)
                .set(updatedFields);

        // Increment version
        var maybeVersionField = JOOQUtils.resolveVersionField(entity);
        // Randomize the increase a bit, so it's clear for consumers that it is not a number or monotonically increasing field to be dependent on
        // Instead, due to the large possibility of version increments, it will wrap around very soon and very often
        var versionIncrement = secureRandom.nextLong(1, VERSION_MODULUS >> 1);
        if(maybeVersionField.isPresent()) {
            update = update.set(maybeVersionField.get(), maybeVersionField.get().plus(versionIncrement).modulo(VERSION_MODULUS));
        }

        try {
            // If previous value was not found with an update, the user does not have permission to update the object
            // so we act as if it does not exist at all
            var oldValue = getByIdRequired(application, data.getIdentity().toRequest(), permitUpdatePredicate);
            var newValue = oldValue;

            if (update.isExecutable()) {
                var finalUpdate = update;
                newValue = DslContextUtils.executeInSavepoint(dslContext, () -> finalUpdate
                        .where(primaryKey.eq(id.getValue()))
                        .returning(attributeFields)
                        .fetchOptionalMap()
                        .map(result -> EntityDataMapper.from(entity, result))
                        .orElseThrow(() -> new EntityIdNotFoundException(entity.getName(), data.getId())));

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
            }

            updateEventConsumer.onEntityUpdate(application, oldValue, newValue);

            return new UpdateResult(
                    oldValue,
                    newValue
            );
        } catch (DataAccessException e) {
            throw ExceptionUtils.handleException(e, () -> switch (PostgresqlErrorType.from(e)) {
                case UNIQUE_CONSTRAINT_VIOLATION -> handleUniqueConstraintViolation(application, data.getIdentity(), updatedFields,
                        dslContext);
                case NOT_NULL_CONSTRAINT_VIOLATION -> handleNotNullConstraintViolation(application, data.getIdentity(), updatedFields);
                default -> null;
            });
        }
    }

    private RequiredConstraintViolationException handleNotNullConstraintViolation(@NonNull Application application,
            @NonNull EntityIdentity entityIdentity,
            @NonNull Record entityData) {
        /*
            The conceptual way how this function works to collect all required field violations:
            1. Gather all required attributes (and relations that map to a column in our table)
            2. For every required column, check if the record that we inserted contains a non-null value
         */
        var entity = application.getRequiredEntityByName(entityIdentity.getEntityName());
        Map<Field<?>, PropertyPath> requiredFieldsMapping = entity
                .nestedAttributes(SimpleAttribute.class)
                .filter(e -> e.getAttribute().hasConstraint(RequiredConstraint.class))
                .collect(Collectors.toMap(e -> JOOQUtils.resolveField(e.getAttribute()), Entry::getPath));

        for(var relation: application.getRelationsForSourceEntity(entity)) {
            if(relation.getSourceEndPoint().isRequired() &&
                    JOOQRelationStrategyFactory.forRelation(relation) instanceof HasSourceTableColumnRef<Relation> hasSourceTableColumnRef
            ) {
                requiredFieldsMapping.put(
                        hasSourceTableColumnRef.getSourceTableColumnRef(application, relation),
                        new RelationPath(relation.getSourceEndPoint().getName(), null)
                );
            }

        }

        return ExceptionUtils.createMultiple(requiredFieldsMapping.entrySet(), entry -> {
            if (entityData.get(entry.getKey()) != null) {
                return null;
            }
            return new RequiredConstraintViolationException(
                    entityIdentity.getEntityName(),
                    entityIdentity.getEntityId(),
                    entry.getValue()
            );
        }).orElse(null);
    }

    private UniqueConstraintViolationException handleUniqueConstraintViolation(@NonNull Application application,
            @NonNull EntityIdentity entityIdentity,
            @NonNull Record entityData, @NonNull DSLContext dslContext) {
        /*
            The conceptual way how this function works to collect all unique constraint violations committed by the entity being saved:
            1. Gather all unique attributes (and relations that are stored as a column in our table)
            2. For every unique column, try to find the entity currently holding on to the value that we are trying to save
            3. All other entities that we can find for this are the unique constraint violations

            Implementation:
            To efficiently look up all entities, we want to do this in a single database query.

            We could try this with a simple SELECT * FROM table WHERE unique_attr1 = value1 OR unique_attr2 = value2 OR ...
            However, when a row is returned, this doesn't tell us *which* attribute caused the unique constraint violation.
            We would have to do the attribute comparison again in code (and that gets complicated because of normalization on strings)
            Additionally, a single row could be implicated for multiple unique constraint violations

            Instead, we generate a bunch of queries:
             - SELECT id, version, 'unique_attr1' as _field_name_x FROM table WHERE unique_attr1 = value1
             - SELECT id, version, 'unique_attr2' as _field_name_x FROM table WHERE unique_attr2 = value2
            and then tie them together with a UNION ALL between them.

            Every returned row cleanly maps to back a single unique field that was violated (via _field_name_x).
            We can pull the EntityIdentity from the returned id and version.

            Sidenote: because these are unique fields, we know that each query will only return zero or one item.
            So the maximum amount of data returned is limited by the number of unique fields, not by the size of the dataset.

         */
        var entity = application.getRequiredEntityByName(entityIdentity.getEntityName());
        Map<Field<?>, PropertyPath> uniqueFieldsMapping = entity
                .nestedAttributes(SimpleAttribute.class)
                .filter(attr -> attr.getAttribute().hasConstraint(UniqueConstraint.class))
                .collect(Collectors.toMap(e -> JOOQUtils.resolveField(e.getAttribute()), Entry::getPath));

        for (var relation : application.getRelationsForSourceEntity(entity)) {
            // Only one-to-one relations have a unique constraint
            if(relation instanceof OneToOneRelation &&
                    JOOQRelationStrategyFactory.forRelation(relation) instanceof HasSourceTableColumnRef<Relation> hasSourceTableColumnRef
            ) {
                uniqueFieldsMapping.put(hasSourceTableColumnRef.getSourceTableColumnRef(application, relation),
                        new RelationPath(relation.getSourceEndPoint()
                                .getName(), null));
            }

        }

        var primaryKeyField = JOOQUtils.resolvePrimaryKey(entity);
        var versionField = JOOQUtils.resolveVersionField(entity)
                .orElse(DSL.val(null, Long.class).as(DSL.name("_no_version_"+UUID.randomUUID())));
        var identificationField = DSL.field(DSL.name("_field_name_"+UUID.randomUUID()), String.class);

        var maybeQuery = uniqueFieldsMapping.entrySet().stream()
                .flatMap(entry -> {
                    Field<?> field = entry.getKey();
                    if(entityData.get(field) == null) {
                        return Stream.empty();
                    }

                    var fieldName = field.getName();
                    Field<?> value = DSL.val(entityData.get(field), field.getDataType());


                    if(field.getDataType().isString()) {
                        field = JOOQUtils.normalize(field);
                        value = JOOQUtils.normalize(value);
                    }

                    // Try to find the other entity (that is currently holding the unique value)
                    return Stream.<SelectUnionStep<Record3<UUID, Long, String>>>of(
                            DSL.select(
                                            primaryKeyField,
                                            versionField,
                                            DSL.val(fieldName).as(identificationField)
                                    )
                                    .from(JOOQUtils.resolveTable(entity))
                                    .where(field.equal((Field)value))
                                    .and(primaryKeyField.notEqual(entityIdentity.getEntityId().getValue()))
                    );
                })
                // The select statements generated above are all packed into one query with UNION ALL
                .reduce(SelectUnionStep::unionAll);

        if(maybeQuery.isEmpty()) {
            // There is no query to execute, because there is no potentially unique field at all
            return null;
        }

        var results = dslContext.fetch(maybeQuery.get());

        return ExceptionUtils.createMultiple(results, result -> {
            var propertyPath = uniqueFieldsMapping.entrySet()
                    .stream()
                    .filter(e -> Objects.equals(e.getKey().getName(), result.get(identificationField)))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElseThrow();
            return new UniqueConstraintViolationException(
                    entityIdentity.getEntityName(),
                    entityIdentity.getEntityId(),
                    propertyPath,
                    EntityIdentity.forEntity(
                            entity.getName(),
                            EntityId.of(result.get(primaryKeyField))
                    ).withVersion(EntityVersionUtils.getVersion(result.get(versionField)))
            );
        }).orElse(null);
    }

    private QueryEngineException handleForeignKeyViolation(@NonNull Application application,
            @NonNull EntityIdentity entityIdentity, @NonNull Record entityData,
            DSLContext dslContext) {
        /*
            The conceptual way how this function works to collect all foreign key violations committed by the entity
            1. Collect all relations that are stored as a field in our table
            2. For each relation, perform a query on the target side to find the entity that is being referenced
            3. All queries that don't return any data don't actually exist and are foreign key violations

            Implementation:
            To efficiently look up all entities, we want to do this in a single database query.

            To do this, we generate a bunch of queries:
            - SELECT 'relation1' as _field_name FROM target_entity1 WHERE id = relation_value1
            - SELECT 'relation2' as _field_name FROM target_entity2 WHERE id = relation_value2
            and then combine them together using UNION ALL

            We can now compare the returned rows with our relations.
            All relations that *aren't* present in the rows are a foreign key violation.

         */
        var entity = application.getRequiredEntityByName(entityIdentity.getEntityName());

        Map<Field<UUID>, Relation> fieldRelationMap = application.getRelationsForSourceEntity(entity).stream()
                .flatMap(relation -> {
                    if(JOOQRelationStrategyFactory.forRelation(relation) instanceof HasSourceTableColumnRef<Relation> hasSourceTableColumnRef) {
                        var field = hasSourceTableColumnRef.getSourceTableColumnRef(application, relation);
                        if(entityData.get(field) == null) {
                            // Don't consider relations that don't have any data.
                            // They can't be foreign key violations (non-null violations are handled elsewhere)
                            return Stream.empty();
                        }
                        return Stream.of(Map.entry(field, relation));
                    } else {
                        return Stream.empty();
                    }
                }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        var identificationField = DSL.field(DSL.name("_field_name"), String.class);

        var maybeQuery = fieldRelationMap.entrySet().stream()
                .<SelectUnionStep<Record1<String>>>map(entry -> {
                    var field = entry.getKey();
                    var value = DSL.val(entityData.get(entry.getKey()), field.getDataType());

                    var targetEntity = application.getRelationTargetEntity(entry.getValue());
                    var targetEntityTable = JOOQUtils.resolveTable(targetEntity);
                    var targetEntityPk = JOOQUtils.resolvePrimaryKey(targetEntity);

                    return DSL.select(
                                    DSL.val(field.getName()).as(identificationField)
                            ).from(targetEntityTable)
                            .where(targetEntityPk.eq(value));
                })
                .reduce(SelectUnionStep::unionAll);

        if (maybeQuery.isEmpty()) {
            // There is no query to execute, because there are no relations stored in our table at all
            return null;
        }

        var results = dslContext.fetchValues(maybeQuery.get());

        // We can remove the fields that we received from our fieldRelationMap
        // Going further, this will be our map of relations that have FK violations
        fieldRelationMap.entrySet().removeIf(e -> results.contains(e.getKey().getName()));

        return ExceptionUtils.createMultiple(
                fieldRelationMap.entrySet(),
                entry -> new EntityIdNotFoundException(
                        entry.getValue().getTargetEndPoint().getEntity(),
                        EntityId.of(entityData.get(entry.getKey()))
                )
        ).orElse(null);
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
            @NonNull ThunkExpression<Boolean> permitDeletePredicate,
            @NonNull DeleteEventConsumer deleteEventConsumer)
            throws QueryEngineException {
        var dslContext = resolver.resolve(application);
        var entity = application.getRequiredEntityByName(entityRequest.getEntityName());
        var table = JOOQUtils.resolveTable(entity);
        var primaryKey = JOOQUtils.resolvePrimaryKey(entity);

        assertPermission(application, entityRequest, permitDeletePredicate);

        // Remove relations that reference this entity
        for (var relation : application.getRelationsForSourceEntity(entity)) {
            var strategy = JOOQRelationStrategyFactory.forRelation(relation);
            // If data is not stored in the row of this entity, cascade-delete it
            // Do not delete relations that are stored in this entity, as the row will be deleted anyway,
            // and we might run into relations that are required on this side (and thus can't be cleared)
            if(!(strategy instanceof HasSourceTableColumnRef<?>)) {
                strategy.delete(dslContext, application, relation, entityRequest.getEntityId());
            }
        }

        Optional<EntityData> deleted;

        try {
            // No foreign key constraint violation can be thrown here anymore,
            // because all relations that reference the entity and are not stored in our own table have
            // already been removed (or failed with an exception) above.
            // However, due to concurrent execution with relation updates, this *can* cause a foreign key constraint violation.
            // The only way to handle that is to re-run the whole logic in a retry, after which we are hopefully in the clear
            deleted = dslContext.deleteFrom(table)
                    .where(primaryKey.eq(entityRequest.getEntityId().getValue()))
                    .returning(JOOQUtils.resolveAttributeFields(entity))
                    .fetchOptionalMap()
                    .map(result -> EntityDataMapper.from(entity, result))
                    .map(checkVersionSatisfied(entityRequest));
        } catch(DataAccessException ex) {
            if(PostgresqlErrorType.from(ex).is(PostgresqlErrorType.FOREIGN_KEY_CONSTRAINT_VIOLATION)) {
                throw new ConcurrencyFailureException(ex);
            }
            throw ex;
        }

        deleted.ifPresent(data -> deleteEventConsumer.onEntityDelete(application, data));

        return deleted;

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
        return findTargetWithoutPermissionCheck(application, relationRequest);
    }

    private Optional<EntityIdAndVersion> findTargetWithoutPermissionCheck(@NonNull Application application, @NonNull RelationRequest relationRequest) {
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
    public EntityIdAndVersion setLink(@NonNull Application application, @NonNull RelationRequest relationRequest, @NonNull EntityId targetId,
            @NonNull ThunkExpression<Boolean> permitUpdatePredicate, @NonNull LinkEventConsumer linkEventConsumer) throws QueryEngineException {

        // Permission check
        var oldEntityData = getByIdRequired(application, relationRequest, permitUpdatePredicate);

        var expectedId = findTargetWithoutPermissionCheck(application, relationRequest)
                .map(entityIdAndVersion -> ExpectedId.exactly(entityIdAndVersion.entityId()))
                .orElse(ExpectedId.exactly(null));

        var dslContext = resolver.resolve(application);
        var relation = application.getRequiredRelationForEntity(relationRequest.getEntityName(), relationRequest.getRelationName());
        var strategy = JOOQRelationStrategyFactory.forToOneRelation(relation);
        try {
            strategy.create(dslContext, application, relation, relationRequest.getEntityId(), targetId, expectedId);
        } catch (ExpectedIdMismatchException e) {
            var ex = new UnsatisfiedVersionException(
                    getRelationVersion(relationRequest, e.getActualEntityId()),
                    relationRequest.getVersionConstraint()
            );
            ex.initCause(e);
            throw ex;
        }

        // Also does implicit post-update permission check
        var newEntityData = getByIdRequired(application, relationRequest, permitUpdatePredicate);

        linkEventConsumer.onLink(application, oldEntityData, newEntityData);

        var updatedVersion = getRelationVersion(relationRequest, Optional.of(targetId));
        return new EntityIdAndVersion(targetId, updatedVersion);
    }

    @Override
    public void unsetLink(@NonNull Application application, @NonNull RelationRequest relationRequest,
            @NonNull ThunkExpression<Boolean> permitUpdatePredicate, @NonNull UnlinkEventConsumer unlinkEventConsumer) throws QueryEngineException {

        // Also does permission check
        var oldEntityData = getByIdRequired(application, relationRequest, permitUpdatePredicate);

        var dslContext = resolver.resolve(application);
        var relation = application.getRequiredRelationForEntity(relationRequest.getEntityName(), relationRequest.getRelationName());

        if(relation instanceof OneToOneRelation || relation instanceof ManyToOneRelation) {
            var expectedId = findTargetWithoutPermissionCheck(application, relationRequest)
                    .map(entityIdAndVersion -> ExpectedId.exactly(entityIdAndVersion.entityId()))
                    .orElse(ExpectedId.exactly(null));

            try {
                JOOQRelationStrategyFactory.forToOneRelation(relation)
                        .delete(dslContext, application, relation, relationRequest.getEntityId(), expectedId);
            } catch (ExpectedIdMismatchException e) {
                var ex = new UnsatisfiedVersionException(
                        getRelationVersion(relationRequest, e.getActualEntityId()),
                        relationRequest.getVersionConstraint()
                );
                ex.initCause(e);
                throw ex;
            }

        } else {
            assertPermission(application, EntityRequest.forEntity(relationRequest.getEntityName(), relationRequest.getEntityId()), permitUpdatePredicate);
        }

        JOOQRelationStrategyFactory.forRelation(relation)
                .delete(dslContext, application, relation, relationRequest.getEntityId());

        // Also does permission check
        var newEntityData = getByIdRequired(application, relationRequest, permitUpdatePredicate);

        unlinkEventConsumer.onUnlink(application, oldEntityData, newEntityData);
    }

    @Override
    public void addLinks(@NonNull Application application, @NonNull RelationRequest relationRequest, @NonNull Set<EntityId> targetIds,
            @NonNull ThunkExpression<Boolean> permitUpdatePredicate, @NonNull LinkEventConsumer linkEventConsumer) throws QueryEngineException {

        // Also does a permission check
        var oldEntityData = getByIdRequired(application, relationRequest, permitUpdatePredicate);

        var dslContext = resolver.resolve(application);
        var relation = application.getRequiredRelationForEntity(relationRequest.getEntityName(), relationRequest.getRelationName());
        var strategy = JOOQRelationStrategyFactory.forToManyRelation(relation);
        strategy.add(dslContext, application, relation, relationRequest.getEntityId(), targetIds);

        // Also does a permission check
        var newEntityData = getByIdRequired(application, relationRequest, permitUpdatePredicate);

        linkEventConsumer.onLink(application, oldEntityData, newEntityData);
    }

    @Override
    public void removeLinks(@NonNull Application application, @NonNull RelationRequest relationRequest, @NonNull Set<EntityId> targetIds,
            @NonNull ThunkExpression<Boolean> permitUpdatePredicate, @NonNull UnlinkEventConsumer unlinkEventConsumer) throws QueryEngineException {

        // Also does a permission check
        var oldEntityData = getByIdRequired(application, relationRequest, permitUpdatePredicate);

        var dslContext = resolver.resolve(application);
        var relation = application.getRequiredRelationForEntity(relationRequest.getEntityName(), relationRequest.getRelationName());
        var strategy = JOOQRelationStrategyFactory.forToManyRelation(relation);
        strategy.remove(dslContext, application, relation, relationRequest.getEntityId(), targetIds);

        // Also does a permission check
        var newEntityData = getByIdRequired(application, relationRequest, permitUpdatePredicate);

        unlinkEventConsumer.onUnlink(application, oldEntityData, newEntityData);
    }

    private EntityData getByIdRequired(@NotNull Application application, @NotNull RelationRequest relationRequest,
            @NotNull ThunkExpression<Boolean> permitUpdatePredicate) {
        return getByIdRequired(application,
                EntityRequest.forEntity(relationRequest.getEntityName(), relationRequest.getEntityId()),
                permitUpdatePredicate);
    }

    private EntityData getByIdRequired(@NotNull Application application, @NotNull EntityRequest entityRequest,
            @NotNull ThunkExpression<Boolean> permitPredicate) {
        return findById(application, entityRequest, permitPredicate)
                .orElseThrow(() -> new EntityIdNotFoundException(entityRequest.getEntityName(), entityRequest.getEntityId()));
    }

    @Override
    public ItemCount count(@NonNull Application application, @NonNull Entity entity,
            @NonNull ThunkExpression<Boolean> expression) throws QueryEngineException {
        var dslContext = resolver.resolve(application);
        var context = new JOOQContext(application, entity);
        var alias = context.getRootAlias();
        var table = JOOQUtils.resolveTable(entity, alias);

        var condition = visitor.createCondition(expression, context);
        return countStrategy.count(dslContext, DSL.selectFrom(table).where(condition));
    }
}
