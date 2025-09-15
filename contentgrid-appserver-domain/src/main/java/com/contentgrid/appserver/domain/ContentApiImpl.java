package com.contentgrid.appserver.domain;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.attributes.ContentAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.contentstore.api.ContentReference;
import com.contentgrid.appserver.contentstore.api.ContentStore;
import com.contentgrid.appserver.contentstore.api.UnreadableContentException;
import com.contentgrid.appserver.contentstore.api.range.ContentRangeRequest;
import com.contentgrid.appserver.contentstore.api.range.UnsatisfiableContentRangeException;
import com.contentgrid.appserver.domain.authorization.AuthorizationContext;
import com.contentgrid.appserver.domain.data.DataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.NullDataEntry;
import com.contentgrid.appserver.domain.data.InvalidPropertyDataException;
import com.contentgrid.appserver.domain.data.MapRequestInputData;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.domain.values.EntityRequest;
import com.contentgrid.appserver.domain.values.User;
import com.contentgrid.appserver.domain.values.version.Version;
import com.contentgrid.appserver.domain.values.version.VersionConstraint;
import com.contentgrid.appserver.query.engine.api.data.CompositeAttributeData;
import com.contentgrid.appserver.query.engine.api.data.SimpleAttributeData;
import com.contentgrid.appserver.query.engine.api.exception.EntityIdNotFoundException;
import com.contentgrid.appserver.query.engine.api.exception.UnsatisfiedVersionException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Optional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

@RequiredArgsConstructor
public class ContentApiImpl implements ContentApi {
    private final DatamodelApiImpl datamodelApi;
    private final ContentStore contentStore;

    private AttributeDataContent extractContent(
            @NonNull Application application,
            @NonNull InternalEntityInstance entityData,
            @NonNull AttributeName attributeName
    ) {
        var contentAttribute = application.getRequiredEntityByName(entityData.getIdentity().getEntityName())
                .getAttributeByName(attributeName)
                .filter(ContentAttribute.class::isInstance)
                .map(ContentAttribute.class::cast)
                .orElseThrow(); // TODO: throw a properly typed exception when the wrong attribute name is given

        return new AttributeDataContent(
                contentAttribute,
                entityData.getByAttributeName(attributeName, CompositeAttributeData.class).orElse(null)
        );
    }

    @Override
    public Optional<Content> find(@NonNull Application application, @NonNull EntityName entityName,
            @NonNull EntityId id, @NonNull AttributeName attributeName,
            @NonNull AuthorizationContext authorizationContext) {

        return datamodelApi.findById(application, EntityRequest.forEntity(entityName, id), authorizationContext)
                .map(entityData -> extractContent(application, entityData, attributeName))
                .filter(content -> content.getContentId().isPresent())
                .map(Content.class::cast);
    }

    @Override
    public Content update(@NonNull Application application, @NonNull EntityName entityName, @NonNull EntityId id,
            @NonNull AttributeName attributeName, @NonNull VersionConstraint versionConstraint,
            @NonNull DataEntry.FileDataEntry file, @NonNull AuthorizationContext authorizationContext
    ) throws InvalidPropertyDataException {
        var original = requireEntityWithConstraint(application, entityName, id, attributeName, versionConstraint,
                authorizationContext);

        var updated = datamodelApi.updatePartial(application, original, MapRequestInputData.fromMap(Map.of(
                attributeName.getValue(), file
        )), authorizationContext);

        return extractContent(application, updated, attributeName);
    }

    private InternalEntityInstance requireEntityWithConstraint(
            Application application,
            EntityName entityName,
            EntityId id,
            AttributeName attributeName,
            VersionConstraint versionConstraint,
            @NonNull AuthorizationContext authorizationContext) {
        var original = datamodelApi.findById(application, EntityRequest.forEntity(entityName, id), authorizationContext)
                .orElseThrow(() -> new EntityIdNotFoundException(
                        entityName, id));

        var contentVersion = extractContent(application, original, attributeName).getVersion();

        if(!versionConstraint.isSatisfiedBy(contentVersion)) {
            throw new UnsatisfiedVersionException(
                    contentVersion,
                    versionConstraint
            );
        }
        return original;
    }

    @Override
    public void delete(@NonNull Application application, @NonNull EntityName entityName, @NonNull EntityId id,
            @NonNull AttributeName attributeName, @NonNull VersionConstraint versionConstraint,
            @NonNull AuthorizationContext authorizationContext) throws InvalidPropertyDataException {
        var original = requireEntityWithConstraint(application, entityName, id, attributeName, versionConstraint,
                authorizationContext);
        datamodelApi.updatePartial(application, original, MapRequestInputData.fromMap(Map.of(
                attributeName.getValue(), NullDataEntry.INSTANCE
        )), authorizationContext);
    }

    // package-private for testing
    @SneakyThrows(NoSuchAlgorithmException.class)
    static String hash(String... inputs) {
        var md = MessageDigest.getInstance("SHA256");
        for (var input : inputs) {
            md.update(input.getBytes(StandardCharsets.UTF_8));
            md.update((byte) 0); // NUL-byte as separator for fields
        }
        var digest = md.digest();
        // An always-positive bigint, limited to 16 bytes (truncated sha-256 hash), to reduce the size of the version
        // This reduces the size of the version from 50 characters to a more sensible 25 characters
        return new BigInteger(1, digest, 0, 16).toString(Character.MAX_RADIX);
    }

    @RequiredArgsConstructor
    private class AttributeDataContent implements Content {
        @NonNull
        private final ContentAttribute contentAttribute;
        private final CompositeAttributeData attributeData;

        @NonNull
        private final ContentRangeRequest contentRange;

        public AttributeDataContent(
                ContentAttribute contentAttribute,
                CompositeAttributeData attributeData
        ) {
            this(contentAttribute, attributeData, ContentRangeRequest.createRange(0));
        }

        protected Optional<ContentReference> getContentId() {
            return Optional.ofNullable(getAttribute(contentAttribute.getId(), String.class))
                    .map(ContentReference::of);
        }

        @SneakyThrows
        @Override
        public Content withByteRange(long start, long endInclusive) {
            return new AttributeDataContent(contentAttribute, attributeData, ContentRangeRequest.createRange(start, endInclusive));
        }

        private <T> T getAttribute(SimpleAttribute attribute, Class<T> type) {
            if(attributeData == null) {
                return null;
            }
            return (T)attributeData.getAttributeByName(attribute.getName())
                    .map(SimpleAttributeData.class::cast)
                    .orElseThrow()
                    .getValue();
        }

        @Override
        public String getDescription() {
            return "ContentAttribute %s: '%s' [range: %s]".formatted(
                    contentAttribute.getName(),
                    getContentId().orElseThrow(),
                    contentRange
            );
        }

        @Override
        public String getFilename() {
            return getAttribute(contentAttribute.getFilename(), String.class);
        }

        @Override
        public long getLength() {
            return getAttribute(contentAttribute.getLength(), Long.class);
        }

        @Override
        public String getMimeType() {
            return getAttribute(contentAttribute.getMimetype(), String.class);
        }

        @Override
        public InputStream getInputStream() throws IOException {
            try {
                var reader = contentStore.getReader(
                        getContentId().orElseThrow(),
                        contentRange.resolve(getLength())
                );
                return reader.getContentInputStream();
            } catch (UnreadableContentException | UnsatisfiableContentRangeException e) {
                throw new IOException(e);
            }
        }

        @Override
        public Version getVersion() {
            var contentId = getAttribute(contentAttribute.getId(), String.class);
            if(contentId == null) {
                return Version.nonExisting();
            }
            // hash contentId, so it is not recognizable anymore in the exposed version
            // Also hash in mimetype, because a change in mimetype changes the interpretation of the content,
            // which is a semantically-significant part of representation metadata (which we want to cover with the version)
            // A change in filename is not semantically-significant, as it does not affect the interpretation of the content.
            // Length is irrelevant, since the only way to change length is to upload new content, which changes the content id
            return Version.exactly(hash(
                    contentId,
                    getMimeType()
            ));
        }

    }
}
