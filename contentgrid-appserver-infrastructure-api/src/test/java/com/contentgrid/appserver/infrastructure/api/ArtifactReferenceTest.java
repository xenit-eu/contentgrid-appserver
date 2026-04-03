package com.contentgrid.appserver.infrastructure.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.contentgrid.appserver.infrastructure.api.ArtifactReference.Scheme;
import org.junit.jupiter.api.Test;

public class ArtifactReferenceTest {

    @Test
    void parse_fileScheme() {
        var ref = ArtifactReference.parse("file:/some/path");
        assertThat(ref.getScheme()).isEqualTo(Scheme.FILE);
        assertThat(ref.getPath()).isEqualTo("/some/path");
        assertThat(ref.toString()).isEqualTo("file:/some/path");
    }

    @Test
    void parse_zipScheme() {
        var ref = ArtifactReference.parse("zip:/some/path/artifact.zip");
        assertThat(ref.getScheme()).isEqualTo(Scheme.ZIP);
        assertThat(ref.getPath()).isEqualTo("/some/path/artifact.zip");
    }

    @Test
    void parse_classpathScheme() {
        var ref = ArtifactReference.parse("classpath:some/relative/path");
        assertThat(ref.getScheme()).isEqualTo(Scheme.CLASSPATH);
        assertThat(ref.getPath()).isEqualTo("some/relative/path");
    }

    @Test
    void parse_unknownScheme_throws() {
        assertThatThrownBy(() -> ArtifactReference.parse("s3:bucket/key"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("s3");
    }
}
