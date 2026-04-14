package com.contentgrid.appserver.infrastructure.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ArtifactReferenceTest {

    @Test
    void parse_fileScheme() {
        var ref = ArtifactReference.parse("file:/some/path");
        assertThat(ref.getScheme()).isEqualTo("file");
        assertThat(ref.getPath()).isEqualTo("/some/path");
        assertThat(ref).hasToString("file:/some/path");
    }

    @Test
    void parse_zipScheme() {
        var ref = ArtifactReference.parse("zip:/some/path/artifact.zip");
        assertThat(ref.getScheme()).isEqualTo("zip");
        assertThat(ref.getPath()).isEqualTo("/some/path/artifact.zip");
        assertThat(ref).hasToString("zip:/some/path/artifact.zip");
    }

    @Test
    void parse_classpathScheme() {
        var ref = ArtifactReference.parse("classpath:some/relative/path");
        assertThat(ref.getScheme()).isEqualTo("classpath");
        assertThat(ref.getPath()).isEqualTo("some/relative/path");
        assertThat(ref).hasToString("classpath:some/relative/path");
    }

    @Test
    void parse_unknownScheme_throws() {
        var ref = ArtifactReference.parse("s3:bucket/key");
        assertThat(ref.getScheme()).isEqualTo("s3");
        assertThat(ref.getPath()).isEqualTo("bucket/key");
        assertThat(ref).hasToString("s3:bucket/key");
    }
}
