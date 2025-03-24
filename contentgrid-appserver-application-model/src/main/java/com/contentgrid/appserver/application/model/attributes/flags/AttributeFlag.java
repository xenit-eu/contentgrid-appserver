package com.contentgrid.appserver.application.model.attributes.flags;

import com.contentgrid.appserver.application.model.attributes.Attribute;

public interface AttributeFlag {

    boolean isSupported(Attribute attribute);
}
