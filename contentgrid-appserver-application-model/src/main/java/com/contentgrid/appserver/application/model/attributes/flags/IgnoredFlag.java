package com.contentgrid.appserver.application.model.attributes.flags;

import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.ContentAttribute;
import com.contentgrid.appserver.application.model.exceptions.InvalidFlagException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * AttributeFlag that indicates the attribute should not be included in the request or response bodies.
 * <p>
 * When placed on a CompositeAttribute, each sub-attribute will be ignored.
 */
@NoArgsConstructor(access = AccessLevel.PACKAGE)
public class IgnoredFlag implements AttributeFlag {

    public static final IgnoredFlag INSTANCE = new IgnoredFlag();

    @Override
    public void checkSupported(Attribute attribute) {
        if (attribute instanceof ContentAttribute contentAttribute) {
            if (contentAttribute.getPathSegment() != null) {
                throw new InvalidFlagException(
                        "Content attribute '%s' can only be ignored if it has no pathSegment".formatted(
                                attribute.getName()));
            }
            if (contentAttribute.getLinkName() != null) {
                throw new InvalidFlagException(
                        "Content attribute '%s' can only be ignored if it has no linkName".formatted(
                                attribute.getName()));
            }
        }

    }
}
