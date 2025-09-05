package com.contentgrid.appserver.content.impl.fs;

import com.contentgrid.appserver.content.api.ContentStore;
import com.contentgrid.appserver.content.impl.utils.testing.AbstractContentStoreBehaviorTest;
import java.nio.file.Path;
import lombok.Getter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

class FilesystemContentStoreTest extends AbstractContentStoreBehaviorTest  {

    @Getter
    ContentStore contentStore;

    @BeforeEach
    void setupStore(@TempDir Path storeDirectory) {
        contentStore = new FilesystemContentStore(storeDirectory);
    }

}