package com.contentgrid.appserver.rest.hal.forms;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.MediaType;

@Configuration(proxyBeanMethods = false)
public class HalFormsMediaTypeConfiguration {

    public List<MediaType> getMediaTypes() {
        return List.of(MediaTypes.HAL_FORMS_JSON);
    }
}
