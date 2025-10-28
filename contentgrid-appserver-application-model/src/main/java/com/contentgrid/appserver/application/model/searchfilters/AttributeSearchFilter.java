package com.contentgrid.appserver.application.model.searchfilters;

import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.exceptions.InvalidSearchFilterException;
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

import java.util.Set;

/**
 * AttributeSearchFilter is a class representing search filters that operate on entity attributes.
 */
@Getter
public class AttributeSearchFilter extends BaseAttributeSearchFilter {

    @NonNull
    private final Operation operation;

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
        super(name, translations, attributePath, flags);

        this.operation = operation;
    }

    public static AttributeSearchFilterBuilder builder() {
        return new AttributeSearchFilterBuilder()
                .translations(new TranslatableImpl<>(ConfigurableSearchFilterTranslations::new));
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
