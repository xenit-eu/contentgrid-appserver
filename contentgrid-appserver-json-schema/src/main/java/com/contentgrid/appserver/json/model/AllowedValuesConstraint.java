package com.contentgrid.appserver.json.model;

import lombok.Getter;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class AllowedValuesConstraint implements AttributeConstraint {
    private List<String> values;
}
