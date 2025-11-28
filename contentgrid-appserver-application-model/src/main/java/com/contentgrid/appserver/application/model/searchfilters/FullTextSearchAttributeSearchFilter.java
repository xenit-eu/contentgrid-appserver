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


/**
 * FullTextSearchAttributeSearchFilter is a search filter that performs full-text search operations on a specified attribute.
 * <br>
 * The main difference between this and a regular {@link AttributeSearchFilter} is that this filter specifies a {@link Locale}.
 */
@Getter
public class FullTextSearchAttributeSearchFilter extends BaseAttributeSearchFilter implements LocaleAwareSearchFilter {


    Locale locale;

    @Builder
    FullTextSearchAttributeSearchFilter(@NonNull FilterName name,
                                        @NonNull ConfigurableTranslatable<SearchFilterTranslations, ConfigurableSearchFilterTranslations> translations,
                                        @NonNull PropertyPath attributePath,
                                        @NonNull @Singular Set<SearchFilterFlag> flags,
                                        @NonNull Locale locale) {
        super(name, translations, attributePath, flags);

        this.locale = locale;
    }

    public static FullTextSearchAttributeSearchFilterBuilder builder() {
        return new FullTextSearchAttributeSearchFilterBuilder()
                .translations(new TranslatableImpl<>(ConfigurableSearchFilterTranslations::new));
    }

    public static class FullTextSearchAttributeSearchFilterBuilder extends TranslationBuilderSupport<SearchFilterTranslations, ConfigurableSearchFilterTranslations, FullTextSearchAttributeSearchFilter.FullTextSearchAttributeSearchFilterBuilder> {
        {
            getTranslations = () -> translations;
        }

        public FullTextSearchAttributeSearchFilterBuilder attribute(@NonNull SimpleAttribute attribute) {
            this.attributePath = PropertyPath.of(attribute.getName());
            return this;
        }
    }

}
