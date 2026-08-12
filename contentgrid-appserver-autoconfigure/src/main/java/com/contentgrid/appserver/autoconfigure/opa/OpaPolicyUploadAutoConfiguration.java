package com.contentgrid.appserver.autoconfigure.opa;

import com.contentgrid.appserver.actuator.policy.IsOpaSidecarModeCondition;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifact;
import com.contentgrid.appserver.security.opa.OpaHealthIndicator;
import com.contentgrid.appserver.security.opa.OpaPolicyUploadInitializer;
import com.contentgrid.appserver.security.opa.OpaPolicyUploadRetryProperties;
import com.contentgrid.appserver.security.opa.OpaPolicyUploadService;
import com.contentgrid.opa.client.OpaClient;
import com.contentgrid.thunx.opa.autoconfigure.OpaClientAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@AutoConfiguration(after = OpaClientAutoConfiguration.class)
@Conditional(IsOpaSidecarModeCondition.class)
@ConditionalOnBean(OpaClient.class)
@EnableAsync
@EnableConfigurationProperties(OpaPolicyUploadRetryProperties.class)
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
            OpaHealthIndicator opaHealthIndicator) {
        return new OpaPolicyUploadService(opaClient, retryProperties, opaHealthIndicator);
    }

    @Bean
    public OpaPolicyUploadInitializer opaPolicyUploadInitializer(
            BlueprintArtifact blueprintArtifact,
            OpaPolicyUploadService opaPolicyUploadService) {
        return new OpaPolicyUploadInitializer(blueprintArtifact, opaPolicyUploadService);
    }
}
