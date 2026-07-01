package com.contentgrid.appserver.autoconfigure.opa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifact;
import com.contentgrid.opa.client.OpaClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.logging.ConditionEvaluationReportLoggingListener;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class OpaPolicyUploaderAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(ConditionEvaluationReportLoggingListener.forLogLevel(LogLevel.INFO))
            .withBean(BlueprintArtifact.class, () -> mock(BlueprintArtifact.class))
            .withConfiguration(AutoConfigurations.of(OpaPolicyUploaderAutoConfiguration.class));

    @Test
    void withoutOpaServiceUrl_noBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(OpaClient.class);
            assertThat(context).doesNotHaveBean(OpaPolicyUploader.class);
        });
    }

    @Test
    void withOpaServiceUrl_beansCreated() {
        contextRunner
                .withPropertyValues("opa.service.url=http://test:8080")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(OpaClient.class);
                    assertThat(context).hasSingleBean(OpaPolicyUploader.class);
                });
    }

    @Test
    void withUserProvidedOpaClient_autoConfiguredOpaClientNotCreated() {
        contextRunner
                .withBean("customOpaClient", OpaClient.class, () -> mock(OpaClient.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean("opaClient");
                    assertThat(context).hasBean("customOpaClient");
                    assertThat(context).hasSingleBean(OpaClient.class);
                    assertThat(context).hasSingleBean(OpaPolicyUploader.class);
                });
    }
}
