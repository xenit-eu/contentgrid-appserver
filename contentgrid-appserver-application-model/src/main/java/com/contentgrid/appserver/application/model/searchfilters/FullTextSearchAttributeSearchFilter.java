package com.contentgrid.appserver.application.model.searchfilters;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.i18n.ConfigurableTranslatable;
import com.contentgrid.appserver.application.model.i18n.TranslatableImpl;
import com.contentgrid.appserver.application.model.searchfilters.flags.SearchFilterFlag;
import com.contentgrid.appserver.application.model.values.FilterName;
import com.contentgrid.appserver.application.model.values.PropertyPath;
import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;

import java.util.Locale;
import java.util.Set;

import static com.contentgrid.appserver.application.model.searchfilters.AttributeSearchFilter.Operation.FTS;

/**
 * FullTextSearchAttributeSearchFilter is a search filter that performs full-text search operations on a specified attribute.
 * <br>
 * The main difference between this and a regular {@link AttributeSearchFilter} is that this filter specifies a {@link Locale}.
 */
public class FullTextSearchAttributeSearchFilter extends AttributeSearchFilter implements LocaleAwareSearchFilter {

    /**
     * The locale for which the search filter is defined.
     * Might be null, in which case the default locale for the {@link com.contentgrid.appserver.application.model.Application} will be used.
     */
    Locale locale;

    public Locale getLocale(@NonNull Application application) {
        return locale == null? application.getLocale() : locale;
    }

    @Builder
    FullTextSearchAttributeSearchFilter(@NonNull FilterName name,
                                        @NonNull ConfigurableTranslatable<SearchFilterTranslations, ConfigurableSearchFilterTranslations> translations,
                                        @NonNull PropertyPath attributePath,
                                        @NonNull @Singular Set<SearchFilterFlag> flags,
                                        Locale locale) {
        super(FTS, name, translations, attributePath, flags);

        this.locale = locale;
    }

    public static FullTextSearchAttributeSearchFilterBuilder builder() {
        return new FullTextSearchAttributeSearchFilterBuilder()
                .translations(new TranslatableImpl<>(ConfigurableSearchFilterTranslations::new));
    }

    public static class FullTextSearchAttributeSearchFilterBuilder extends AttributeSearchFilterBuilder {
        {
            getTranslations = () -> translations;
        }

        @Override
        public AttributeSearchFilterBuilder operation(@NonNull Operation operation) throws IllegalArgumentException {
            if (!FTS.equals(operation)) throw new IllegalArgumentException("FullTextSearchAttributeSearchFilter instances only support the FTS operation (but %s was provided).".formatted(operation));
            return this; // What's the point of calling the super method then?
        }
    }

}
