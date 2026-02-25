package com.contentgrid.appserver.query.engine.jooq.thunk;

import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.TableName;
import com.contentgrid.appserver.query.engine.jooq.JOOQUtils;
import java.util.UUID;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.Field;

@Getter
@RequiredArgsConstructor
abstract sealed class Join {

    private final TableName sourceAlias;
    private final TableName targetAlias;
    private final TableName targetTable;

    public abstract Condition getCondition();

    static final class SourceColumnJoin extends Join {

        private final SimpleAttribute sourcePrimaryKey;
        private final ColumnName sourceReference;

        public SourceColumnJoin(TableName sourceAlias, TableName targetAlias, TableName targetTable,
                SimpleAttribute sourcePrimaryKey, ColumnName sourceReference) {
            super(sourceAlias, targetAlias, targetTable);
            this.sourcePrimaryKey = sourcePrimaryKey;
            this.sourceReference = sourceReference;
        }

        @Override
        public Condition getCondition() {
            return ((Field<UUID>) JOOQUtils.resolveField(getTargetAlias(), sourceReference, sourcePrimaryKey.getType(),
                    false))
                    .eq(JOOQUtils.resolvePrimaryKey(getSourceAlias(), sourcePrimaryKey));
        }
    }

    static final class TargetColumnJoin extends Join {

        private final SimpleAttribute targetPrimaryKey;
        private final ColumnName targetReference;

        public TargetColumnJoin(TableName sourceAlias, TableName targetAlias, TableName targetTable,
                SimpleAttribute targetPrimaryKey, ColumnName targetReference) {
            super(sourceAlias, targetAlias, targetTable);
            this.targetPrimaryKey = targetPrimaryKey;
            this.targetReference = targetReference;
        }

        @Override
        public Condition getCondition() {
            return JOOQUtils.resolvePrimaryKey(getTargetAlias(), targetPrimaryKey)
                    .eq((Field<UUID>) JOOQUtils.resolveField(getSourceAlias(), targetReference,
                            targetPrimaryKey.getType(), false));
        }
    }
}
