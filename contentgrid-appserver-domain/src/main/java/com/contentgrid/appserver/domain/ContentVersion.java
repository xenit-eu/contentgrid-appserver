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

    private static final int HASH_LENGTH_BYTES = 16;

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

    @SneakyThrows(NoSuchAlgorithmException.class)
    private static String hash(String... inputs) {
        var md = MessageDigest.getInstance("SHA256");

        for (var input : inputs) {
            md.update(input.getBytes(StandardCharsets.UTF_8));
            md.update((byte) 0); // NUL-byte as separator for fields
        }

        var digest = md.digest();

        // An always-positive bigint, limited to 16 bytes (truncated sha-256 hash), to reduce the size of the version
        // This reduces the size of the version from 50 characters to a more sensible 25 characters
        return new BigInteger(1, digest, 0, HASH_LENGTH_BYTES)
                .toString(Character.MAX_RADIX);
    }
}
