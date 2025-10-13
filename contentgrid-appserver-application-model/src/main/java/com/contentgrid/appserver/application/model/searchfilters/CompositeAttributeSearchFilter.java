package com.contentgrid.appserver.application.model.searchfilters;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.i18n.ConfigurableTranslatable;
import com.contentgrid.appserver.application.model.i18n.TranslatableImpl;
import com.contentgrid.appserver.application.model.i18n.TranslationBuilderSupport;
import com.contentgrid.appserver.application.model.searchfilters.flags.SearchFilterFlag;
import com.contentgrid.appserver.application.model.values.FilterName;
import com.contentgrid.appserver.application.model.values.PropertyPath;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

@Getter
public class CompositeAttributeSearchFilter extends BaseAttributeSearchFilter  {

    private final @NonNull Set<@NonNull PropertyPath> attributePaths;

    @Builder
    CompositeAttributeSearchFilter(@NonNull Operation operation, @NonNull FilterName name,
                                   @NonNull ConfigurableTranslatable<SearchFilterTranslations, ConfigurableSearchFilterTranslations> translations,
                                   @NonNull Collection<@NonNull PropertyPath> attributePaths,
                                   @NonNull Set<SearchFilterFlag> flags) {
        super(operation, name, translations, flags);

        this.attributePaths = Set.copyOf(attributePaths);
    }

    public @NonNull Stream<@NonNull SimpleAttributeSearchFilter> toSimpleAttributeSearchFilters() {
        return this.attributePaths.stream()
                .map(attributePath -> SimpleAttributeSearchFilter.builder()
                        .operation(this.getOperation())
                        .name(this.getName()) 
                        .attributePath(attributePath) // TODO: what about translations here?
                        .flags(this.getFlags())
                        .build()
                );
    }

    public static @NonNull CompositeAttributeSearchFilterBuilder builder() {
        return new CompositeAttributeSearchFilterBuilder()
                .translations(new TranslatableImpl<>(ConfigurableSearchFilterTranslations::new))
                .flags(Set.of());
    }

    public static class CompositeAttributeSearchFilterBuilder extends TranslationBuilderSupport<SearchFilterTranslations, ConfigurableSearchFilterTranslations, CompositeAttributeSearchFilterBuilder> {
        {
            getTranslations = () -> translations;
        }

        CompositeAttributeSearchFilterBuilder() {
            this.attributePaths = new HashSet<>();
        }

        public @NonNull CompositeAttributeSearchFilterBuilder attributePaths(@NonNull Collection<PropertyPath> attributePaths) {
            this.attributePaths.addAll(attributePaths);

            return this;
        }

        public @NonNull CompositeAttributeSearchFilterBuilder attributePaths(@NonNull PropertyPath... attributePaths) {
            return attributePaths(Set.of(attributePaths));
        }

        public @NonNull CompositeAttributeSearchFilterBuilder attribute(@NonNull PropertyPath attributePath) {
            return attributePaths(Set.of(attributePath));
        }

    }

}
