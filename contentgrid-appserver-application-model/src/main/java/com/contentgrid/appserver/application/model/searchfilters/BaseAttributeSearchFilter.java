package com.contentgrid.appserver.application.model.searchfilters;

import com.contentgrid.appserver.application.model.i18n.ConfigurableTranslatable;
import com.contentgrid.appserver.application.model.i18n.Translatable;
import com.contentgrid.appserver.application.model.searchfilters.flags.SearchFilterFlag;
import com.contentgrid.appserver.application.model.values.FilterName;
import com.contentgrid.appserver.application.model.propertypath.PropertyPath;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Singular;
import lombok.experimental.Delegate;

import java.util.Locale;
import java.util.Set;

/**
 * Base class for the attribute-based search filters (e.g. {@link FullTextSearchAttributeSearchFilter} and {@link AttributeSearchFilter}).
 * This base class was originally introduced to share common logic between different attribute search filters,
 * while making sure that each filter type can have its own builder and specific properties.
 */
@Getter
public abstract class BaseAttributeSearchFilter implements SearchFilter {

    /**
     * The name of the search filter.
     */
    @NonNull
    private final FilterName name;

    /**
     * The path to the attribute this search filter operates on.
     */
    @NonNull
    private final PropertyPath.ResolvesToAttribute attributePath;

    @NonNull
    @Delegate
    @Getter(value = AccessLevel.NONE)
    private final Translatable<SearchFilterTranslations> translations;

    /**
     * Flags on the search filter
     */
    @NonNull
    private final Set<SearchFilterFlag> flags;

    protected BaseAttributeSearchFilter(
            @NonNull FilterName name,
            @NonNull ConfigurableTranslatable<SearchFilterTranslations, ConfigurableSearchFilterTranslations> translations,
            @NonNull PropertyPath.ResolvesToAttribute attributePath,
            @NonNull @Singular Set<SearchFilterFlag> flags) {
        this.name = name;
        this.translations = translations.withTranslationsBy(Locale.ROOT, t -> {
            if(t.getName() == null) {
                t = t.withName(name.getValue());
            }
            return t;
        });
        this.attributePath = attributePath;
        this.flags = Set.copyOf(flags);

        flags.forEach(flag -> flag.checkSupported(this));
    }

}
