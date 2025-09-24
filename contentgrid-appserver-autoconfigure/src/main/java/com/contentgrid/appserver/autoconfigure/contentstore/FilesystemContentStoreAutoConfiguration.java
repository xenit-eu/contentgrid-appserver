package com.contentgrid.appserver.autoconfigure.contentstore;

import com.contentgrid.appserver.contentstore.api.ContentStore;
import com.contentgrid.appserver.contentstore.impl.fs.FilesystemContentStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(FilesystemContentStore.class)
public class FilesystemContentStoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(value = "contentgrid.appserver.content-store.type", havingValue = "ephemeral", matchIfMissing = true)
    public ContentStore ephemeralContentStore() throws IOException {
        var permissions = EnumSet.of(
                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE
        );
        return new FilesystemContentStore(Files.createTempDirectory("contentgrid", PosixFilePermissions.asFileAttribute(permissions)));
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(value = "contentgrid.appserver.content-store.type", havingValue = "fs")
    public ContentStore filesystemContentStore(@Value("${contentgrid.appserver.content.fs.path}") Path path) {
        if (Files.isDirectory(path)) {
            return new FilesystemContentStore(path);
        } else {
            throw new IllegalStateException("directory %s does not exist".formatted(path));
        }
    }
}
