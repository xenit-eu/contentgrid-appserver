package com.contentgrid.appserver.infrastructure.impl.fs.classpath;

import com.contentgrid.appserver.infrastructure.api.ArtifactEntry;
import com.contentgrid.appserver.infrastructure.api.Artifact;
import com.contentgrid.appserver.infrastructure.api.ArtifactEntryReference;
import com.contentgrid.appserver.infrastructure.api.ArtifactException;
import com.contentgrid.appserver.infrastructure.api.ArtifactReference;
import com.contentgrid.appserver.infrastructure.impl.fs.directory.FilesystemDirectoryArtifact;
import com.contentgrid.appserver.infrastructure.impl.fs.zip.ZipArtifact;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ClassPathArtifact implements Artifact {

    public static final String SCHEME = "classpath";

    @NonNull
    private final ClassLoader classLoader;
    @NonNull
    private final Path directory;
    private final Map<Path, ZipArtifact> zipArtifactCache = new ConcurrentHashMap<>();

    @Override
    public ArtifactReference getReference() {
        return ArtifactReference.of(SCHEME + ":" + directory);
    }

    @Override
    public Optional<ArtifactEntry> load(Path path) {
        var ref = ArtifactEntryReference.of(getReference(), path.toString());
        var classpathPath = directory.resolve(path).normalize().toString();
        return Optional.ofNullable(classLoader.getResource(classpathPath))
                .map(resource -> new ClassPathArtifactEntry(ref, resource));
    }

    @Override
    public List<ArtifactEntry> loadAll(Path path) throws ArtifactException {
        var ref = getReference();
        var targetPath = directory.resolve(path).normalize();
        var resourceName = targetPath.toString();
        var result = new ArrayList<ArtifactEntry>();

        try {
            var urls = classLoader.getResources(resourceName);
            while (urls.hasMoreElements()) {
                var url = urls.nextElement();
                // Classpath resources can be on filesystem directly or inside jar files.
                // Delegate to FilesystemDirectoryArtifact when the protocol is 'file'
                // and to ZipArtifact when the protocol is 'jar'
                switch (url.getProtocol()) {
                    case "file" -> {
                        var fsArtifact = new FilesystemDirectoryArtifact(Path.of(url.toURI()));
                        for (var entry : fsArtifact.loadAll(Path.of(""))) {
                            var classpathPath = targetPath.resolve(entry.getEntryReference().getPath());
                            var entryRef = ArtifactEntryReference.of(ref, directory.relativize(classpathPath).toString());
                            result.add(entry.withEntryReference(entryRef));
                        }
                    }
                    case "jar" -> {
                        var jarConn = (JarURLConnection) url.openConnection();
                        var zipArtifact = zipArtifactCache.computeIfAbsent(
                                Path.of(jarConn.getJarFileURL().toURI()), ZipArtifact::new);
                        for (var entry : zipArtifact.loadAll(targetPath)) {
                            var classpathPath = Path.of(entry.getEntryReference().getPath());
                            var entryRef = ArtifactEntryReference.of(ref, directory.relativize(classpathPath).toString());
                            result.add(entry.withEntryReference(entryRef));
                        }
                    }
                    default -> throw new UnsupportedOperationException("Protocol %s not supported".formatted(url.getProtocol()));
                }
            }
        } catch (ArtifactException | IOException | URISyntaxException e) {
            throw new ArtifactException(ref, e);
        }

        return result;
    }
}
