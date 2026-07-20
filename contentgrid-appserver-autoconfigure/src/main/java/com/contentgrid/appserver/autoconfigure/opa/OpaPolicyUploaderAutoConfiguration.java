package com.contentgrid.appserver.autoconfigure.opa;

import com.contentgrid.appserver.actuator.policy.OnMissingPolicyPackageCondition;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifact;
import com.contentgrid.appserver.security.opa.OpaPolicyUploadRetryProperties;
import com.contentgrid.appserver.security.opa.OpaPolicyUploader;
import com.contentgrid.opa.client.OpaClient;
import com.contentgrid.thunx.opa.autoconfigure.OpaProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;

@AutoConfiguration
@Conditional(OnMissingPolicyPackageCondition.class)
@ConditionalOnClass(OpaClient.class)
@EnableConfigurationProperties({OpaProperties.class, OpaPolicyUploadRetryProperties.class})
public class OpaPolicyUploaderAutoConfiguration {

    @Bean
    @ConditionalOnBean(OpaClient.class)
    public OpaPolicyUploader opaPolicyUploader(
            BlueprintArtifact blueprintArtifact,
            OpaClient opaClient,
            @Value("${contentgrid.system.policyPackage:}") String policyPackage,
            OpaPolicyUploadRetryProperties retryProperties) {
        return new OpaPolicyUploader(blueprintArtifact, opaClient, policyPackage, retryProperties);
    }
}
