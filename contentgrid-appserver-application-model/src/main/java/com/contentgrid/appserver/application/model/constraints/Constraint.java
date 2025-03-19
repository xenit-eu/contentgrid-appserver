package com.contentgrid.appserver.application.model.constraints;

import java.util.List;

public sealed interface Constraint permits AllowedValuesConstraint, RequiredConstraint, UniqueConstraint {

    static RequiredConstraint required() {
        return new RequiredConstraint();
    }

    static UniqueConstraint unique() {
        return new UniqueConstraint();
    }

    static AllowedValuesConstraint allowedValues(List<String> values) {
        return new AllowedValuesConstraint(values);
    }

}
