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
import com.contentgrid.appserver.application.model.values.AttributePath;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.RelationName;
import com.contentgrid.appserver.application.model.values.TableName;
import com.contentgrid.appserver.query.engine.api.QueryEngine;
import com.contentgrid.appserver.query.engine.api.UpdateResult;
import com.contentgrid.appserver.query.engine.api.data.EntityCreateData;
import com.contentgrid.appserver.query.engine.api.data.EntityData;
import com.contentgrid.appserver.query.engine.api.data.EntityId;
import com.contentgrid.appserver.query.engine.api.data.PageData;
import com.contentgrid.appserver.query.engine.api.data.RelationData;
import com.contentgrid.appserver.query.engine.api.data.SliceData;
import com.contentgrid.appserver.query.engine.api.data.SliceData.PageInfo;
import com.contentgrid.appserver.query.engine.api.data.SortData;
import com.contentgrid.appserver.query.engine.api.data.SortData.FieldSort;
import com.contentgrid.appserver.query.engine.api.data.XToManyRelationData;
import com.contentgrid.appserver.query.engine.api.data.XToOneRelationData;
import com.contentgrid.appserver.query.engine.api.exception.ConstraintViolationException;
import com.contentgrid.appserver.query.engine.api.exception.EntityNotFoundException;
import com.contentgrid.appserver.query.engine.api.exception.InvalidDataException;
import com.contentgrid.appserver.query.engine.api.exception.QueryEngineException;
import com.contentgrid.appserver.query.engine.jooq.JOOQThunkExpressionVisitor.JOOQContext;
import com.contentgrid.appserver.query.engine.jooq.resolver.DSLContextResolver;
import com.contentgrid.appserver.query.engine.jooq.strategy.JOOQRelationStrategyFactory;
import com.contentgrid.thunx.predicates.model.ThunkExpression;
import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochRandomGenerator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.Record;
import org.jooq.SortField;
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
            @NonNull ThunkExpression<Boolean> expression, SortData sortData, PageData pageData) throws QueryEngineException {
        var dslContext = resolver.resolve(application);
        var context = new JOOQContext(application, entity);
        var alias = context.getRootAlias();
        var table = JOOQUtils.resolveTable(entity, alias);
        var orderBy = sortData != null
                ? sortData.getSortedFields().stream().map(field -> convert(entity, field)).toList()
                : List.<OrderField<?>>of();

        var condition = DSL.condition((Field<Boolean>) expression.accept(visitor, context));
        var results = dslContext.selectFrom(table)
                .where(condition)
                .orderBy(orderBy)
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

    private static SortField<Object> convert(Entity entity, FieldSort field) {
        var path = entity.getSortableFieldByName(field.getName()).orElseThrow().getPropertyPath();
        if (!(path instanceof AttributePath attrPath)) {
            throw new IllegalArgumentException("Sorting by complex property paths is not supported.");
        }
        var attr = entity.getAttributeByName(attrPath.getFirst()).orElseThrow();
        // TODO multi-column attributes should get some way to mark which one is suitable for sorting...
        var dslField = DSL.field(attr.getColumns().getFirst().getValue());
        return switch (field.getDirection()) {
            case ASC -> dslField.asc();
            case DESC -> dslField.desc();
        };
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
    public EntityData create(@NonNull Application application, @NonNull EntityCreateData data) throws QueryEngineException {
        var dslContext = resolver.resolve(application);
        var entity = getRequiredEntity(application, data.getEntityName());
        var table = JOOQUtils.resolveTable(entity);
        var primaryKey = JOOQUtils.resolvePrimaryKey(entity);
        var id = generateId(entity);

        var step = dslContext.insertInto(table)
                .set(primaryKey, id.getValue());

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

            switch (relationData) {
                case XToOneRelationData xToOneRelationData -> {
                    // add to step if owning relation, otherwise add to non-owning relations
                    if (relation instanceof SourceOneToOneRelation || relation instanceof ManyToOneRelation) {
                        var strategy = JOOQRelationStrategyFactory.forToOneRelation(relation);
                        var targetRef = strategy.getTargetRef(relation);
                        step = step.set(targetRef, xToOneRelationData.getRef().getValue());
                    } else if (relation instanceof TargetOneToOneRelation) {
                        nonOwningRelations.add(relationData);
                    } else {
                        throw new InvalidDataException("Relation '%s' is not a one-to-one or many-to-one relation"
                                .formatted(relationData.getName()));
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
            switch (relationData) {
                case XToOneRelationData xToOneRelationData -> this.setLink(application, relation, id, xToOneRelationData.getRef());
                case XToManyRelationData xToManyRelationData -> this.addLinks(application, relation, id, xToManyRelationData.getRefs());
            }
        }

        return insertedData;
    }

    private Entity getRequiredEntity(Application application, EntityName entityName) throws InvalidDataException {
        try {
            return application.getRequiredEntityByName(entityName);
        } catch (com.contentgrid.appserver.application.model.exceptions.EntityNotFoundException e) {
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
    public UpdateResult update(@NonNull Application application, @NonNull EntityData data) throws QueryEngineException {
        var dslContext = resolver.resolve(application);
        var entity = getRequiredEntity(application, data.getName());
        var table = JOOQUtils.resolveTable(entity);
        var primaryKey = JOOQUtils.resolvePrimaryKey(entity);
        var id = data.getId();

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
            var oldValue = findById(application, entity, data.getId())
                    .orElseThrow(() -> new EntityNotFoundException("Entity with primary key '%s' not found".formatted(id)));

            var newValue = step.where(primaryKey.eq(id.getValue()))
                    .returning(JOOQUtils.resolveAttributeFields(entity))
                    .fetchOptionalMap()
                    .map(result -> EntityDataMapper.from(entity, result))
                    .orElseThrow(() -> new EntityNotFoundException("Entity with primary key '%s' was not updated".formatted(id)));

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

    @Override
    public Optional<EntityData> delete(@NonNull Application application, @NonNull Entity entity, @NonNull EntityId id)
            throws QueryEngineException {
        var dslContext = resolver.resolve(application);
        var table = JOOQUtils.resolveTable(entity);
        var primaryKey = JOOQUtils.resolvePrimaryKey(entity);

        try {
            return dslContext.deleteFrom(table)
                    .where(primaryKey.eq(id.getValue()))
                    .returning(JOOQUtils.resolveAttributeFields(entity))
                    .fetchOptionalMap()
                    .map(result -> EntityDataMapper.from(entity, result));

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

    @Override
    public boolean isLinked(@NonNull Application application, @NonNull Relation relation, @NonNull EntityId sourceId,
            @NonNull EntityId targetId) throws QueryEngineException {
        var dslContext = resolver.resolve(application);
        var strategy = JOOQRelationStrategyFactory.forRelation(relation);
        return strategy.isLinked(dslContext, relation, sourceId, targetId);
    }

    @Override
    public Optional<EntityId> findTarget(@NonNull Application application, @NonNull Relation relation, @NonNull EntityId id)
            throws QueryEngineException {
        var dslContext = resolver.resolve(application);
        var strategy = JOOQRelationStrategyFactory.forToOneRelation(relation);
        return strategy.findTarget(dslContext, relation, id);
    }

    @Override
    public void setLink(@NonNull Application application, @NonNull Relation relation, @NonNull EntityId id, @NonNull EntityId targetId)
            throws QueryEngineException {
        var dslContext = resolver.resolve(application);
        var strategy = JOOQRelationStrategyFactory.forToOneRelation(relation);
        strategy.create(dslContext, relation, id, targetId);
    }

    @Override
    public void unsetLink(@NonNull Application application, @NonNull Relation relation, @NonNull EntityId id)
            throws QueryEngineException {
        var dslContext = resolver.resolve(application);
        var strategy = JOOQRelationStrategyFactory.forRelation(relation);
        strategy.delete(dslContext, relation, id);
    }

    @Override
    public void addLinks(@NonNull Application application, @NonNull Relation relation, @NonNull EntityId id, @NonNull Set<EntityId> targetIds)
            throws QueryEngineException {
        var dslContext = resolver.resolve(application);
        var strategy = JOOQRelationStrategyFactory.forToManyRelation(relation);
        strategy.add(dslContext, relation, id, targetIds);
    }

    @Override
    public void removeLinks(@NonNull Application application, @NonNull Relation relation, @NonNull EntityId id, @NonNull Set<EntityId> targetIds)
            throws QueryEngineException {
        var dslContext = resolver.resolve(application);
        var strategy = JOOQRelationStrategyFactory.forToManyRelation(relation);
        strategy.remove(dslContext, relation, id, targetIds);
    }
}
