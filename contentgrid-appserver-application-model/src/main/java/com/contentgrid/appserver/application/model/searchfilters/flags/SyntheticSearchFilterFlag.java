package com.contentgrid.appserver.application.model.searchfilters.flags;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Marks a search filter as <i>synthetic</i>; generated internally for the benefit of the application itself
 * <p>
 * Synthetic search filters are not part of the application model, and are always hidden as well
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SyntheticSearchFilterFlag extends HiddenSearchFilterFlag {
    public static final SyntheticSearchFilterFlag INSTANCE = new SyntheticSearchFilterFlag();

}
