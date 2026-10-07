package com.contentgrid.appserver.autoconfigure.s3;

import com.contentgrid.appserver.autoconfigure.s3.S3ContentStoreAutoConfiguration.S3Properties;
import com.contentgrid.appserver.contentstore.api.ContentStore;
import com.contentgrid.appserver.contentstore.impl.s3.S3ContentStore;
import com.contentgrid.appserver.domain.content.ContentStoreResolver;
import java.net.URI;
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
        @NonNull URI url,
        String accessKey,
        String secretKey,
        @NonNull String bucket,
        String region,
        @DefaultValue("true") boolean pathStyleAccess,
        @DefaultValue("0") int connectionPoolSize,
        @DefaultValue("1") int connectionPoolKeepAliveSeconds
    ) implements S3ConfigurationProperties {

        @Override
        public URI endpoint() {
            return url;
        }
    }

    @Bean
    @ConditionalOnMissingBean
    S3AsyncClient s3AsyncClient(S3Properties properties) {
        return new S3AsyncClientFactory(properties).createClient();
    }

    @Bean
    @ConditionalOnBean(S3AsyncClient.class)
    ContentStoreResolver s3ContentStoreResolver(S3AsyncClient s3AsyncClient, S3Properties properties) {
        var contentStore = new S3ContentStore(s3AsyncClient, properties.bucket());
        return application -> contentStore;
    }

}
