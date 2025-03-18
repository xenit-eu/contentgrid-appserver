package com.contentgrid.appserver.application.model;

import com.contentgrid.appserver.application.model.constraints.Constraint;
import java.util.List;
import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;

@Value
@Builder
public class Attribute {

    @NonNull
    String name;

    @NonNull
    String column;

    @NonNull
    Type type;

    @Singular
    List<Constraint> constraints;

    public enum Type {
        LONG,
        DOUBLE,
        BOOLEAN,
        TEXT,
        DATETIME,
        CONTENT,
        AUDIT_METADATA,
        UUID
    }

}
