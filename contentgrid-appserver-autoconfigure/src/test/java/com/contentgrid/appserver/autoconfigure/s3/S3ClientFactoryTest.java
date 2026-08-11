package com.contentgrid.appserver.autoconfigure.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;

class S3ClientFactoryTest {

    @Test
    void createS3AsyncClient_withValidConfiguration_builds() {
        try (var client = S3ClientFactory.createS3AsyncClient("http://localhost:9000", "accessKey", "secretKey",
                "none", true, NettyNioAsyncHttpClient.builder(), false)) {
            assertThat(client).isNotNull();
        }
    }

    @Test
    void createS3AsyncClient_endpointWithoutScheme_isRejected() {
        assertThatThrownBy(() -> S3ClientFactory.createS3AsyncClient("minio.example.com", "accessKey", "secretKey",
                null, true, NettyNioAsyncHttpClient.builder(), false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> S3ClientFactory.createS3AsyncClient("localhost:9000", "accessKey", "secretKey",
                null, true, NettyNioAsyncHttpClient.builder(), false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createS3AsyncClient_missingEndpoint_isRejected() {
        assertThatThrownBy(() -> S3ClientFactory.createS3AsyncClient(null, "accessKey", "secretKey", null,
                true, NettyNioAsyncHttpClient.builder(), false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createS3AsyncClient_missingCredentials_isRejected() {
        assertThatThrownBy(() -> S3ClientFactory.createS3AsyncClient("http://localhost:9000", null, null, null,
                true, NettyNioAsyncHttpClient.builder(), false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
