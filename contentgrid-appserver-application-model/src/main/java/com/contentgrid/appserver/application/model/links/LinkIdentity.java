package com.contentgrid.appserver.application.model.links;

import java.io.Serializable;
import java.net.URI;
import lombok.NonNull;

public sealed interface LinkIdentity {
    URI rel();

    record UnnamedLink(@NonNull URI rel) implements LinkIdentity, Serializable {

        @Override
        public String toString() {
            return "UnnamedLink(" + rel + ")";
        }
    }

    record NamedLink(@NonNull URI rel, @NonNull String name) implements LinkIdentity, Serializable {

        public NamedLink {
            if (name.isBlank()) {
                throw new IllegalArgumentException(String.format("NamedLink cannot have a Blank name component."));
            }
        }

        @Override
        public String toString() {
            return "NamedLink(" + rel + ", name=" + name + ")";
        }
    }
}
