package com.contentgrid.appserver.application.model.relations;

import com.contentgrid.appserver.application.model.Entity;
import java.util.Objects;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Value;

@Getter
public abstract class Relation {

    protected Relation(@NonNull RelationEndPoint source, @NonNull RelationEndPoint target) {
        if (source.getName() == null) {
            throw new IllegalArgumentException("Source endpoint must have a name");
        }
        if (source.getEntity().getName().equals(target.getEntity().getName())
                && Objects.equals(source.getName(), target.getName())) {
            throw new IllegalArgumentException("Source and target must have a different name when on the same entity");
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
