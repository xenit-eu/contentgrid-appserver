package com.contentgrid.appserver.autoconfigure.archive;

import java.nio.file.Path;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.ProtocolResolver;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

class ArchiveProtocolResolver implements ProtocolResolver {

    static final String ARCHIVE_PREFIX = "archive:";

    private final Path extractedArchiveDir;

    ArchiveProtocolResolver(Path extractedArchiveDir) {
        this.extractedArchiveDir = extractedArchiveDir;
    }

    @Override
    public Resource resolve(String location, ResourceLoader resourceLoader) {
        if (!location.startsWith(ARCHIVE_PREFIX)) {
            return null;
        }
        String relativePath = location.substring(ARCHIVE_PREFIX.length());
        return new FileSystemResource(extractedArchiveDir.resolve(relativePath).normalize());
    }
}
