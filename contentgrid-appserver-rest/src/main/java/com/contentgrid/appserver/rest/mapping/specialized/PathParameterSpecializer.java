package com.contentgrid.appserver.rest.mapping.specialized;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.rest.mapping.specialized.SpecializationHandler.Specializer;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.servlet.HandlerMapping;
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

    @Override
    public void handleMatch(Application application, HttpServletRequest request) {
        var multiSegmentParameters = pathParameterPatternsSupplier.getPathParameters()
                .stream()
                .filter(PathParameterSpecializer::isMultiSegmentParam)
                .toList();

        var uriParams = new HashMap<>((Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE));
        for (var pathParameter : multiSegmentParameters) {
            var parts = new ArrayList<String>();
            for (int i = 0; ; i++) {
                var segmentParam = createSegmentParamName(pathParameter, i);
                if (!uriParams.containsKey(segmentParam)) {
                    // This segment does not exist. Stop gathering segments
                    break;
                }
                parts.add(uriParams.remove(segmentParam));
            }
            // Spring includes the leading slash in the value of a multi-segment parameter
            uriParams.put(stripMultiSegmentPrefix(pathParameter), "/" + String.join("/", parts));
        }

        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Collections.unmodifiableMap(uriParams));
    }

    private String restrictPathParams(Map<String, String> paramPatterns, String pattern) {
        for (var entry : paramPatterns.entrySet()) {
            String paramName = entry.getKey();
            String paramValue = entry.getValue();
            pattern = pattern.replace(
                    "{%s}".formatted(paramName),
                    isMultiSegmentParam(paramName)
                            ? createSegments(paramName, paramValue.split("/"))
                            : "{%s:%s}".formatted(paramName, Pattern.quote(paramValue))
            );
        }
        return pattern;
    }

    /**
     * A multi-segment parameter ({@code {*name}}) can match multiple path segments, but spring does not allow it to
     * have a pattern. So instead, it is split up in a separate parameter per segment, which are joined back together
     * in {@link #handleMatch(Application, HttpServletRequest)}
     */
    private static boolean isMultiSegmentParam(String paramName) {
        return paramName.startsWith("*");
    }

    private static String stripMultiSegmentPrefix(String paramName) {
        return isMultiSegmentParam(paramName)?paramName.substring(1):paramName;
    }

    private static String createSegments(String paramName, String[] parts) {
        return IntStream.range(0, parts.length)
                .mapToObj(i -> "{%s:%s}".formatted(createSegmentParamName(paramName, i), Pattern.quote(parts[i])))
                .collect(Collectors.joining("/"));
    }

    private static String createSegmentParamName(String paramName, int i) {
        return "_%s__%d".formatted(stripMultiSegmentPrefix(paramName), i);
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
