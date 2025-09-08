package com.contentgrid.appserver.content.impl.fs;

import com.contentgrid.appserver.content.api.ContentAccessor;
import com.contentgrid.appserver.content.api.ContentReader;
import com.contentgrid.appserver.content.api.ContentReference;
import com.contentgrid.appserver.content.api.ContentStore;
import com.contentgrid.appserver.content.api.UnreadableContentException;
import com.contentgrid.appserver.content.api.UnwritableContentException;
import com.contentgrid.appserver.content.api.range.ResolvedContentRange;
import com.contentgrid.appserver.content.impl.utils.CountingInputStream;
import com.contentgrid.appserver.content.impl.utils.EmulatedRangedContentReader;
import com.contentgrid.appserver.content.impl.utils.GuardedContentReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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
                                path,
                                contentReference,
                                contentRange.getContentSize()
                        ),
                        contentRange
                )
        );
    }

    @Override
    public ContentAccessor writeContent(@NonNull InputStream inputStream) throws UnwritableContentException {
        var contentReference = ContentReference.of(UUID.randomUUID().toString());
        var path = resolvePath(contentReference);
        var countInputStream = new CountingInputStream(inputStream);
        try (var outputStream = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW)) {
            countInputStream.transferTo(outputStream);
        } catch (IOException e) {
            throw new UnwritableContentException(contentReference, e);
        }

        return new FileContentAccessor(contentReference, countInputStream.getSize());
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
