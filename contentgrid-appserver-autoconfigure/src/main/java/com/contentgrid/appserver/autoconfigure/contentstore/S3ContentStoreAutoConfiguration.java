package com.contentgrid.appserver.autoconfigure.contentstore;

import com.contentgrid.appserver.autoconfigure.contentstore.S3ContentStoreAutoConfiguration.S3Properties;
import com.contentgrid.appserver.autoconfigure.s3.S3ClientFactory;
import com.contentgrid.appserver.contentstore.api.ContentStore;
import com.contentgrid.appserver.contentstore.impl.s3.S3ContentStore;
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
import software.amazon.awssdk.http.apache5.Apache5HttpClient;
import software.amazon.awssdk.services.s3.S3Client;

@AutoConfiguration
@ConditionalOnClass({ContentStoreResolver.class, ContentStore.class, S3ContentStore.class, S3Client.class})
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
        @DefaultValue("0") int connectionPoolSize,
        @DefaultValue("1") int connectionPoolKeepAliveSeconds
    ) {}

    @Bean
    @ConditionalOnMissingBean
    S3Client s3Client(S3Properties properties) {
        // connection-pool-size 0 (the default) means connections must not be re-used at all (ACC-2696).
        // The Apache http client has no equivalent of OkHttp's zero-size connection pool, so no-reuse is
        // enforced with a `Connection: close` header on every request instead. With a pool, idle
        // connections are kept around for the keep-alive period.
        var reuseConnections = properties.connectionPoolSize() > 0;
        var httpClientBuilder = Apache5HttpClient.builder();
        if (reuseConnections) {
            httpClientBuilder.connectionMaxIdleTime(Duration.ofSeconds(properties.connectionPoolKeepAliveSeconds()));
        }

        return S3ClientFactory.createS3Client(
                properties.url(),
                properties.accessKey(),
                properties.secretKey(),
                properties.region(),
                httpClientBuilder,
                reuseConnections
        );
    }

    @Bean
    @ConditionalOnBean(S3Client.class)
    ContentStoreResolver s3ContentStoreResolver(S3Client s3Client, S3Properties properties) {
        var contentStore = new S3ContentStore(s3Client, properties.bucket());
        return application -> contentStore;
    }

}
