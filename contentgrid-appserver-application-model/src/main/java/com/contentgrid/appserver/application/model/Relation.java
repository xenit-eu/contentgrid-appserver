package com.contentgrid.appserver.application.model;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Value
@Builder
public class Relation {

    @NonNull
    RelationEndPoint source;

    @NonNull
    RelationEndPoint target;

    @Value
    @Builder
    public static class RelationEndPoint {
        String name;

        @NonNull
        Entity entity;
        @NonNull
        Cardinality cardinality;
    }

    public enum Cardinality {
        ONE,
        MANY
    }

    public static class RelationBuilder {
         public RelationBuilder source(RelationEndPoint source) {
             if (source.getName() == null) {
                 throw new IllegalArgumentException("Source endpoint must have a name");
             }
             this.source = source;
             return this;
         }
    }

}
