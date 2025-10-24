package com.contentgrid.appserver.application.model.searchfilters;

import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.i18n.ConfigurableTranslatable;
import com.contentgrid.appserver.application.model.i18n.TranslatableImpl;
import com.contentgrid.appserver.application.model.i18n.TranslationBuilderSupport;
import com.contentgrid.appserver.application.model.searchfilters.flags.SearchFilterFlag;
import com.contentgrid.appserver.application.model.values.FilterName;
import com.contentgrid.appserver.application.model.values.PropertyPath;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Singular;

import java.util.Locale;
import java.util.Set;

import static java.util.Locale.ENGLISH;

/**
 * FullTextSearchAttributeSearchFilter is a search filter that performs full-text search operations on a specified attribute.
 * <br>
 * The main difference between this and a regular AttributeSearchFilter is that this filter specifies a {@link Locale}.
 */
@Getter
public class FullTextSearchAttributeSearchFilter extends AttributeSearchFilter {

    /**
     * The locale for which the search filter is defined.
     */
    @NonNull
    Locale locale;

    @Builder
    FullTextSearchAttributeSearchFilter(@NonNull Operation operation, @NonNull FilterName name,
                                        @NonNull ConfigurableTranslatable<SearchFilterTranslations, ConfigurableSearchFilterTranslations> translations,
                                        @NonNull PropertyPath attributePath,
                                        @NonNull @Singular Set<SearchFilterFlag> flags,
                                        @NonNull Locale locale) {
        super(operation, name, translations, attributePath, flags);
        this.locale = locale;
    }

    @Override
    public boolean hasFlag(Class<? extends SearchFilterFlag> flagClass) {
        return super.hasFlag(flagClass);
    }

    public static FullTextSearchAttributeSearchFilterBuilder builder() {
        return new FullTextSearchAttributeSearchFilterBuilder()
                .translations(new TranslatableImpl<>(ConfigurableSearchFilterTranslations::new))
                .operation(Operation.FTS)
                .locale(ENGLISH); // Default.
    }

    public static class FullTextSearchAttributeSearchFilterBuilder extends AttributeSearchFilterBuilder {
        {
            getTranslations = () -> translations;
        }

        public FullTextSearchAttributeSearchFilterBuilder attribute(@NonNull SimpleAttribute attribute) {
            this.attributePath = PropertyPath.of(attribute.getName());
            return this;
        }

        public FullTextSearchAttributeSearchFilterBuilder operation(@NonNull Operation operation) throws IllegalArgumentException {
            if (!operation.equals(Operation.FTS)) throw new IllegalArgumentException("FullTextSearchAttributeSearchFilter only supports FTS operation (but got %s).".formatted(operation));

            this.operation = operation;
            return this;
        }

    }

}
