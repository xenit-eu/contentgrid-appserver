package com.contentgrid.appserver.query.engine;

import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.relations.ManyToManyRelation;
import com.contentgrid.appserver.application.model.relations.ManyToOneRelation;
import com.contentgrid.appserver.application.model.relations.OneToManyRelation;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.application.model.relations.SourceOneToOneRelation;
import com.contentgrid.appserver.application.model.relations.TargetOneToOneRelation;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.TableName;
import com.contentgrid.appserver.query.engine.JoinCollection.Join.SourceColumnJoin;
import com.contentgrid.appserver.query.engine.JoinCollection.Join.TargetColumnJoin;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.SelectJoinStep;
import org.jooq.impl.DSL;

@Getter
public class JoinCollection {

    private final TableName rootTable;
    private final TableName rootAlias;

    private TableName currentTable;
    private TableName currentAlias;

    @Getter(AccessLevel.NONE)
    private int aliasCount = 0;

    @Getter(AccessLevel.NONE)
    private final List<Join> joins = new ArrayList<>();

    public JoinCollection(@NonNull TableName rootTable) {
        this.rootTable = rootTable;
        this.rootAlias = generateAlias(rootTable);
        this.currentTable = this.rootTable;
        this.currentAlias = this.rootAlias;
    }

    private TableName generateAlias(TableName table) {
        this.currentTable = table;
        this.currentAlias = TableName.of(table.getValue().charAt(0) + String.valueOf(aliasCount));
        this.aliasCount += 1;
        return this.currentAlias;
    }

    public void addRelation(Relation relation) {
        if (!relation.getSourceEndPoint().getEntity().getTable().equals(currentTable)) {
            throw new IllegalArgumentException("Relation source table %s does not match table %s"
                    .formatted(relation.getSourceEndPoint().getEntity().getTable(), currentTable));
        }
        var sourceAlias = currentAlias;
        switch (relation) {
            case SourceOneToOneRelation oneToOneRelation -> {
                var targetAlias = this.generateAlias(relation.getTargetEndPoint().getEntity().getTable());
                joins.add(new TargetColumnJoin(sourceAlias, targetAlias, currentTable,
                        relation.getTargetEndPoint().getEntity().getPrimaryKey(),
                        oneToOneRelation.getTargetReference()));
            }
            case ManyToOneRelation manyToOneRelation -> {
                var targetAlias = this.generateAlias(relation.getTargetEndPoint().getEntity().getTable());
                joins.add(new TargetColumnJoin(sourceAlias, targetAlias, currentTable,
                        relation.getTargetEndPoint().getEntity().getPrimaryKey(),
                        manyToOneRelation.getTargetReference()));
            }
            case TargetOneToOneRelation oneToOneRelation -> {
                var targetAlias = this.generateAlias(relation.getTargetEndPoint().getEntity().getTable());
                joins.add(new SourceColumnJoin(sourceAlias, targetAlias, currentTable,
                        relation.getSourceEndPoint().getEntity().getPrimaryKey(),
                        oneToOneRelation.getSourceReference()));
            }
            case OneToManyRelation oneToManyRelation -> {
                var targetAlias = this.generateAlias(relation.getTargetEndPoint().getEntity().getTable());
                joins.add(new SourceColumnJoin(sourceAlias, targetAlias, currentTable,
                        relation.getSourceEndPoint().getEntity().getPrimaryKey(),
                        oneToManyRelation.getSourceReference()));
            }
            case ManyToManyRelation manyToManyRelation -> {
                var joinTableAlias = this.generateAlias(manyToManyRelation.getJoinTable());
                joins.add(new SourceColumnJoin(sourceAlias, joinTableAlias, currentTable,
                        relation.getSourceEndPoint().getEntity().getPrimaryKey(),
                        manyToManyRelation.getSourceReference()));
                var targetAlias = this.generateAlias(relation.getTargetEndPoint().getEntity().getTable());
                joins.add(new TargetColumnJoin(joinTableAlias, targetAlias, currentTable,
                        relation.getTargetEndPoint().getEntity().getPrimaryKey(),
                        manyToManyRelation.getTargetReference()));
            }
        }
    }

    public void resetCurrentTable() {
        this.currentTable = rootTable;
        this.currentAlias = rootAlias;
    }

    public Condition collect(Condition condition) {
        SelectJoinStep<?> selectBuilder = null;
        Condition where = null;
        for (var join : joins) {
            if (selectBuilder == null) {
                selectBuilder = DSL.selectOne().from(JOOQUtils.resolveTable(join.getTargetTable(), join.getTargetAlias()));
                where = join.getCondition();
            } else {
                selectBuilder = selectBuilder.join(JOOQUtils.resolveTable(join.getTargetTable(), join.getTargetAlias()))
                        .on(join.getCondition());
            }
        }

        joins.clear();
        this.resetCurrentTable();

        if (selectBuilder == null || where == null) {
            return condition;
        } else {
            return DSL.exists(selectBuilder.where(DSL.and(where, condition)));
        }
    }

    @Getter
    @RequiredArgsConstructor
    public abstract static sealed class Join {

        private final TableName sourceAlias;
        private final TableName targetAlias;
        private final TableName targetTable;

        public abstract Condition getCondition();

        public static final class SourceColumnJoin extends Join {

            private final SimpleAttribute sourcePrimaryKey;
            private final ColumnName sourceReference;

            public SourceColumnJoin(TableName sourceAlias, TableName targetAlias, TableName targetTable, SimpleAttribute sourcePrimaryKey, ColumnName sourceReference) {
                super(sourceAlias, targetAlias, targetTable);
                this.sourcePrimaryKey = sourcePrimaryKey;
                this.sourceReference = sourceReference;
            }

            @Override
            public Condition getCondition() {
                return ((Field<Object>) JOOQUtils.resolveField(getTargetAlias(), sourceReference, sourcePrimaryKey.getType(), false))
                        .eq((Field<Object>) JOOQUtils.resolveField(getSourceAlias(), sourcePrimaryKey.getColumn(), sourcePrimaryKey.getType(), true));
            }
        }

        public static final class TargetColumnJoin extends Join {

            private final SimpleAttribute targetPrimaryKey;
            private final ColumnName targetReference;

            public TargetColumnJoin(TableName sourceAlias, TableName targetAlias, TableName targetTable, SimpleAttribute targetPrimaryKey, ColumnName targetReference) {
                super(sourceAlias, targetAlias, targetTable);
                this.targetPrimaryKey = targetPrimaryKey;
                this.targetReference = targetReference;
            }

            @Override
            public Condition getCondition() {
                return ((Field<Object>) JOOQUtils.resolveField(getTargetAlias(), targetPrimaryKey.getColumn(), targetPrimaryKey.getType(), true))
                        .eq((Field<Object>) JOOQUtils.resolveField(getSourceAlias(), targetReference, targetPrimaryKey.getType(), false));
            }
        }
    }
}
