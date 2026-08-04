package com.contentgrid.appserver.autoconfigure.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.http.apache5.Apache5HttpClient;

class S3ClientFactoryTest {

    @Test
    void createS3Client_withValidConfiguration_builds() {
        try (var client = S3ClientFactory.createS3Client("http://localhost:9000", "accessKey", "secretKey", null,
                true, Apache5HttpClient.builder(), false)) {
            assertThat(client).isNotNull();
        }
    }

    @Test
    void createS3AsyncClient_withValidConfiguration_builds() {
        try (var client = S3ClientFactory.createS3AsyncClient("http://localhost:9000", "accessKey", "secretKey",
                "none", true, true)) {
            assertThat(client).isNotNull();
        }
    }

    @Test
    void createS3Client_endpointWithoutScheme_isRejected() {
        assertThatThrownBy(() -> S3ClientFactory.createS3Client("minio.example.com", "accessKey", "secretKey",
                null, true, Apache5HttpClient.builder(), false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> S3ClientFactory.createS3Client("localhost:9000", "accessKey", "secretKey",
                null, true, Apache5HttpClient.builder(), false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createS3Client_missingEndpoint_isRejected() {
        assertThatThrownBy(() -> S3ClientFactory.createS3Client(null, "accessKey", "secretKey", null,
                true, Apache5HttpClient.builder(), false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createS3Client_missingCredentials_isRejected() {
        assertThatThrownBy(() -> S3ClientFactory.createS3Client("http://localhost:9000", null, null, null,
                true, Apache5HttpClient.builder(), false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
