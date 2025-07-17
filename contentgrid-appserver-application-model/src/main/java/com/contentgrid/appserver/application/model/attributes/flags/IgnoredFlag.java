package com.contentgrid.appserver.application.model.attributes.flags;

/**
 * AttributeFlag that indicates the attribute should not be included in the request or response bodies.
 * <p>
 * When placed on a CompositeAttribute, each sub-attribute will be ignored.
 */
public interface IgnoredFlag extends AttributeFlag {

    IgnoredFlag INSTANCE = attribute -> {};

}
