package com.contentgrid.appserver.application.model.values;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.Value;

@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class LinkRel {

    String curie;

    @NonNull
    String rel;

    public static LinkRel of(String curie, @NonNull String rel) {
        if (curie != null && curie.contains(":")) {
            throw new IllegalArgumentException("curie cannot contain ':'");
        }
        if (rel.contains(":")) {
            throw new IllegalArgumentException("rel cannot contain ':'");
        }
        return new LinkRel(curie, rel);
    }

    public static LinkRel parse(@NonNull String value) {
        var colonIndex = value.indexOf(":");
        if (colonIndex == -1) {
            return of(null, value);
        }
        var curie = value.substring(0, colonIndex);
        var rel = value.substring(colonIndex + 1);
        return of(curie, rel);
    }

    public String getValue() {
        if (curie != null) {
            return getCurie() + ":" + getRel();
        } else {
            return getRel();
        }
    }

    @Override
    public String toString() {
        return getValue();
    }
}
