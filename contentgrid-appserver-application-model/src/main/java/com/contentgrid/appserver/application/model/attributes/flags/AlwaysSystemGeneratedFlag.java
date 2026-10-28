package com.contentgrid.appserver.application.model.attributes.flags;

import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.exceptions.InvalidFlagException;

/**
 * AttributeFlag that indicates that an attribute is <em>always</em> system-generated, and never derived from user input.
 * <p>
 * This bypasses attribute validation, so it must only be used with attributes that are never provided via any user input.
 */
public enum AlwaysSystemGeneratedFlag implements AttributeFlag {
    INSTANCE;

    @Override
    public void checkSupported(Attribute attribute) {
        if(!attribute.hasFlag(ReadOnlyFlag.class) && !attribute.hasFlag(IgnoredFlag.class)) {
            throw new InvalidFlagException("Flag AlwaysSystemGenerated can only be applied to attributes with ReadOnlyFlag or IgnoredFlag");
        }
    }
}
