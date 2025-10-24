package com.contentgrid.appserver.query.engine.api.thunx.expression;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.searchfilters.AttributeSearchFilter;
import com.contentgrid.appserver.application.model.searchfilters.FullTextSearchAttributeSearchFilter;
import com.contentgrid.thunx.predicates.model.Comparison;
import com.contentgrid.thunx.predicates.model.ThunkExpression;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;

import java.util.Locale;

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
                                                                           @NonNull Application application,
                                                                           @NonNull AttributeSearchFilter searchFilter) throws IllegalArgumentException {
        if (!(searchFilter instanceof FullTextSearchAttributeSearchFilter fullTextSearchAttributeSearchFilter)) throw new IllegalArgumentException("Excepted an instance of AttributeSearchFilter, but got (%s).".formatted(searchFilter));
        return contentGridFullTextSearchMatch(leftTerm, rightTerm, fullTextSearchAttributeSearchFilter.getLocale(application));
    }

    public static ContentGridFullTextSearch contentGridFullTextSearchMatch(@NonNull ThunkExpression<?> leftTerm,
                                                                           @NonNull ThunkExpression<String> rightTerm,
                                                                           @NonNull Locale locale) {
        return new ContentGridFullTextSearch(leftTerm, rightTerm, locale);
    }

    public static final class ContentGridPrefixSearch extends StringComparison {

        private ContentGridPrefixSearch(@NonNull ThunkExpression<?> leftTerm, @NonNull ThunkExpression<String> rightTerm) {
            super("cg_prefix_search", leftTerm, rightTerm);
        }
    }

    @Getter
    public static final class ContentGridFullTextSearch extends StringComparison {

        private final @NonNull Locale locale;

        private ContentGridFullTextSearch(@NonNull ThunkExpression<?> leftTerm,
                                          @NonNull ThunkExpression<String> rightTerm,
                                          @NonNull Locale locale) {
            super("cg_fulltext_search", leftTerm, rightTerm);

            this.locale = locale;
        }
    }

}
