package com.contentgrid.appserver.application.model.attributes.flags;

import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.UserAttribute;
import com.contentgrid.appserver.application.model.exceptions.InvalidFlagException;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ModifierFlag implements ReadOnlyFlag {

    @Override
    public void checkSupported(Attribute attribute) {
        if (!(attribute instanceof UserAttribute)) {
            throw new InvalidFlagException("Flag Modifier is only supported on user attributes");
        }
    }
}
