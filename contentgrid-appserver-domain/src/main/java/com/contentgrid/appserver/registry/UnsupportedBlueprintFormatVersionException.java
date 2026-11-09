package com.contentgrid.appserver.registry;

import java.util.Set;
import lombok.Getter;

@Getter
public class UnsupportedBlueprintFormatVersionException extends RuntimeException {

    public UnsupportedBlueprintFormatVersionException(int formatVersion, Set<Integer> supportedFormatVersions) {
        super(("Blueprint manifest has blueprint format version %d, but this appserver only supports blueprint "
                + "format versions [%s]. Upgrade the appserver to a version that supports blueprint format "
                + "version %d.")
                .formatted(
                        formatVersion,
                        String.join(", ", supportedFormatVersions.stream().sorted().map(String::valueOf).toList()),
                        formatVersion
                ));
    }
}
