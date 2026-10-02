package com.contentgrid.appserver.rest.mapping.specialized;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.AnnotatedElementUtils;

/**
 * Converts a method-based {@link SpecializationHandler} to one based on the annotation (on the method or its declaring class)
 * <p>
 * After an initial lookup, the located annotations are cached
 *
 * @param <T> The annotation type that is passed to the delegate handler
 */
@RequiredArgsConstructor
public class AnnotationBasedSpecializationHandlerAdapter<T extends Annotation> implements
        SpecializationHandler<Method> {

    @NonNull
    private final Class<T> annotationClass;

    @NonNull
    private final SpecializationHandler<T> delegate;

    private final Map<Method, Optional<T>> annotationCache = new ConcurrentHashMap<>();

    private Optional<T> getAnnotation(Method method) {
        return annotationCache.computeIfAbsent(method,
                m -> Optional.ofNullable(AnnotatedElementUtils.findMergedAnnotation(m, annotationClass))
                        .or(() -> Optional.ofNullable(
                                AnnotatedElementUtils.findMergedAnnotation(m.getDeclaringClass(), annotationClass))));
    }

    @Override
    public Optional<Specializer> getSpecializerFor(Method method) {
        return getAnnotation(method)
                .flatMap(delegate::getSpecializerFor);
    }
}
