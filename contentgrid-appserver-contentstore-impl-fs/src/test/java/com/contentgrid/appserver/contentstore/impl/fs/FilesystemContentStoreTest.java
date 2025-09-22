package com.contentgrid.appserver.contentstore.impl.fs;

import com.contentgrid.appserver.contentstore.api.ContentStore;
import com.contentgrid.appserver.contentstore.impl.utils.testing.AbstractContentStoreBehaviorTest;
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