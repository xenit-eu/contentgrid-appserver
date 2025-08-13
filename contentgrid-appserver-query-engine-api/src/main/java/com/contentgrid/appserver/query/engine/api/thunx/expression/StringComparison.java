package com.contentgrid.appserver.query.engine.api.thunx.expression;

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

    public static Comparison startsWith(@NonNull ThunkExpression<?> leftTerm, @NonNull ThunkExpression<String> rightTerm) {
        return new StartsWith(leftTerm, rightTerm);
    }

    public static Comparison fulltext(@NonNull ThunkExpression<?> leftTerm, @NonNull ThunkExpression<String> rightTerm) {
        return new Fulltext(leftTerm, rightTerm);
    }

    public static Comparison normalizedEqual(@NonNull ThunkExpression<?> leftTerm, @NonNull ThunkExpression<?> rightTerm) {
        return Comparison.areEqual(
                StringFunctionExpression.normalize(leftTerm),
                StringFunctionExpression.normalize(rightTerm)
        );
    }

    public static Comparison contentGridPrefixSearchMatch(@NonNull ThunkExpression<?> leftTerm, @NonNull ThunkExpression<String> rightTerm) {
        return startsWith(
                StringFunctionExpression.contentGridPrefixSearchNormalize(leftTerm),
                StringFunctionExpression.contentGridPrefixSearchNormalize(rightTerm)
        );
    }

    public static Comparison contentGridFullTextSearchMatch(@NonNull ThunkExpression<?> leftTerm, @NonNull ThunkExpression<String> rightTerm) {
        return fulltext(
                StringFunctionExpression.contentGridFullTextSearchNormalizeExpression(leftTerm),
                StringFunctionExpression.contentGridFullTextSearchNormalizeExpression(rightTerm)
        );
    }

    public static final class StartsWith extends StringComparison {

        private StartsWith(@NonNull ThunkExpression<?> leftTerm, @NonNull ThunkExpression<String> rightTerm) {
            super("starts_with", leftTerm, rightTerm);
        }
    }

    public static final class Fulltext extends StringComparison {
        private Fulltext(@NonNull ThunkExpression<?> leftTerm, @NonNull ThunkExpression<?> rightTerm) {
            super("fulltext", leftTerm, rightTerm);
        }
    }
}
