package com.contentgrid.appserver.infrastructure.impl.fs.classpath;

import com.contentgrid.appserver.infrastructure.api.ArtifactEntry;
import com.contentgrid.appserver.infrastructure.api.Artifact;
import com.contentgrid.appserver.infrastructure.api.ArtifactEntryNotFoundException;
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
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ClassPathArtifact implements Artifact {

    private final ClassLoader classLoader;
    private final Path directory;

    @Override
    public ArtifactReference getReference() {
        return ArtifactReference.of(ArtifactReference.Scheme.CLASSPATH, directory.toString());
    }

    @Override
    public ArtifactEntry load(Path path) throws ArtifactException {
        var ref = ArtifactEntryReference.of(getReference(), path.toString());
        var classpathPath = directory.resolve(path).normalize();
        var resourceName = classpathPath.toString().replace('\\', '/');
        if (classLoader.getResource(resourceName) == null) {
            throw new ArtifactEntryNotFoundException(ref);
        }
        return new ClassPathArtifactEntry(ref, classLoader, classpathPath);
    }

    @Override
    public List<ArtifactEntry> loadAll(Path path) throws ArtifactException {
        var ref = getReference();
        var targetPath = directory.resolve(path).normalize();
        var resourceName = targetPath.toString().replace('\\', '/');
        var result = new ArrayList<ArtifactEntry>();

        try {
            var urls = classLoader.getResources(resourceName);
            while (urls.hasMoreElements()) {
                var url = urls.nextElement();
                switch (url.getProtocol()) {
                    case "file" -> {
                        var fsArtifact = new FilesystemDirectoryArtifact(Path.of(url.toURI()));
                        for (var entry : fsArtifact.loadAll(Path.of(""))) {
                            var classpathPath = targetPath.resolve(entry.getEntryReference().getRelativePath());
                            result.add(new ClassPathArtifactEntry(
                                    ArtifactEntryReference.of(ref, directory.relativize(classpathPath).toString()),
                                    classLoader,
                                    classpathPath));
                        }
                    }
                    case "jar" -> {
                        var jarConn = (JarURLConnection) url.openConnection();
                        var zipArtifact = new ZipArtifact(Path.of(jarConn.getJarFileURL().toURI()));
                        for (var entry : zipArtifact.loadAll(targetPath)) {
                            var classpathPath = Path.of(entry.getEntryReference().getRelativePath());
                            result.add(new ClassPathArtifactEntry(
                                    ArtifactEntryReference.of(ref, directory.relativize(classpathPath).toString()),
                                    classLoader,
                                    classpathPath));
                        }
                    }
                }
            }
        } catch (ArtifactException | IOException | URISyntaxException e) {
            throw new ArtifactException(ref, e);
        }

        return result;
    }
}
