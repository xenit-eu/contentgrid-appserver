package com.contentgrid.appserver.infrastructure.impl.fs.classpath;

import com.contentgrid.appserver.infrastructure.api.BlueprintArtifactItem;
import com.contentgrid.appserver.infrastructure.api.BlueprintArtifactItemReference;
import com.contentgrid.appserver.infrastructure.api.BlueprintArtifactItemUnreadableException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ClassPathBlueprintArtifactItem implements BlueprintArtifactItem {

    @Getter
    @NonNull
    private final BlueprintArtifactItemReference itemReference;
    @NonNull
    private final URL resource;

    @Override
    public InputStream getInputStream() throws BlueprintArtifactItemUnreadableException {
        try {
            return resource.openStream();
        } catch (IOException e) {
            throw new BlueprintArtifactItemUnreadableException(itemReference, e);
        }
    }
}
