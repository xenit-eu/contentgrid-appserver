package com.contentgrid.appserver.domain;

import com.contentgrid.appserver.application.model.attributes.ContentAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.domain.values.version.Version;
import com.contentgrid.appserver.query.engine.api.data.CompositeAttributeData;
import com.contentgrid.appserver.query.engine.api.data.SimpleAttributeData;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import lombok.SneakyThrows;

public final class ContentVersion {

    private ContentVersion() {
    }

    public static Optional<Version> calculate(
            ContentAttribute contentAttribute,
            CompositeAttributeData attributeData
    ) {
        var contentId = getAttribute(contentAttribute.getId(), attributeData);

        if (contentId == null) {
            return Optional.empty();
        }

        // Hash the content ID so it is not recognizable in the exposed version.
        // MIME type is included because it changes the interpretation of the content.
        // Filename is deliberately not included because changing it does not change
        // the semantic content representation.

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

    @SneakyThrows(NoSuchAlgorithmException.class)
    private static String hash(String... inputs) {
        var md = MessageDigest.getInstance("SHA256");

        for (var input : inputs) {
            md.update(input.getBytes(StandardCharsets.UTF_8));
            md.update((byte) 0);
        }

        var digest = md.digest();

        return new BigInteger(1, digest, 0, 16)
                .toString(Character.MAX_RADIX);
    }
}
