package com.contentgrid.appserver.autoconfigure.opa;

import com.contentgrid.appserver.actuator.policy.OnMissingPolicyPackageCondition;
import com.contentgrid.appserver.autoconfigure.opa.OpaPolicyUploaderAutoConfiguration.OpaPolicyUploadRetryProperties;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifact;
import com.contentgrid.opa.client.OpaClient;
import com.contentgrid.thunx.opa.autoconfigure.OpaProperties;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;

@AutoConfiguration
@Conditional(OnMissingPolicyPackageCondition.class)
@ConditionalOnClass(OpaClient.class)
@EnableConfigurationProperties({OpaProperties.class, OpaPolicyUploadRetryProperties.class})
public class OpaPolicyUploaderAutoConfiguration {

    /**
     * Backoff between attempts grows as {@code initialDelay * multiplier^attempt}, capped at {@code maxDelay}.
     * After {@code maxRetries} failed retries, the upload is given up on.
     */
    @ConfigurationProperties(prefix = "contentgrid.appserver.opa.policy-upload.retry")
    public record OpaPolicyUploadRetryProperties(
            @DefaultValue("100ms") Duration initialDelay,
            @DefaultValue("30s") Duration maxDelay,
            @DefaultValue("2") double multiplier,
            @DefaultValue("5") long maxRetries
    ) {}

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
