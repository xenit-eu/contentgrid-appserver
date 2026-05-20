package com.contentgrid.appserver.infrastructure.impl.fs;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.appserver.infrastructure.api.ArtifactReference;
import com.contentgrid.appserver.infrastructure.impl.fs.classpath.ClassPathArtifact;
import com.contentgrid.appserver.infrastructure.impl.fs.directory.FilesystemDirectoryArtifact;
import com.contentgrid.appserver.infrastructure.impl.fs.zip.ZipArtifact;
import com.contentgrid.appserver.infrastructure.impl.fs.zip.ZipUtils;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FilesystemArtifactReferenceResolverTest {

    private final FilesystemArtifactReferenceResolver resolver = new FilesystemArtifactReferenceResolver();

    @Test
    void resolve_fileReference_returnsFileSystemDirectoryArtifact(@TempDir Path dir) throws IOException {
        var ref = ArtifactReference.of(FilesystemDirectoryArtifact.SCHEME, dir.toString());
        try (var artifact = resolver.resolve(ref)) {
            assertThat(artifact).isInstanceOf(FilesystemDirectoryArtifact.class);
            assertThat(artifact.getReference()).isEqualTo(ref);
        }
    }

    @Test
    void resolve_zipReference_returnsZipArtifact(@TempDir Path dir) throws IOException {
        var zip = dir.resolve("artifact.zip");
        ZipUtils.createZip(zip);
        var ref = ArtifactReference.of(ZipArtifact.SCHEME, zip.toString());
        try (var artifact = resolver.resolve(ref)) {
            assertThat(artifact).isInstanceOf(ZipArtifact.class);
            assertThat(artifact.getReference()).isEqualTo(ref);
        }
    }

    @Test
    void resolve_classpathReference_returnsClassPathArtifact() throws IOException {
        var ref = ArtifactReference.of(ClassPathArtifact.SCHEME, "some/path");
        try (var artifact = resolver.resolve(ref)) {
            assertThat(resolver.resolve(ref)).isInstanceOf(ClassPathArtifact.class);
            assertThat(artifact.getReference()).isEqualTo(ref);
        }
    }

    @Test
    void resolve_unsupportedReference_returnsNull() throws IOException {
        var ref = ArtifactReference.of("s3", "bucket/key");
        try (var artifact = resolver.resolve(ref)) {
            assertThat(artifact).isNull();
        }
    }
}
