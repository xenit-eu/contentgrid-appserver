package com.contentgrid.appserver.autoconfigure.s3.testing;

import com.contentgrid.appserver.autoconfigure.s3.S3AsyncClientFactory;
import com.contentgrid.appserver.autoconfigure.s3.S3ConfigurationProperties;
import java.net.URI;
import java.time.Duration;
import lombok.experimental.UtilityClass;
import software.amazon.awssdk.services.s3.S3AsyncClient;

/**
 * Creates S3 clients for tests through the same factory the appserver builds its client with, so tests run
 * against the production client settings instead of a copy that can drift.
 */
@UtilityClass
public class S3TestClients {

    public static S3AsyncClientFactory createFactory(String endpoint) {
        return new S3AsyncClientFactory(new S3ConfigurationProperties() {
            @Override
            public URI endpoint() {
                return URI.create(endpoint);
            }

            @Override
            public String accessKey() {
                return "test";
            }

            @Override
            public String secretKey() {
                return "test";
            }

            @Override
            public String region() {
                return null;
            }

            @Override
            public boolean pathStyleAccess() {
                return true;
            }

            @Override
            public int connectionPoolSize() {
                return 0;
            }

            @Override
            public Duration connectionPoolIdleTimeout() {
                return null;
            }

            @Override
            public Duration connectionTimeout() {
                return null;
            }

            @Override
            public Duration readTimeout() {
                return null;
            }

            @Override
            public Duration writeTimeout() {
                return null;
            }
        });
    }

    public static S3AsyncClient s3AsyncClient(String endpoint) {
        return createFactory(endpoint).createClient();
    }
}
