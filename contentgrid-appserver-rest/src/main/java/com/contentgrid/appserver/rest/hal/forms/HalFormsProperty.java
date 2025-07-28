package com.contentgrid.appserver.rest.hal.forms;

import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.With;
import org.springframework.hateoas.mediatype.hal.forms.HalFormsOptions;
import org.springframework.hateoas.mediatype.html.HtmlInputType;

@With
@Value
@JsonInclude(Include.NON_DEFAULT)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class HalFormsProperty {

    String name;
    String prompt;
    boolean readOnly;
    boolean required;
    String regex;
    Object value;
    boolean templated;
    Integer cols, rows;
    Number min, max, step;
    Integer minLength, maxLength;
    String type;
    String placeholder;
    HalFormsOptions options;

    public static HalFormsProperty named(String name) {
        return new HalFormsProperty(name, null, false, false, null, null, false, null, null, null, null, null, null, null, null, null, null);
    }

    public HalFormsProperty withAttributeType(SimpleAttribute.Type type) {
        var inputType = switch (type) {
            case TEXT, UUID -> HtmlInputType.TEXT_VALUE;
            case LONG, DOUBLE -> HtmlInputType.NUMBER_VALUE;
            case BOOLEAN -> HtmlInputType.CHECKBOX_VALUE;
            case DATETIME -> "datetime";
        };
        return withType(inputType);
    }
}
