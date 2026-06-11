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

    private static final Path PATH = Path.of("db", "migration");

    private final BlueprintArtifact blueprintArtifact;

    @Override
    @SneakyThrows
    public LoadableResource getResource(String name) {
        return blueprintArtifact.load(PATH.resolve(name))
                .map(ArtifactEntryLoadableResource::new)
                .orElse(null);
    }

    @Override
    @SneakyThrows
    public Collection<LoadableResource> getResources(String prefix, String[] suffixes) {
        return blueprintArtifact.loadAll(PATH).stream()
                .map(ArtifactEntryLoadableResource::new)
                .filter(resource -> resource.getFilename().startsWith(prefix))
                .filter(resource -> Stream.of(suffixes)
                        .anyMatch(suffix -> resource.getFilename().endsWith(suffix)))
                .map(LoadableResource.class::cast)
                .toList();
    }

    @RequiredArgsConstructor
    private static class ArtifactEntryLoadableResource extends LoadableResource {

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
            // Relative path should be relative to migrations root directory, not relative to blueprint artifact root,
            // so drop "db/migration/" from relative path
            var relativePathToBlueprintArtifactRoot = Path.of(item.getItemReference().getPath());
            return PATH.relativize(relativePathToBlueprintArtifactRoot).toString();
        }
    }
}
