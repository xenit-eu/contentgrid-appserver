package com.contentgrid.appserver.domain.values;

import lombok.NonNull;
import lombok.Value;

@Value
public class User {
    @NonNull String id;
    String namespace;
    @NonNull String name;
}
