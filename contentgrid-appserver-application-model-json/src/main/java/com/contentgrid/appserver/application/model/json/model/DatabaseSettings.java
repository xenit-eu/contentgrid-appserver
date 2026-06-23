package com.contentgrid.appserver.application.model.json.model;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

@Getter
@Setter
public final class DatabaseSettings {

    @NonNull
    private String schema;
}
