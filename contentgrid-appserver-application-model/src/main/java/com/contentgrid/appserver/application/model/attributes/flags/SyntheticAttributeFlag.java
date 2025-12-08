package com.contentgrid.appserver.application.model.attributes.flags;

/**
 * Marks an attribute as <i>synthetic</i>; generated internally for the benefit of the application itself
 * <p>
 * Synthetic attributes are not part of the application model, and are always hidden as well
 */
public interface SyntheticAttributeFlag extends IgnoredFlag {
    SyntheticAttributeFlag INSTANCE = attribute -> {};
}
