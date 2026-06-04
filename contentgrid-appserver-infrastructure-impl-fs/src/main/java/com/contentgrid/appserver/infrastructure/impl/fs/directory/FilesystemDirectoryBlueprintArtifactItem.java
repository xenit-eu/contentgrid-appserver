package com.contentgrid.appserver.infrastructure.impl.fs.directory;

import com.contentgrid.appserver.infrastructure.api.BlueprintArtifactItem;
import com.contentgrid.appserver.infrastructure.api.BlueprintArtifactItemReference;
import com.contentgrid.appserver.infrastructure.api.BlueprintArtifactItemUnreadableException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class FilesystemDirectoryBlueprintArtifactItem implements BlueprintArtifactItem {

    @Getter
    @NonNull
    private final BlueprintArtifactItemReference itemReference;
    @NonNull
    private final Path absolutePath;

    @Override
    public InputStream getInputStream() throws BlueprintArtifactItemUnreadableException {
        try {
            return Files.newInputStream(absolutePath);
        } catch (IOException e) {
            throw new BlueprintArtifactItemUnreadableException(itemReference, e);
        }
    }
}
