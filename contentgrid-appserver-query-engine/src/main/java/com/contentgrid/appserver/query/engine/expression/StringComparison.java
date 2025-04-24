package com.contentgrid.appserver.query.engine.expression;

import com.contentgrid.thunx.predicates.model.Comparison;
import com.contentgrid.thunx.predicates.model.ThunkExpression;
import java.util.Locale;
import lombok.EqualsAndHashCode;
import lombok.NonNull;

@EqualsAndHashCode(callSuper = true)
public sealed class StringComparison extends Comparison implements CustomFunctionExpression<Boolean> {

    @NonNull
    private final String key;

    protected StringComparison(@NonNull String key, @NonNull ThunkExpression<?> leftTerm, @NonNull ThunkExpression<?> rightTerm) {
        super(Operator.CUSTOM, leftTerm, rightTerm);
        this.key = key;
    }

    @Override
    public String toDebugString() {
        return key.toUpperCase(Locale.ROOT) + "(" + getLeftTerm() + ", " + getRightTerm() + ")";
    }

    public static StartsWith startsWith(@NonNull ThunkExpression<?> leftTerm, @NonNull ThunkExpression<String> rightTerm) {
        return new StartsWith(leftTerm, rightTerm);
    }

    public static final class StartsWith extends StringComparison {

        private StartsWith(@NonNull ThunkExpression<?> leftTerm, @NonNull ThunkExpression<String> rightTerm) {
            super("starts_with", leftTerm, rightTerm);
        }
    }
}
