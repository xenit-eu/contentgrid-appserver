package com.contentgrid.appserver.autoconfigure.contentstore;

import com.contentgrid.appserver.autoconfigure.contentstore.S3ContentStoreAutoConfiguration.S3Properties;
import com.contentgrid.appserver.contentstore.api.ContentStore;
import com.contentgrid.appserver.contentstore.impl.s3.S3ContentStore;
import com.contentgrid.appserver.domain.spi.contentstore.resolver.ContentStoreResolver;
import io.minio.MinioAsyncClient;
import java.util.concurrent.TimeUnit;
import lombok.NonNull;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass({ContentStoreResolver.class, ContentStore.class, S3ContentStore.class, MinioAsyncClient.class})
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
    MinioAsyncClient s3MinioAsyncClient(S3Properties properties) {
        var okHttpClient = new OkHttpClient.Builder()
                .connectionPool(new ConnectionPool(
                        properties.connectionPoolSize(),
                        properties.connectionPoolKeepAliveSeconds(),
                        TimeUnit.SECONDS))
                .retryOnConnectionFailure(true)
                .build();

        var builder = MinioAsyncClient.builder()
                .endpoint(properties.url())
                .httpClient(okHttpClient);

        if (properties.accessKey() != null && properties.secretKey() != null) {
            builder.credentials(properties.accessKey(), properties.secretKey());
        }
        if (properties.region() != null) {
            builder.region(properties.region());
        }

        return builder.build();
    }

    @Bean
    @ConditionalOnBean(MinioAsyncClient.class)
    ContentStoreResolver s3ContentStoreResolver(MinioAsyncClient minioClient, S3Properties properties) {
        var contentStore = new S3ContentStore(minioClient, properties.bucket());
        return application -> contentStore;
    }

}
