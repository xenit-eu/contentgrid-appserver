package com.contentgrid.appserver.rest.hal.forms;

import com.contentgrid.appserver.rest.hal.forms.JsonViews.DefaultView;
import com.contentgrid.appserver.rest.hal.forms.JsonViews.HalFormsView;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.config.HypermediaMappingInformation;
import org.springframework.hateoas.mediatype.hal.HalMediaTypeConfiguration;
import org.springframework.http.MediaType;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class HalFormsMediaTypeConfiguration implements HypermediaMappingInformation {

    private final HalMediaTypeConfiguration halMediaTypeConfiguration;

    @Override
    public List<MediaType> getMediaTypes() {
        return Collections.singletonList(MediaTypes.HAL_FORMS_JSON);
    }

    @Override
    public JsonMapper.Builder configureJsonMapper(JsonMapper.Builder mapper) {
        mapper = halMediaTypeConfiguration.configureJsonMapper(mapper);
        addView(mapper, HalFormsView.class);

        return mapper;
    }

    /**
     * Bean that customizes the {@link JsonMapper} to only expose properties with the {@link DefaultView}.
     * Properties with a different view will be ignored.
     */
    @Bean
    public JsonMapperBuilderCustomizer defaultViewJsonMapperCustomizer() {
        return builder -> addView(builder, DefaultView.class);
    }

    private void addView(JsonMapper.Builder jsonMapperBuilder, Class<?> view) {
        jsonMapperBuilder
                .defaultSerializationView(view)
                .enable(MapperFeature.DEFAULT_VIEW_INCLUSION);
    }
}
