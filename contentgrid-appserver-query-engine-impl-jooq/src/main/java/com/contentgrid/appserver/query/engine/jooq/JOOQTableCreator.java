package com.contentgrid.appserver.query.engine.jooq;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Constraint.UniqueConstraint;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.CompositeAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.query.engine.api.TableCreator;
import com.contentgrid.appserver.query.engine.api.exception.InvalidSqlException;
import com.contentgrid.appserver.query.engine.jooq.resolver.DSLContextResolver;
import com.contentgrid.appserver.query.engine.jooq.strategy.JOOQRelationStrategyFactory;
import lombok.RequiredArgsConstructor;
import org.jooq.CreateTableElementListStep;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.jdbc.BadSqlGrammarException;
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
            var strategy = JOOQRelationStrategyFactory.forRelation(relation);
            strategy.make(dslContext, application, relation);
        }
    }

    private void createTableForEntity(DSLContext dslContext, Entity entity) {
        var step = dslContext.createTable(entity.getTable().getValue())
                .column(JOOQUtils.resolvePrimaryKey(entity))
                .primaryKey(entity.getPrimaryKey().getColumn().getValue());
        for (var attribute : entity.getAttributes()) {
            step = createColumnsForAttribute(step, attribute);
        }
        try {
            step.execute();
        } catch (BadSqlGrammarException e) {
            throw new InvalidSqlException(e.getMessage(), e);
        }
    }

    private CreateTableElementListStep createColumnsForAttribute(CreateTableElementListStep step, Attribute attribute) {
        switch (attribute) {
            case SimpleAttribute simpleAttribute -> {
                var result = step.column(JOOQUtils.resolveField(simpleAttribute));
                if (simpleAttribute.hasConstraint(UniqueConstraint.class)) {
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
            var strategy = JOOQRelationStrategyFactory.forRelation(relation);
            strategy.destroy(dslContext, application, relation);
        }

        // Drop entity tables after relations are dropped
        for (var entity : application.getEntities()) {
            var table = JOOQUtils.resolveTable(entity);
            try {
                dslContext.dropTable(table).execute();
            } catch (BadSqlGrammarException e) {
                throw new InvalidSqlException(e.getMessage(), e);
            }
        }
    }

}
