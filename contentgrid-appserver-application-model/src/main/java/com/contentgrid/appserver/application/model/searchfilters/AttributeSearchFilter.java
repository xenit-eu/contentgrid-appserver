package com.contentgrid.appserver.application.model.searchfilters;

import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.CompositeAttribute;
import com.contentgrid.appserver.application.model.attributes.MultivalueAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.exceptions.InvalidSearchFilterException;
import com.contentgrid.appserver.application.model.i18n.ConfigurableTranslatable;
import com.contentgrid.appserver.application.model.i18n.TranslatableImpl;
import com.contentgrid.appserver.application.model.i18n.TranslationBuilderSupport;
import com.contentgrid.appserver.application.model.propertypath.SimpleAttributePath;
import com.contentgrid.appserver.application.model.searchfilters.flags.SearchFilterFlag;
import com.contentgrid.appserver.application.model.values.FilterName;
import com.contentgrid.appserver.application.model.propertypath.PropertyPath;
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
            @NonNull PropertyPath.ResolvesToAttribute attributePath,
            @NonNull @Singular Set<SearchFilterFlag> flags) {
        super(name, translations, attributePath, flags);

        this.operation = operation;
    }

    public static AttributeSearchFilterBuilder builder() {
        return new AttributeSearchFilterBuilder()
                .translations(new TranslatableImpl<>(ConfigurableSearchFilterTranslations::new));
    }

    @Override
    public boolean supports(Attribute attribute) {
        return operation.supports(attribute);
    }

    public enum Operation {
        EXACT(Set.of(Type.TEXT, Type.UUID, Type.LONG, Type.DOUBLE, Type.BOOLEAN, Type.DATE, Type.DATETIME)),
        CONTAINS(Set.of()),
        PREFIX(Set.of(Type.TEXT)),
        GREATER_THAN(Set.of(Type.LONG, Type.DOUBLE, Type.DATE, Type.DATETIME)),
        GREATER_THAN_OR_EQUAL(Set.of(Type.LONG, Type.DOUBLE, Type.DATE, Type.DATETIME)),
        LESS_THAN(Set.of(Type.LONG, Type.DOUBLE, Type.DATE, Type.DATETIME)),
        LESS_THAN_OR_EQUAL(Set.of(Type.LONG, Type.DOUBLE, Type.DATE, Type.DATETIME)),
        ;

        private final Set<Type> supportedTypes;

        Operation(Set<Type> supportedTypes) {
            this.supportedTypes = supportedTypes;
        }

        public boolean supports(Attribute attribute) {
            return switch (attribute) {
                case SimpleAttribute simpleAttribute -> supportedTypes.contains(simpleAttribute.getType());
                case MultivalueAttribute multivalueAttribute ->
                        this == CONTAINS && multivalueAttribute.getItemType() == Type.TEXT;
                case CompositeAttribute ignored -> false;
            };
        }
    }

    public static class AttributeSearchFilterBuilder extends TranslationBuilderSupport<SearchFilterTranslations, ConfigurableSearchFilterTranslations, AttributeSearchFilterBuilder> {
        {
            getTranslations = () -> translations;
        }

        public AttributeSearchFilterBuilder attribute(@NonNull SimpleAttribute attribute) {
            this.attributePath = new SimpleAttributePath(attribute.getName());
            return this;
        }

        public AttributeSearchFilterBuilder attribute(@NonNull MultivalueAttribute attribute) {
            this.attributePath = new SimpleAttributePath(attribute.getName());
            return this;
        }
    }
}
