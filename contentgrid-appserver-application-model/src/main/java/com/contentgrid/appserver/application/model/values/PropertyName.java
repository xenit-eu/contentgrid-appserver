package com.contentgrid.appserver.application.model.values;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@EqualsAndHashCode
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
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
