package com.contentgrid.appserver.infrastructure.impl.fs.classpath;

import com.contentgrid.appserver.infrastructure.api.BlueprintArtifactItem;
import com.contentgrid.appserver.infrastructure.api.BlueprintArtifact;
import com.contentgrid.appserver.infrastructure.api.BlueprintArtifactItemReference;
import com.contentgrid.appserver.infrastructure.api.BlueprintArtifactException;
import com.contentgrid.appserver.infrastructure.api.BlueprintArtifactReference;
import com.contentgrid.appserver.infrastructure.impl.fs.directory.FilesystemDirectoryBlueprintArtifact;
import com.contentgrid.appserver.infrastructure.impl.fs.zip.ZipBlueprintArtifact;
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
public class ClassPathBlueprintArtifact implements BlueprintArtifact {

    public static final String SCHEME = "classpath";

    @NonNull
    private final ClassLoader classLoader;
    @NonNull
    private final Path directory;
    private final Map<Path, ZipBlueprintArtifact> zipBlueprintArtifactCache = new ConcurrentHashMap<>();

    @Override
    public BlueprintArtifactReference getReference() {
        return BlueprintArtifactReference.of(SCHEME + ":" + directory);
    }

    @Override
    public Optional<BlueprintArtifactItem> load(Path path) {
        var ref = BlueprintArtifactItemReference.of(getReference(), path.toString());
        var classpathPath = directory.resolve(path).normalize().toString();
        return Optional.ofNullable(classLoader.getResource(classpathPath))
                .map(resource -> new ClassPathBlueprintArtifactItem(ref, resource));
    }

    @Override
    public List<BlueprintArtifactItem> loadAll(Path path) throws BlueprintArtifactException {
        var ref = getReference();
        var targetPath = directory.resolve(path).normalize();
        var resourceName = targetPath.toString();
        var result = new ArrayList<BlueprintArtifactItem>();

        try {
            var urls = classLoader.getResources(resourceName);
            while (urls.hasMoreElements()) {
                var url = urls.nextElement();
                // Classpath resources can be on filesystem directly or inside jar files.
                // Delegate to FilesystemDirectoryBlueprintArtifact when the protocol is 'file'
                // and to ZipBlueprintArtifact when the protocol is 'jar'
                switch (url.getProtocol()) {
                    case "file" -> {
                        var fsBlueprintArtifact = new FilesystemDirectoryBlueprintArtifact(Path.of(url.toURI()));
                        for (var item : fsBlueprintArtifact.loadAll(Path.of(""))) {
                            var classpathPath = targetPath.resolve(item.getItemReference().getPath());
                            var itemRef = BlueprintArtifactItemReference.of(ref, directory.relativize(classpathPath).toString());
                            result.add(item.withItemReference(itemRef));
                        }
                    }
                    case "jar" -> {
                        var jarConn = (JarURLConnection) url.openConnection();
                        var zipBlueprintArtifact = zipBlueprintArtifactCache.computeIfAbsent(
                                Path.of(jarConn.getJarFileURL().toURI()), ZipBlueprintArtifact::new);
                        for (var item : zipBlueprintArtifact.loadAll(targetPath)) {
                            var classpathPath = Path.of(item.getItemReference().getPath());
                            var itemRef = BlueprintArtifactItemReference.of(ref, directory.relativize(classpathPath).toString());
                            result.add(item.withItemReference(itemRef));
                        }
                    }
                    default -> throw new UnsupportedOperationException("Protocol %s not supported".formatted(url.getProtocol()));
                }
            }
        } catch (BlueprintArtifactException | IOException | URISyntaxException e) {
            throw new BlueprintArtifactException(ref, e);
        }

        return result;
    }
}
