package com.contentgrid.appserver.application.model.links;

import java.io.Serializable;
import java.net.URI;
import lombok.NonNull;

public sealed interface LinkIdentity extends Serializable {
    URI rel();

    record UnnamedLink(@NonNull URI rel) implements LinkIdentity {

        @Override
        public String toString() {
            return "UnnamedLink(" + rel + ")";
        }
    }

    record NamedLink(@NonNull URI rel, @NonNull String name) implements LinkIdentity {

        public NamedLink {
            if (name.isBlank()) {
                throw new IllegalArgumentException("NamedLink cannot have a Blank name component.");
            }
            if (name.endsWith("/")) {
                throw new IllegalArgumentException("NamedLink name component cannot have a trailing slash.");
            }
        }

        @Override
        public String toString() {
            return "NamedLink(" + rel + ", name=" + name + ")";
        }
    }
}
