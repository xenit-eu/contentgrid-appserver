package com.contentgrid.appserver.rest;

import com.contentgrid.appserver.rest.data.conversion.StringDataEntryToBooleanDataEntryConverter;
import com.contentgrid.appserver.rest.data.conversion.StringDataEntryToDecimalDataEntryConverter;
import com.contentgrid.appserver.rest.data.conversion.StringDataEntryToInstantDataEntryConverter;
import com.contentgrid.appserver.rest.data.conversion.StringDataEntryToLongDataEntryConverter;
import com.fasterxml.jackson.databind.DeserializationFeature;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.ConversionService;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class ContentGridRestConfiguration {
    @Bean
    WebMvcConfigurer contentgridRestWebmvcConfigurer() {
        return new WebMvcConfigurer() {
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
            }
        };
    }

    @Bean
    Jackson2ObjectMapperBuilderCustomizer contentgridRestObjectMapperCustomizer() {
        return builder -> {
            builder.featuresToDisable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
        };
    }

}
