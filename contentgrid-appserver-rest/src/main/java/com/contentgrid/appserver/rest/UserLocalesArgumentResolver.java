package com.contentgrid.appserver.rest;

import com.contentgrid.appserver.application.model.i18n.UserLocales;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Locale.LanguageRange;
import java.util.Map;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class UserLocalesArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().isAssignableFrom(UserLocales.class);
    }

    @Override
    public UserLocales resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        var acceptHeader = webRequest.getHeaderValues(HttpHeaders.ACCEPT_LANGUAGE);
        List<String> headerValues = acceptHeader == null?List.of():List.of(acceptHeader);

        var httpHeaders = new HttpHeaders(MultiValueMap.fromMultiValue(Map.of(HttpHeaders.ACCEPT_LANGUAGE, headerValues)));

        return new AcceptLanguageUserLocales(httpHeaders);

    }

    @RequiredArgsConstructor
    private static class AcceptLanguageUserLocales implements UserLocales {
        private final HttpHeaders headers;
        private List<LanguageRange> cachedRanges;
        private List<Locale> cachedLocales;


        private List<LanguageRange> getCachedRanges() {
            if(cachedRanges == null) {
                cachedRanges = headers.getAcceptLanguage();
            }
            return cachedRanges;
        }

        private List<Locale> getCachedLocales() {
            if(cachedLocales == null) {
                cachedLocales = headers.getAcceptLanguageAsLocales();
            }
            return cachedLocales;
        }

        @Override
        public Locale resolvePreferredLocale(Collection<Locale> supportedLocales) {
            var ranges = getCachedRanges();
            var lookupResult = Locale.lookup(ranges, supportedLocales);
            if(lookupResult != null) {
                return lookupResult;
            }

            var filteredLocales = Locale.filter(ranges, supportedLocales);

            if(!filteredLocales.isEmpty()) {
                return filteredLocales.getFirst();
            }

            return null;
        }

        @Override
        public Stream<Locale> preferredLocales() {
            return getCachedLocales().stream();
        }
    }
}
