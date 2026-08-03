package com.contentgrid.appserver.autoconfigure.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.http.apache5.Apache5HttpClient;

class S3ClientFactoryTest {

    @Test
    void normalizeEndpoint_bareHostname_prependsHttps() {
        assertThat(S3ClientFactory.normalizeEndpoint("minio.example.com")).hasToString("https://minio.example.com");
    }

    @Test
    void normalizeEndpoint_keepsExplicitScheme() {
        assertThat(S3ClientFactory.normalizeEndpoint("http://localhost:9000")).hasToString("http://localhost:9000");
        assertThat(S3ClientFactory.normalizeEndpoint("https://s3.example.com/")).hasToString("https://s3.example.com/");
    }

    @Test
    void normalizeEndpoint_withPath_isRejected() {
        assertThatThrownBy(() -> S3ClientFactory.normalizeEndpoint("https://s3.example.com/some/path"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalizeEndpoint_emptyOrNull_isRejected() {
        assertThatThrownBy(() -> S3ClientFactory.normalizeEndpoint(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> S3ClientFactory.normalizeEndpoint(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void isAwsEndpoint_detectsAwsHosts() {
        assertThat(S3ClientFactory.isAwsEndpoint(URI.create("https://s3.eu-west-1.amazonaws.com"))).isTrue();
        assertThat(S3ClientFactory.isAwsEndpoint(URI.create("https://S3.EU-WEST-1.AMAZONAWS.COM"))).isTrue();
        assertThat(S3ClientFactory.isAwsEndpoint(URI.create("https://s3.cn-north-1.amazonaws.com.cn"))).isTrue();
        assertThat(S3ClientFactory.isAwsEndpoint(URI.create("https://s3.fr-par.scw.cloud"))).isFalse();
        assertThat(S3ClientFactory.isAwsEndpoint(URI.create("http://localhost:9000"))).isFalse();
    }

    @Test
    void createS3Client_bareHostnameWithoutCredentialsOrRegion_builds() {
        try (var client = S3ClientFactory.createS3Client("minio.example.com", null, null, null,
                Apache5HttpClient.builder(), false)) {
            assertThat(client).isNotNull();
        }
    }
}
