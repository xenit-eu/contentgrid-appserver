package com.contentgrid.appserver.application.model.attributes.flags;

import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.UserAttribute;
import com.contentgrid.appserver.application.model.exceptions.InvalidFlagException;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Value;

@Value
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CreatorFlag implements ReadOnlyFlag {

    public static final CreatorFlag INSTANCE = new CreatorFlag();

    @Override
    public void checkSupported(Attribute attribute) {
        if (!(attribute instanceof UserAttribute)) {
            throw new InvalidFlagException("Flag Creator is only supported on user attributes");
        }
    }
}
