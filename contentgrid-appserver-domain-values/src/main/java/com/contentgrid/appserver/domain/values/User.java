package com.contentgrid.appserver.domain.values;

import lombok.Value;

@Value
public class User {
    String id;
    String namespace;
    String name;
}
