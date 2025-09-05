package com.contentgrid.appserver.content.impl.encryption;

import com.contentgrid.appserver.content.impl.encryption.keys.WrappingKeyId;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Getter;

@Getter
public class KeyUnwrappingFailedException extends Exception {
    private final WrappingKeyId wrappingKeyId;

    KeyUnwrappingFailedException(WrappingKeyId wrappingKeyId, Throwable cause) {
        super(cause);
        this.wrappingKeyId = wrappingKeyId;
    }

    @Override
    public String getMessage() {
        return "Failed to unwrap key '%s'".formatted(wrappingKeyId);
    }

    private Stream<WrappingKeyId> failedWrappingKeyIds() {
        return Stream.concat(
                        Stream.of(this.getWrappingKeyId()),
                        Arrays.stream(getSuppressed())
                                .filter(KeyUnwrappingFailedException.class::isInstance)
                                .flatMap(ex -> ((KeyUnwrappingFailedException)ex).failedWrappingKeyIds())
                );
    }

    Set<WrappingKeyId> getAllFailedWrappingKeyIds() {
        return failedWrappingKeyIds().collect(Collectors.toUnmodifiableSet());
    }

}
