package com.contentgrid.appserver.rest.assembler;

import com.contentgrid.appserver.rest.assembler.JsonViews.HalFormsView;
import com.contentgrid.appserver.rest.hal.forms.HalFormsTemplate;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.hateoas.RepresentationModel;

public class RepresentationModelWithTemplates<T extends RepresentationModelWithTemplates<T>> extends RepresentationModel<T> {

    @JsonIgnore
    private final LinkedHashMap<String, HalFormsTemplate> templates = new LinkedHashMap<>();

    @JsonProperty(value = "_templates")
    @JsonView(value = HalFormsView.class)
    public Map<String, HalFormsTemplate> getTemplates() {
        return Collections.unmodifiableMap(templates);
    }

    public void addTemplate(HalFormsTemplate template) {
        this.templates.putLast(template.getKey(), template);
    }

    public void addTemplates(List<HalFormsTemplate> templates) {
        templates.forEach(this::addTemplate);
    }
}
