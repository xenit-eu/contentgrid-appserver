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
import com.contentgrid.appserver.domain.content.ContentStoreResolver;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.domain.values.EntityRequest;
import com.contentgrid.appserver.domain.values.version.Version;
import com.contentgrid.appserver.domain.values.version.VersionConstraint;
import com.contentgrid.appserver.query.engine.api.data.CompositeAttributeData;
import com.contentgrid.appserver.query.engine.api.data.SimpleAttributeData;
import com.contentgrid.appserver.query.engine.api.exception.EntityIdNotFoundException;
import com.contentgrid.appserver.query.engine.api.exception.UnsatisfiedVersionException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

@RequiredArgsConstructor
public class ContentApiImpl implements ContentApi {
    private final DatamodelApiImpl datamodelApi;
    private final ContentStoreResolver contentStoreResolver;

    private AttributeDataContent extractContent(
            @NonNull Application application,
            @NonNull InternalEntityInstance entityData,
            @NonNull AttributeName attributeName
    ) {
        var contentStore = contentStoreResolver.resolve(application);
        var contentAttribute = application.getRequiredEntityByName(entityData.getIdentity().getEntityName())
                .getAttributeByName(attributeName)
                .filter(ContentAttribute.class::isInstance)
                .map(ContentAttribute.class::cast)
                .orElseThrow(); // TODO: throw a properly typed exception when the wrong attribute name is given

        return new AttributeDataContent(
                contentStore, contentAttribute,
                entityData.getByAttributeName(attributeName, CompositeAttributeData.class).orElse(null)
        );
    }

    @Override
    public Optional<Content> find(@NonNull Application application, @NonNull EntityName entityName,
            @NonNull EntityId id, @NonNull AttributeName attributeName,
            @NonNull AuthorizationContext authorizationContext) {

        var entityData = datamodelApi.findById(application, EntityRequest.forEntity(entityName, id), authorizationContext)
                .orElseThrow(() -> new EntityIdNotFoundException(entityName, id));
        return Optional.of(extractContent(application, entityData, attributeName))
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

    @RequiredArgsConstructor
    private class AttributeDataContent implements Content {
        private final ContentStore contentStore;
        @NonNull
        private final ContentAttribute contentAttribute;
        private final CompositeAttributeData attributeData;

        private final ContentRangeRequest contentRange;

        public AttributeDataContent(
                ContentStore contentStore,
                ContentAttribute contentAttribute,
                CompositeAttributeData attributeData
        ) {
            this(contentStore, contentAttribute, attributeData, null);
        }

        protected Optional<ContentReference> getContentId() {
            return Optional.ofNullable(getAttribute(contentAttribute.getId(), String.class))
                    .map(ContentReference::of);
        }

        @SneakyThrows
        @Override
        public Content withByteRange(long start, long endInclusive) {
            return new AttributeDataContent(contentStore, contentAttribute, attributeData, ContentRangeRequest.createRange(start, endInclusive));
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
            return "ContentAttribute %s: '%s'%s".formatted(
                    contentAttribute.getName(),
                    getContentId().orElseThrow(),
                    contentRange == null ? "":" [range: %s]".formatted(contentRange)
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
                        contentRange == null ? null : contentRange.resolve(getLength())
                );
                return reader.getContentInputStream();
            } catch (UnreadableContentException | UnsatisfiableContentRangeException e) {
                throw new IOException(e);
            }
        }

        @Override
        public Version getVersion() {
            return ContentVersion.calculate(contentAttribute, attributeData)
                    .orElseGet(Version::nonExisting);
        }
    }
}
