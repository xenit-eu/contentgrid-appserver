package com.contentgrid.appserver.query.engine.jooq;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Constraint.UniqueConstraint;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.CompositeAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.relations.ManyToManyRelation;
import com.contentgrid.appserver.application.model.relations.ManyToOneRelation;
import com.contentgrid.appserver.application.model.relations.OneToManyRelation;
import com.contentgrid.appserver.application.model.relations.Relation.RelationEndPoint;
import com.contentgrid.appserver.application.model.relations.SourceOneToOneRelation;
import com.contentgrid.appserver.application.model.relations.TargetOneToOneRelation;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.query.engine.api.TableCreator;
import com.contentgrid.appserver.query.engine.jooq.resolver.DSLContextResolver;
import lombok.RequiredArgsConstructor;
import org.jooq.CreateTableElementListStep;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional
public class JOOQTableCreator implements TableCreator {

    private final DSLContextResolver resolver;

    @Override
    public void createTables(Application application) {
        var dslContext = resolver.resolve(application);
        for (var entity : application.getEntities()) {
            createTableForEntity(dslContext, entity);
        }
        // Create relations after tables are created, so that each table referenced in the foreign key constraint exists
        for (var relation : application.getRelations()) {
            var sourceEndPoint = relation.getSourceEndPoint();
            var targetEndPoint = relation.getTargetEndPoint();
            switch (relation) {
                case SourceOneToOneRelation oneToOneRelation ->
                        createRelation(dslContext, sourceEndPoint, targetEndPoint, oneToOneRelation.getTargetReference(), true);
                case ManyToOneRelation manyToOneRelation ->
                        createRelation(dslContext, sourceEndPoint, targetEndPoint, manyToOneRelation.getTargetReference(), false);
                case TargetOneToOneRelation oneToOneRelation ->
                        createRelation(dslContext, targetEndPoint, sourceEndPoint, oneToOneRelation.getSourceReference(), true);
                case OneToManyRelation oneToManyRelation ->
                        createRelation(dslContext, targetEndPoint, sourceEndPoint, oneToManyRelation.getSourceReference(), false);
                case ManyToManyRelation manyToManyRelation -> {
                    // Create the join table
                    dslContext.createTable(manyToManyRelation.getJoinTable().getValue())
                            .column(JOOQUtils.resolveRelationSourceRef(manyToManyRelation))
                            .column(JOOQUtils.resolveRelationTargetRef(manyToManyRelation))
                            .primaryKey(manyToManyRelation.getSourceReference().getValue(), manyToManyRelation.getTargetReference().getValue())
                            .constraint(DSL.foreignKey(manyToManyRelation.getSourceReference().getValue())
                                    .references(sourceEndPoint.getEntity().getTable().getValue(),
                                            sourceEndPoint.getEntity().getPrimaryKey().getColumn().getValue()))
                            .constraint(DSL.foreignKey(manyToManyRelation.getTargetReference().getValue())
                                    .references(targetEndPoint.getEntity().getTable().getValue(),
                                            targetEndPoint.getEntity().getPrimaryKey().getColumn().getValue()))
                            .execute();
                }
            }
        }
    }

    private void createRelation(DSLContext dslContext, RelationEndPoint owningEndPoint, RelationEndPoint otherEndPoint, ColumnName reference, boolean unique) {
        dslContext.alterTable(owningEndPoint.getEntity().getTable().getValue())
                .add(JOOQUtils.resolveField(reference, otherEndPoint.getEntity().getPrimaryKey().getType(), owningEndPoint.isRequired()),
                        DSL.foreignKey(reference.getValue()).references(otherEndPoint.getEntity().getTable().getValue(),
                                otherEndPoint.getEntity().getPrimaryKey().getColumn().getValue()))
                .execute();
        if (unique) {
            dslContext.alterTable(owningEndPoint.getEntity().getTable().getValue())
                    .add(DSL.unique(reference.getValue()))
                    .execute();
        }
    }

    private void createTableForEntity(DSLContext dslContext, Entity entity) {
        var step = dslContext.createTable(entity.getTable().getValue())
                .column(JOOQUtils.resolvePrimaryKey(entity))
                .primaryKey(entity.getPrimaryKey().getColumn().getValue());
        for (var attribute : entity.getAttributes()) {
            step = createColumnsForAttribute(step, attribute);
        }
        step.execute();
    }

    private CreateTableElementListStep createColumnsForAttribute(CreateTableElementListStep step, Attribute attribute) {
        switch (attribute) {
            case SimpleAttribute simpleAttribute -> {
                var result = step.column(JOOQUtils.resolveField(simpleAttribute));
                if (simpleAttribute.getConstraint(UniqueConstraint.class).isPresent()) {
                    result = result.constraint(DSL.unique(simpleAttribute.getColumn().getValue()));
                }
                return result;
            }
            case CompositeAttribute compositeAttribute -> {
                for (var nestedAttribute : compositeAttribute.getAttributes()) {
                    step = createColumnsForAttribute(step, nestedAttribute);
                }
                return step;
            }
        }
    }

    @Override
    public void dropTables(Application application) {
        var dslContext = resolver.resolve(application);

        // drop relations first
        for (var relation : application.getRelations()) {
            var sourceEndPoint = relation.getSourceEndPoint();
            var targetEndPoint = relation.getTargetEndPoint();
            switch (relation) {
                case SourceOneToOneRelation oneToOneRelation -> {
                    var table = JOOQUtils.resolveTable(sourceEndPoint.getEntity());
                    var foreignKey = JOOQUtils.resolveField(oneToOneRelation.getTargetReference(),
                            targetEndPoint.getEntity().getPrimaryKey().getType(), sourceEndPoint.isRequired());
                    dslContext.alterTable(table)
                            .dropColumnIfExists(foreignKey)
                            .execute();
                }
                case ManyToOneRelation manyToOneRelation -> {
                    var table = JOOQUtils.resolveTable(sourceEndPoint.getEntity());
                    var foreignKey = JOOQUtils.resolveField(manyToOneRelation.getTargetReference(),
                            targetEndPoint.getEntity().getPrimaryKey().getType(), sourceEndPoint.isRequired());
                    dslContext.alterTable(table)
                            .dropColumnIfExists(foreignKey)
                            .execute();
                }
                case TargetOneToOneRelation oneToOneRelation -> {
                    var table = JOOQUtils.resolveTable(targetEndPoint.getEntity());
                    var foreignKey = JOOQUtils.resolveField(oneToOneRelation.getSourceReference(),
                            sourceEndPoint.getEntity().getPrimaryKey().getType(), targetEndPoint.isRequired());
                    dslContext.alterTable(table)
                            .dropColumnIfExists(foreignKey)
                            .execute();
                }
                case OneToManyRelation oneToManyRelation -> {
                    var table = JOOQUtils.resolveTable(targetEndPoint.getEntity());
                    var foreignKey = JOOQUtils.resolveField(oneToManyRelation.getSourceReference(),
                            sourceEndPoint.getEntity().getPrimaryKey().getType(), targetEndPoint.isRequired());
                    dslContext.alterTable(table)
                            .dropColumnIfExists(foreignKey)
                            .execute();
                }
                case ManyToManyRelation manyToManyRelation -> {
                    var table = JOOQUtils.resolveTable(manyToManyRelation.getJoinTable());
                    dslContext.dropTable(table).execute();
                }
            }
        }

        // Drop entity tables after relations are dropped
        for (var entity : application.getEntities()) {
            var table = JOOQUtils.resolveTable(entity);
            dslContext.dropTable(table).execute();
        }
    }

}
