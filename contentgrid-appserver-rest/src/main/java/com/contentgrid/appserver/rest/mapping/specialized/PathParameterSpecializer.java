package com.contentgrid.appserver.rest.mapping.specialized;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.rest.mapping.specialized.SpecializationHandler.Specializer;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

@RequiredArgsConstructor
class PathParameterSpecializer implements Specializer {

    @NonNull
    private final PathParameterPatternsSupplier pathParameterPatternsSupplier;

    @Override
    public void validate(@NonNull RequestMappingInfo mapping) {
        for (var requiredParam : pathParameterPatternsSupplier.getPathParameters()) {
            mapping.getPatternValues().forEach(pathPattern -> {
                if (!pathPattern.contains('{' + requiredParam + '}')) {
                    throw new IllegalStateException(
                            "Can not specialize mapping %s: Pattern '%s' is missing path variable '%s'".formatted(
                                    mapping, pathPattern, requiredParam
                            )
                    );
                }
            });
        }

    }

    @Override
    public Stream<RequestMappingInfo> specialize(@NonNull Application application,
            @NonNull RequestMappingInfo requestMappingInfo) {
        var patternValues = requestMappingInfo.getPatternValues();
        var newPatterns = pathParameterPatternsSupplier.getPathParameterValues(application)
                .flatMap(paramPatterns -> patternValues.stream()
                        .map(pattern -> restrictPathParams(paramPatterns, pattern)))
                .toArray(String[]::new);
        if (newPatterns.length == 0) {
            // No patterns are retained, so this mapping has no paths where it's going to be served
            // An empty list of path patterns maps to the 'empty' pattern, which matches the root.
            // We don't want that here, so we just do not register the mapping at all (which properly signals that it is empty)
            return Stream.empty();
        }
        return Stream.of(requestMappingInfo.mutate()
                .paths(newPatterns)
                .build());
    }

    private String restrictPathParams(Map<String, String> paramPatterns, String pattern) {
        for (var entry : paramPatterns.entrySet()) {
            pattern = pattern.replace("{%s}".formatted(entry.getKey()),
                    "{%s:%s}".formatted(entry.getKey(), Pattern.quote(entry.getValue())));
        }
        return pattern;
    }


    public interface PathParameterPatternsSupplier {

        /**
         * A set of path parameters that are configured by this supplier
         */
        Set<String> getPathParameters();

        /**
         * A stream of mappings of path parameter to the value to use for that parameter
         * <p>
         * Each entry in the stream will be considered as a separate set of parameters to configure
         */
        Stream<Map<String, String>> getPathParameterValues(Application application);
    }
}
