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

    /**
     * Alias of {@link Comparison#areEqual}
     */
    public static Comparison normalizedEqual(@NonNull ThunkExpression<?> leftTerm, @NonNull ThunkExpression<?> rightTerm) {
        return Comparison.areEqual(leftTerm, rightTerm);
    }

    public static Comparison contentGridPrefixSearchMatch(@NonNull ThunkExpression<?> leftTerm, @NonNull ThunkExpression<String> rightTerm) {
        return new ContentGridPrefixSearch(leftTerm, rightTerm);
    }

    public static Comparison contentGridFullTextSearchMatch(@NonNull ThunkExpression<?> leftTerm, @NonNull ThunkExpression<String> rightTerm) {
        return new ContentGridFullTextSearch(leftTerm, rightTerm);
    }

    public static final class ContentGridPrefixSearch extends StringComparison {

        private ContentGridPrefixSearch(@NonNull ThunkExpression<?> leftTerm, @NonNull ThunkExpression<String> rightTerm) {
            super("cg_prefix_search", leftTerm, rightTerm);
        }
    }

    public static final class ContentGridFullTextSearch extends StringComparison {

        private ContentGridFullTextSearch(@NonNull ThunkExpression<?> leftTerm, @NonNull ThunkExpression<String> rightTerm) {
            super("cg_fulltext_search", leftTerm, rightTerm);
        }
    }

}
