package com.contentgrid.appserver.content.impl.utils.testing;

import com.contentgrid.appserver.content.api.ContentStore;

class MockContentStoreTest extends AbstractContentStoreBehaviorTest {

    @Override
    protected ContentStore getContentStore() {
        return new MockContentStore();
    }
}