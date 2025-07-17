package com.contentgrid.appserver.application.model.attributes.flags;

import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.UserAttribute;
import com.contentgrid.appserver.application.model.exceptions.InvalidFlagException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.Value;

@Value
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ModifierFlag implements ReadOnlyFlag {

    public static final ModifierFlag INSTANCE = new ModifierFlag();

    @Override
    public void checkSupported(Attribute attribute) {
        if (!(attribute instanceof UserAttribute)) {
            throw new InvalidFlagException("Flag Modifier is only supported on user attributes");
        }
    }
}
