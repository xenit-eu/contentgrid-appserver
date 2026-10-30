package com.contentgrid.appserver.query.engine.api.data;

import com.contentgrid.appserver.application.model.values.AttributeName;
import java.util.function.Supplier;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class SimpleAttributeData<T> implements AttributeData {

    @Builder
    public SimpleAttributeData(AttributeName name, T value) {
        this(name, () -> value);
    }

    public static <T> SimpleAttributeData<T> ofLazy(@NonNull AttributeName attributeName, @NonNull Supplier<T> value) {
        return new SimpleAttributeData<>(attributeName, value);
    }

    @NonNull
    AttributeName name;

    @NonNull
    Supplier<T> value;

    public T getValue() {
        return value.get();
    }

}
