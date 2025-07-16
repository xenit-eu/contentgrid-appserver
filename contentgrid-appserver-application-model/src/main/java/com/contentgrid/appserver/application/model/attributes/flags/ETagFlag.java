package com.contentgrid.appserver.application.model.attributes.flags;

import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.exceptions.InvalidFlagException;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Value;

@Value
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ETagFlag implements IgnoredFlag {

    public static final ETagFlag INSTANCE = new ETagFlag();

    @Override
    public void checkSupported(Attribute attribute) {
        if (!(attribute instanceof SimpleAttribute simpleAttribute) || !simpleAttribute.getType().equals(Type.LONG)) {
            throw new InvalidFlagException("Flag ETag is only supported on long attributes");
        }
    }
}
