package com.contentgrid.appserver.autoconfigure.content;

import com.contentgrid.appserver.autoconfigure.content.S3ContentStoreAutoConfiguration.S3Properties;
import com.contentgrid.appserver.content.api.ContentStore;
import com.contentgrid.appserver.content.impl.s3.S3ContentStore;
import io.minio.MinioAsyncClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass({ContentStore.class, S3ContentStore.class, MinioAsyncClient.class})
@ConditionalOnProperty(value = "contentgrid.appserver.content.type", havingValue = "s3")
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
    MinioAsyncClient minioAsyncClient(S3Properties properties) {
        return MinioAsyncClient.builder()
                .endpoint(properties.url())
                .credentials(properties.accessKey(), properties.secretKey())
                .region(properties.region())
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    ContentStore s3ContentStore(MinioAsyncClient minioClient, S3Properties properties) {
        return new S3ContentStore(minioClient, properties.bucket());
    }

}
