package com.contentgrid.appserver.application.model.openapi.type;

import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.CompositeAttribute;
import com.contentgrid.appserver.application.model.attributes.ContentAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.UserAttribute;
import lombok.Value;

public sealed interface AttributeType<T extends Attribute> extends SemanticType{
    T getAttribute();

    static <T extends Attribute> AttributeType<T> of(T attribute) {
        return (AttributeType<T>) switch (attribute) {
            case SimpleAttribute simpleAttribute -> new SimpleAttributeType(simpleAttribute);
            case ContentAttribute contentAttribute -> new ContentAttributeType(contentAttribute);
            case UserAttribute userAttribute -> new UserAttributeType(userAttribute);
            case CompositeAttribute compositeAttribute -> new CompositeAttributeType(compositeAttribute);
        };
    }

    @Value
    class CompositeAttributeType implements AttributeType<CompositeAttribute> {
        CompositeAttribute attribute;
    }

    @Value
    class SimpleAttributeType implements AttributeType<SimpleAttribute> {
        SimpleAttribute attribute;
    }

    @Value
    class ContentAttributeType implements AttributeType<ContentAttribute> {
        ContentAttribute attribute;
    }

    @Value
    class UserAttributeType implements AttributeType<UserAttribute> {
        UserAttribute attribute;
    }
}
