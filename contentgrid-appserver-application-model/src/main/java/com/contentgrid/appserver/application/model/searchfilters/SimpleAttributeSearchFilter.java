package com.contentgrid.appserver.application.model.searchfilters;

import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.exceptions.InvalidSearchFilterException;
import com.contentgrid.appserver.application.model.i18n.ConfigurableTranslatable;
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

/**
 * SimpleAttributeSearchFilter is a class representing search filters that operate on entity attributes.
 */
@Getter
public class SimpleAttributeSearchFilter extends BaseAttributeSearchFilter {

        /**
     * The path to the attribute this search filter operates on.
     * For simple attributes, this will be a single-element list.
     * For composite attributes, this will be a multi-element list representing the path.
     */
    @NonNull
    private final PropertyPath attributePath;

    /**
     * Constructs an SimpleAttributeSearchFilter with the specified parameters.
     *
     * @param name the name of the search filter
     * @param attributePath the path to the attribute to apply the filter on
     * @param flags the flags of the search filter
     * @throws InvalidSearchFilterException if the attribute type is not supported
     */
    @Builder
    SimpleAttributeSearchFilter(
            @NonNull Operation operation,
            @NonNull FilterName name,
            @NonNull ConfigurableTranslatable<SearchFilterTranslations, ConfigurableSearchFilterTranslations> translations,
            @NonNull PropertyPath attributePath,
            @NonNull @Singular Set<SearchFilterFlag> flags) {
        super(operation, name, translations, flags);

        this.attributePath = attributePath;
    }

    public static SimpleAttributeSearchFilterBuilder builder() {
        return new SimpleAttributeSearchFilterBuilder()
                .translations(new TranslatableImpl<>(ConfigurableSearchFilterTranslations::new));
    }

    public static class SimpleAttributeSearchFilterBuilder extends TranslationBuilderSupport<SearchFilterTranslations, ConfigurableSearchFilterTranslations, SimpleAttributeSearchFilterBuilder> {
        {
            getTranslations = () -> translations;
        }

        public SimpleAttributeSearchFilterBuilder attribute(@NonNull SimpleAttribute attribute) {
            this.attributePath = PropertyPath.of(attribute.getName());
            return this;
        }
    }
    
}
