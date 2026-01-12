package com.contentgrid.appserver.application.model.searchfilters;

import com.contentgrid.appserver.application.model.attributes.ContentAttribute;
import com.contentgrid.appserver.application.model.i18n.ConfigurableTranslatable;
import com.contentgrid.appserver.application.model.i18n.TranslatableImpl;
import com.contentgrid.appserver.application.model.i18n.TranslationBuilderSupport;
import com.contentgrid.appserver.application.model.searchfilters.flags.SearchFilterFlag;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.FilterName;
import com.contentgrid.appserver.application.model.values.PropertyPath;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * FullTextSearchContentAttributeSearchFilter is a search filter that performs full-text search against
 * a specific content attribute.
 * <br>
 * It needs to be different from the {@link FullTextSearchAttributeSearchFilter} because it uses
 * custom logic to resolve the content attribute to the correct database table with the extracted
 * text when querying.
 */
@Getter
public class FullTextSearchContentAttributeSearchFilter extends BaseAttributeSearchFilter implements LocaleAwareSearchFilter {
    public static final String HIDDEN_EXTRACTED_TEXT_ATTRIBUTE_FORMAT = "%s__cg_text";

    @NonNull private final Locale locale;

    @Builder
    public FullTextSearchContentAttributeSearchFilter(
            @NonNull FilterName name,
            @NonNull ConfigurableTranslatable<SearchFilterTranslations, ConfigurableSearchFilterTranslations> translations,
            @NonNull PropertyPath attributePath,
            @NonNull Set<SearchFilterFlag> flags,
            @NonNull Locale locale) {
        super(name, translations, attributePath, flags);

        this.locale = locale;
    }

    public static @NonNull FullTextSearchContentAttributeSearchFilterBuilder builder() {
        return new FullTextSearchContentAttributeSearchFilterBuilder()
                .translations(new TranslatableImpl<>(ConfigurableSearchFilterTranslations::new))
                .flags(Set.of());
    }

    public String getHiddenTextAttributeFormattedName() {
        List<String> path = this.getAttributePath().toList();
        // Format the paths from x.y.z to x_y_z so they can be used to construct
        // the attribute name. However, if a path is formatted like
        // some.path_to.attribute, and another attribute is formatted like
        // some.path.to.attribute, both will end up with the same generated name for
        // the extracted text attribute. To get around this, we can replace all underscores in each
        // path element's name with two underscores. This way, the paths above become
        // some_path__to_attribute and some_path_to_attribute.
        String formattedPath = path.stream()
                .map(element -> element.replace("_", "__"))
                .collect(Collectors.joining("_"));
        return HIDDEN_EXTRACTED_TEXT_ATTRIBUTE_FORMAT.formatted(formattedPath);
    }

    public static class FullTextSearchContentAttributeSearchFilterBuilder extends TranslationBuilderSupport<SearchFilterTranslations, ConfigurableSearchFilterTranslations, FullTextSearchContentAttributeSearchFilterBuilder> {
        {
            getTranslations = () -> translations;
        }

        public FullTextSearchContentAttributeSearchFilterBuilder attribute(@NonNull ContentAttribute attribute) {
            this.attributePath = PropertyPath.of(attribute.getName());
            return this;
        }
    }
}
