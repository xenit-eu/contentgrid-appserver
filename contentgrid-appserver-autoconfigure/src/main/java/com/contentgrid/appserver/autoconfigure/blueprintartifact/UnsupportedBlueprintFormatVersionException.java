package com.contentgrid.appserver.autoconfigure.blueprintartifact;

import java.util.Set;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class UnsupportedBlueprintFormatVersionException extends RuntimeException {

    private final int formatVersion;

    @NonNull
    private final Set<Integer> supportedFormatVersions;

    @Override
    public String getMessage() {
        return ("Blueprint manifest has blueprint format version %d, but this appserver only supports blueprint "
                + "format versions [%s]. Upgrade the appserver to a version that supports blueprint format "
                + "version %d.")
                .formatted(
                        formatVersion,
                        String.join(", ", supportedFormatVersions.stream().sorted().map(String::valueOf).toList()),
                        formatVersion
                );
    }
}
