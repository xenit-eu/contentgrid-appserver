package com.contentgrid.appserver.application.model.searchfilters;

import com.contentgrid.appserver.application.model.i18n.Translatable;
import com.contentgrid.appserver.application.model.searchfilters.SearchFilter.SearchFilterTranslations;
import com.contentgrid.appserver.application.model.searchfilters.flags.SearchFilterFlag;
import com.contentgrid.appserver.application.model.values.FilterName;
import java.util.Set;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Value;
import lombok.With;

/**
 * Represents a search filter that can be applied to entity queries.
 * <p>
 * Search filters define how entities can be searched or filtered based on specific criteria.
 */
public interface SearchFilter extends Translatable<SearchFilterTranslations> {

    interface SearchFilterTranslations {
        String getName();
        String getDescription();
    }

    @Value
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PACKAGE, force = true)
    @With
    class ConfigurableSearchFilterTranslations implements SearchFilterTranslations {
        String name;
        String description;
    }

    /**
     * Gets the name of the search filter.
     * 
     * @return the name of the search filter
     */
    FilterName getName();

    Set<SearchFilterFlag> getFlags();

    default boolean hasFlag(Class<? extends SearchFilterFlag> flagClass) {
        return getFlags()
                .stream()
                .anyMatch(flagClass::isInstance);
    }

}
