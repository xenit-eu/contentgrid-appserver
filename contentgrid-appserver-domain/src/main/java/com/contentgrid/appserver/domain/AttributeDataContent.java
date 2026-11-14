package com.contentgrid.appserver.domain;

import com.contentgrid.appserver.application.model.attributes.ContentAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.contentstore.api.ContentReference;
import com.contentgrid.appserver.contentstore.api.ContentStore;
import com.contentgrid.appserver.contentstore.api.UnreadableContentException;
import com.contentgrid.appserver.contentstore.api.range.ContentRangeRequest;
import com.contentgrid.appserver.contentstore.api.range.UnsatisfiableContentRangeException;
import com.contentgrid.appserver.domain.ContentApi.Content;
import com.contentgrid.appserver.domain.values.version.Version;
import com.contentgrid.appserver.query.engine.api.data.CompositeAttributeData;
import com.contentgrid.appserver.query.engine.api.data.SimpleAttributeData;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

@RequiredArgsConstructor
class AttributeDataContent implements Content {

    private final ContentStore contentStore;
    @NonNull
    private final ContentAttribute contentAttribute;
    private final CompositeAttributeData attributeData;

    private final ContentRangeRequest contentRange;

    AttributeDataContent(
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
        return new AttributeDataContent(contentStore, contentAttribute, attributeData,
                ContentRangeRequest.createRange(start, endInclusive));
    }

    private <T> T getAttribute(SimpleAttribute attribute, Class<T> type) {
        if (attributeData == null) {
            return null;
        }
        return (T) attributeData.getAttributeByName(attribute.getName())
                .map(SimpleAttributeData.class::cast)
                .orElseThrow()
                .getValue();
    }

    @Override
    public String getDescription() {
        return "ContentAttribute %s: '%s'%s".formatted(
                contentAttribute.getName(),
                getContentId().orElseThrow(),
                contentRange == null ? "" : " [range: %s]".formatted(contentRange)
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
