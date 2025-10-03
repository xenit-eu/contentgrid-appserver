package com.contentgrid.appserver.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.context.request.ServletWebRequest;

class UserLocalesArgumentResolverTest {

    public static final Locale DUTCH = Locale.of("nl");
    public static final Locale NL_BE = Locale.of("nl", "BE");
    public static final Locale NL_NL = Locale.of("nl", "NL");
    public static final UserLocalesArgumentResolver RESOLVER = new UserLocalesArgumentResolver();

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

    public static Stream<Arguments> parsesAcceptLanguage() {
        return Stream.of(
                Arguments.of("nl", List.of(Locale.ROOT, Locale.ENGLISH, Locale.CHINESE), Stream.of(Locale.ROOT, null).collect(
                        Collectors.toSet())),
                Arguments.of("nl", List.of(DUTCH, Locale.ENGLISH, Locale.CHINESE), Set.of(DUTCH)),
                Arguments.of("nl", List.of(NL_BE, Locale.ENGLISH, Locale.CHINESE), Set.of(NL_BE)),
                Arguments.of("nl", List.of(NL_BE, NL_NL, Locale.ENGLISH, Locale.CHINESE), Set.of(NL_BE, NL_NL)),
                Arguments.of("nl-BE", List.of(NL_BE, NL_NL, Locale.ENGLISH, Locale.CHINESE), Set.of(NL_BE)),
                Arguments.of("nl-BE", List.of(NL_NL, DUTCH, Locale.ENGLISH, Locale.CHINESE), Set.of(DUTCH)),
                Arguments.of("nl, en", List.of(NL_BE, DUTCH, Locale.ENGLISH, Locale.CHINESE), Set.of(NL_BE, DUTCH)),
                Arguments.of("nl, en", List.of(Locale.UK, Locale.US, Locale.CHINESE), Set.of(Locale.UK, Locale.US))
        );
    }

    @ParameterizedTest
    @MethodSource
    void parsesAcceptLanguage(String acceptLanguage, Collection<Locale> supportedLocales, Set<Locale> acceptableLocales) {

        var userLocales = RESOLVER.resolveArgument(null, null, createRequest(HttpHeaders.ACCEPT_LANGUAGE, acceptLanguage), null);

        assertThat(userLocales.resolvePreferredLocale(supportedLocales))
                .isIn(acceptableLocales);

    }

    @Test
    void noHeader() {
        var userLocales = RESOLVER.resolveArgument(null, null, createRequest(new HttpHeaders()), null);

        assertThat(userLocales.resolvePreferredLocale(List.of(DUTCH, Locale.ENGLISH))).isNull();
    }


}