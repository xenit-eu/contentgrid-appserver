package com.contentgrid.appserver.content.impl.utils.testing;

import com.contentgrid.appserver.content.api.ContentReader;
import com.contentgrid.appserver.content.api.ContentReference;
import com.contentgrid.appserver.content.api.ContentStore;
import com.contentgrid.appserver.content.api.ContentWriter;
import com.contentgrid.appserver.content.api.UnreadableContentException;
import com.contentgrid.appserver.content.api.UnwritableContentException;
import com.contentgrid.appserver.content.api.range.ResolvedContentRange;
import com.contentgrid.appserver.content.impl.fs.FilesystemContentStore;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import lombok.SneakyThrows;

public class MockContentStore implements ContentStore, AutoCloseable {
    private final Path tempDir;
    private final ContentStore backingStorage;

    @SneakyThrows
    public MockContentStore() {
        tempDir = Files.createTempDirectory("cg-mock-store-");
        Runtime.getRuntime().addShutdownHook(new Thread(this::close, "MockContentStore cleanup for '%s'".formatted(tempDir)));
        backingStorage = new FilesystemContentStore(tempDir);
    }

    @Override
    public ContentReader getReader(ContentReference contentReference, ResolvedContentRange contentRange)
            throws UnreadableContentException {
        return backingStorage.getReader(contentReference, contentRange);
    }

    @Override
    public ContentWriter createNewWriter() throws UnwritableContentException {
        return backingStorage.createNewWriter();
    }

    @Override
    public void remove(ContentReference contentReference) throws UnwritableContentException {
        backingStorage.remove(contentReference);
    }

    @Override
    @SneakyThrows(IOException.class)
    public void close() {
        if(!Files.exists(tempDir)) {
            return;
        }
        Files.walkFileTree(tempDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
