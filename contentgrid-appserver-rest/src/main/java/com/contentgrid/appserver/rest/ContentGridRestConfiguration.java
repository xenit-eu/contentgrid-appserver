package com.contentgrid.appserver.rest;

import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.query.engine.api.data.EntityData;
import com.contentgrid.appserver.registry.ApplicationNameExtractor;
import com.contentgrid.appserver.registry.ApplicationResolver;
import com.contentgrid.appserver.rest.assembler.profile.BlueprintLinkRelationsConfiguration;
import com.contentgrid.appserver.rest.data.conversion.StringDataEntryToBooleanDataEntryConverter;
import com.contentgrid.appserver.rest.data.conversion.StringDataEntryToDecimalDataEntryConverter;
import com.contentgrid.appserver.rest.data.conversion.StringDataEntryToInstantDataEntryConverter;
import com.contentgrid.appserver.rest.data.conversion.StringDataEntryToLongDataEntryConverter;
import com.contentgrid.appserver.rest.hal.forms.HalFormsMediaTypeConfiguration;
import com.contentgrid.appserver.rest.links.ContentGridLinksConfiguration;
import com.contentgrid.appserver.rest.paging.ItemCountPageMetadata;
import com.contentgrid.appserver.rest.paging.ItemCountPageMetadataOmitLegacyPropertiesMixin;
import com.contentgrid.appserver.rest.problem.ContentgridProblemDetailConfiguration;
import com.contentgrid.hateoas.spring.pagination.PaginationHandlerMethodArgumentResolver;
import com.contentgrid.hateoas.spring.pagination.SlicedResourcesAssembler;
import com.contentgrid.thunx.spring.data.context.AbacContextSupplier;
import com.fasterxml.jackson.databind.DeserializationFeature;
import java.text.ParseException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.convert.ConversionService;
import org.springframework.format.Formatter;
import org.springframework.format.FormatterRegistry;
import org.springframework.hateoas.config.EnableHypermediaSupport;
import org.springframework.hateoas.config.EnableHypermediaSupport.HypermediaType;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
@EnableHypermediaSupport(type = { HypermediaType.HAL })
@Import({ContentgridProblemDetailConfiguration.class, ContentGridLinksConfiguration.class, BlueprintLinkRelationsConfiguration.class, HalFormsMediaTypeConfiguration.class})
public class ContentGridRestConfiguration {
    @Bean
    WebMvcConfigurer contentgridRestWebmvcConfigurer(ApplicationResolver applicationResolver, ApplicationNameExtractor applicationNameExtractor,
            AbacContextSupplier abacContextSupplier, EncodedCursorPaginationHandlerMethodArgumentResolver paginationHandlerMethodArgumentResolver) {
        return new WebMvcConfigurer() {

            @Override
            public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                resolvers.add(new ApplicationArgumentResolver(applicationResolver, applicationNameExtractor));
                resolvers.add(new VersionConstraintArgumentResolver());
                resolvers.add(new PermissionPredicateArgumentResolver(abacContextSupplier));
                resolvers.add(paginationHandlerMethodArgumentResolver);
            }

            @Override
            public void addFormatters(FormatterRegistry registry) {
                if(registry instanceof ConversionService conversionService) {
                    registry.addConverter(new StringDataEntryToBooleanDataEntryConverter(conversionService));
                    registry.addConverter(new StringDataEntryToDecimalDataEntryConverter(conversionService));
                    registry.addConverter(new StringDataEntryToInstantDataEntryConverter(conversionService));
                    registry.addConverter(new StringDataEntryToLongDataEntryConverter(conversionService));
                } else {
                    throw new IllegalStateException("Registry is not a ConversionService");
                }
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
        };
    }

    @Bean
    Jackson2ObjectMapperBuilderCustomizer contentgridRestObjectMapperCustomizer() {
        return builder -> {
            builder.featuresToDisable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
            builder.mixIn(ItemCountPageMetadata.class, ItemCountPageMetadataOmitLegacyPropertiesMixin.class);
        };
    }

    @Bean
    SlicedResourcesAssembler<EntityData> slicedResourcesAssembler(PaginationHandlerMethodArgumentResolver resolver) {
        return new SlicedResourcesAssembler<>(resolver);
    }

    @Bean
    EncodedCursorPaginationHandlerMethodArgumentResolver encodedCursorPaginationHandlerMethodArgumentResolver() {
        return new EncodedCursorPaginationHandlerMethodArgumentResolver();
    }
}
