package com.contentgrid.appserver.application.model.values;

import java.io.Serializable;
import lombok.NonNull;
import lombok.Value;

@Value(staticConstructor = "of")
public class EntityName implements Serializable {

    @NonNull
    String value;

    @Override
    public String toString() {
        return getValue();
    }
}
