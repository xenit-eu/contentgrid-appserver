package com.contentgrid.appserver.autoconfigure.s3;

import com.contentgrid.appserver.autoconfigure.s3.S3ContentStoreAutoConfiguration.S3Properties;
import com.contentgrid.appserver.contentstore.api.ContentStore;
import com.contentgrid.appserver.contentstore.impl.s3.S3ContentStore;
import com.contentgrid.appserver.contentstore.impl.s3.S3ReadRetryPolicy;
import com.contentgrid.appserver.domain.content.ContentStoreResolver;
import java.time.Duration;
import lombok.NonNull;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.services.s3.S3AsyncClient;

@AutoConfiguration
@ConditionalOnClass({ContentStoreResolver.class, ContentStore.class, S3ContentStore.class, S3AsyncClient.class,
        NettyNioAsyncHttpClient.class})
@ConditionalOnProperty(value = "contentgrid.appserver.content-store.type", havingValue = "s3")
@EnableConfigurationProperties(S3Properties.class)
public class S3ContentStoreAutoConfiguration {

    @ConfigurationProperties(prefix = "contentgrid.appserver.content.s3")
    public record S3Properties(
        String url,
        String accessKey,
        String secretKey,
        @NonNull String bucket,
        String region,
        @DefaultValue("true") boolean pathStyleAccess,
        @DefaultValue("0") int connectionPoolSize,
        @DefaultValue("1") int connectionPoolKeepAliveSeconds,
        @DefaultValue("2s") @NonNull Duration connectionTimeout,
        @DefaultValue("5s") @NonNull Duration tlsNegotiationTimeout,
        @DefaultValue("10s") @NonNull Duration connectionAcquisitionTimeout,
        @DefaultValue("30s") @NonNull Duration readTimeout,
        @DefaultValue("30s") @NonNull Duration writeTimeout,
        @DefaultValue @NonNull S3ReadRetryProperties readRetry
    ) {
        public S3Properties {
            validateTimeout(connectionTimeout, "connectionTimeout", false);
            validateTimeout(tlsNegotiationTimeout, "tlsNegotiationTimeout", false);
            validateTimeout(connectionAcquisitionTimeout, "connectionAcquisitionTimeout", false);
            validateTimeout(readTimeout, "readTimeout", true);
            validateTimeout(writeTimeout, "writeTimeout", true);
            readRetry.toPolicy();
        }

        private static void validateTimeout(Duration timeout, String name, boolean zeroAllowed) {
            if (timeout.isNegative() || (!zeroAllowed && timeout.isZero())) {
                throw new IllegalArgumentException(name + " must be " + (zeroAllowed ? "nonnegative" : "strictly positive"));
            }
        }
    }

    public record S3ReadRetryProperties(
        @DefaultValue("1") int maxRetries,
        @DefaultValue("10s") @NonNull Duration acquisitionTimeout,
        @DefaultValue("0ms") @NonNull Duration minDelay,
        @DefaultValue("250ms") @NonNull Duration maxDelay
    ) {
        public S3ReadRetryProperties {
            new S3ReadRetryPolicy(maxRetries, acquisitionTimeout, minDelay, maxDelay);
        }

        S3ReadRetryPolicy toPolicy() {
            return new S3ReadRetryPolicy(maxRetries, acquisitionTimeout, minDelay, maxDelay);
        }
    }

    @Bean
    @ConditionalOnMissingBean
    S3AsyncClient s3AsyncClient(S3Properties properties) {
        // connection-pool-size 0 (the default) means connections must not be re-used at all (ACC-2696):
        // no-reuse is enforced with a `Connection: close` header on every request. With a pool, its size
        // caps the number of concurrent connections, and idle connections are kept around for the
        // keep-alive period.
        var reuseConnections = properties.connectionPoolSize() > 0;
        var httpClientBuilder = NettyNioAsyncHttpClient.builder()
                .connectionTimeout(properties.connectionTimeout())
                // Netty otherwise inherits an explicitly set connection timeout for TLS negotiation.
                .tlsNegotiationTimeout(properties.tlsNegotiationTimeout())
                .connectionAcquisitionTimeout(properties.connectionAcquisitionTimeout())
                .readTimeout(properties.readTimeout())
                .writeTimeout(properties.writeTimeout());
        if (reuseConnections) {
            httpClientBuilder
                    .maxConcurrency(properties.connectionPoolSize())
                    .connectionMaxIdleTime(Duration.ofSeconds(properties.connectionPoolKeepAliveSeconds()));
        }

        return S3ClientFactory.createS3AsyncClient(
                properties.url(),
                properties.accessKey(),
                properties.secretKey(),
                properties.region(),
                properties.pathStyleAccess(),
                httpClientBuilder,
                reuseConnections
        );
    }

    @Bean
    @ConditionalOnBean(S3AsyncClient.class)
    ContentStoreResolver s3ContentStoreResolver(S3AsyncClient s3AsyncClient, S3Properties properties) {
        var contentStore = new S3ContentStore(s3AsyncClient, properties.bucket(), properties.readRetry().toPolicy());
        return application -> contentStore;
    }

}
