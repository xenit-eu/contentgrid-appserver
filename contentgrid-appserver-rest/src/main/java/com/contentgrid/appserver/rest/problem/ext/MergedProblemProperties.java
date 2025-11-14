package com.contentgrid.appserver.rest.problem.ext;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.experimental.Delegate;
import org.springframework.hateoas.mediatype.problem.Problem;
import org.springframework.hateoas.mediatype.problem.Problem.ExtendedProblem;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@JsonInclude(Include.NON_EMPTY)
public class MergedProblemProperties {
    private final Object original;
    private final Object extension;

    @JsonUnwrapped
    @JsonInclude(Include.NON_NULL)
    public Object getOriginal() {
        return MapWrapper.wrap(original);
    }

    @JsonUnwrapped
    public Object getExtension() {
        return MapWrapper.wrap(extension);
    }

    public static Problem extend(Problem problem, Object... extensions) {
        var properties = create(problem);
        for (Object extension : extensions) {
            properties = properties.extendedWith(extension);
        }
        return problem.withProperties(properties);
    }

    public static Problem extend(Problem problem, UnaryOperator<MergedProblemProperties> customizer) {
        var properties = create(problem);
        var updatedProperties = customizer.apply(properties);
        if (properties == updatedProperties) {
            return problem;
        }
        return problem.withProperties(updatedProperties);
    }

    public static MergedProblemProperties create(Problem problem) {
        if (problem instanceof ExtendedProblem<?> extendedProblem) {
            return new MergedProblemProperties(extendedProblem.getProperties(), null);
        } else {
            return new MergedProblemProperties(null, null);
        }
    }

    public static MergedProblemProperties createFromExtension(Object extension) {
        return new MergedProblemProperties(extension, null);
    }

    public MergedProblemProperties extendedWith(Object extension) {
        if (this.extension == null) {
            return new MergedProblemProperties(original, extension);
        } else {
            return new MergedProblemProperties(this, extension);
        }
    }

    public <T> Optional<T> findExtension(Class<T> type) {
        if (type.isInstance(extension)) {
            return Optional.of((T) extension);
        } else if (type.isInstance(original)) {
            return Optional.of((T) original);
        } else if (original instanceof MergedProblemProperties mergedProblemProperties) {
            return mergedProblemProperties.findExtension(type);
        } else {
            return Optional.empty();
        }
    }

    /**
     * This wraps a plain {@link Map}, because jackson does not know how to apply {@link JsonUnwrapped} on a map.
     * To flatten all the map key-value pairs into the object, you need {@link JsonAnyGetter} instead, which this class does.
     */
    @Value
    private static class MapWrapper {
        @JsonAnyGetter
        @NonNull
        Map map;

        private static Object wrap(Object object) {
            if(object instanceof Map map) {
                return new MapWrapper(map);
            }
            return object;
        }
    }

}
