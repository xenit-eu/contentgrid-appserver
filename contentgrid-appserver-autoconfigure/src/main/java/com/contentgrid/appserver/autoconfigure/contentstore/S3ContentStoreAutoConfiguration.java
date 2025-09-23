package com.contentgrid.appserver.autoconfigure.contentstore;

import com.contentgrid.appserver.autoconfigure.contentstore.S3ContentStoreAutoConfiguration.S3Properties;
import com.contentgrid.appserver.contentstore.api.ContentStore;
import com.contentgrid.appserver.contentstore.impl.s3.S3ContentStore;
import io.minio.MinioAsyncClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass({ContentStore.class, S3ContentStore.class, MinioAsyncClient.class})
@ConditionalOnProperty(value = "contentgrid.appserver.content-store.type", havingValue = "s3")
@EnableConfigurationProperties(S3Properties.class)
public class S3ContentStoreAutoConfiguration {

    @ConfigurationProperties(prefix = "contentgrid.appserver.content.s3")
    public record S3Properties(
        String url,
        String accessKey,
        String secretKey,
        String bucket,
        String region
    ) {}

    @Bean
    @ConditionalOnProperty("contentgrid.appserver.content.s3.url")
    MinioAsyncClient minioAsyncClient(S3Properties properties) {
        var builder = MinioAsyncClient.builder()
                .endpoint(properties.url());

        if (properties.accessKey() != null && properties.secretKey() != null) {
            builder.credentials(properties.accessKey(), properties.secretKey());
        }
        if (properties.region() != null) {
            builder.region(properties.region());
        }

        return builder.build();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(MinioAsyncClient.class)
    @ConditionalOnProperty("contentgrid.appserver.content.s3.bucket")
    ContentStore s3ContentStore(MinioAsyncClient minioClient, S3Properties properties) {
        return new S3ContentStore(minioClient, properties.bucket());
    }

}
