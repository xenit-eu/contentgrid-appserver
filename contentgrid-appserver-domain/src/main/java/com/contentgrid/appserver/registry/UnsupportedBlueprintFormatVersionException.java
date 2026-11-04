package com.contentgrid.appserver.registry;

import lombok.Getter;

@Getter
public class UnsupportedBlueprintFormatVersionException extends RuntimeException {

    private final int formatVersion;
    private final int supportedFormatVersion;

    public UnsupportedBlueprintFormatVersionException(int formatVersion, int supportedFormatVersion) {
        super(("Blueprint manifest has blueprint format version %d, but this appserver only supports blueprint "
                + "format versions up to %d. Upgrade the appserver to a version that supports blueprint format "
                + "version %d.").formatted(formatVersion, supportedFormatVersion, formatVersion));
        this.formatVersion = formatVersion;
        this.supportedFormatVersion = supportedFormatVersion;
    }
}
