package com.contentgrid.appserver.autoconfigure.opa;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.env.MockEnvironment;

class OpaSidecarAbacSourceEnvironmentPostProcessorTest {

    private final OpaSidecarAbacSourceEnvironmentPostProcessor postProcessor =
            new OpaSidecarAbacSourceEnvironmentPostProcessor();

    static Stream<Arguments> scenarios() {
        return Stream.of(
                Arguments.of("policyPackage blank defaults abac.source to opa", null, null, "opa"),
                Arguments.of("policyPackage set defaults abac.source to none", "tenant.xyz", null, "none"),
                Arguments.of("explicit abac.source wins over the sidecar default", null, "none", "none"),
                Arguments.of("explicit abac.source wins over the centralized default", "tenant.xyz", "opa", "opa"),
                Arguments.of("explicit non-opa abac.source is preserved verbatim", null, "header", "header")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void derivesAbacSourceFromPolicyPackage(String description, String policyPackage, String initialAbacSource,
            String expectedAbacSource) {
        var environment = new MockEnvironment();
        if (policyPackage != null) {
            environment.setProperty("contentgrid.system.policyPackage", policyPackage);
        }
        if (initialAbacSource != null) {
            environment.setProperty("contentgrid.thunx.abac.source", initialAbacSource);
        }

        postProcessor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("contentgrid.thunx.abac.source")).isEqualTo(expectedAbacSource);
    }
}
