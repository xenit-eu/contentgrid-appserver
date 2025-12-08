package com.contentgrid.appserver.application.model.attributes.flags;

/**
 * Flag that indicates an attribute should not be serialized into application schemas.
 * Useful for meta-attributes that are automatically created at runtime.
 */
public interface DoNotSerializeFlag extends AttributeFlag {
    DoNotSerializeFlag INSTANCE = attribute -> {};
}
