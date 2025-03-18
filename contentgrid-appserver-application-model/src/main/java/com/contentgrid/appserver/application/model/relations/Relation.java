package com.contentgrid.appserver.application.model.relations;

import com.contentgrid.appserver.application.model.Entity;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Value;

@Getter
public abstract class Relation {

    Relation(@NonNull RelationEndPoint source, @NonNull RelationEndPoint target) {
        if (source.getName() == null) {
            throw new IllegalArgumentException("Source endpoint must have a name");
        }
        this.source = source;
        this.target = target;
    }

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
    }


}
