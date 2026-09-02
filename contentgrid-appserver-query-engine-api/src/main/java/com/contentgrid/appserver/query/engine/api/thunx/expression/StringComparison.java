package com.contentgrid.appserver.query.engine.api.thunx.expression;

import com.contentgrid.thunx.predicates.model.Comparison;
import com.contentgrid.thunx.predicates.model.SetValue;
import com.contentgrid.thunx.predicates.model.ThunkExpression;
import java.util.Locale;
import lombok.EqualsAndHashCode;
import lombok.Getter;
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

    public static ContentGridFullTextSearch contentGridFullTextSearchMatch(@NonNull ThunkExpression<?> leftTerm,
                                                                           @NonNull ThunkExpression<String> rightTerm,
                                                                           @NonNull Locale locale) {
        return new ContentGridFullTextSearch(leftTerm, rightTerm, locale);
    }

    /**
     * Matches a multi-value text attribute when any of its elements equals any of the search values.
     * All values travel in the single right-hand {@link SetValue}, so one expression resolves to one
     * overlap condition, for one as well as for many values.
     */
    public static ContentGridArraySearch contentGridArraySearchMatch(@NonNull ThunkExpression<?> leftTerm,
                                                                     @NonNull SetValue rightTerm) {
        return new ContentGridArraySearch(leftTerm, rightTerm);
    }

    public static final class ContentGridPrefixSearch extends StringComparison {

        private ContentGridPrefixSearch(@NonNull ThunkExpression<?> leftTerm, @NonNull ThunkExpression<String> rightTerm) {
            super("cg_prefix_search", leftTerm, rightTerm);
        }
    }

    @Getter
    @EqualsAndHashCode(callSuper = true)
    public static final class ContentGridFullTextSearch extends StringComparison {

        private final @NonNull Locale locale;

        private ContentGridFullTextSearch(@NonNull ThunkExpression<?> leftTerm,
                                          @NonNull ThunkExpression<String> rightTerm,
                                          @NonNull Locale locale) {
            super("cg_fulltext_search", leftTerm, rightTerm);

            this.locale = locale;
        }
    }

    public static final class ContentGridArraySearch extends StringComparison {

        private ContentGridArraySearch(@NonNull ThunkExpression<?> leftTerm, @NonNull SetValue rightTerm) {
            super("cg_array_search", leftTerm, rightTerm);
        }
    }

}
