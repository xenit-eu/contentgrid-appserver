package com.contentgrid.appserver.content.impl.utils.testing;

import com.contentgrid.appserver.content.api.ContentReader;
import com.contentgrid.appserver.content.api.ContentReference;
import com.contentgrid.appserver.content.api.ContentStore;
import com.contentgrid.appserver.content.api.ContentWriter;
import com.contentgrid.appserver.content.api.UnreadableContentException;
import com.contentgrid.appserver.content.api.UnwritableContentException;
import com.contentgrid.appserver.content.api.range.ResolvedContentRange;
import com.contentgrid.appserver.content.impl.utils.CloseCallbackOutputStream;
import com.contentgrid.appserver.content.impl.utils.EmulatedRangedContentReader;
import com.contentgrid.appserver.content.impl.utils.GuardedContentReader;
import com.contentgrid.appserver.content.impl.utils.GuardedContentWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;

public class InMemoryMockContentStore implements ContentStore {
    private final Map<ContentReference, byte[]> storage = new HashMap<>();

    @Override
    public ContentReader getReader(ContentReference contentReference, ResolvedContentRange contentRange)
            throws UnreadableContentException {
        var stored = storage.get(contentReference);
        if(stored == null) {
            throw new UnreadableContentException(contentReference);
        }
        if(stored.length != contentRange.getContentSize()) {
            throw new UnreadableContentException(contentReference, "range size does not match stored size");
        }
        return new GuardedContentReader(
                new EmulatedRangedContentReader(
                        new MockContentReader(stored, contentReference),
                        contentRange
                )
        );
    }

    @Override
    public ContentWriter createNewWriter() throws UnwritableContentException {
        var reference = ContentReference.of(UUID.randomUUID().toString());
        return new GuardedContentWriter(
                new ContentWriter() {
                    @Getter
                    long contentSize = 0;

                    @Override
                    public OutputStream getContentOutputStream() {
                        var outputStream = new ByteArrayOutputStream();
                        return new CloseCallbackOutputStream(outputStream, () -> {
                            storage.put(reference, outputStream.toByteArray());
                            contentSize = outputStream.size();
                        });
                    }

                    @Override
                    public ContentReference getReference() {
                        return reference;
                    }

                    @Override
                    public String getDescription() {
                        return "In-Memory '%s'".formatted(reference);
                    }
                }
        );
    }

    @Override
    public void remove(ContentReference contentReference) {
        storage.remove(contentReference);
    }

}
