package com.contentgrid.appserver.query.engine.api.thunx.expression;

import com.contentgrid.thunx.predicates.model.ThunkExpression;
import java.util.List;
import java.util.Locale;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@EqualsAndHashCode
public sealed class StringFunctionExpression implements CustomFunctionExpression<String> {

    @NonNull
    private final String key;

    @NonNull
    @Getter
    private final ThunkExpression<?> term;

    @Override
    public List<ThunkExpression<?>> getTerms() {
        return List.of(term);
    }

    @Override
    public Class<? extends String> getResultType() {
        return String.class;
    }

    @Override
    public String toDebugString() {
        return key.toUpperCase(Locale.ROOT) + "(" + term + ")";
    }

    @Override
    public String toString() {
        return this.toDebugString();
    }

    public static NormalizeExpression normalize(@NonNull ThunkExpression<?> term) {
        return new NormalizeExpression(term);
    }

    public static ContentGridPrefixSearchNormalizeExpression contentGridPrefixSearchNormalize(@NonNull ThunkExpression<?> term) {
        return new ContentGridPrefixSearchNormalizeExpression(term);
    }

    public static ContentGridFullTextSearchNormalizeExpression contentGridFullTextSearchNormalizeExpression(@NonNull ThunkExpression<?> term) {
        return new ContentGridFullTextSearchNormalizeExpression(term);
    }

    public static final class NormalizeExpression extends StringFunctionExpression {

        private NormalizeExpression(@NonNull ThunkExpression<?> term) {
            super("normalize", term);
        }
    }

    public static final class ContentGridPrefixSearchNormalizeExpression extends StringFunctionExpression {

        private ContentGridPrefixSearchNormalizeExpression(@NonNull ThunkExpression<?> term) {
            super("cg_prefix_search_normalize", term);
        }
    }

    public static final class ContentGridFullTextSearchNormalizeExpression extends StringFunctionExpression {
        public ContentGridFullTextSearchNormalizeExpression(@NonNull ThunkExpression<?> term) {
            super("cg_fulltext_search_normalize", term);
        }
    }
}
