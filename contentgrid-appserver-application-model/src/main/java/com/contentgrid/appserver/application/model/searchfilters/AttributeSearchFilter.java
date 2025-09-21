package com.contentgrid.appserver.application.model.searchfilters;

import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.exceptions.InvalidSearchFilterException;
import com.contentgrid.appserver.application.model.i18n.ManipulatableTranslatable;
import com.contentgrid.appserver.application.model.i18n.Translatable;
import com.contentgrid.appserver.application.model.searchfilters.flags.SearchFilterFlag;
import com.contentgrid.appserver.application.model.values.FilterName;
import com.contentgrid.appserver.application.model.values.PropertyPath;
import java.util.Locale;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Delegate;

/**
 * Base class for search filters that operate on entity attributes.
 * 
 * AttributeSearchFilter is an abstract class that provides common functionality
 * for search filters that filter entities based on attribute values.
 */
@Getter
public abstract class AttributeSearchFilter implements SearchFilter {

    /**
     * The name of the search filter.
     */
    @NonNull
    private final FilterName name;

    /**
     * The path to the attribute this search filter operates on.
     * For simple attributes, this will be a single-element list.
     * For composite attributes, this will be a multi-element list representing the path.
     */
    @NonNull
    private final PropertyPath attributePath;

    @NonNull
    @Delegate
    @Getter(value = AccessLevel.NONE)
    private final Translatable<SearchFilterTranslations> translations;

    /**
     * Flags on the search filter
     */
    @NonNull
    private final Set<SearchFilterFlag> flags;

    /**
     * Constructs an AttributeSearchFilter with the specified parameters.
     *
     * @param name the name of the search filter
     * @param attributePath the path to the attribute to apply the filter on
     * @param flags
     * @throws InvalidSearchFilterException if the attribute type is not supported
     */
    protected AttributeSearchFilter(
            @NonNull FilterName name,
            @NonNull ManipulatableTranslatable<SearchFilterTranslations> translations,
            @NonNull PropertyPath attributePath,
            @NonNull Set<SearchFilterFlag> flags
    ) {
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

    /**
     * Determines if this search filter supports the given attribute.
     * 
     * @param attribute the attribute to check support for
     * @return true if the attribute is supported, false otherwise
     */
    public abstract boolean supports(SimpleAttribute attribute);

}
