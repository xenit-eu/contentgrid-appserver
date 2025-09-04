package com.contentgrid.appserver.content.impl.encryption.testing;

import com.contentgrid.appserver.content.impl.encryption.engine.ContentEncryptionEngine;

class XorTestEncryptionEngineTest extends AbstractEncryptionEngineTest{

    @Override
    protected ContentEncryptionEngine getContentEncryptionEngine() {
        return new XorTestEncryptionEngine();
    }
}