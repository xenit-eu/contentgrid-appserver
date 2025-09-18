package com.contentgrid.appserver.contentstore.impl.utils.testing;

import com.contentgrid.appserver.contentstore.api.ContentStore;

class MockContentStoreTest extends AbstractContentStoreBehaviorTest {

    @Override
    protected ContentStore getContentStore() {
        return new MockContentStore();
    }
}