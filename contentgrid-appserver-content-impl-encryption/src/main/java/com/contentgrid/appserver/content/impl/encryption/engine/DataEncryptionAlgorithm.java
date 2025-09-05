package com.contentgrid.appserver.content.impl.encryption.engine;

import lombok.Value;

@Value(staticConstructor = "of")
public class DataEncryptionAlgorithm {
    String value;
}
