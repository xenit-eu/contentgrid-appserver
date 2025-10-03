package com.contentgrid.appserver.autoconfigure.contentstore;

import com.contentgrid.appserver.contentstore.api.ContentStore;
import com.contentgrid.appserver.contentstore.impl.fs.FilesystemContentStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(FilesystemContentStore.class)
public class FilesystemContentStoreAutoConfiguration {

    private static final FileAttribute<Set<PosixFilePermission>> PERMISSIONS = PosixFilePermissions.asFileAttribute(
            EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE)
    );

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(value = "contentgrid.appserver.content-store.type", havingValue = "ephemeral", matchIfMissing = true)
    public ContentStore ephemeralContentStore() throws IOException {
        return new FilesystemContentStore(Files.createTempDirectory("contentgrid", PERMISSIONS));
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(value = "contentgrid.appserver.content-store.type", havingValue = "fs")
    public ContentStore filesystemContentStore(@Value("${contentgrid.appserver.content.fs.path:}") Path path) throws IOException {
        if (path == null || path.toString().isBlank()) {
            throw new IllegalArgumentException("Property 'contentgrid.appserver.content.fs.path' is required when 'contentgrid.appserver.content-store.type' is 'fs'");
        }
        return new FilesystemContentStore(Files.createDirectories(path, PERMISSIONS));
    }
}
