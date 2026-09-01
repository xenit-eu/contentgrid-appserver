package com.contentgrid.appserver.autoconfigure.s3;

import com.contentgrid.appserver.autoconfigure.s3.S3ContentStoreAutoConfiguration.S3Properties;
import com.contentgrid.appserver.contentstore.api.ContentStore;
import com.contentgrid.appserver.contentstore.impl.s3.S3ContentStore;
import com.contentgrid.appserver.domain.content.ContentStoreResolver;
import java.time.Duration;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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
        @DefaultValue("1") int connectionPoolKeepAliveSeconds
    ) {}

    @Bean
    @ConditionalOnMissingBean
    S3AsyncClient s3AsyncClient(S3Properties properties) {
        var started = System.nanoTime();
        // connection-pool-size 0 (the default) means connections must not be re-used at all (ACC-2696):
        // no-reuse is enforced with a `Connection: close` header on every request. With a pool, its size
        // caps the number of concurrent connections, and idle connections are kept around for the
        // keep-alive period.
        var reuseConnections = properties.connectionPoolSize() > 0;
        var httpClientBuilder = NettyNioAsyncHttpClient.builder();
        if (reuseConnections) {
            httpClientBuilder
                    .maxConcurrency(properties.connectionPoolSize())
                    .connectionMaxIdleTime(Duration.ofSeconds(properties.connectionPoolKeepAliveSeconds()));
        }

        var client = S3ClientFactory.createS3AsyncClient(
                properties.url(),
                properties.accessKey(),
                properties.secretKey(),
                properties.region(),
                properties.pathStyleAccess(),
                httpClientBuilder,
                reuseConnections
        );
        log.info("Startup timing: created content-store S3 client in {} ms",
                (System.nanoTime() - started) / 1_000_000);
        return client;
    }

    @Bean
    @ConditionalOnBean(S3AsyncClient.class)
    ContentStoreResolver s3ContentStoreResolver(S3AsyncClient s3AsyncClient, S3Properties properties) {
        var contentStore = new S3ContentStore(s3AsyncClient, properties.bucket());
        return application -> contentStore;
    }

}
