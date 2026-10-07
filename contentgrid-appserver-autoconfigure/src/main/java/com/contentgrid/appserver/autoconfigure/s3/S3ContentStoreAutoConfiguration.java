package com.contentgrid.appserver.autoconfigure.s3;

import com.contentgrid.appserver.autoconfigure.s3.S3ContentStoreAutoConfiguration.S3Properties;
import com.contentgrid.appserver.contentstore.api.ContentStore;
import com.contentgrid.appserver.contentstore.impl.s3.S3ContentStore;
import com.contentgrid.appserver.domain.content.ContentStoreResolver;
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

/**
 * S3 content storage auto-configuration.
 * <p>
 * Without a user-provided {@link S3AsyncClient}, managed clients are built from the application
 * properties: a read client with multipart operation disabled (ordinary single-request downloads) and
 * a write client with multipart operation enabled (transparent multipart uploads), sharing one HTTP
 * transport and connection pool. The managed clients and their transport are closed together with the
 * application context.
 * <p>
 * With a user-provided {@link S3AsyncClient}, no managed clients or transports are created. The content
 * store then uses that single client for every operation, so the custom client alone determines download
 * behavior: downloads are single requests only when the custom client has multipart operation disabled.
 */
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
        @DefaultValue("1") int connectionPoolKeepAliveSeconds
    ) {}

    @Bean
    @ConditionalOnMissingBean(S3AsyncClient.class)
    S3ContentStoreClients s3ContentStoreClients(S3Properties properties) {
        // connection-pool-size 0 (the default) means connections must not be re-used at all (ACC-2696):
        // no-reuse is enforced with a `Connection: close` header on every request. With a pool, its size
        // caps the number of concurrent connections, and idle connections are kept around for the
        // keep-alive period.
        var reuseConnections = properties.connectionPoolSize() > 0;
        var httpClient = S3ClientFactory.createSharedHttpClient(properties.connectionPoolSize(),
                properties.connectionPoolKeepAliveSeconds());
        try {
            var readClient = S3ClientFactory.createS3ReadAsyncClient(properties.url(), properties.accessKey(),
                    properties.secretKey(), properties.region(), properties.pathStyleAccess(), httpClient,
                    reuseConnections);
            try {
                var writeClient = S3ClientFactory.createS3WriteAsyncClient(properties.url(),
                        properties.accessKey(), properties.secretKey(), properties.region(),
                        properties.pathStyleAccess(), httpClient, reuseConnections);
                return new S3ContentStoreClients(readClient, writeClient, httpClient);
            } catch (RuntimeException e) {
                readClient.close();
                throw e;
            }
        } catch (RuntimeException e) {
            httpClient.close();
            throw e;
        }
    }

    @Bean(destroyMethod = "")
    @ConditionalOnBean(S3ContentStoreClients.class)
    S3AsyncClient s3AsyncClient(S3ContentStoreClients clients) {
        // Keeps the historical s3AsyncClient bean available for external injectors with its previous
        // multipart-enabled write behavior. The holder above owns (and closes) the returned client, so
        // Spring must not close it again here.
        return clients.writeClient();
    }

    @Bean("s3ContentStoreResolver")
    @ConditionalOnBean(S3ContentStoreClients.class)
    ContentStoreResolver s3ContentStoreResolver(S3ContentStoreClients clients, S3Properties properties) {
        var contentStore = new S3ContentStore(clients.readClient(), clients.writeClient(), properties.bucket());
        return application -> contentStore;
    }

    @Bean("s3ContentStoreResolver")
    @ConditionalOnMissingBean(S3ContentStoreClients.class)
    @ConditionalOnBean(S3AsyncClient.class)
    ContentStoreResolver s3ContentStoreResolverCustom(S3AsyncClient s3AsyncClient, S3Properties properties) {
        var contentStore = new S3ContentStore(s3AsyncClient, properties.bucket());
        return application -> contentStore;
    }

}
