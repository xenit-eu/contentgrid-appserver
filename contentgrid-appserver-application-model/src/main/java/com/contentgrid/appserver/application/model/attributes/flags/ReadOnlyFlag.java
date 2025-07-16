package com.contentgrid.appserver.application.model.attributes.flags;

/**
 * AttributeFlag that indicates the attribute is only included in response bodies.
 * <p>
 * When placed on a CompositeAttribute, each sub-attribute will be read-only.
 */
public interface ReadOnlyFlag extends AttributeFlag {

    ReadOnlyFlag INSTANCE = attribute -> {};

}
