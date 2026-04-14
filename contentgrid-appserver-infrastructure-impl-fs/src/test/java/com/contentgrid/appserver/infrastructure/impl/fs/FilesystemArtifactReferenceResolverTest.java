package com.contentgrid.appserver.infrastructure.impl.fs;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.appserver.infrastructure.api.ArtifactReference;
import com.contentgrid.appserver.infrastructure.api.ArtifactReference.Scheme;
import com.contentgrid.appserver.infrastructure.impl.fs.classpath.ClassPathArtifact;
import com.contentgrid.appserver.infrastructure.impl.fs.directory.FilesystemDirectoryArtifact;
import com.contentgrid.appserver.infrastructure.impl.fs.zip.ZipArtifact;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FilesystemArtifactReferenceResolverTest {

    private final FilesystemArtifactReferenceResolver resolver = new FilesystemArtifactReferenceResolver();

    @Test
    void resolve_fileReference_returnsFileSystemDirectoryArtifact(@TempDir Path dir) {
        var ref = ArtifactReference.of(Scheme.FILE, dir.toString());
        assertThat(resolver.resolve(ref)).isInstanceOfSatisfying(FilesystemDirectoryArtifact.class,
                artifact -> assertThat(artifact.getReference()).isEqualTo(ref));
    }

    @Test
    void resolve_zipReference_returnsZipArtifact(@TempDir Path dir) throws IOException {
        var zip = Files.createFile(dir.resolve("artifact.zip"));
        var ref = ArtifactReference.of(Scheme.ZIP, zip.toString());
        assertThat(resolver.resolve(ref)).isInstanceOfSatisfying(ZipArtifact.class,
                artifact -> assertThat(artifact.getReference()).isEqualTo(ref));
    }

    @Test
    void resolve_classpathReference_returnsClassPathArtifact() {
        var ref = ArtifactReference.of(Scheme.CLASSPATH, "some/path");
        assertThat(resolver.resolve(ref)).isInstanceOfSatisfying(ClassPathArtifact.class,
                artifact -> assertThat(artifact.getReference()).isEqualTo(ref));
    }
}
