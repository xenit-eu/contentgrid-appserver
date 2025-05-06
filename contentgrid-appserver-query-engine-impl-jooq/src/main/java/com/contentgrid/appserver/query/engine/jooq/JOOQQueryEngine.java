package com.contentgrid.appserver.query.engine.jooq;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.application.model.values.TableName;
import com.contentgrid.appserver.query.engine.api.QueryEngine;
import com.contentgrid.appserver.query.engine.api.data.EntityData;
import com.contentgrid.appserver.query.engine.api.data.PageData;
import com.contentgrid.appserver.query.engine.api.data.RelationData;
import com.contentgrid.appserver.query.engine.api.data.SliceData;
import com.contentgrid.appserver.query.engine.api.data.SliceData.PageInfo;
import com.contentgrid.appserver.query.engine.api.exception.ConstraintViolationException;
import com.contentgrid.appserver.query.engine.api.exception.EntityNotFoundException;
import com.contentgrid.appserver.query.engine.api.exception.InvalidDataException;
import com.contentgrid.appserver.query.engine.api.exception.QueryEngineException;
import com.contentgrid.appserver.query.engine.jooq.resolver.DSLContextResolver;
import com.contentgrid.thunx.predicates.model.ThunkExpression;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
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

    @Override
    public SliceData findAll(@NonNull Application application, @NonNull Entity entity,
            @NonNull ThunkExpression<Boolean> expression, PageData pageData) throws QueryEngineException {
        var dslContext = resolver.resolve(application); // TODO: is it part of the transaction?
        var context = new JOOQThunkExpressionVisitor.JOOQContext(application, entity);
        var alias = context.getRootAlias();
        var table = JOOQUtils.resolveTable(entity, alias);

        var condition = (Condition) expression.accept(visitor, context);
        var results = dslContext.selectFrom(table)
                .where(condition)
                .fetch()
                .intoMaps();

        return SliceData.builder()
                .entities(results.stream()
                        .map(result -> EntityDataMapper.from(entity, result))
                        .toList())
                .pageInfo(PageInfo.builder()
                        // TODO: ACC-2048 support paging
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
    public Optional<EntityData> findById(@NonNull Application application, @NonNull Entity entity, @NonNull Object id) {
        var dslContext = resolver.resolve(application); // TODO: is it part of the transaction?
        var alias = getRootAlias(entity);
        var table = JOOQUtils.resolveTable(entity, alias);
        var primaryKey = (Field<Object>) JOOQUtils.resolvePrimaryKey(alias, entity);

        return dslContext.selectFrom(table)
                .where(primaryKey.eq(id))
                .fetchOptional()
                .map(Record::intoMap)
                .map(result -> EntityDataMapper.from(entity, result));
    }

    @Override
    public Object create(@NonNull Application application, @NonNull EntityData data,
            @NonNull List<RelationData> relations) throws QueryEngineException {
        var dslContext = resolver.resolve(application); // TODO: is it part of the transaction?
        var entity = application.getEntityByName(data.getName())
                .orElseThrow(() -> new InvalidDataException("Entity '%s' not found in application '%s'"
                        .formatted(data.getName(), application.getName())));
        var table = DSL.table(entity.getTable().getValue());
        var primaryKey = (Field<Object>) JOOQUtils.resolvePrimaryKey(entity);
        var id = generateId(entity);

        var step = dslContext.insertInto(table)
                .set(primaryKey, id);

        var list = EntityDataConverter.convert(data, entity);
        for (var entry : list) {
            if (primaryKey.equals(entry.field())) {
                throw new InvalidDataException("Provided data contains primary key, which is auto-generated");
            }
            step = step.set(entry.field(), entry.value());
        }

        // TODO: add owning relations to step

        try {
            step.execute();
        } catch (DuplicateKeyException e) {
            throw new ConstraintViolationException("Provided value for unique field already exists" + e.getMessage(), e);
        } catch (DataIntegrityViolationException | IntegrityConstraintViolationException e) {
            throw new ConstraintViolationException(e.getMessage(), e);
        }

        // TODO: add relations owned by other entities

        return id;
    }

    private Object generateId(Entity entity) {
        return switch (entity.getPrimaryKey().getType()) {
            case UUID -> UUID.randomUUID();
            case TEXT -> UUID.randomUUID().toString();
            default -> throw new InvalidDataException("Primary key with type %s not supported".formatted(entity.getPrimaryKey().getType()));
        };
    }

    @Override
    public void update(@NonNull Application application, @NonNull EntityData data) throws QueryEngineException {
        var dslContext = resolver.resolve(application); // TODO: is it part of the transaction?
        var entity = application.getEntityByName(data.getName())
                .orElseThrow(() -> new InvalidDataException("Entity '%s' not found in application '%s'"
                        .formatted(data.getName(), application.getName())));
        var table = DSL.table(entity.getTable().getValue());
        var primaryKey = (Field<Object>) JOOQUtils.resolvePrimaryKey(entity);

        UpdateSetFirstStep<?> update = dslContext.update(table);
        UpdateSetMoreStep<?> step = null;
        Object id = null;

        var list = EntityDataConverter.convert(data, entity);
        for (var entry : list) {
            if (primaryKey.equals(entry.field())) {
                // Primary key ends up in the where clause.
                id = entry.value();
            } else if (step == null) {
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
            var updated = step.where(primaryKey.eq(id))
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
    public void delete(@NonNull Application application, @NonNull Entity entity, @NonNull Object id)
            throws QueryEngineException {
        var dslContext = resolver.resolve(application); // TODO: is it part of the transaction?
        var table = DSL.table(entity.getTable().getValue());
        var primaryKey = (Field<Object>) JOOQUtils.resolvePrimaryKey(entity);

        // TODO: Try deleting relations first?

        var deleted = dslContext.deleteFrom(table)
                .where(primaryKey.eq(id))
                .execute();

        if (deleted == 0) {
            throw new EntityNotFoundException("Entity with primary key '%s' not found".formatted(id));
        }
    }

    @Override
    public void deleteAll(@NonNull Application application, @NonNull Entity entity) throws QueryEngineException {
        var dslContext = resolver.resolve(application); // TODO: is it part of the transaction?
        var table = DSL.table(entity.getTable().getValue());

        dslContext.deleteFrom(table).execute();
    }

    @Override
    public void setLink(@NonNull Application application, @NonNull Entity entity, @NonNull Object id,
            @NonNull RelationData data) throws QueryEngineException {

    }

    @Override
    public void unsetLink(@NonNull Application application, @NonNull Entity entity, @NonNull Object id,
            @NonNull Relation relation) throws QueryEngineException {

    }

    @Override
    public void addLink(@NonNull Application application, @NonNull Entity entity, @NonNull Object id,
            @NonNull RelationData data) throws QueryEngineException {

    }

    @Override
    public void removeLink(@NonNull Application application, @NonNull Entity entity, @NonNull Object id,
            @NonNull RelationData data) throws QueryEngineException {

    }
}
