package com.contentgrid.appserver.autoconfigure.flyway;

import com.contentgrid.appserver.infrastructure.api.Artifact;
import com.contentgrid.appserver.infrastructure.api.ArtifactEntry;
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
public class ArtifactFlywayResourceProvider implements ResourceProvider {

    private final Artifact artifact;

    @Override
    @SneakyThrows
    public LoadableResource getResource(String name) {
        return artifact.load(Path.of(name))
                .map(ArtifactEntryLoadableResource::new)
                .orElse(null);
    }

    @Override
    @SneakyThrows
    public Collection<LoadableResource> getResources(String prefix, String[] suffixes) {
        return artifact.loadAll(Path.of(".")).stream()
                .map(ArtifactEntryLoadableResource::new)
                .filter(resource -> resource.getFilename().startsWith(prefix))
                .filter(resource -> Stream.of(suffixes)
                        .anyMatch(suffix -> resource.getFilename().endsWith(suffix)))
                .map(LoadableResource.class::cast)
                .toList();
    }

    @RequiredArgsConstructor
    public static class ArtifactEntryLoadableResource extends LoadableResource {

        private final ArtifactEntry entry;

        @Override
        @SneakyThrows
        public Reader read() {
            return new InputStreamReader(entry.getInputStream());
        }

        @Override
        public String getAbsolutePath() {
            return entry.getEntryReference().toString();
        }

        @Override
        public String getAbsolutePathOnDisk() {
            return entry.getEntryReference().toString();
        }

        @Override
        public String getFilename() {
            var entryPath = entry.getEntryReference().getPath();
            return entryPath.substring(entryPath.lastIndexOf("/") + 1);
        }

        @Override
        public String getRelativePath() {
            return entry.getEntryReference().getPath();
        }
    }
}
