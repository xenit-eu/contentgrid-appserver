package com.contentgrid.appserver.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.contentgrid.appserver.domain.values.version.Version;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.server.ResponseStatusException;

class VersionConstraintArgumentResolverTest {

    private static final VersionConstraintArgumentResolver RESOLVER = new VersionConstraintArgumentResolver();

    public static Stream<Arguments> versions() {
        return Stream.of(
                Arguments.of("\"abc\"", Version.exactly("abc"), true),
                Arguments.of("\"abc\"", Version.unspecified(), false),
                Arguments.of("\"abc\"", Version.nonExisting(), false),
                Arguments.of("\"def\"", Version.exactly("abc"), false),
                Arguments.of("\"def\", \"abc\"", Version.exactly("abc"), true),
                Arguments.of("*", Version.exactly("abc"), true),
                Arguments.of("*", Version.unspecified(), true),
                Arguments.of("*", Version.nonExisting(), false)
        );
    }

    public static Stream<Arguments> weakVersions() {
        return Stream.of(
                Arguments.of("W/\"abc\"", Version.exactly("abc"), true),
                Arguments.of("W/\"abc\"", Version.unspecified(), false),
                Arguments.of("W/\"abc\"", Version.nonExisting(), false),
                Arguments.of("W/\"def\"", Version.exactly("abc"), false),
                Arguments.of("W/\"def\", W/\"abc\"", Version.exactly("abc"), true)
        );
    }

    private static ServletWebRequest createRequest(HttpHeaders headers) {
        return new ServletWebRequest(MockMvcRequestBuilders.get("/")
                .headers(headers)
                .buildRequest(null));
    }

    private static ServletWebRequest createRequest(String name, String headerValue) {
        var headers = new HttpHeaders();
        headers.set(name, headerValue);
        return createRequest(headers);
    }

    public static Stream<String> invalidHeaderValues() {
        return Stream.of(
                "abc",
                "\"abc",
                "W/abc",
                "W/\"abc"
        );
    }

    @ParameterizedTest
    @MethodSource("versions")
    void ifMatch(String headerValue, Version version, boolean shouldMatch) {
        var request = createRequest("If-Match", headerValue);

        var versionConstraint = RESOLVER.resolveArgument(null, null, request, null);

        assertThat(versionConstraint).isNotNull();
        assertThat(versionConstraint.isSatisfiedBy(version)).isEqualTo(shouldMatch);
    }

    @ParameterizedTest
    @MethodSource("weakVersions")
    void ifMatchWeak(String headerValue, Version version, boolean shouldMatch) {
        // If-Match never considers weak versions to equal
        ifMatch(headerValue, version, false);
    }

    @ParameterizedTest
    @MethodSource({"versions", "weakVersions"})
    void ifNoneMatch(String headerValue, Version version, boolean shouldMatch) {
        var request = createRequest("If-None-Match", headerValue);

        var versionConstraint = RESOLVER.resolveArgument(null, null, request, null);

        assertThat(versionConstraint).isNotNull();
        assertThat(versionConstraint.isSatisfiedBy(version)).isEqualTo(!shouldMatch);
    }

    @ParameterizedTest
    @MethodSource
    void invalidHeaderValues(String headerValue) {
        var ifMatchRequest = createRequest("If-Match", headerValue);

        assertThatThrownBy(() -> RESOLVER.resolveArgument(null, null, ifMatchRequest, null))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(400);
                });

        var ifNoneMatchRequest = createRequest("If-None-Match", headerValue);

        assertThatThrownBy(() -> RESOLVER.resolveArgument(null, null, ifNoneMatchRequest, null))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(400);
                });

    }

    public static Stream<Arguments> combinations() {
        return Stream.of(
                Arguments.of("\"abc\"", "\"def\"", Version.exactly("abc"), true),
                Arguments.of("\"abc\"", "\"def\"", Version.unspecified(), false),
                Arguments.of("\"abc\"", "\"def\"", Version.nonExisting(), false),
                Arguments.of("\"abc\"", "\"abc\"", Version.exactly("abc"), false),
                Arguments.of("*", "\"abc\"", Version.exactly("abc"), false),
                Arguments.of("*", "\"abc\"", Version.exactly("def"), true),
                Arguments.of("*", "\"abc\"", Version.unspecified(), true),
                Arguments.of("*", "\"abc\"", Version.nonExisting(), false),
                Arguments.of("\"abc\"", "*", Version.exactly("abc"), false),
                Arguments.of("\"abc\"", "*", Version.exactly("def"), false),
                Arguments.of("\"abc\"", "*", Version.unspecified(), false),
                Arguments.of("\"abc\"", "*", Version.nonExisting(), false)
        );
    }

    @ParameterizedTest
    @MethodSource
    void combinations(
            String ifMatch,
            String ifNoneMatch,
            Version version,
            boolean shouldMatch
    ) {
        var headers = new HttpHeaders();
        headers.set("If-Match", ifMatch);
        headers.set("If-None-Match", ifNoneMatch);
        var request = createRequest(headers);

        var versionConstraint = RESOLVER.resolveArgument(null, null, request, null);

        assertThat(versionConstraint).isNotNull();
        assertThat(versionConstraint.isSatisfiedBy(version)).isEqualTo(shouldMatch);
    }
}