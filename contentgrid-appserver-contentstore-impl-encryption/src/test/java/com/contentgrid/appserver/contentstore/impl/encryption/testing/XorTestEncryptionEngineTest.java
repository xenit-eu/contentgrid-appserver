package com.contentgrid.appserver.contentstore.impl.encryption.testing;

import com.contentgrid.appserver.contentstore.impl.encryption.engine.ContentEncryptionEngine;

class XorTestEncryptionEngineTest extends AbstractEncryptionEngineTest{

    @Override
    protected ContentEncryptionEngine getContentEncryptionEngine() {
        return new XorTestEncryptionEngine();
    }
}