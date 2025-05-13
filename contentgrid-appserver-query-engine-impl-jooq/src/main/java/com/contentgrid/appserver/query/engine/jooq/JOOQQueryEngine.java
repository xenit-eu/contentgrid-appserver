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
import com.contentgrid.appserver.application.model.values.RelationName;
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
import java.util.HashSet;
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
        var processedRelations = new HashSet<RelationName>();

        for (var relationData : relations) {
            if (!entity.getName().equals(relationData.getEntity())) {
                throw new InvalidDataException("Entity '%s' from relation '%s' does not match entity '%s' from data"
                        .formatted(relationData.getEntity(), relationData.getName(), data.getName()));
            }
            if (!processedRelations.add(relationData.getName())) {
                throw new InvalidDataException("Multiple RelationData instances provided for relation '%s'"
                        .formatted(relationData.getName()));
            }
            var relation = getRelation(application, relationData);

            switch (relationData) {
                case XToOneRelationData xToOneRelationData -> {
                    // add to step if owning relation, otherwise add to non-owning relations
                    if (relation instanceof SourceOneToOneRelation || relation instanceof ManyToOneRelation) {
                        if (xToOneRelationData.getRef() == null) {
                            continue; // skip empty relations
                        }
                        var targetRef = JOOQUtils.resolveRelationTargetRef(relation);
                        step = step.set(targetRef, xToOneRelationData.getRef().getValue());
                    } else if (relation instanceof TargetOneToOneRelation) {
                        if (xToOneRelationData.getRef() != null) {
                            // only add non-empty relations
                            nonOwningRelations.add(relationData);
                        }
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

        try {
            step.execute();
        } catch (DuplicateKeyException e) {
            throw new ConstraintViolationException("Provided value for unique field already exists. " + e.getMessage(), e);
        } catch (DataIntegrityViolationException | IntegrityConstraintViolationException e) {
            throw new ConstraintViolationException(e.getMessage(), e); // null for required field or foreign key does not exist
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

        // Delete foreign key references to this item first
        for (var relation : application.getRelationsForSourceEntity(entity)) {
            if (relation instanceof SourceOneToOneRelation || relation instanceof ManyToOneRelation) {
                // Skip foreign key references on the same table row, they will be deleted when the row is deleted
                continue;
            }
            unsetLink(application, relation, id);
        }

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

        // Delete relations on other tables first
        for (var relation : application.getRelationsForSourceEntity(entity)) {
            var relationTable = JOOQUtils.resolveRelationTable(relation);
            var sourceRef = JOOQUtils.resolveRelationSourceRef(relation);
            if (relation instanceof TargetOneToOneRelation || relation instanceof OneToManyRelation) {
                // TODO: not necessary if target = source
                try {
                    dslContext.update(relationTable)
                            .set(sourceRef, (UUID) null)
                            .execute();
                } catch (DataIntegrityViolationException | IntegrityConstraintViolationException e) {
                    throw new ConstraintViolationException(e.getMessage(), e); // inverse relation could be required.
                }
            } else if (relation instanceof ManyToManyRelation) {
                // TODO: many-to-many relations are processed twice if source = target
                dslContext.deleteFrom(relationTable).execute();
            }
        }

        dslContext.deleteFrom(table).execute();
    }

    @Override
    public boolean isLinked(@NonNull Application application, @NonNull Relation relation, @NonNull EntityId sourceId,
            @NonNull EntityId targetId) throws QueryEngineException {
        var dslContext = resolver.resolve(application);
        var table = JOOQUtils.resolveRelationTable(relation);
        var sourceRef = JOOQUtils.resolveRelationSourceRef(relation);
        var targetRef = JOOQUtils.resolveRelationTargetRef(relation);

        return dslContext.fetchExists(DSL.selectOne()
                .from(table)
                .where(DSL.and(sourceRef.eq(sourceId.getValue()), targetRef.eq(targetId.getValue()))));
    }

    @Override
    public RelationData findLink(@NonNull Application application, @NonNull Relation relation, @NonNull EntityId id)
            throws QueryEngineException {
        var dslContext = resolver.resolve(application);
        var table = JOOQUtils.resolveRelationTable(relation);
        var sourceRef = JOOQUtils.resolveRelationSourceRef(relation);
        var targetRef = JOOQUtils.resolveRelationTargetRef(relation);
        var sourceEntity = relation.getSourceEndPoint().getEntity();

        if (relation instanceof OneToManyRelation || relation instanceof ManyToManyRelation) {
            // *-to-many relation
            var results = dslContext.select(targetRef)
                    .from(table)
                    .where(sourceRef.eq(id.getValue()))
                    .fetch()
                    .getValues(targetRef)
                    .stream()
                    .map(EntityId::of)
                    .toList();

            return XToManyRelationData.builder()
                    .entity(sourceEntity.getName())
                    .name(relation.getSourceEndPoint().getName())
                    .refs(results)
                    .build();
        } else {
            // *-to-one relation
            var result = dslContext.select(targetRef)
                    .from(table)
                    .where(sourceRef.eq(id.getValue()))
                    .fetchOne();

            return XToOneRelationData.builder()
                    .entity(sourceEntity.getName())
                    .name(relation.getSourceEndPoint().getName())
                    .ref(result == null || result.value1() == null? null : EntityId.of(result.value1()))
                    .build();
        }
    }

    @Override
    public void setLink(@NonNull Application application, @NonNull RelationData data, @NonNull EntityId id)
            throws QueryEngineException {
        var dslContext = resolver.resolve(application);
        var relation = getRelation(application, data);
        var table = JOOQUtils.resolveRelationTable(relation);
        var sourceRef = JOOQUtils.resolveRelationSourceRef(relation);
        var targetRef = JOOQUtils.resolveRelationTargetRef(relation);

        switch (data) {
            case XToOneRelationData xToOneRelationData -> {
                if (xToOneRelationData.getRef() == null) {
                    // TODO: check if relation is not a *-to-many relation
                    unsetLink(application, relation, id);
                    return;
                }
                switch (relation) {
                    case SourceOneToOneRelation ignored -> {
                        try {
                            var updated = dslContext.update(table)
                                    .set(targetRef, xToOneRelationData.getRef().getValue())
                                    .where(sourceRef.eq(id.getValue()))
                                    .execute();

                            if (updated == 0) {
                                throw new EntityNotFoundException(
                                        "Entity with primary key '%s' not found".formatted(id));
                            }
                        } catch (DuplicateKeyException e) {
                            throw new ConstraintViolationException("Target %s already linked".formatted(xToOneRelationData.getRef()), e);
                        } catch (DataIntegrityViolationException | IntegrityConstraintViolationException e) {
                            throw new ConstraintViolationException(e.getMessage(), e); // also thrown when foreign key was not found
                        }
                    }
                    case ManyToOneRelation ignored -> {
                        try {
                            var updated = dslContext.update(table)
                                    .set(targetRef, xToOneRelationData.getRef().getValue())
                                    .where(sourceRef.eq(id.getValue()))
                                    .execute();

                            if (updated == 0) {
                                throw new EntityNotFoundException(
                                        "Entity with primary key '%s' not found".formatted(id));
                            }
                        } catch (DataIntegrityViolationException | IntegrityConstraintViolationException e) {
                            throw new ConstraintViolationException(e.getMessage(), e); // also thrown when foreign key was not found
                        }
                    }
                    case TargetOneToOneRelation ignored -> {
                        try {
                            var updated = dslContext.update(table)
                                    .set(sourceRef, id.getValue())
                                    .where(targetRef.eq(xToOneRelationData.getRef().getValue()))
                                    .execute();

                            if (updated == 0) {
                                throw new EntityNotFoundException("Entity with primary key '%s' not found".formatted(
                                        xToOneRelationData.getRef()));
                            }
                        } catch (DataIntegrityViolationException | IntegrityConstraintViolationException e) {
                            throw new ConstraintViolationException(e.getMessage(), e); // provided id could not exist
                        }
                    }
                    default -> throw new InvalidDataException("Relation '%s' is not a one-to-one or many-to-one relation".formatted(data.getName()));
                }
            }
            case XToManyRelationData xToManyRelationData -> {
                switch (relation) {
                    case OneToManyRelation oneToManyRelation -> {
                        var refs = xToManyRelationData.getRefs().stream()
                                .map(EntityId::getValue)
                                .toList();

                        // Delete existing links, except if the inverse is required
                        if (oneToManyRelation.getTargetEndPoint().isRequired()) {
                            // Assert existing values are a subset of provided values
                            if (dslContext.fetchExists(
                                    DSL.selectOne()
                                            .from(table)
                                            .where(DSL.and(sourceRef.eq(id.getValue()), targetRef.notIn(refs)))
                            )) {
                                throw new ConstraintViolationException(
                                        "Cannot remove links of one-to-many relation '%s' because inverse many-to-one relation is required"
                                                .formatted(oneToManyRelation.getSourceEndPoint().getName()));
                            }
                        } else {
                            // Delete existing links
                            dslContext.update(table)
                                    .set(sourceRef, (UUID) null)
                                    .where(sourceRef.eq(id.getValue()))
                                    .execute();
                        }

                        try {
                            var updated = dslContext.update(table)
                                    .set(sourceRef, id.getValue())
                                    .where(targetRef.in(refs))
                                    .execute();

                            if (updated < refs.size()) {
                                throw new EntityNotFoundException("Some entities from provided data not found");
                            }
                        } catch (DataIntegrityViolationException | IntegrityConstraintViolationException e) {
                            throw new ConstraintViolationException(e.getMessage(), e); // provided id could not exist
                        }
                    }
                    case ManyToManyRelation ignored -> {
                        // Delete before insert
                        dslContext.deleteFrom(table)
                                .where(sourceRef.eq(id.getValue()))
                                .execute();

                        var step = dslContext.insertInto(table, sourceRef, targetRef);

                        for (var ref : xToManyRelationData.getRefs()) {
                            step = step.values(id.getValue(), ref.getValue());
                        }

                        try {
                            step.execute();
                        } catch (DataIntegrityViolationException | IntegrityConstraintViolationException e) {
                            throw new ConstraintViolationException(e.getMessage(), e); // provided id or ref could not exist
                        }

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
        var table = JOOQUtils.resolveRelationTable(relation);
        var sourceRef = JOOQUtils.resolveRelationSourceRef(relation);
        var targetRef = JOOQUtils.resolveRelationTargetRef(relation);
        var sourceEntity = relation.getSourceEndPoint().getEntity();

        switch (relation) {
            case SourceOneToOneRelation ignored -> {
                try {
                    var updated = dslContext.update(table)
                            .set(targetRef, (UUID) null)
                            .where(sourceRef.eq(id.getValue()))
                            .execute();

                    if (updated == 0) {
                        throw new EntityNotFoundException("Entity with primary key '%s' not found".formatted(id));
                    }
                } catch (DataIntegrityViolationException | IntegrityConstraintViolationException e) {
                    throw new ConstraintViolationException(e.getMessage(), e); // this endpoint could be required
                }
            }
            case ManyToOneRelation ignored -> {
                try {
                    var updated = dslContext.update(table)
                            .set(targetRef, (UUID) null)
                            .where(sourceRef.eq(id.getValue()))
                            .execute();

                    if (updated == 0) {
                        throw new EntityNotFoundException("Entity with primary key '%s' not found".formatted(id));
                    }
                } catch (DataIntegrityViolationException | IntegrityConstraintViolationException e) {
                    throw new ConstraintViolationException(e.getMessage(), e); // this endpoint could be required
                }
            }
            case TargetOneToOneRelation ignored -> {
                try {
                    var updated = dslContext.update(table)
                            .set(sourceRef, (UUID) null)
                            .where(sourceRef.eq(id.getValue()))
                            .execute();

                    if (updated == 0) {
                        // assert referenced entity exists
                        var sourceTable = JOOQUtils.resolveTable(sourceEntity);
                        var primaryKey = JOOQUtils.resolvePrimaryKey(sourceEntity);
                        if (!dslContext.fetchExists(DSL.selectOne().from(sourceTable).where(primaryKey.eq(id.getValue())))) {
                            throw new EntityNotFoundException("Entity with primary key '%s' not found".formatted(id));
                        }
                    }
                } catch (DataIntegrityViolationException | IntegrityConstraintViolationException e) {
                    throw new ConstraintViolationException(e.getMessage(), e); // inverse could be required
                }
            }
            case OneToManyRelation ignored -> {
                try {
                    var updated = dslContext.update(table)
                            .set(sourceRef, (UUID) null)
                            .where(sourceRef.eq(id.getValue()))
                            .execute();

                    if (updated == 0) {
                        // assert referenced entity exists
                        var sourceTable = JOOQUtils.resolveTable(sourceEntity);
                        var primaryKey = JOOQUtils.resolvePrimaryKey(sourceEntity);
                        if (!dslContext.fetchExists(DSL.selectOne().from(sourceTable).where(primaryKey.eq(id.getValue())))) {
                            throw new EntityNotFoundException("Entity with primary key '%s' not found".formatted(id));
                        }
                    }
                } catch (DataIntegrityViolationException | IntegrityConstraintViolationException e) {
                    throw new ConstraintViolationException(e.getMessage(), e); // inverse could be required
                }
            }
            case ManyToManyRelation ignored -> {
                var deleted = dslContext.deleteFrom(table)
                                .where(sourceRef.eq(id.getValue()))
                                .execute();

                if (deleted == 0) {
                    // assert referenced entity exists
                    var sourceTable = JOOQUtils.resolveTable(sourceEntity);
                    var primaryKey = JOOQUtils.resolvePrimaryKey(sourceEntity);
                    if (!dslContext.fetchExists(DSL.selectOne().from(sourceTable).where(primaryKey.eq(id.getValue())))) {
                        throw new EntityNotFoundException("Entity with primary key '%s' not found".formatted(id));
                    }
                }
            }
        }
    }

    @Override
    public void addLinks(@NonNull Application application, @NonNull XToManyRelationData data, @NonNull EntityId id)
            throws QueryEngineException {
        var dslContext = resolver.resolve(application);
        var relation = getRelation(application, data);
        var table = JOOQUtils.resolveRelationTable(relation);
        var sourceRef = JOOQUtils.resolveRelationSourceRef(relation);
        var targetRef = JOOQUtils.resolveRelationTargetRef(relation);

        switch (relation) {
            case OneToManyRelation ignored -> {
                var refs = data.getRefs().stream()
                        .map(EntityId::getValue)
                        .toList();
                try {
                    var updated = dslContext.update(table)
                            .set(sourceRef, id.getValue())
                            .where(targetRef.in(refs))
                            .execute();

                    if (updated < refs.size()) {
                        throw new EntityNotFoundException("Some entities from provided data not found");
                    }
                } catch (DataIntegrityViolationException | IntegrityConstraintViolationException e) {
                    throw new ConstraintViolationException(e.getMessage(), e); // provided source id could not exist
                }
            }
            case ManyToManyRelation ignored -> {
                var step = dslContext.insertInto(table, sourceRef, targetRef);

                for (var ref : data.getRefs()) {
                    step = step.values(id.getValue(), ref.getValue());
                }

                try {
                    step.execute();
                } catch (DuplicateKeyException e) {
                    throw new ConstraintViolationException("One of the provided references already linked with provided id", e);
                } catch (DataIntegrityViolationException | IntegrityConstraintViolationException e) {
                    throw new ConstraintViolationException(e.getMessage(), e); // provided source id could not exist
                }
            }
            default -> throw new InvalidDataException("Relation '%s' of entity '%s' is not a one-to-many or many-to-many relation."
                    .formatted(data.getName(), data.getEntity()));
        }
    }

    @Override
    public void removeLinks(@NonNull Application application, @NonNull XToManyRelationData data, @NonNull EntityId id)
            throws QueryEngineException {
        var dslContext = resolver.resolve(application);
        var relation = getRelation(application, data);
        var table = JOOQUtils.resolveRelationTable(relation);
        var sourceRef = (Field<UUID>) JOOQUtils.resolveRelationSourceRef(relation);
        var targetRef = (Field<UUID>) JOOQUtils.resolveRelationTargetRef(relation);
        var refs = data.getRefs().stream()
                .map(EntityId::getValue)
                .toList();

        switch (relation) {
            case OneToManyRelation ignored -> {
                try {
                    var updated = dslContext.update(table)
                            .set(sourceRef, (UUID) null)
                            .where(DSL.and(sourceRef.eq(id.getValue()), targetRef.in(refs)))
                            .execute();

                    if (updated < refs.size()) {
                        throw new EntityNotFoundException(
                                "Not all target entities found with a relation to entity '%s' with primary key '%s'"
                                        .formatted(data.getEntity(), id));
                    }
                } catch (IntegrityConstraintViolationException | DataIntegrityViolationException e) {
                    if (relation.getTargetEndPoint().isRequired()) {
                        throw new ConstraintViolationException(
                                "Cannot remove references from relation '%s' because inverse many-to-one relation is required"
                                        .formatted(relation.getSourceEndPoint().getName()), e);
                    } else {
                        throw new ConstraintViolationException(e.getMessage(), e);
                    }
                }
            }
            case ManyToManyRelation ignored -> {
                var deleted = dslContext.deleteFrom(table)
                        .where(DSL.and(sourceRef.eq(id.getValue()), targetRef.in(refs)))
                        .execute();

                if (deleted < refs.size()) {
                    throw new EntityNotFoundException("Some provided target entities of relation '%s' not found"
                            .formatted(data.getName()));
                }
            }
            default -> throw new InvalidDataException("Relation '%s' of entity '%s' is not a one-to-many or many-to-many relation."
                    .formatted(data.getName(), data.getEntity()));
        }
    }
}
