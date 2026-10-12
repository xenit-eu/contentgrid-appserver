package com.contentgrid.appserver.application.model.links;

import java.net.URI;
import lombok.NonNull;

public sealed interface LinkIdentity {
    URI rel();

    default String getRelKey() {
        return "";
    }

    record UnnamedLink(@NonNull URI rel) implements LinkIdentity {

        @Override
        public String toString() {
            return "UnnamedLink(" + rel + ")";
        }
    }

    record NamedLink(@NonNull URI rel, @NonNull String name) implements LinkIdentity {

        @Override
        public String toString() {
            return "NamedLink(" + rel + ", name=" + name + ")";
        }
    }

    record OwnedLink(@NonNull URI rel, @NonNull String property) implements LinkIdentity {
        @Override
        public String toString() { return "OwnedLink(" + property + ", " + rel + ")"; }
    }
}
