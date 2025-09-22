package com.contentgrid.appserver.autoconfigure.contentstore;

import com.contentgrid.appserver.contentstore.api.ContentStore;
import com.contentgrid.appserver.contentstore.impl.fs.FilesystemContentStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(FilesystemContentStore.class)
@ConditionalOnProperty(value = "contentgrid.appserver.content.type", havingValue = "fs", matchIfMissing = true)
public class FileSystemContentStoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ContentStore filesystemContentStore() throws IOException {
        var permissions = EnumSet.of(
                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE
        );
        return new FilesystemContentStore(Files.createTempDirectory("contentgrid", PosixFilePermissions.asFileAttribute(permissions)));
    }
}
