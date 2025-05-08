package com.contentgrid.appserver.query.engine.jooq;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.relations.ManyToManyRelation;
import com.contentgrid.appserver.application.model.relations.ManyToOneRelation;
import com.contentgrid.appserver.application.model.relations.OneToManyRelation;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.application.model.relations.SourceOneToOneRelation;
import com.contentgrid.appserver.application.model.relations.TargetOneToOneRelation;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.TableName;
import com.contentgrid.appserver.query.engine.api.QueryEngine;
import com.contentgrid.appserver.query.engine.api.data.EntityData;
import com.contentgrid.appserver.query.engine.api.data.EntityId;
import com.contentgrid.appserver.query.engine.api.data.PageData;
import com.contentgrid.appserver.query.engine.api.data.RelationData;
import com.contentgrid.appserver.query.engine.api.data.SliceData;
import com.contentgrid.appserver.query.engine.api.data.SliceData.PageInfo;
import com.contentgrid.appserver.query.engine.api.data.XToManyRelationData;
import com.contentgrid.appserver.query.engine.api.data.XToOneRelationData;
import com.contentgrid.appserver.query.engine.api.exception.ConstraintViolationException;
import com.contentgrid.appserver.query.engine.api.exception.EntityNotFoundException;
import com.contentgrid.appserver.query.engine.api.exception.InvalidDataException;
import com.contentgrid.appserver.query.engine.api.exception.QueryEngineException;
import com.contentgrid.appserver.query.engine.jooq.resolver.DSLContextResolver;
import com.contentgrid.thunx.predicates.model.ThunkExpression;
import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochRandomGenerator;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.UpdateSetFirstStep;
import org.jooq.UpdateSetMoreStep;
import org.jooq.exception.IntegrityConstraintViolationException;
import org.jooq.impl.DSL;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional
public class JOOQQueryEngine implements QueryEngine {

    private final DSLContextResolver resolver;
    private final JOOQThunkExpressionVisitor visitor = new JOOQThunkExpressionVisitor();

    private final TimeBasedEpochRandomGenerator uuidGenerator = Generators.timeBasedEpochRandomGenerator(); // uuid v7 generator

    @Override
    public SliceData findAll(@NonNull Application application, @NonNull Entity entity,
            @NonNull ThunkExpression<Boolean> expression, PageData pageData) throws QueryEngineException {
        var dslContext = resolver.resolve(application);
        var context = new JOOQThunkExpressionVisitor.JOOQContext(application, entity);
        var alias = context.getRootAlias();
        var table = JOOQUtils.resolveTable(entity, alias);

        var condition = DSL.condition((Field<Boolean>) expression.accept(visitor, context));
        var results = dslContext.selectFrom(table)
                .where(condition)
                .fetch()
                .intoMaps();

        return SliceData.builder()
                .entities(results.stream()
                        .map(result -> EntityDataMapper.from(entity, result))
                        .toList())
                .pageInfo(PageInfo.builder()
                        // TODO: ACC-2048: support paging
                        .build())
                .build();
    }

    /**
     * Return root alias for entity table, to be used in places without JOOQThunkExpressionVisitor.
     */
    private TableName getRootAlias(Entity entity) {
        return new JoinCollection(entity.getTable()).getRootAlias();
    }

    @Override
    public Optional<EntityData> findById(@NonNull Application application, @NonNull Entity entity, @NonNull EntityId id) {
        var dslContext = resolver.resolve(application);
        var alias = getRootAlias(entity);
        var table = JOOQUtils.resolveTable(entity, alias);
        var primaryKey = JOOQUtils.resolvePrimaryKey(alias, entity);

        return dslContext.selectFrom(table)
                .where(primaryKey.eq(id.getValue()))
                .fetchOptional()
                .map(Record::intoMap)
                .map(result -> EntityDataMapper.from(entity, result));
    }

    @Override
    public EntityId create(@NonNull Application application, @NonNull EntityData data,
            @NonNull List<RelationData> relations) throws QueryEngineException {
        var dslContext = resolver.resolve(application);
        var entity = getEntity(application, data.getName());
        var table = JOOQUtils.resolveTable(entity);
        var primaryKey = JOOQUtils.resolvePrimaryKey(entity);
        var id = generateId(entity);
        if (data.getId() != null) {
            throw new InvalidDataException("Provided data should not contain primary key, it is auto-generated");
        }

        var step = dslContext.insertInto(table)
                .set(primaryKey, id.getValue());

        var list = EntityDataConverter.convert(data, entity);
        for (var entry : list) {
            step = step.set(entry.field(), entry.value());
        }

        // add owning relations to step and keep track of relations owned by other entities
        var nonOwningRelations = new ArrayList<RelationData>();

        for (var relationData : relations) {
            if (!entity.getName().equals(relationData.getEntity())) {
                throw new InvalidDataException("Entity '%s' from relation '%s' does not match entity '%s' from data"
                        .formatted(relationData.getEntity(), relationData.getName(), data.getName()));
            }
            var relation = getRelation(application, relationData);
            var targetEntity = relation.getTargetEndPoint().getEntity();

            switch (relationData) {
                case XToOneRelationData xToOneRelationData -> {
                    if (xToOneRelationData.getRef() == null) {
                        continue; // skip empty relations
                    }
                    switch (relation) {
                        case SourceOneToOneRelation oneToOneRelation -> {
                            var field = (Field<UUID>) JOOQUtils.resolveField(oneToOneRelation.getTargetReference(),
                                    targetEntity.getPrimaryKey()
                                            .getType(), oneToOneRelation.getSourceEndPoint().isRequired());
                            step = step.set(field, xToOneRelationData.getRef().getValue());
                        }
                        case ManyToOneRelation manyToOneRelation -> {
                            var field = (Field<UUID>) JOOQUtils.resolveField(manyToOneRelation.getTargetReference(),
                                    targetEntity.getPrimaryKey()
                                            .getType(), manyToOneRelation.getSourceEndPoint().isRequired());
                            step = step.set(field, xToOneRelationData.getRef().getValue());
                        }
                        case TargetOneToOneRelation ignored -> nonOwningRelations.add(relationData);
                        default -> throw new InvalidDataException(
                                "Relation '%s' is not a one-to-one or many-to-one relation".formatted(
                                        relationData.getName()));
                    }
                }
                case XToManyRelationData ignored -> {
                    if (relation instanceof OneToManyRelation || relation instanceof ManyToManyRelation) {
                        nonOwningRelations.add(relationData);
                    } else {
                        throw new InvalidDataException("Relation '%s' is not a one-to-many or many-to-many relation"
                                .formatted(relationData.getName()));
                    }
                }
            }
        }

        try {
            step.execute();
        } catch (DuplicateKeyException e) {
            throw new ConstraintViolationException("Provided value for unique field already exists. " + e.getMessage(), e);
        } catch (DataIntegrityViolationException | IntegrityConstraintViolationException e) {
            throw new ConstraintViolationException(e.getMessage(), e);
        }

        // add relations owned by other entities
        for (var relationData : nonOwningRelations) {
            this.setLink(application, relationData, id);
        }

        return id;
    }

    private Entity getEntity(Application application, EntityName entityName) {
        return application.getEntityByName(entityName)
                .orElseThrow(() -> new InvalidDataException("Entity '%s' not found in application '%s'"
                        .formatted(entityName, application.getName())));
    }

    private Relation getRelation(Application application, RelationData relationData) {
        return application.getRelationForEntity(relationData.getEntity(), relationData.getName())
                .orElseThrow(() -> new InvalidDataException("Relation '%s' of entity '%s' not found in application '%s'"
                        .formatted(relationData.getName(), relationData.getEntity(), application.getName())));
    }

    private EntityId generateId(Entity entity) {
        if (!Type.UUID.equals(entity.getPrimaryKey().getType())) {
            throw new InvalidDataException("Primary key with type %s not supported".formatted(entity.getPrimaryKey().getType()));
        }
        return EntityId.of(uuidGenerator.generate());
    }

    @Override
    public void update(@NonNull Application application, @NonNull EntityData data) throws QueryEngineException {
        var dslContext = resolver.resolve(application);
        var entity = getEntity(application, data.getName());
        var table = JOOQUtils.resolveTable(entity);
        var primaryKey = JOOQUtils.resolvePrimaryKey(entity);
        var id = data.getId();
        if (id == null) {
            throw new InvalidDataException("No entity id provided");
        }

        UpdateSetFirstStep<?> update = dslContext.update(table);
        UpdateSetMoreStep<?> step = null;

        var list = EntityDataConverter.convert(data, entity);
        for (var entry : list) {
            if (step == null) {
                step = update.set(entry.field(), entry.value());
            } else {
                step = step.set(entry.field(), entry.value());
            }
        }

        if (step == null) {
            throw new InvalidDataException("Provided data is empty");
        }

        try {
            var updated = step.where(primaryKey.eq(id.getValue()))
                    .execute();

            if (updated == 0) {
                throw new EntityNotFoundException("Entity with primary key '%s' not found".formatted(id));
            }
        } catch (DuplicateKeyException e) {
            throw new ConstraintViolationException("Provided value for unique field already exists" + e.getMessage(), e);
        } catch (DataIntegrityViolationException | IntegrityConstraintViolationException e) {
            throw new ConstraintViolationException(e.getMessage(), e);
        }
    }

    @Override
    public void delete(@NonNull Application application, @NonNull Entity entity, @NonNull EntityId id)
            throws QueryEngineException {
        var dslContext = resolver.resolve(application);
        var table = JOOQUtils.resolveTable(entity);
        var primaryKey = JOOQUtils.resolvePrimaryKey(entity);

        // TODO: ACC-2059: Try deleting relations first?

        var deleted = dslContext.deleteFrom(table)
                .where(primaryKey.eq(id.getValue()))
                .execute();

        if (deleted == 0) {
            throw new EntityNotFoundException("Entity with primary key '%s' not found".formatted(id));
        }
    }

    @Override
    public void deleteAll(@NonNull Application application, @NonNull Entity entity) throws QueryEngineException {
        var dslContext = resolver.resolve(application);
        var table = JOOQUtils.resolveTable(entity);

        dslContext.deleteFrom(table).execute();
    }

    @Override
    public boolean isLinked(@NonNull Application application, @NonNull Relation relation, @NonNull EntityId sourceId,
            @NonNull EntityId targetId) throws QueryEngineException {
        var dslContext = resolver.resolve(application);
        var sourceEntity = relation.getSourceEndPoint().getEntity();
        var targetEntity = relation.getTargetEndPoint().getEntity();

        return switch (relation) {
            case SourceOneToOneRelation oneToOneRelation -> {
                var table = JOOQUtils.resolveTable(sourceEntity);
                var field = (Field<UUID>) JOOQUtils.resolveField(oneToOneRelation.getTargetReference(),
                        targetEntity.getPrimaryKey().getType(), oneToOneRelation.getSourceEndPoint().isRequired());
                var primaryKey = JOOQUtils.resolvePrimaryKey(sourceEntity);

                yield dslContext.fetchExists(DSL.selectOne()
                        .from(table)
                        .where(DSL.and(primaryKey.eq(sourceId.getValue()), field.eq(targetId.getValue())))
                );
            }
            case ManyToOneRelation manyToOneRelation -> {
                var table = JOOQUtils.resolveTable(sourceEntity);
                var field = (Field<UUID>) JOOQUtils.resolveField(manyToOneRelation.getTargetReference(),
                        targetEntity.getPrimaryKey().getType(), manyToOneRelation.getSourceEndPoint().isRequired());
                var primaryKey = JOOQUtils.resolvePrimaryKey(sourceEntity);

                yield dslContext.fetchExists(DSL.selectOne()
                        .from(table)
                        .where(DSL.and(primaryKey.eq(sourceId.getValue()), field.eq(targetId.getValue())))
                );
            }
            case TargetOneToOneRelation oneToOneRelation -> {
                var table = JOOQUtils.resolveTable(targetEntity);
                var field = (Field<UUID>) JOOQUtils.resolveField(oneToOneRelation.getSourceReference(),
                        sourceEntity.getPrimaryKey().getType(), oneToOneRelation.getTargetEndPoint().isRequired());
                var primaryKey = JOOQUtils.resolvePrimaryKey(targetEntity);

                yield dslContext.fetchExists(DSL.selectOne()
                        .from(table)
                        .where(DSL.and(primaryKey.eq(targetId.getValue()), field.eq(sourceId.getValue())))
                );
            }
            case OneToManyRelation oneToManyRelation -> {
                var table = JOOQUtils.resolveTable(targetEntity);
                var field = (Field<UUID>) JOOQUtils.resolveField(oneToManyRelation.getSourceReference(),
                        sourceEntity.getPrimaryKey().getType(),
                        oneToManyRelation.getTargetEndPoint().isRequired());
                var primaryKey = JOOQUtils.resolvePrimaryKey(targetEntity);

                yield dslContext.fetchExists(DSL.selectOne()
                        .from(table)
                        .where(DSL.and(primaryKey.eq(targetId.getValue()), field.eq(sourceId.getValue())))
                );
            }
            case ManyToManyRelation manyToManyRelation -> {
                var table = JOOQUtils.resolveTable(manyToManyRelation.getJoinTable());
                var sourceRef = (Field<UUID>) JOOQUtils.resolveField(manyToManyRelation.getSourceReference(),
                        sourceEntity.getPrimaryKey().getType(), true);
                var targetRef = (Field<UUID>) JOOQUtils.resolveField(manyToManyRelation.getTargetReference(),
                        targetEntity.getPrimaryKey().getType(), true);

                yield dslContext.fetchExists(DSL.selectOne()
                        .from(table)
                        .where(DSL.and(sourceRef.eq(sourceId.getValue()), targetRef.eq(targetId.getValue())))
                );
            }
        };
    }

    @Override
    public RelationData findLink(@NonNull Application application, @NonNull Relation relation, @NonNull EntityId id)
            throws QueryEngineException {
        var dslContext = resolver.resolve(application);
        var sourceEntity = relation.getSourceEndPoint().getEntity();
        var targetEntity = relation.getTargetEndPoint().getEntity();

        return switch (relation) {
            case SourceOneToOneRelation oneToOneRelation -> {
                var table = JOOQUtils.resolveTable(sourceEntity);
                var field = (Field<UUID>) JOOQUtils.resolveField(oneToOneRelation.getTargetReference(),
                        targetEntity.getPrimaryKey().getType(), oneToOneRelation.getSourceEndPoint().isRequired());
                var primaryKey = JOOQUtils.resolvePrimaryKey(sourceEntity);

                var result = dslContext.select(field)
                        .from(table)
                        .where(primaryKey.eq(id.getValue()))
                        .fetchOne();

                yield XToOneRelationData.builder()
                        .entity(sourceEntity.getName())
                        .name(relation.getSourceEndPoint().getName())
                        .ref(result == null || result.value1() == null? null : EntityId.of(result.value1()))
                        .build();
            }
            case ManyToOneRelation manyToOneRelation -> {
                var table = JOOQUtils.resolveTable(sourceEntity);
                var field = (Field<UUID>) JOOQUtils.resolveField(manyToOneRelation.getTargetReference(),
                        targetEntity.getPrimaryKey().getType(), manyToOneRelation.getSourceEndPoint().isRequired());
                var primaryKey = JOOQUtils.resolvePrimaryKey(sourceEntity);

                var result = dslContext.select(field)
                        .from(table)
                        .where(primaryKey.eq(id.getValue()))
                        .fetchOne();

                yield XToOneRelationData.builder()
                        .entity(sourceEntity.getName())
                        .name(relation.getSourceEndPoint().getName())
                        .ref(result == null || result.value1() == null? null : EntityId.of(result.value1()))
                        .build();
            }
            case TargetOneToOneRelation oneToOneRelation -> {
                var table = JOOQUtils.resolveTable(targetEntity);
                var field = (Field<UUID>) JOOQUtils.resolveField(oneToOneRelation.getSourceReference(),
                        sourceEntity.getPrimaryKey().getType(), oneToOneRelation.getTargetEndPoint().isRequired());
                var primaryKey = JOOQUtils.resolvePrimaryKey(targetEntity);

                var result = dslContext.select(primaryKey)
                        .from(table)
                        .where(field.eq(id.getValue()))
                        .fetchOne();

                yield XToOneRelationData.builder()
                        .entity(sourceEntity.getName())
                        .name(relation.getSourceEndPoint().getName())
                        .ref(result == null || result.value1() == null? null : EntityId.of(result.value1()))
                        .build();
            }
            case OneToManyRelation oneToManyRelation -> {
                var table = JOOQUtils.resolveTable(targetEntity);
                var field = (Field<UUID>) JOOQUtils.resolveField(oneToManyRelation.getSourceReference(),
                        sourceEntity.getPrimaryKey().getType(),
                        oneToManyRelation.getTargetEndPoint().isRequired());
                var primaryKey = JOOQUtils.resolvePrimaryKey(targetEntity);

                var results = dslContext.select(primaryKey)
                        .from(table)
                        .where(field.eq(id.getValue()))
                        .fetch()
                        .getValues(primaryKey)
                        .stream()
                        .map(EntityId::of)
                        .toList();

                // TODO: paging or throw exception?

                yield XToManyRelationData.builder()
                        .entity(sourceEntity.getName())
                        .name(relation.getSourceEndPoint().getName())
                        .refs(results)
                        .build();
            }
            case ManyToManyRelation manyToManyRelation -> {
                var table = JOOQUtils.resolveTable(manyToManyRelation.getJoinTable());
                var sourceRef = (Field<UUID>) JOOQUtils.resolveField(manyToManyRelation.getSourceReference(),
                        sourceEntity.getPrimaryKey().getType(), true);
                var targetRef = (Field<UUID>) JOOQUtils.resolveField(manyToManyRelation.getTargetReference(),
                        targetEntity.getPrimaryKey().getType(), true);

                var results = dslContext.select(targetRef)
                        .from(table)
                        .where(sourceRef.eq(id.getValue()))
                        .fetch()
                        .getValues(targetRef)
                        .stream()
                        .map(EntityId::of)
                        .toList();

                // TODO: paging or throw exception?

                yield XToManyRelationData.builder()
                        .entity(sourceEntity.getName())
                        .name(relation.getSourceEndPoint().getName())
                        .refs(results)
                        .build();
            }
        };
    }

    @Override
    public void setLink(@NonNull Application application, @NonNull RelationData data, @NonNull EntityId id)
            throws QueryEngineException {
        var dslContext = resolver.resolve(application);
        var relation = getRelation(application, data);
        var sourceEntity = relation.getSourceEndPoint().getEntity();
        var targetEntity = relation.getTargetEndPoint().getEntity();

        switch (data) {
            case XToOneRelationData xToOneRelationData -> {
                switch (relation) {
                    case SourceOneToOneRelation oneToOneRelation -> {
                        var table = JOOQUtils.resolveTable(sourceEntity);
                        var field = (Field<UUID>) JOOQUtils.resolveField(oneToOneRelation.getTargetReference(),
                                targetEntity.getPrimaryKey().getType(), oneToOneRelation.getSourceEndPoint().isRequired());
                        var primaryKey = JOOQUtils.resolvePrimaryKey(sourceEntity);

                        var updated = dslContext.update(table)
                                .set(field, xToOneRelationData.getRef().getValue())
                                .where(primaryKey.eq(id.getValue()))
                                .execute();

                        if (updated == 0) {
                            throw new EntityNotFoundException(
                                    "Entity with primary key '%s' not found".formatted(id));
                        }
                    }
                    case ManyToOneRelation manyToOneRelation -> {
                        var table = JOOQUtils.resolveTable(sourceEntity);
                        var field = (Field<UUID>) JOOQUtils.resolveField(manyToOneRelation.getTargetReference(),
                                targetEntity.getPrimaryKey().getType(), manyToOneRelation.getSourceEndPoint().isRequired());
                        var primaryKey = JOOQUtils.resolvePrimaryKey(sourceEntity);

                        var updated = dslContext.update(table)
                                .set(field, xToOneRelationData.getRef().getValue())
                                .where(primaryKey.eq(id.getValue()))
                                .execute();

                        if (updated == 0) {
                            throw new EntityNotFoundException("Entity with primary key '%s' not found".formatted(id));
                        }
                    }
                    case TargetOneToOneRelation oneToOneRelation -> {
                        var table = JOOQUtils.resolveTable(targetEntity);
                        var field = (Field<UUID>) JOOQUtils.resolveField(oneToOneRelation.getSourceReference(),
                                sourceEntity.getPrimaryKey().getType(), oneToOneRelation.getTargetEndPoint().isRequired());
                        var primaryKey = JOOQUtils.resolvePrimaryKey(targetEntity);

                        var updated = dslContext.update(table)
                                .set(field, id.getValue())
                                .where(primaryKey.eq(xToOneRelationData.getRef().getValue()))
                                .execute();

                        if (updated == 0) {
                            throw new EntityNotFoundException("Entity with primary key '%s' not found".formatted(xToOneRelationData.getRef()));
                        }
                    }
                    default -> throw new InvalidDataException("Relation '%s' is not a one-to-one or many-to-one relation".formatted(data.getName()));
                }
            }
            case XToManyRelationData xToManyRelationData -> {
                switch (relation) {
                    case OneToManyRelation oneToManyRelation -> {
                        var table = JOOQUtils.resolveTable(targetEntity);
                        var field = (Field<UUID>) JOOQUtils.resolveField(oneToManyRelation.getSourceReference(),
                                sourceEntity.getPrimaryKey().getType(),
                                oneToManyRelation.getTargetEndPoint().isRequired());
                        var primaryKey = JOOQUtils.resolvePrimaryKey(targetEntity);
                        var refs = xToManyRelationData.getRefs().stream()
                                .map(EntityId::getValue)
                                .toList();

                        var updated = dslContext.update(table)
                                .set(field, id.getValue())
                                .where(primaryKey.in(refs))
                                .execute();

                        // delete first might be simpler, but what if inverse many-to-one is required?
                        dslContext.update(table)
                                .set(field, (UUID) null)
                                .where(DSL.and(field.eq(id.getValue()), primaryKey.notIn(refs)))
                                .execute();

                        if (updated < refs.size()) {
                            throw new EntityNotFoundException("Some entities from provided data not found");
                        }
                    }
                    case ManyToManyRelation manyToManyRelation -> {
                        var table = JOOQUtils.resolveTable(manyToManyRelation.getJoinTable());
                        var sourceRef = (Field<UUID>) JOOQUtils.resolveField(manyToManyRelation.getSourceReference(),
                                sourceEntity.getPrimaryKey().getType(), true);
                        var targetRef = (Field<UUID>) JOOQUtils.resolveField(manyToManyRelation.getTargetReference(),
                                targetEntity.getPrimaryKey().getType(), true);

                        var step = dslContext.with("deleted")
                                .as(DSL.deleteFrom(table)
                                        .where(sourceRef.eq(id.getValue()))
                                        .returningResult())
                                .insertInto(table, sourceRef, targetRef);

                        for (var ref : xToManyRelationData.getRefs()) {
                            step = step.values(id.getValue(), ref.getValue());
                        }

                        step.execute();

                    }
                    default -> throw new InvalidDataException("Relation '%s' is not a one-to-many or many-to-many relation".formatted(data.getName()));
                }
            }
        }
    }

    @Override
    public void unsetLink(@NonNull Application application, @NonNull Relation relation, @NonNull EntityId id)
            throws QueryEngineException {
        var dslContext = resolver.resolve(application);
        var sourceEntity = relation.getSourceEndPoint().getEntity();
        var targetEntity = relation.getTargetEndPoint().getEntity();

        switch (relation) {
            case SourceOneToOneRelation oneToOneRelation -> {
                var table = JOOQUtils.resolveTable(sourceEntity);
                var field = (Field<UUID>) JOOQUtils.resolveField(oneToOneRelation.getTargetReference(),
                        targetEntity.getPrimaryKey().getType(), oneToOneRelation.getSourceEndPoint().isRequired());
                var primaryKey = JOOQUtils.resolvePrimaryKey(sourceEntity);

                var updated = dslContext.update(table)
                        .set(field, (UUID) null)
                        .where(primaryKey.eq(id.getValue()))
                        .execute();

                if (updated == 0) {
                    throw new EntityNotFoundException("Entity with primary key '%s' not found".formatted(id));
                }
            }
            case ManyToOneRelation manyToOneRelation -> {
                var table = JOOQUtils.resolveTable(sourceEntity);
                var field = (Field<UUID>) JOOQUtils.resolveField(manyToOneRelation.getTargetReference(),
                        targetEntity.getPrimaryKey().getType(), manyToOneRelation.getSourceEndPoint().isRequired());
                var primaryKey = JOOQUtils.resolvePrimaryKey(sourceEntity);

                var updated = dslContext.update(table)
                        .set(field, (UUID) null)
                        .where(primaryKey.eq(id.getValue()))
                        .execute();

                if (updated == 0) {
                    throw new EntityNotFoundException("Entity with primary key '%s' not found".formatted(id));
                }
            }
            case TargetOneToOneRelation oneToOneRelation -> {
                var table = JOOQUtils.resolveTable(targetEntity);
                var field = (Field<UUID>) JOOQUtils.resolveField(oneToOneRelation.getSourceReference(),
                        sourceEntity.getPrimaryKey().getType(), oneToOneRelation.getTargetEndPoint().isRequired());

                var updated = dslContext.update(table)
                        .set(field, (UUID) null)
                        .where(field.eq(id.getValue()))
                        .execute();

                if (updated == 0) {
                    throw new EntityNotFoundException("No entity '%s' found with a relation to entity '%s' with primary key '%s'"
                            .formatted(targetEntity.getName(), sourceEntity.getName(), id));
                }
            }
            case OneToManyRelation oneToManyRelation -> {
                var table = JOOQUtils.resolveTable(targetEntity);
                var field = (Field<UUID>) JOOQUtils.resolveField(oneToManyRelation.getSourceReference(),
                        sourceEntity.getPrimaryKey().getType(),
                        oneToManyRelation.getTargetEndPoint().isRequired());

                var updated = dslContext.update(table)
                        .set(field, (UUID) null)
                        .where(field.eq(id.getValue()))
                        .execute();

                if (updated == 0) {
                    throw new EntityNotFoundException("No entity '%s' found with a relation to entity '%s' with primary key '%s'"
                            .formatted(targetEntity.getName(), sourceEntity.getName(), id));
                }
            }
            case ManyToManyRelation manyToManyRelation -> {
                var table = JOOQUtils.resolveTable(manyToManyRelation.getJoinTable());
                var sourceRef = (Field<UUID>) JOOQUtils.resolveField(manyToManyRelation.getSourceReference(),
                        sourceEntity.getPrimaryKey().getType(), true);

                var deleted = dslContext.deleteFrom(table)
                                .where(sourceRef.eq(id.getValue()))
                                .execute();

                if (deleted == 0) {
                    throw new EntityNotFoundException("No entity '%s' found with a relation to entity '%s' with primary key '%s'"
                            .formatted(targetEntity.getName(), sourceEntity.getName(), id));
                }
            }
        }
    }

    @Override
    public void addLinks(@NonNull Application application, @NonNull XToManyRelationData data, @NonNull EntityId id)
            throws QueryEngineException {
        var dslContext = resolver.resolve(application);
        var relation = getRelation(application, data);
        var sourceEntity = relation.getSourceEndPoint().getEntity();
        var targetEntity = relation.getTargetEndPoint().getEntity();

        switch (relation) {
            case OneToManyRelation oneToManyRelation -> {
                var table = JOOQUtils.resolveTable(targetEntity);
                var field = (Field<UUID>) JOOQUtils.resolveField(oneToManyRelation.getSourceReference(),
                        sourceEntity.getPrimaryKey().getType(),
                        oneToManyRelation.getTargetEndPoint().isRequired());
                var primaryKey = JOOQUtils.resolvePrimaryKey(targetEntity);
                var refs = data.getRefs().stream()
                        .map(EntityId::getValue)
                        .toList();

                var updated = dslContext.update(table)
                        .set(field, id.getValue())
                        .where(primaryKey.in(refs))
                        .execute();

                if (updated < refs.size()) {
                    throw new EntityNotFoundException("Some entities from provided data not found");
                }
            }
            case ManyToManyRelation manyToManyRelation -> {
                var table = JOOQUtils.resolveTable(manyToManyRelation.getJoinTable());
                var sourceRef = (Field<UUID>) JOOQUtils.resolveField(manyToManyRelation.getSourceReference(),
                        sourceEntity.getPrimaryKey().getType(), true);
                var targetRef = (Field<UUID>) JOOQUtils.resolveField(manyToManyRelation.getTargetReference(),
                        targetEntity.getPrimaryKey().getType(), true);

                var step = dslContext.insertInto(table, sourceRef, targetRef);

                for (var ref : data.getRefs()) {
                    step = step.values(id.getValue(), ref.getValue());
                }

                step.execute();
            }
            default -> throw new InvalidDataException("Relation '%s' of entity '%s' is not a one-to-many or many-to-many relation."
                    .formatted(data.getName(), sourceEntity.getName()));
        }
    }

    @Override
    public void removeLinks(@NonNull Application application, @NonNull XToManyRelationData data, @NonNull EntityId id)
            throws QueryEngineException {
        var dslContext = resolver.resolve(application);
        var relation = getRelation(application, data);
        var sourceEntity = relation.getSourceEndPoint().getEntity();
        var targetEntity = relation.getTargetEndPoint().getEntity();

        switch (relation) {
            case OneToManyRelation oneToManyRelation -> {
                var table = JOOQUtils.resolveTable(targetEntity);
                var field = (Field<UUID>) JOOQUtils.resolveField(oneToManyRelation.getSourceReference(),
                        sourceEntity.getPrimaryKey().getType(),
                        oneToManyRelation.getTargetEndPoint().isRequired());

                var updated = dslContext.update(table)
                        .set(field, (UUID) null)
                        .where(field.eq(id.getValue()))
                        .execute();

                if (updated == 0) {
                    throw new EntityNotFoundException("No entity '%s' found with a relation to entity '%s' with primary key '%s'"
                            .formatted(targetEntity.getName(), sourceEntity.getName(), id));
                }
            }
            case ManyToManyRelation manyToManyRelation -> {
                var table = JOOQUtils.resolveTable(manyToManyRelation.getJoinTable());
                var sourceRef = (Field<UUID>) JOOQUtils.resolveField(manyToManyRelation.getSourceReference(),
                        sourceEntity.getPrimaryKey().getType(), true);

                var deleted = dslContext.deleteFrom(table)
                        .where(sourceRef.eq(id.getValue()))
                        .execute();

                if (deleted == 0) {
                    throw new EntityNotFoundException("No entity '%s' found with a relation to entity '%s' with primary key '%s'"
                            .formatted(targetEntity.getName(), sourceEntity.getName(), id));
                }
            }
            default -> throw new InvalidDataException("Relation '%s' of entity '%s' is not a one-to-many or many-to-many relation."
                    .formatted(data.getName(), sourceEntity.getName()));
        }
    }
}
