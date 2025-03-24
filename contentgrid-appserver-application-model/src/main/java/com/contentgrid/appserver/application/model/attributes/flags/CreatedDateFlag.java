package com.contentgrid.appserver.application.model.attributes.flags;

import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CreatedDateFlag implements AttributeFlag {

    @Override
    public boolean isSupported(Attribute attribute) {
        return attribute instanceof SimpleAttribute simpleAttribute && simpleAttribute.getType().equals(Type.DATETIME);
    }
}
