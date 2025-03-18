package com.contentgrid.appserver.application.model.constraints;

import java.util.List;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

@Value
@Builder
public class AllowedValuesConstraint implements Constraint {

    @Singular
    List<String> allowedValues;

}
