package com.contentgrid.appserver.application.model.attributes.flags;

import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.UserAttribute;
import com.contentgrid.appserver.application.model.exceptions.InvalidFlagException;
import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class CreatorFlag implements AttributeFlag {

    @Override
    public void checkSupported(Attribute attribute) {
        if (!(attribute instanceof UserAttribute)) {
            throw new InvalidFlagException("Flag Creator is only supported on user attributes");
        }
    }
}
