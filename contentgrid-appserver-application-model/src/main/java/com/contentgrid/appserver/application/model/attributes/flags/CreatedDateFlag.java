package com.contentgrid.appserver.application.model.attributes.flags;

import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.exceptions.InvalidFlagException;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CreatedDateFlag implements ReadOnlyFlag {

    @Override
    public void checkSupported(Attribute attribute) {
        if (!(attribute instanceof SimpleAttribute simpleAttribute) || !simpleAttribute.getType().equals(Type.DATETIME)) {
            throw new InvalidFlagException("Flag CreatedDate is only supported on datetime attributes");
        }
    }
}
