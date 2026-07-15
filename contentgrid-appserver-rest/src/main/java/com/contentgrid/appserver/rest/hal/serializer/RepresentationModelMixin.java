package com.contentgrid.appserver.rest.hal.serializer;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.hateoas.Links;
import org.springframework.hateoas.RepresentationModel;
import tools.jackson.databind.annotation.JsonSerialize;

public abstract class RepresentationModelMixin extends RepresentationModel<RepresentationModelMixin> {
    @Override
    @JsonProperty("_links")
    @JsonInclude(Include.NON_EMPTY)
    @JsonSerialize(using=HalModule.HalLinksSerializer.class)
    public abstract Links getLinks();

}
