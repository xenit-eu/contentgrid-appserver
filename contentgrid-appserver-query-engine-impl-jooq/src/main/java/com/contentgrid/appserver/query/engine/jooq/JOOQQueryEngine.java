package com.contentgrid.appserver.query.engine.jooq;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.relations.Relation;
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
import com.contentgrid.appserver.query.engine.api.exception.ConstraintViolationException;
import com.contentgrid.appserver.query.engine.api.exception.EntityNotFoundException;
import com.contentgrid.appserver.query.engine.api.exception.InvalidDataException;
import com.contentgrid.appserver.query.engine.api.exception.QueryEngineException;
import com.contentgrid.appserver.query.engine.jooq.resolver.DSLContextResolver;
import com.contentgrid.thunx.predicates.model.ThunkExpression;
import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochRandomGenerator;
import java.util.List;
import java.util.Optional;
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
            throw new InvalidDataException("Provided data contains primary key, which is auto-generated");
        }

        var step = dslContext.insertInto(table)
                .set(primaryKey, id.getValue());

        var list = EntityDataConverter.convert(data, entity);
        for (var entry : list) {
            step = step.set(entry.field(), entry.value());
        }

        // TODO: ACC-2059: add owning relations to step

        try {
            step.execute();
        } catch (DuplicateKeyException e) {
            throw new ConstraintViolationException("Provided value for unique field already exists" + e.getMessage(), e);
        } catch (DataIntegrityViolationException | IntegrityConstraintViolationException e) {
            throw new ConstraintViolationException(e.getMessage(), e);
        }

        // TODO: ACC-2059: add relations owned by other entities

        return id;
    }

    private Entity getEntity(Application application, EntityName entityName) {
        return application.getEntityByName(entityName)
                .orElseThrow(() -> new InvalidDataException("Entity '%s' not found in application '%s'"
                        .formatted(entityName, application.getName())));
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

        if (id == null) {
            throw new InvalidDataException("Provided data does not contain primary key attribute '%s'"
                    .formatted(entity.getPrimaryKey().getName()));
        } else if (step == null) {
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
        return false; // TODO: ACC-2059
    }

    @Override
    public RelationData findLink(@NonNull Application application, @NonNull Relation relation, @NonNull EntityId id)
            throws QueryEngineException {
        return null; // TODO: ACC-2059
    }

    @Override
    public void setLink(@NonNull Application application, @NonNull RelationData data, @NonNull EntityId id)
            throws QueryEngineException {
        // TODO: ACC-2059
    }

    @Override
    public void unsetLink(@NonNull Application application, @NonNull Relation relation, @NonNull EntityId id)
            throws QueryEngineException {
        // TODO: ACC-2059
    }

    @Override
    public void addLinks(@NonNull Application application, @NonNull XToManyRelationData<?> data, @NonNull EntityId id)
            throws QueryEngineException {
        // TODO: ACC-2059
    }

    @Override
    public void removeLinks(@NonNull Application application, @NonNull XToManyRelationData<?> data, @NonNull EntityId id)
            throws QueryEngineException {
        // TODO: ACC-2059
    }
}
