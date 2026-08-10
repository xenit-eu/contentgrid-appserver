package com.contentgrid.appserver.autoconfigure.opa;

import com.contentgrid.appserver.actuator.policy.OnMissingPolicyPackageCondition;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifact;
import com.contentgrid.appserver.security.opa.OpaPolicyUploadInitializer;
import com.contentgrid.appserver.security.opa.OpaPolicyUploadRetryProperties;
import com.contentgrid.appserver.security.opa.OpaPolicyUploadService;
import com.contentgrid.appserver.security.opa.OpaStatusImpl;
import com.contentgrid.opa.client.OpaClient;
import com.contentgrid.thunx.opa.autoconfigure.OpaClientAutoConfiguration;
import com.contentgrid.thunx.opa.autoconfigure.OpaProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@AutoConfiguration(after = {OpaClientAutoConfiguration.class, OpaHealthIndicatorAutoConfiguration.class})
@Conditional(OnMissingPolicyPackageCondition.class)
@ConditionalOnBean(OpaClient.class)
@EnableAsync
@EnableConfigurationProperties({OpaProperties.class, OpaPolicyUploadRetryProperties.class})
public class OpaPolicyUploadAutoConfiguration {

    @Bean
    public ThreadPoolTaskExecutor opaRetryExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setThreadNamePrefix("opa-policy-retry-");
        return executor;
    }

    @Bean
    public OpaPolicyUploadService opaPolicyUploadService(
            OpaClient opaClient,
            OpaPolicyUploadRetryProperties retryProperties,
            OpaStatusImpl opaStatus) {
        return new OpaPolicyUploadService(opaClient, retryProperties, opaStatus);
    }

    @Bean
    public OpaPolicyUploadInitializer opaPolicyUploadInitializer(
            BlueprintArtifact blueprintArtifact,
            OpaPolicyUploadService opaPolicyUploadService) {
        return new OpaPolicyUploadInitializer(blueprintArtifact, opaPolicyUploadService);
    }
}
