package com.contentgrid.appserver.rest.mapping;

import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.registry.ApplicationNameExtractor;
import com.contentgrid.appserver.registry.ApplicationResolver;
import com.contentgrid.appserver.rest.EntityRestController;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.PathMatcher;
import org.springframework.util.StringValueResolver;
import org.springframework.web.accept.ContentNegotiationManager;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.CorsProcessor;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.handler.HandlerMethodMappingNamingStrategy;
import org.springframework.web.servlet.handler.RequestMatchResult;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.UrlPathHelper;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * Dispatches {@link SpecializedOnPropertyType} request mappings to an application-specific {@link StaticApplicationRequestMappingHandlerMapping}
 * The non-specialized request mappings are kept here. These are only called upon when no specialized mappings are matched.
 * <p>
 * To be able to check the {@link SpecializedOnPropertyType} path variables on startup, that check is also performed here
 */
@RequiredArgsConstructor
@Slf4j
public class DynamicDispatchApplicationHandlerMapping extends RequestMappingHandlerMapping {

    private final ApplicationResolver applicationResolver;
    private final ApplicationNameExtractor applicationNameExtractor;

    /* This has to be concurrency-safe, because it is written to (and read from) from multiple concurrent HTTP threads.
        TODO: When multiple applications are served by one application, have a way to clean up mappings for applications that have been removed
     */
    private final ConcurrentMap<ApplicationName, StaticApplicationRequestMappingHandlerMapping> delegateHandlerMappings = new ConcurrentHashMap<>();

    /* This does not have to be a concurrency-safe list, because configurers are only added before the request mapping is initialized.
        Afterwards, the configurers are only read concurrently from multiple threads, they are no longer written to at all
     */

    private final List<Consumer<RequestMappingHandlerMapping>> configurers = new ArrayList<>();

    private void addConfigurer(Consumer<RequestMappingHandlerMapping> configurer) {
        /* Some setters are called from within the constructor of a parent class.
            In that case, configurers is not initialized yet.
            We don't need to store that configurer at all, because it will also be called
            during construction of the delegate mapping (StaticApplicationRequestMappingHandlerMapping).
         */
        if (configurers != null) {
            configurers.add(configurer);
        }
    }

    private StaticApplicationRequestMappingHandlerMapping resolveHandlerMapping(HttpServletRequest request) {
        var applicationName = applicationNameExtractor.extract(request);

        return delegateHandlerMappings.computeIfAbsent(applicationName, name -> {
            var application = applicationResolver.resolve(name);
            var mapping = new StaticApplicationRequestMappingHandlerMapping(application);
            mapping.setApplicationContext(obtainApplicationContext());
            mapping.setServletContext(getServletContext());

            for (var configurer : configurers) {
                configurer.accept(mapping);
            }
            mapping.afterPropertiesSet();
            log.info("Created delegate HandlerMapping for application {}", name);
            return mapping;
        });
    }

    @Override
    public void setEmbeddedValueResolver(StringValueResolver resolver) {
        super.setEmbeddedValueResolver(resolver);
        addConfigurer(m -> m.setEmbeddedValueResolver(resolver));
    }

    @Override
    public void setContentNegotiationManager(ContentNegotiationManager contentNegotiationManager) {
        super.setContentNegotiationManager(contentNegotiationManager);
        addConfigurer(m -> m.setContentNegotiationManager(contentNegotiationManager));
    }

    @Override
    public void setPathMatcher(PathMatcher pathMatcher) {
        super.setPathMatcher(pathMatcher);
        addConfigurer(m -> m.setPathMatcher(pathMatcher));
    }

    @Override
    public void setPatternParser(PathPatternParser patternParser) {
        super.setPatternParser(patternParser);
        addConfigurer(m -> m.setPatternParser(patternParser));
    }

    @Override
    public void setUrlPathHelper(UrlPathHelper urlPathHelper) {
        super.setUrlPathHelper(urlPathHelper);
        addConfigurer(m -> m.setUrlPathHelper(urlPathHelper));
    }

    @Override
    public void setPathPrefixes(Map<String, Predicate<Class<?>>> prefixes) {
        super.setPathPrefixes(prefixes);
        addConfigurer(m -> m.setPathPrefixes(prefixes));
    }

    @Override
    public void setCorsConfigurations(Map<String, CorsConfiguration> corsConfigurations) {
        super.setCorsConfigurations(corsConfigurations);
        addConfigurer(m -> m.setCorsConfigurations(corsConfigurations));
    }

    @Override
    public void setCorsConfigurationSource(CorsConfigurationSource source) {
        super.setCorsConfigurationSource(source);
        addConfigurer(m -> m.setCorsConfigurationSource(source));
    }

    @Override
    public void setCorsProcessor(CorsProcessor corsProcessor) {
        super.setCorsProcessor(corsProcessor);
        addConfigurer(m -> m.setCorsProcessor(corsProcessor));
    }

    @Override
    public void setHandlerMethodMappingNamingStrategy(
            HandlerMethodMappingNamingStrategy<RequestMappingInfo> namingStrategy) {
        super.setHandlerMethodMappingNamingStrategy(namingStrategy);
        addConfigurer(m -> m.setHandlerMethodMappingNamingStrategy(namingStrategy));
    }

    @Override
    public void setDetectHandlerMethodsInAncestorContexts(boolean detectHandlerMethodsInAncestorContexts) {
        super.setDetectHandlerMethodsInAncestorContexts(detectHandlerMethodsInAncestorContexts);
        addConfigurer(m -> m.setDetectHandlerMethodsInAncestorContexts(detectHandlerMethodsInAncestorContexts));
    }

    @Override
    protected void registerHandlerMethod(Object handler, Method method, RequestMappingInfo mapping) {
        StaticApplicationRequestMappingHandlerMapping.lookupPropertyType(method)
                .ifPresentOrElse(
                        propertyType -> StaticApplicationRequestMappingHandlerMapping.validateSpecializedMapping(
                                mapping, propertyType),
                        () -> super.registerHandlerMethod(handler, method, mapping)
                );
    }

    @Override
    protected HandlerMethod getHandlerInternal(HttpServletRequest request) throws Exception {
        var handler = resolveHandlerMapping(request).getHandlerInternal(request);
        if (handler != null) {
            return handler;
        }
        return super.getHandlerInternal(request);
    }

    @Override
    protected HandlerMethod lookupHandlerMethod(String lookupPath, HttpServletRequest request) throws Exception {
        HandlerMethod handlerMethod = super.lookupHandlerMethod(lookupPath, request);

        if (handlerMethod == null) {
            return null;
        }

        // If the handler belongs to our EntityRestController, check whether the first path segment matches a known
        // entity for the resolved application. Otherwise let it fall back (return null) to other handler mappings,
        // so things like /openapi.yml can work.
        if (handlerMethod.getBeanType().equals(EntityRestController.class)) {
            var firstSegment = extractFirstPathSegment(lookupPath);

            if (firstSegment != null) {
                var applicationName = applicationNameExtractor.extract(request);
                var application = applicationResolver.resolve(applicationName);
                var maybeEntity = application.getEntityByPathSegment(PathSegmentName.of(firstSegment));
                if (maybeEntity.isEmpty()) {
                    return null;
                }
            }
        }

        return handlerMethod;
    }

    private String extractFirstPathSegment(String lookupPath) {
        if (lookupPath == null || lookupPath.isEmpty() || lookupPath.equals("/")) {
            return null;
        }

        String path = lookupPath.startsWith("/") ? lookupPath.substring(1) : lookupPath;
        int index = path.indexOf('/');
        return index == -1 ? path : path.substring(0, index);
    }

    @Override
    public RequestMatchResult match(HttpServletRequest request, String pattern) {
        var match = resolveHandlerMapping(request).match(request, pattern);
        if (match != null) {
            return match;
        }
        return super.match(request, pattern);
    }
}
