package com.contentgrid.appserver.rest.mapping.specialized;

import com.contentgrid.appserver.application.model.Application;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

/**
 * Performs <em>specialization</em> of a {@link RequestMapping#path()} parameters.
 * <p>
 * Specialization of a parameter means restricting its matching to only specific values,
 * by making use of a pattern that restricts the allowed values for the parameter.
 * @param <T> The type of the object to use to look up the specializer
 */
public interface SpecializationHandler<T> {

    /**
     * Obtains the specializer for a lookup object
     *
     * @param lookup The object for looking up the specializer
     * @return A specializer if the request mapping should be specialized
     */
    Optional<Specializer> getSpecializerFor(T lookup);

    /**
     * Specializes a {@link RequestMappingInfo}
     */
    interface Specializer {

        /**
         * Validates that the mapping info contains all the required information to perform the specialization
         * @param requestMappingInfo The mapping info to validate
         */
        void validate(RequestMappingInfo requestMappingInfo);

        /**
         * Specializes the mapping info for a particular application
         * @param application The application to base the specializations on
         * @param requestMappingInfo The original mapping info to specialize
         * @return A stream of replacement mapping infos after they have been specialized
         */
        Stream<RequestMappingInfo> specialize(Application application, RequestMappingInfo requestMappingInfo);
    }

}
