package com.contentgrid.appserver.content.impl.encryption.keys;

import lombok.NonNull;
import lombok.Value;

@Value(staticConstructor = "of")
public class WrappingKeyId {
    @NonNull
    String value;

    public static WrappingKeyId unwrapped() {
        return WrappingKeyId.of("");
    }
}
