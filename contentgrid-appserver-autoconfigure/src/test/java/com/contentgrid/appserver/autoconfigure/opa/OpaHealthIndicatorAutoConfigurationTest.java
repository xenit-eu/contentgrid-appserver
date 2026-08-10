package com.contentgrid.appserver.autoconfigure.opa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifact;
import com.contentgrid.appserver.security.opa.OpaStatus;
import com.contentgrid.appserver.security.opa.OpaStatusImpl;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.logging.ConditionEvaluationReportLoggingListener;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class OpaHealthIndicatorAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(ConditionEvaluationReportLoggingListener.forLogLevel(LogLevel.INFO))
            .withBean(BlueprintArtifact.class, () -> mock(BlueprintArtifact.class))
            .withConfiguration(AutoConfigurations.of(OpaHealthIndicatorAutoConfiguration.class));
    
    static Stream<Arguments> opaClientPresentScenarios() {
        return Stream.of(
                Arguments.argumentSet("sidecar mode: upload beans exist, indicator starts down",
                        "", Status.DOWN),
                Arguments.argumentSet("centralized mode: upload beans absent, indicator starts up",
                        "tenant.xyz", Status.UP)
        );
    }

    @ParameterizedTest
    @MethodSource("opaClientPresentScenarios")
    void behavesAccordingToPolicyPackage(String policyPackage, Status expectedStatus) {
        contextRunner
                .withPropertyValues("contentgrid.system.policyPackage=" + policyPackage)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(OpaStatusImpl.class);
                    assertThat(context).hasSingleBean(HealthIndicator.class);
                    assertThat(context.getBean(OpaStatus.class).getHealth().getStatus()).isEqualTo(expectedStatus);
                });
    }
}
