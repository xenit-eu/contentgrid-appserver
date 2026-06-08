package com.contentgrid.appserver.autoconfigure.flyway;

import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifact;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifactItem;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Path;
import java.util.Collection;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.flywaydb.core.api.ResourceProvider;
import org.flywaydb.core.api.resource.LoadableResource;

@RequiredArgsConstructor
public class BlueprintArtifactFlywayResourceProvider implements ResourceProvider {

    private final BlueprintArtifact blueprintArtifact;

    @Override
    @SneakyThrows
    public LoadableResource getResource(String name) {
        return blueprintArtifact.load(Path.of(name))
                .map(ArtifactEntryLoadableResource::new)
                .orElse(null);
    }

    @Override
    @SneakyThrows
    public Collection<LoadableResource> getResources(String prefix, String[] suffixes) {
        return blueprintArtifact.loadAll(Path.of(".")).stream()
                .map(ArtifactEntryLoadableResource::new)
                .filter(resource -> resource.getFilename().startsWith(prefix))
                .filter(resource -> Stream.of(suffixes)
                        .anyMatch(suffix -> resource.getFilename().endsWith(suffix)))
                .map(LoadableResource.class::cast)
                .toList();
    }

    @RequiredArgsConstructor
    public static class ArtifactEntryLoadableResource extends LoadableResource {

        private final BlueprintArtifactItem item;

        @Override
        @SneakyThrows
        public Reader read() {
            return new InputStreamReader(item.getInputStream());
        }

        @Override
        public String getAbsolutePath() {
            return item.getItemReference().toString();
        }

        @Override
        public String getAbsolutePathOnDisk() {
            return item.getItemReference().toString();
        }

        @Override
        public String getFilename() {
            var itemPath = item.getItemReference().getPath();
            return itemPath.substring(itemPath.lastIndexOf("/") + 1);
        }

        @Override
        public String getRelativePath() {
            return item.getItemReference().getPath();
        }
    }
}
