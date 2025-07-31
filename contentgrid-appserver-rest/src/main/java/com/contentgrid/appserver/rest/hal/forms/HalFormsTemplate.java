package com.contentgrid.appserver.rest.hal.forms;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;
import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

@Value
@Builder
@JsonPropertyOrder({ "title", "method", "target", "contentType", "properties" })
public class HalFormsTemplate {

    public static final String DEFAULT_KEY = "default";

    @NonNull
    @Builder.Default
    @JsonIgnore
    String key = DEFAULT_KEY;

    @JsonInclude(Include.NON_NULL)
    String title;

    @JsonIgnore
    HttpMethod httpMethod;

    @JsonInclude(Include.NON_NULL)
    MediaType contentType;

    @JsonInclude(Include.NON_NULL)
    String target;

    @Singular
    @JsonInclude(Include.NON_EMPTY)
    List<HalFormsProperty> properties;

    @JsonProperty
    @JsonInclude(Include.NON_NULL)
    String getMethod() {
        return this.httpMethod == null ? null : this.httpMethod.name();
    }
}
