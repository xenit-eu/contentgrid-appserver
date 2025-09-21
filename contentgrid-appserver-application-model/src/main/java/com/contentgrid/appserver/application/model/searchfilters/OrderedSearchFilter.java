package com.contentgrid.appserver.application.model.searchfilters;

import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.exceptions.InvalidSearchFilterException;
import com.contentgrid.appserver.application.model.i18n.ManipulatableTranslatable;
import com.contentgrid.appserver.application.model.i18n.TranslatableImpl;
import com.contentgrid.appserver.application.model.i18n.TranslationBuilderSupport;
import com.contentgrid.appserver.application.model.searchfilters.flags.SearchFilterFlag;
import com.contentgrid.appserver.application.model.values.FilterName;
import com.contentgrid.appserver.application.model.values.PropertyPath;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Singular;

@Getter
public class OrderedSearchFilter extends AttributeSearchFilter {

    private static final Set<Type> SUPPORTED_TYPES = Set.of(Type.LONG, Type.DOUBLE, Type.DATETIME);

    private final Operation operation;

    /**
     * Constructs an OrderedSearchFilter with the specified parameters.
     *
     * @param name the name of the search filter
     * @param attributePath the path to the attribute to apply the filter on
     * @throws InvalidSearchFilterException if the attribute type is not supported
     */
    @Builder
    OrderedSearchFilter(
            @NonNull Operation operation,
            @NonNull FilterName name,
            @NonNull ManipulatableTranslatable<SearchFilterTranslations> translations,
            @NonNull PropertyPath attributePath,
            @NonNull @Singular Set<SearchFilterFlag> flags) {
        super(name, translations, attributePath, flags);
        this.operation = operation;
    }

    @Override
    public boolean supports(SimpleAttribute attribute) {
        return SUPPORTED_TYPES.contains(attribute.getType());
    }

    public enum Operation {
        GREATER_THAN,
        GREATER_THAN_OR_EQUAL,
        LESS_THAN,
        LESS_THAN_OR_EQUAL,
    }

    public static OrderedSearchFilterBuilder builder() {
        return new OrderedSearchFilterBuilder()
                .translations(new TranslatableImpl<>(SearchFilterTranslations::new));
    }

    public static OrderedSearchFilterBuilder greaterThan() {
        return builder().operation(Operation.GREATER_THAN);
    }

    public static OrderedSearchFilterBuilder greaterThanOrEqual() {
        return builder().operation(Operation.GREATER_THAN_OR_EQUAL);
    }

    public static OrderedSearchFilterBuilder lessThan() {
        return builder().operation(Operation.LESS_THAN);
    }

    public static OrderedSearchFilterBuilder lessThanOrEqual() {
        return builder().operation(Operation.LESS_THAN_OR_EQUAL);
    }

    public static class OrderedSearchFilterBuilder extends TranslationBuilderSupport<SearchFilterTranslations, OrderedSearchFilterBuilder> {
        {
            getTranslations = () -> translations;
        }

        public OrderedSearchFilterBuilder attribute(@NonNull SimpleAttribute attribute) {
            this.attributePath = PropertyPath.of(attribute.getName());
            return this;
        }
    }
}
