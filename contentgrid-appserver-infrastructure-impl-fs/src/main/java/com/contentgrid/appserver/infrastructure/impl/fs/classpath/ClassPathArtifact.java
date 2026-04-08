package com.contentgrid.appserver.infrastructure.impl.fs.classpath;

import com.contentgrid.appserver.infrastructure.api.ArtifactEntry;
import com.contentgrid.appserver.infrastructure.api.Artifact;
import com.contentgrid.appserver.infrastructure.api.ArtifactEntryNotFoundException;
import com.contentgrid.appserver.infrastructure.api.ArtifactException;
import com.contentgrid.appserver.infrastructure.api.ArtifactReference;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.nio.file.Files;
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
        var ref = getReference();
        var targetPath = directory.resolve(path).normalize();
        var resourceName = targetPath.toString().replace('\\', '/');
        if (classLoader.getResource(resourceName) == null) {
            throw new ArtifactEntryNotFoundException(ref, path);
        }
        return new ClassPathArtifactEntry(ref, classLoader, targetPath);
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
                        var fsPath = Path.of(url.toURI());
                        if (Files.isDirectory(fsPath)) {
                            try (var stream = Files.walk(fsPath)) {
                                stream.filter(Files::isRegularFile)
                                        .map(file -> new ClassPathArtifactEntry(ref, classLoader,
                                                targetPath.resolve(fsPath.relativize(file))))
                                        .forEach(result::add);
                            }
                        } else if (Files.isRegularFile(fsPath)) {
                            result.add(new ClassPathArtifactEntry(ref, classLoader, targetPath));
                        }
                    }
                    case "jar" -> {
                        var jarConn = (JarURLConnection) url.openConnection();
                        var jarPrefix = resourceName + "/";
                        jarConn.getJarFile().entries().asIterator().forEachRemaining(entry -> {
                            if (entry.getName().startsWith(jarPrefix) && !entry.isDirectory()) {
                                result.add(new ClassPathArtifactEntry(ref, classLoader,
                                        Path.of(entry.getName())));
                            }
                        });
                    }
                }
            }
        } catch (IOException | URISyntaxException e) {
            throw new ArtifactException(ref, e);
        }

        return result;
    }
}
