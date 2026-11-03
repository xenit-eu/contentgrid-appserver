package com.contentgrid.appserver.domain;

import com.contentgrid.appserver.application.model.attributes.ContentAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.domain.values.version.Version;
import com.contentgrid.appserver.query.engine.api.data.CompositeAttributeData;
import com.contentgrid.appserver.query.engine.api.data.SimpleAttributeData;
import java.util.Optional;

public final class ContentVersion extends AbstractHashbasedVersion {

    private ContentVersion() {
    }

    public static Optional<Version> calculate(
            ContentAttribute contentAttribute,
            CompositeAttributeData attributeData
    ) {
        if (attributeData == null) {
            return Optional.empty();
        }

        var contentId = getAttribute(contentAttribute.getId(), attributeData);

        if (contentId == null) {
            return Optional.empty();
        }

        // hash contentId, so it is not recognizable anymore in the exposed version
        // Also hash in mimetype, because a change in mimetype changes the interpretation of the content,
        // which is a semantically-significant part of representation metadata (which we want to cover with the version)
        // A change in filename is not semantically-significant, as it does not affect the interpretation of the content.
        // Length is irrelevant, since the only way to change length is to upload new content, which changes the content id
        var mimeType = getAttribute(contentAttribute.getMimetype(), attributeData);

        return Optional.of(Version.exactly(hash(
                contentId,
                mimeType
        )));
    }

    private static String getAttribute(
            SimpleAttribute attribute,
            CompositeAttributeData attributeData
    ) {
        return attributeData.getAttributeByName(attribute.getName())
                .map(SimpleAttributeData.class::cast)
                .map(SimpleAttributeData::getValue)
                .map(String.class::cast)
                .orElse(null);
    }
}
