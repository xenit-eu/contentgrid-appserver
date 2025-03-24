package com.contentgrid.appserver.application.model.attributes.flags;

import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.UserAttribute;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ModifierFlag implements AttributeFlag {

    @Override
    public boolean isSupported(Attribute attribute) {
        return attribute instanceof UserAttribute;
    }
}
