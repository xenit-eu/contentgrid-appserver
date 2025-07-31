package com.contentgrid.appserver.rest.hal.forms;

import com.contentgrid.appserver.rest.assembler.JsonViews.DefaultView;
import com.contentgrid.appserver.rest.assembler.JsonViews.HalFormsView;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.client.LinkDiscoverer;
import org.springframework.hateoas.config.HypermediaMappingInformation;
import org.springframework.hateoas.mediatype.MediaTypeConfigurationCustomizer;
import org.springframework.hateoas.mediatype.MediaTypeConfigurationFactory;
import org.springframework.hateoas.mediatype.MessageResolver;
import org.springframework.hateoas.mediatype.hal.CurieProvider;
import org.springframework.hateoas.mediatype.hal.HalConfiguration;
import org.springframework.hateoas.mediatype.hal.Jackson2HalModule;
import org.springframework.hateoas.mediatype.hal.Jackson2HalModule.HalHandlerInstantiator;
import org.springframework.hateoas.mediatype.hal.forms.HalFormsLinkDiscoverer;
import org.springframework.hateoas.server.core.DelegatingLinkRelationProvider;
import org.springframework.hateoas.server.mvc.JacksonSerializers.MediaTypeDeserializer;
import org.springframework.http.MediaType;

@Configuration(proxyBeanMethods = false)
public class HalFormsMediaTypeConfiguration implements HypermediaMappingInformation {

    private final DelegatingLinkRelationProvider relProvider;
    private final ObjectProvider<CurieProvider> curieProvider;
    private final MediaTypeConfigurationFactory<HalConfiguration, ? extends MediaTypeConfigurationCustomizer<HalConfiguration>> configurationFactory;
    private final MessageResolver resolver;
    private final AbstractAutowireCapableBeanFactory beanFactory;

    HalFormsMediaTypeConfiguration(
            DelegatingLinkRelationProvider relProvider,
            ObjectProvider<CurieProvider> curieProvider,
            ObjectProvider<HalConfiguration> halConfiguration,
            ObjectProvider<MediaTypeConfigurationCustomizer<HalConfiguration>> halCustomizers,
            MessageResolver resolver, AbstractAutowireCapableBeanFactory beanFactory
    ) {
        this.relProvider = relProvider;
        this.curieProvider = curieProvider;
        this.configurationFactory = new MediaTypeConfigurationFactory<>(
                () -> halConfiguration.getIfAvailable(HalConfiguration::new), halCustomizers);
        this.resolver = resolver;
        this.beanFactory = beanFactory;
    }

    @Override
    public List<MediaType> getMediaTypes() {
        return Collections.singletonList(MediaTypes.HAL_FORMS_JSON);
    }

    @Override
    public ObjectMapper configureObjectMapper(ObjectMapper mapper) {
        HalConfiguration halConfig = configurationFactory.getConfiguration();
        CurieProvider provider = curieProvider.getIfAvailable(() -> CurieProvider.NONE);

        addView(mapper, HalFormsView.class);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.registerModule(getHalFormsModule());
        mapper.setHandlerInstantiator(
                new HalHandlerInstantiator(relProvider, provider, resolver, halConfig, beanFactory));

        halConfig.customize(mapper);

        return mapper;
    }

    @Bean
    public LinkDiscoverer halFormsLinkDiscoverer() {
        return new HalFormsLinkDiscoverer();
    }

    @Bean
    public HalFormsPropertyContributor halFormsPropertyContributor() {
        return new HalFormsPropertyContributor();
    }

    @Bean
    public HalFormsTemplateGenerator halFormsTemplateGenerator(HalFormsPropertyContributor halFormsPropertyContributor) {
        return new HalFormsTemplateGenerator(halFormsPropertyContributor);
    }

    /**
     * Bean that customizes the {@link ObjectMapper} to only expose properties with the {@link DefaultView}.
     * Properties with a different view will be ignored.
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer defaultViewObjectMapperBuilderCustomizer() {
        return builder -> builder.postConfigurer(objectMapper -> addView(objectMapper, DefaultView.class));
    }

    private void addView(ObjectMapper objectMapper, Class<?> view) {
        var serializationConfig = objectMapper.getSerializationConfig();
        serializationConfig = serializationConfig.withView(view);
        serializationConfig = serializationConfig.with(MapperFeature.DEFAULT_VIEW_INCLUSION);
        objectMapper.setConfig(serializationConfig);
    }

    private SimpleModule getHalFormsModule() {
        var result = new Jackson2HalModule();
        result.setMixInAnnotation(MediaType.class, MediaTypeMixin.class);
        return result;
    }

    @JsonSerialize(using = ToStringSerializer.class)
    @JsonDeserialize(using = MediaTypeDeserializer.class)
    interface MediaTypeMixin {}
}
