package com.contentgrid.appserver.autoconfigure.opa;

import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifact;
import com.contentgrid.opa.client.OpaClient;
import com.contentgrid.opa.client.rest.RestClientConfiguration;
import com.contentgrid.thunx.gateway.autoconfigure.OpaProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(OpaClient.class)
@EnableConfigurationProperties(OpaProperties.class)
public class OpaPolicyUploaderAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty("opa.service.url")
    public OpaClient opaClient(OpaProperties opaProperties) {
        return OpaClient.builder()
                .httpLogging(RestClientConfiguration.LogSpecification::all)
                .url(opaProperties.getService().getUrl())
                .build();
    }

    @Bean
    @ConditionalOnBean(OpaClient.class)
    public OpaPolicyUploader opaPolicyUploader(
            BlueprintArtifact blueprintArtifact,
            OpaClient opaClient,
            @Value("${contentgrid.system.policyPackage:}") String policyPackage) {
        return new OpaPolicyUploader(blueprintArtifact, opaClient, policyPackage);
    }
}
