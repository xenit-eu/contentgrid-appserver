package com.contentgrid.appserver.application.model.values;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PROTECTED, force = true)
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class PropertyName {
    @NonNull
    @Getter
    private final String value;

    @Override
    public String toString() {
        return getValue();
    }

    public static PropertyName of(String value) {
        return new PropertyName(value);
    }
}
