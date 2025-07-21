package com.contentgrid.appserver.rest.mapping;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.rest.mapping.ReplacementPathVariablesGenerator.ReplacementPathVariableValues;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Creates specialized request mappings from class-level and method-level {@link org.springframework.web.bind.annotation.RequestMapping}s that are annotated with {@link SpecializedOnPropertyType}
 * <p>
 * The specialization done based on the {@link Application} configuration
 */
@RequiredArgsConstructor
class StaticApplicationRequestMappingHandlerMapping extends RequestMappingHandlerMapping {

    private final Application application;

    private static final Map<Method, Optional<SpecializedOnPropertyType>> annotationsCache = new ConcurrentHashMap<>();

    @Override
    protected void registerHandlerMethod(Object handler, Method method, RequestMappingInfo mapping) {
        lookupPropertyType(method)
                .ifPresent(propertyType -> {
                    var replacementVariablesByEntity = Arrays.stream(propertyType.type())
                            .flatMap(type -> type.replacementPathVariablesGenerator.generateForApplication(application))
                            .collect(Collectors.toMap(
                                    ReplacementPathVariableValues::entityPathSegment,
                                    r -> List.of(r.propertyPathSegment()),
                                    (a, b) -> {
                                        var list = new ArrayList<>(a);
                                        list.addAll(b);
                                        return list;
                                    }
                            ));

                    if (replacementVariablesByEntity.isEmpty()) {
                        // When there are no replacements available at all, do not register the handler method at all
                        // Note: this is not an optimalization; it's necessary for correctness, otherwise a mapping without any patterns would be registered (which matches *EVERYTHING*)
                        return;
                    }

                    String[] specializedPathPatterns;
                    specializedPathPatterns = mapping.getPatternValues().stream()
                            .flatMap(pathPattern -> replacementVariablesByEntity.entrySet().stream()
                                    .map(replacements -> {
                                        var entityReplacedPathPattern = restrictPatternVariable(pathPattern,
                                                propertyType.entityPathVariable(), List.of(replacements.getKey()));
                                        return restrictPatternVariable(entityReplacedPathPattern,
                                                propertyType.propertyPathVariable(), replacements.getValue());
                                    })
                            ).toArray(String[]::new);

                    var specializedMapping = mapping.mutate()
                            .paths(specializedPathPatterns)
                            .build();
                    super.registerHandlerMethod(handler, method, specializedMapping);
                });
    }

    private String restrictPatternVariable(String pattern, String pathVariable, List<PathSegmentName> options) {
        return pattern.replace(
                "{%s}".formatted(pathVariable),
                "{%s:%s}".formatted(pathVariable, options.stream()
                        .map(PathSegmentName::getValue)
                        .map(Pattern::quote)
                        .collect(Collectors.joining("|")))
        );
    }

    static Optional<SpecializedOnPropertyType> lookupPropertyType(Method method) {
        return annotationsCache.computeIfAbsent(method, m -> {
            var propertyType = AnnotatedElementUtils.findMergedAnnotation(m, SpecializedOnPropertyType.class);
            if (propertyType == null) {
                propertyType = AnnotatedElementUtils.findMergedAnnotation(m.getDeclaringClass(),
                        SpecializedOnPropertyType.class);
            }
            return Optional.ofNullable(propertyType);
        });
    }

    static void validateSpecializedMapping(RequestMappingInfo mapping, SpecializedOnPropertyType propertyType) {
        checkMappingContainsVariable(mapping, propertyType.entityPathVariable());
        checkMappingContainsVariable(mapping, propertyType.propertyPathVariable());
    }

    private static void checkMappingContainsVariable(RequestMappingInfo mapping, String variable) {
        mapping.getPatternValues().forEach(pathPattern -> {
            if (!pathPattern.contains('{' + variable + '}')) {
                throw new IllegalStateException(
                        "Can not specialize mapping %s: Pattern '%s' is missing path variable '%s'".formatted(
                                mapping, pathPattern, variable
                        )
                );
            }
        });
    }

}
