package com.contentgrid.appserver.blueprintartifact.impl.fs;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifactReference;
import com.contentgrid.appserver.blueprintartifact.impl.fs.classpath.ClassPathBlueprintArtifact;
import com.contentgrid.appserver.blueprintartifact.impl.fs.directory.FilesystemDirectoryBlueprintArtifact;
import com.contentgrid.appserver.blueprintartifact.impl.fs.zip.ZipBlueprintArtifact;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FilesystemBlueprintArtifactReferenceResolverTest {

    private final FilesystemBlueprintArtifactReferenceResolver resolver = new FilesystemBlueprintArtifactReferenceResolver();

    @Test
    void resolve_fileReference_returnsFileSystemDirectoryBlueprintArtifact(@TempDir Path dir) {
        var ref = BlueprintArtifactReference.of(FilesystemDirectoryBlueprintArtifact.SCHEME + ":" + dir);
        assertThat(resolver.resolve(ref)).isInstanceOfSatisfying(FilesystemDirectoryBlueprintArtifact.class,
                blueprintArtifact -> assertThat(blueprintArtifact.getReference()).isEqualTo(ref));
    }

    @Test
    void resolve_zipReference_returnsZipBlueprintArtifact(@TempDir Path dir) throws IOException {
        var zip = Files.createFile(dir.resolve("blueprint-artifact.zip"));
        var ref = BlueprintArtifactReference.of(ZipBlueprintArtifact.SCHEME + ":" + zip);
        assertThat(resolver.resolve(ref)).isInstanceOfSatisfying(ZipBlueprintArtifact.class,
                blueprintArtifact -> assertThat(blueprintArtifact.getReference()).isEqualTo(ref));
    }

    @Test
    void resolve_classpathReference_returnsClassPathBlueprintArtifact() {
        var ref = BlueprintArtifactReference.of(ClassPathBlueprintArtifact.SCHEME + ":some/path");
        assertThat(resolver.resolve(ref)).isInstanceOfSatisfying(ClassPathBlueprintArtifact.class,
                blueprintArtifact -> assertThat(blueprintArtifact.getReference()).isEqualTo(ref));
    }

    @Test
    void resolve_unsupportedReference_returnsNull() {
        var ref = BlueprintArtifactReference.of("s3:bucket/key");
        assertThat(resolver.resolve(ref)).isNull();
    }
}
