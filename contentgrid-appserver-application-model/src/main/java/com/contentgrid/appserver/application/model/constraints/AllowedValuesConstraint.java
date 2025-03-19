package com.contentgrid.appserver.application.model.constraints;

import java.util.List;
import java.util.Set;
import lombok.NonNull;
import lombok.Value;

@Value
public class AllowedValuesConstraint implements Constraint {

    @NonNull
    List<String> values;

    AllowedValuesConstraint(@NonNull List<String> values) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Values can not be empty");
        }
        if (values.stream().anyMatch(value -> value == null || value.isEmpty())) {
            throw new IllegalArgumentException("All values should be non-null and non-empty");
        }
        if (Set.copyOf(values).size() < values.size()) {
            throw new IllegalArgumentException("All values should be unique");
        }
        this.values = values;
    }

}
