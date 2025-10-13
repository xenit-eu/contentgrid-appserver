package com.contentgrid.appserver.application.model.searchfilters;

import static lombok.AccessLevel.PROTECTED;

import java.util.Locale;
import java.util.Set;

import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.i18n.Translatable;
import com.contentgrid.appserver.application.model.i18n.ConfigurableTranslatable;
import com.contentgrid.appserver.application.model.searchfilters.SearchFilter.SearchFilterTranslations;
import com.contentgrid.appserver.application.model.searchfilters.flags.SearchFilterFlag;
import com.contentgrid.appserver.application.model.values.FilterName;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;

@Getter
public abstract class BaseAttributeSearchFilter implements SearchFilter {

    @NonNull
    private final Operation operation;

    /**
     * The name of the search filter.
     */
    @NonNull
    private final FilterName name;

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
            @NonNull Set<SearchFilterFlag> flags) {
        this.operation = operation;
        this.name = name;
        this.translations = translations.withTranslationsBy(Locale.ROOT, t -> {
            if(t.getName() == null) {
                t = t.withName(name.getValue());
            }
            return t;
        });
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
        EXACT(Set.of(Type.TEXT, Type.UUID, Type.LONG, Type.DOUBLE, Type.BOOLEAN, Type.DATETIME)),
        PREFIX(Set.of(Type.TEXT)),
        FTS(Set.of(Type.TEXT)),
        GREATER_THAN(Set.of(Type.LONG, Type.DOUBLE, Type.DATETIME)),
        GREATER_THAN_OR_EQUAL(Set.of(Type.LONG, Type.DOUBLE, Type.DATETIME)),
        LESS_THAN(Set.of(Type.LONG, Type.DOUBLE, Type.DATETIME)),
        LESS_THAN_OR_EQUAL(Set.of(Type.LONG, Type.DOUBLE, Type.DATETIME)),
        ;

        private final Set<Type> supportedTypes;

        Operation(Set<Type> supportedTypes) {
            this.supportedTypes = supportedTypes;
        }

        public boolean supports(SimpleAttribute attribute) {
            return supportedTypes.contains(attribute.getType());
        }
    }

}
