package com.contentgrid.appserver.rest;

import com.contentgrid.appserver.domain.data.EntityInstance;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.registry.DefaultApplicationNameExtractor;
import com.contentgrid.appserver.rest.assembler.EntityDataRepresentationModelAssembler;
import com.contentgrid.appserver.rest.assembler.profile.BlueprintLinkRelationsConfiguration;
import com.contentgrid.appserver.rest.assembler.profile.hal.ProfileEntityRepresentationModelAssembler;
import com.contentgrid.appserver.rest.automations.ContentGridAutomationsConfiguration;
import com.contentgrid.appserver.rest.converter.RequestInputDataJacksonModule;
import com.contentgrid.appserver.rest.converter.UriListHttpMessageConverter;
import com.contentgrid.appserver.rest.data.conversion.LongDataEntryToDecimalDataEntryConverter;
import com.contentgrid.appserver.rest.data.conversion.StringDataEntryToBooleanDataEntryConverter;
import com.contentgrid.appserver.rest.data.conversion.StringDataEntryToDecimalDataEntryConverter;
import com.contentgrid.appserver.rest.data.conversion.StringDataEntryToInstantDataEntryConverter;
import com.contentgrid.appserver.rest.data.conversion.StringDataEntryToLocalDateDataEntryConverter;
import com.contentgrid.appserver.rest.data.conversion.StringDataEntryToLongDataEntryConverter;
import com.contentgrid.appserver.rest.filter.SingleRangeRequestServletFilter;
import com.contentgrid.appserver.rest.hal.forms.HalFormsMediaTypeConfiguration;
import com.contentgrid.appserver.rest.links.ContentGridLinksConfiguration;
import com.contentgrid.appserver.rest.mapping.ContentGridHandlerMappingConfiguration;
import com.contentgrid.appserver.rest.problem.ContentgridProblemDetailConfiguration;
import com.contentgrid.appserver.rest.property.ContentRestController;
import com.contentgrid.appserver.rest.property.XToManyRelationRestController;
import com.contentgrid.appserver.rest.property.XToOneRelationRestController;
import com.contentgrid.hateoas.spring.pagination.PaginationHandlerMethodArgumentResolver;
import com.contentgrid.hateoas.spring.pagination.SlicedResourcesAssembler;
import com.fasterxml.jackson.databind.DeserializationFeature;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.text.ParseException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.format.Formatter;
import org.springframework.format.FormatterRegistry;
import org.springframework.hateoas.config.EnableHypermediaSupport;
import org.springframework.hateoas.config.EnableHypermediaSupport.HypermediaType;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.support.DefaultHandlerExceptionResolver;

@Configuration(proxyBeanMethods = false)
@EnableHypermediaSupport(type = { HypermediaType.HAL })
@Import({
        ContentgridProblemDetailConfiguration.class,
        ContentGridLinksConfiguration.class,
        BlueprintLinkRelationsConfiguration.class,
        HalFormsMediaTypeConfiguration.class,
        ContentGridHandlerMappingConfiguration.class,
        ContentRestController.class,
        DefaultApplicationNameExtractor.class,
        EntityDataRepresentationModelAssembler.class,
        EntityRestController.class,
        ProfileEntityRepresentationModelAssembler.class,
        ProfileRestController.class,
        RequestInputDataJacksonModule.class,
        RootRestController.class,
        SingleRangeRequestServletFilter.class,
        UriListHttpMessageConverter.class,
        VersionValidator.class,
        XToManyRelationRestController.class,
        XToOneRelationRestController.class,
        ApplicationArgumentResolver.class,
        AuthorizationContextArgumentResolver.class,
        EncodedCursorPaginationHandlerMethodArgumentResolver.class,
        VersionConstraintArgumentResolver.class,
        LinkProviderArgumentResolver.class,
        UserLocalesArgumentResolver.class,
        ContentGridAutomationsConfiguration.class,
})
public class ContentGridRestConfiguration {
    @Bean
    WebMvcConfigurer contentgridRestWebmvcConfigurer(List<HandlerMethodArgumentResolver> customResolvers) {
        return new WebMvcConfigurer() {

            @Override
            public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                resolvers.addAll(customResolvers);
            }

            @Override
            public void addFormatters(FormatterRegistry registry) {
                registry.addConverter(new StringDataEntryToBooleanDataEntryConverter());
                registry.addConverter(new StringDataEntryToDecimalDataEntryConverter());
                registry.addConverter(new StringDataEntryToInstantDataEntryConverter());
                registry.addConverter(new StringDataEntryToLocalDateDataEntryConverter());
                registry.addConverter(new StringDataEntryToLongDataEntryConverter());
                registry.addConverter(new LongDataEntryToDecimalDataEntryConverter());
                registry.addFormatter(new Formatter<EntityId>() {
                    @Override
                    public EntityId parse(String text, Locale locale) throws ParseException {
                        return EntityId.of(UUID.fromString(text));
                    }

                    @Override
                    public String print(EntityId entityId, Locale locale) {
                        return entityId.getValue().toString();
                    }
                });
                registry.addConverter(new VersionConstraintArgumentResolver());
            }

            @Override
            public void extendHandlerExceptionResolvers(List<HandlerExceptionResolver> resolvers) {
                var iterator = resolvers.listIterator();
                while(iterator.hasNext()) {
                    var next = iterator.next();
                    if(next instanceof DefaultHandlerExceptionResolver) {
                        iterator.set(new CustomDefaultHandlerExceptionResolver());
                    }
                }
            }
        };
    }

    @Bean
    Jackson2ObjectMapperBuilderCustomizer contentgridRestObjectMapperCustomizer() {
        return builder -> {
            builder.featuresToDisable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
        };
    }

    @Bean
    SlicedResourcesAssembler<EntityInstance> slicedResourcesAssembler(PaginationHandlerMethodArgumentResolver resolver) {
        return new SlicedResourcesAssembler<>(resolver);
    }

    private static class CustomDefaultHandlerExceptionResolver extends DefaultHandlerExceptionResolver {

        /**
         * Overwrite the default handling for "disconnected client", because this case is not only hit when a client disconnects before/during the request/response.
         * <p>
         * It can also be triggered by this server disconnecting when talking to upstream services (like database, S3, ...)
         * In that case, we certainly don't want to send a 200 OK status code to our client, as that would indicate that
         * everything is OK.
         * <p>
         * To still handle the case when a client is actually disconnected, catch and silence a potential exception during setting the response status to 500 Internal Server Error
         */
        @Override
        protected ModelAndView handleDisconnectedClientException(Exception ex, HttpServletRequest request,
                HttpServletResponse response, Object handler) {
            try {
                sendServerError(ex, request, response);
            } catch (IOException e) {
                // Swallow error, client connection *might* have been closed, so writing the response may fail
            }
            return super.handleDisconnectedClientException(ex, request, response, handler);
        }
    }

}
