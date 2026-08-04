package com.contentgrid.appserver.autoconfigure.s3;

import com.contentgrid.appserver.autoconfigure.blueprintartifact.BlueprintArtifactAutoConfiguration.BlueprintArtifactProperties;
import com.contentgrid.appserver.blueprintartifact.impl.s3.S3BlueprintArtifactReferenceResolver;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifactReferenceResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.http.apache5.Apache5HttpClient;
import software.amazon.awssdk.services.s3.S3Client;

@AutoConfiguration
@ConditionalOnClass({S3BlueprintArtifactReferenceResolver.class, BlueprintArtifactReferenceResolver.class,
        S3Client.class})
@ConditionalOnProperty("contentgrid.appserver.blueprint-artifact.s3.endpoint")
@EnableConfigurationProperties(BlueprintArtifactProperties.class)
public class S3BlueprintArtifactAutoConfiguration {

    @Bean
    BlueprintArtifactReferenceResolver s3BlueprintArtifactReferenceResolver(BlueprintArtifactProperties properties) {
        var s3 = properties.s3();
        var client = S3ClientFactory.createS3Client(s3.endpoint(), s3.accessKey(), s3.secretKey(), s3.region(),
                s3.pathStyleAccess(), Apache5HttpClient.builder(), true);
        return new S3BlueprintArtifactReferenceResolver(client);
    }
}
