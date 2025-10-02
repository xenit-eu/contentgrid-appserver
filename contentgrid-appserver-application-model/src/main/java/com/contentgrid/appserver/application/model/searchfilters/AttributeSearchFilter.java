package com.contentgrid.appserver.application.model.searchfilters;

import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.exceptions.InvalidSearchFilterException;
import com.contentgrid.appserver.application.model.i18n.ConfigurableTranslatable;
import com.contentgrid.appserver.application.model.i18n.Translatable;
import com.contentgrid.appserver.application.model.i18n.TranslatableImpl;
import com.contentgrid.appserver.application.model.i18n.TranslationBuilderSupport;
import com.contentgrid.appserver.application.model.searchfilters.flags.SearchFilterFlag;
import com.contentgrid.appserver.application.model.values.FilterName;
import com.contentgrid.appserver.application.model.values.PropertyPath;
import java.util.Locale;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Singular;
import lombok.experimental.Delegate;

/**
 * AttributeSearchFilter is a class representing search filters that operate on entity attributes.
 */
@Getter
public class AttributeSearchFilter implements SearchFilter {

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

    /**
     * Constructs an AttributeSearchFilter with the specified parameters.
     *
     * @param name the name of the search filter
     * @param attributePath the path to the attribute to apply the filter on
     * @param flags the flags of the search filter
     * @throws InvalidSearchFilterException if the attribute type is not supported
     */
    @Builder
    AttributeSearchFilter(
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
        EXACT(Set.of(Type.TEXT, Type.UUID, Type.LONG, Type.DOUBLE, Type.BOOLEAN, Type.DATETIME)),
        PREFIX(Set.of(Type.TEXT)),
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

    public static AttributeSearchFilterBuilder builder() {
        return new AttributeSearchFilterBuilder()
                .translations(new TranslatableImpl<>(ConfigurableSearchFilterTranslations::new));
    }

    public static class AttributeSearchFilterBuilder extends TranslationBuilderSupport<SearchFilterTranslations, ConfigurableSearchFilterTranslations, AttributeSearchFilterBuilder> {
        {
            getTranslations = () -> translations;
        }

        public AttributeSearchFilterBuilder attribute(@NonNull SimpleAttribute attribute) {
            this.attributePath = PropertyPath.of(attribute.getName());
            return this;
        }
    }
}
