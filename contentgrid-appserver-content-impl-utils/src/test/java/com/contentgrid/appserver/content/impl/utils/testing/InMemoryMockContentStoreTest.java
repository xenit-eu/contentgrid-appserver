package com.contentgrid.appserver.content.impl.utils.testing;

import com.contentgrid.appserver.content.api.ContentStore;

class InMemoryMockContentStoreTest extends AbstractContentStoreBehaviorTest {

    @Override
    protected ContentStore getContentStore() {
        return new InMemoryMockContentStore();
    }
}