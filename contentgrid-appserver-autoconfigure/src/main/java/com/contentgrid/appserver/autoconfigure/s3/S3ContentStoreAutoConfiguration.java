package com.contentgrid.appserver.autoconfigure.s3;

import com.contentgrid.appserver.autoconfigure.s3.S3ContentStoreAutoConfiguration.S3HedgingProperties;
import com.contentgrid.appserver.autoconfigure.s3.S3ContentStoreAutoConfiguration.S3Properties;
import com.contentgrid.appserver.contentstore.api.ContentStore;
import com.contentgrid.appserver.contentstore.impl.s3.NoOperationHedger;
import com.contentgrid.appserver.contentstore.impl.s3.OperationHedger;
import com.contentgrid.appserver.contentstore.impl.s3.S3ContentStore;
import com.contentgrid.appserver.contentstore.impl.s3.SimpleOperationHedger;
import com.contentgrid.appserver.contentstore.impl.s3.SimpleOperationHedger.HedgingSettings;
import com.contentgrid.appserver.domain.content.ContentStoreResolver;
import java.net.URI;
import java.time.Duration;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.services.s3.S3AsyncClient;

@AutoConfiguration
@ConditionalOnClass({ContentStoreResolver.class, ContentStore.class, S3ContentStore.class, S3AsyncClient.class,
        NettyNioAsyncHttpClient.class})
@ConditionalOnProperty(value = "contentgrid.appserver.content-store.type", havingValue = "s3")
@EnableConfigurationProperties({S3Properties.class, S3HedgingProperties.class})
public class S3ContentStoreAutoConfiguration {

    @ConfigurationProperties(prefix = "contentgrid.appserver.content.s3")
    @Slf4j
    public record S3Properties(
        @NonNull URI url,
        String accessKey,
        String secretKey,
        @NonNull String bucket,
        String region,
        @DefaultValue("true") boolean pathStyleAccess,
        @DefaultValue("0") int connectionPoolSize,
        @DeprecatedConfigurationProperty(replacement = "connectionPoolIdleTimeout") Integer connectionPoolKeepAliveSeconds,
        Duration connectionPoolIdleTimeout,
        Duration connectionTimeout,
        Duration readTimeout,
        Duration writeTimeout
    ) implements S3ConfigurationProperties {

        @Override
        public URI endpoint() {
            return url;
        }

        @Override
        public Duration connectionPoolIdleTimeout() {
            if (connectionPoolIdleTimeout != null) {
                return connectionPoolIdleTimeout;
            }
            if (connectionPoolKeepAliveSeconds != null) {
                log.warn("contentgrid.appserver.content.s3.connection-pool-keep-alive-seconds is deprecated; use contentgrid.appserver.content.s3.connection-pool-idle-timeout instead");
                return Duration.ofSeconds(connectionPoolKeepAliveSeconds);
            }
            return null;
        }
    }

    @ConfigurationProperties(prefix = "contentgrid.appserver.content.s3.hedging")
    public record S3HedgingProperties(
            @DefaultValue("3s") Duration operationTimeout,
            @DefaultValue("200ms") Duration attemptDelay,
            @DefaultValue("2") int maxAttempts
    ) implements HedgingSettings {
        private OperationHedger createHedger() {
            if(maxAttempts == 1) {
                return new NoOperationHedger(operationTimeout);
            } else {
                return new SimpleOperationHedger(this);
            }
        }
    }

    @Bean
    @ConditionalOnMissingBean
    S3AsyncClient s3AsyncClient(S3Properties properties) {
        return new S3AsyncClientFactory(properties).createClient();
    }

    @Bean
    @ConditionalOnBean(S3AsyncClient.class)
    ContentStoreResolver s3ContentStoreResolver(S3AsyncClient s3AsyncClient, S3Properties properties, S3HedgingProperties hedgingProperties) {
        var contentStore = new S3ContentStore(s3AsyncClient, properties.bucket(), hedgingProperties.createHedger());
        return application -> contentStore;
    }

}
