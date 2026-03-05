package com.contentgrid.appserver.query.engine.jooq.thunk;

import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.TableName;
import com.contentgrid.appserver.query.engine.jooq.JOOQUtils;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.Field;

@Getter(AccessLevel.PROTECTED)
@RequiredArgsConstructor
abstract sealed class Join {

    @NonNull
    private final TableName sourceAlias;
    @NonNull
    private final TableName targetAlias;
    @NonNull
    private final TableName targetTable;

    public abstract Condition getCondition();

    static final class SourceColumnJoin extends Join {

        @NonNull
        private final SimpleAttribute sourcePrimaryKey;
        @NonNull
        private final ColumnName sourceReference;

        public SourceColumnJoin(@NonNull TableName sourceAlias, @NonNull TableName targetAlias, @NonNull TableName targetTable,
                @NonNull SimpleAttribute sourcePrimaryKey, @NonNull ColumnName sourceReference) {
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

        @NonNull
        private final SimpleAttribute targetPrimaryKey;
        @NonNull
        private final ColumnName targetReference;

        public TargetColumnJoin(@NonNull TableName sourceAlias, @NonNull TableName targetAlias, @NonNull TableName targetTable,
                @NonNull SimpleAttribute targetPrimaryKey, @NonNull ColumnName targetReference) {
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
