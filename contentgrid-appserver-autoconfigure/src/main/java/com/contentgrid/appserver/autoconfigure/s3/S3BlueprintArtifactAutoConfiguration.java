package com.contentgrid.appserver.autoconfigure.s3;

import com.contentgrid.appserver.autoconfigure.blueprintartifact.BlueprintArtifactAutoConfiguration.BlueprintArtifactProperties;
import com.contentgrid.appserver.blueprintartifact.impl.s3.S3BlueprintArtifactReferenceResolver;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifactReferenceResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.services.s3.S3AsyncClient;

@AutoConfiguration
@ConditionalOnClass({S3BlueprintArtifactReferenceResolver.class, BlueprintArtifactReferenceResolver.class,
        S3AsyncClient.class, NettyNioAsyncHttpClient.class})
@ConditionalOnProperty("contentgrid.appserver.blueprint-artifact.s3.endpoint")
@EnableConfigurationProperties(BlueprintArtifactProperties.class)
@Slf4j
public class S3BlueprintArtifactAutoConfiguration {

    @Bean
    BlueprintArtifactReferenceResolver s3BlueprintArtifactReferenceResolver(BlueprintArtifactProperties properties) {
        var started = System.nanoTime();
        var s3 = properties.s3();
        var client = S3ClientFactory.createS3AsyncClient(s3.endpoint(), s3.accessKey(), s3.secretKey(),
                s3.region(), s3.pathStyleAccess(), NettyNioAsyncHttpClient.builder(), true);
        log.info("Startup timing: created blueprint S3 client in {} ms", elapsedMillis(started));
        return new S3BlueprintArtifactReferenceResolver(client);
    }

    private static long elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}
