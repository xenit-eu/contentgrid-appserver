package com.contentgrid.appserver.autoconfigure.s3.testing;

import com.contentgrid.appserver.autoconfigure.s3.S3ClientFactory;
import lombok.experimental.UtilityClass;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.services.s3.S3AsyncClient;

/**
 * Creates S3 clients for tests through the same factory the appserver builds its client with, so tests run
 * against the production client settings instead of a copy that can drift.
 */
@UtilityClass
public class S3TestClients {

    public static S3AsyncClient s3AsyncClient(String endpoint) {
        return S3ClientFactory.createS3AsyncClient(endpoint, "test", "test", null, true,
                NettyNioAsyncHttpClient.builder(), false);
    }
}
