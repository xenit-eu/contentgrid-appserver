package com.contentgrid.appserver.contentstore.impl.encryption.engine;

import lombok.Value;

@Value(staticConstructor = "of")
public class DataEncryptionAlgorithm {
    String value;
}
