package com.contentgrid.appserver.content.impl.fs;

import com.contentgrid.appserver.content.api.ContentReader;
import com.contentgrid.appserver.content.api.ContentReference;
import com.contentgrid.appserver.content.api.ContentStore;
import com.contentgrid.appserver.content.api.ContentWriter;
import com.contentgrid.appserver.content.api.UnreadableContentException;
import com.contentgrid.appserver.content.api.UnwritableContentException;
import com.contentgrid.appserver.content.api.range.ResolvedContentRange;
import com.contentgrid.appserver.content.impl.utils.EmulatedRangedContentReader;
import com.contentgrid.appserver.content.impl.utils.GuardedContentReader;
import com.contentgrid.appserver.content.impl.utils.GuardedContentWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class FilesystemContentStore implements ContentStore {
    @NonNull
    private final Path basePath;

    private Path resolvePath(@NonNull ContentReference contentReference) {
        return basePath.resolve(contentReference.getValue() + ".bin");
    }

    @Override
    public ContentReader getReader(
            @NonNull ContentReference contentReference,
            @NonNull ResolvedContentRange contentRange
    ) throws UnreadableContentException {
        var path = resolvePath(contentReference);

        try {
            if (Files.size(path) != contentRange.getContentSize()) {
                throw new UnreadableContentException(contentReference, "range size does not match actual size");
            }
        } catch (IOException e) {
            throw new UnreadableContentException(contentReference, e);
        }

        return new GuardedContentReader(
                new EmulatedRangedContentReader(
                        new FileContentReader(
                                contentReference,
                                path,
                                contentRange.getContentSize()
                        ),
                        contentRange
                )
        );
    }

    @Override
    public ContentWriter createNewWriter() {
        var contentReference = ContentReference.of(UUID.randomUUID().toString());
        var path = resolvePath(contentReference);
        return new GuardedContentWriter(
                new FileContentWriter(
                        contentReference,
                        path
                )
        );
    }

    @Override
    public void remove(@NonNull ContentReference contentReference) throws UnwritableContentException {
        try {
            Files.deleteIfExists(resolvePath(contentReference));
        } catch (IOException e) {
            throw new UnwritableContentException(contentReference, e);
        }
    }
}
