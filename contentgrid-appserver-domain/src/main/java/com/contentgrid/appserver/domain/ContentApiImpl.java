package com.contentgrid.appserver.domain;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.attributes.ContentAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.exceptions.EntityNotFoundException;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.content.api.ContentReference;
import com.contentgrid.appserver.content.api.ContentStore;
import com.contentgrid.appserver.content.api.UnreadableContentException;
import com.contentgrid.appserver.content.api.range.ContentRangeRequest;
import com.contentgrid.appserver.content.api.range.UnsatisfiableContentRangeException;
import com.contentgrid.appserver.domain.data.DataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.NullDataEntry;
import com.contentgrid.appserver.domain.data.InvalidPropertyDataException;
import com.contentgrid.appserver.domain.data.MapRequestInputData;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.query.engine.api.data.CompositeAttributeData;
import com.contentgrid.appserver.query.engine.api.data.SimpleAttributeData;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

@RequiredArgsConstructor
public class ContentApiImpl implements ContentApi {
    private final DatamodelApi datamodelApi;
    private final ContentStore contentStore;

    @Override
    public Optional<Content> find(@NonNull Application application, @NonNull EntityName entityName,
            @NonNull EntityId id, @NonNull AttributeName attributeName) throws EntityNotFoundException {
        var contentAttribute = application.getRequiredEntityByName(entityName)
                .getAttributeByName(attributeName)
                .filter(ContentAttribute.class::isInstance)
                .map(ContentAttribute.class::cast)
                .orElseThrow(); // TODO: throw a properly typed exception when the wrong attribute name is given

        return datamodelApi.findById(application, application.getRequiredEntityByName(entityName), id)
                .flatMap(entity -> entity.getAttributeByName(attributeName))
                .map(CompositeAttributeData.class::cast)
                .map(attributeData -> {
                    return new AttributeDataContent(contentAttribute, attributeData);
                })
                .filter(content -> content.getContentId().isPresent())
                .map(Content.class::cast);
    }

    @Override
    public void update(@NonNull Application application, @NonNull EntityName entityName, @NonNull EntityId id,
            @NonNull AttributeName attributeName, @NonNull DataEntry.FileDataEntry file)
            throws InvalidPropertyDataException {

        datamodelApi.updatePartial(application, entityName, id, MapRequestInputData.fromMap(Map.of(
                attributeName.getValue(), file
        )));

    }

    @Override
    public void delete(@NonNull Application application, @NonNull EntityName entityName, @NonNull EntityId id,
            @NonNull AttributeName attributeName) throws InvalidPropertyDataException {
        datamodelApi.updatePartial(application, entityName, id, MapRequestInputData.fromMap(Map.of(
                attributeName.getValue(), NullDataEntry.INSTANCE
        )));
    }

    @RequiredArgsConstructor
    private class AttributeDataContent implements Content {
        @NonNull
        private final ContentAttribute contentAttribute;
        @NonNull
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
    }
}
