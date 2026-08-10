package com.contentgrid.appserver.autoconfigure.opa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifact;
import com.contentgrid.appserver.security.opa.OpaPolicyUploadInitializer;
import com.contentgrid.appserver.security.opa.OpaPolicyUploadService;
import com.contentgrid.appserver.security.opa.OpaStatusImpl;
import com.contentgrid.opa.client.OpaClient;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.logging.ConditionEvaluationReportLoggingListener;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class OpaPolicyUploadAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(ConditionEvaluationReportLoggingListener.forLogLevel(LogLevel.INFO))
            .withBean(BlueprintArtifact.class, () -> mock(BlueprintArtifact.class))
            .withConfiguration(AutoConfigurations.of(OpaPolicyUploadAutoConfiguration.class));

    @Test
    void indicatorRegisteredEvenWithoutOpaClient_startsDown() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(OpaClient.class);
            assertThat(context).doesNotHaveBean(OpaPolicyUploadInitializer.class);
            assertThat(context).doesNotHaveBean(OpaPolicyUploadService.class);
            assertThat(context).hasSingleBean(OpaStatusImpl.class);
            assertThat(context).hasSingleBean(HealthIndicator.class);
            assertThat(context.getBean(OpaStatusImpl.class).getHealth().getStatus()).isEqualTo(Status.DOWN);
        });
    }

    static Stream<Arguments> opaClientPresentScenarios() {
        return Stream.of(
                Arguments.argumentSet("sidecar mode: upload beans exist, indicator starts down",
                        "", true, Status.DOWN),
                Arguments.argumentSet("centralized mode: upload beans absent, indicator starts up",
                        "tenant.xyz", false, Status.UP)
        );
    }

    @ParameterizedTest
    @MethodSource("opaClientPresentScenarios")
    void behavesAccordingToPolicyPackage(String policyPackage, boolean uploadBeansExpected, Status expectedStatus) {
        contextRunner
                .withBean("customOpaClient", OpaClient.class, () -> mock(OpaClient.class))
                .withPropertyValues("contentgrid.system.policyPackage=" + policyPackage)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("customOpaClient");
                    assertThat(context).hasSingleBean(OpaClient.class);
                    assertThat(context).hasSingleBean(OpaStatusImpl.class);
                    assertThat(context).hasSingleBean(HealthIndicator.class);
                    if (uploadBeansExpected) {
                        assertThat(context).hasSingleBean(OpaPolicyUploadService.class);
                        assertThat(context).hasSingleBean(OpaPolicyUploadInitializer.class);
                    } else {
                        assertThat(context).doesNotHaveBean(OpaPolicyUploadService.class);
                        assertThat(context).doesNotHaveBean(OpaPolicyUploadInitializer.class);
                    }
                    assertThat(context.getBean(OpaStatusImpl.class).getHealth().getStatus()).isEqualTo(expectedStatus);
                });
    }
}
