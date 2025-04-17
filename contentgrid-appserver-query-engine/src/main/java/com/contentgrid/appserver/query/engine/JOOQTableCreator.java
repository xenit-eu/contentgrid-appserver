package com.contentgrid.appserver.query.engine;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Constraint.RequiredConstraint;
import com.contentgrid.appserver.application.model.Constraint.UniqueConstraint;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.CompositeAttribute;
import com.contentgrid.appserver.application.model.attributes.ContentAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.UserAttribute;
import com.contentgrid.appserver.application.model.relations.ManyToManyRelation;
import com.contentgrid.appserver.application.model.relations.ManyToOneRelation;
import com.contentgrid.appserver.application.model.relations.OneToManyRelation;
import com.contentgrid.appserver.application.model.relations.Relation.RelationEndPoint;
import com.contentgrid.appserver.application.model.relations.SourceOneToOneRelation;
import com.contentgrid.appserver.application.model.relations.TargetOneToOneRelation;
import com.contentgrid.appserver.application.model.values.ColumnName;
import lombok.RequiredArgsConstructor;
import org.jooq.CreateTableElementListStep;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
public class JOOQTableCreator {

    @Transactional
    public void createTables(DSLContext dslContext, Application application) {
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
                            .column(resolveField(manyToManyRelation.getSourceReference(), sourceEndPoint.getEntity().getPrimaryKey()
                                    .getType(), true))
                            .column(resolveField(manyToManyRelation.getTargetReference(), targetEndPoint.getEntity().getPrimaryKey()
                                    .getType(), true))
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
                .add(resolveField(reference, otherEndPoint.getEntity().getPrimaryKey().getType(), owningEndPoint.isRequired()),
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
                .column(resolveField(entity.getPrimaryKey().getColumn(), entity.getPrimaryKey().getType(), true))
                .primaryKey(entity.getPrimaryKey().getColumn().getValue());
        for (var attribute : entity.getAttributes()) {
            step = createColumnsForAttribute(step, attribute);
        }
        step.execute();
    }

    private CreateTableElementListStep createColumnsForAttribute(CreateTableElementListStep step, Attribute attribute) {
        switch (attribute) {
            case SimpleAttribute simpleAttribute -> {
                var result = step.column(resolveField(simpleAttribute));
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
            case ContentAttribute contentAttribute -> {
                step = createColumnsForAttribute(step, contentAttribute.getId());
                step = createColumnsForAttribute(step, contentAttribute.getFilename());
                step = createColumnsForAttribute(step, contentAttribute.getMimetype());
                return createColumnsForAttribute(step, contentAttribute.getLength());
            }
            case UserAttribute userAttribute -> {
                step = createColumnsForAttribute(step, userAttribute.getId());
                step = createColumnsForAttribute(step, userAttribute.getNamespace());
                return createColumnsForAttribute(step, userAttribute.getUsername());
            }
        }
    }

    private static Field<?> resolveField(SimpleAttribute attribute) {
        return resolveField(attribute.getColumn(), attribute.getType(), attribute.getConstraint(RequiredConstraint.class).isPresent());
    }

    private static Field<?> resolveField(ColumnName column, SimpleAttribute.Type type, boolean required) {
        var dataType = switch (type) {
            case UUID -> SQLDataType.UUID;
            case TEXT -> SQLDataType.CLOB;
            case LONG -> SQLDataType.BIGINT;
            case DOUBLE -> SQLDataType.DECIMAL;
            case BOOLEAN -> SQLDataType.BOOLEAN;
            case DATETIME -> SQLDataType.INSTANT;
        };
        dataType = dataType.nullable(!required);
        return DSL.field(column.getValue(), dataType);
    }

}
