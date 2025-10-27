package com.contentgrid.appserver.application.model.searchfilters;

import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.i18n.ConfigurableTranslatable;
import com.contentgrid.appserver.application.model.i18n.Translatable;
import com.contentgrid.appserver.application.model.searchfilters.flags.SearchFilterFlag;
import com.contentgrid.appserver.application.model.values.FilterName;
import com.contentgrid.appserver.application.model.values.PropertyPath;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Singular;
import lombok.experimental.Delegate;

import java.util.Locale;
import java.util.Set;

/**
 * Base class for the attribute-based search filters (e.g. {@link FullTextSearchAttributeSearchFilter} and {@link AttributeSearchFilter}).
 * @implNote This base class was originally introduced to share common logic between different attribute search filters,
 * while making sure that each filter type can have its own builder and specific properties.
 */
@Getter
public abstract class BaseAttributeSearchFilter implements SearchFilter {

    @NonNull
    private final Operation operation;

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

    protected BaseAttributeSearchFilter(
            @NonNull Operation operation,
            @NonNull FilterName name,
            @NonNull ConfigurableTranslatable<SearchFilterTranslations, ConfigurableSearchFilterTranslations> translations,
            @NonNull PropertyPath attributePath,
            @NonNull @Singular Set<SearchFilterFlag> flags) {
        this.operation = operation;
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
     * <p>
     * @param attribute the attribute to check support for
     * @return true if the attribute is supported, false otherwise
     */
    public boolean supports(SimpleAttribute attribute) {
        return operation.supports(attribute);
    }

    public enum Operation {
        EXACT(Set.of(SimpleAttribute.Type.TEXT, SimpleAttribute.Type.UUID, SimpleAttribute.Type.LONG, SimpleAttribute.Type.DOUBLE, SimpleAttribute.Type.BOOLEAN, SimpleAttribute.Type.DATETIME)),
        PREFIX(Set.of(SimpleAttribute.Type.TEXT)),
        FTS(Set.of(SimpleAttribute.Type.TEXT)),
        GREATER_THAN(Set.of(SimpleAttribute.Type.LONG, SimpleAttribute.Type.DOUBLE, SimpleAttribute.Type.DATETIME)),
        GREATER_THAN_OR_EQUAL(Set.of(SimpleAttribute.Type.LONG, SimpleAttribute.Type.DOUBLE, SimpleAttribute.Type.DATETIME)),
        LESS_THAN(Set.of(SimpleAttribute.Type.LONG, SimpleAttribute.Type.DOUBLE, SimpleAttribute.Type.DATETIME)),
        LESS_THAN_OR_EQUAL(Set.of(SimpleAttribute.Type.LONG, SimpleAttribute.Type.DOUBLE, SimpleAttribute.Type.DATETIME)),
        ;

        private final Set<SimpleAttribute.Type> supportedTypes;

        Operation(Set<SimpleAttribute.Type> supportedTypes) {
            this.supportedTypes = supportedTypes;
        }

        public boolean supports(SimpleAttribute attribute) {
            return supportedTypes.contains(attribute.getType());
        }
    }

}
