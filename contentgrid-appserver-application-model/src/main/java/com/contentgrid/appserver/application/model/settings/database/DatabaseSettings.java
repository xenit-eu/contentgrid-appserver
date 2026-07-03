package com.contentgrid.appserver.application.model.settings.database;

import com.contentgrid.appserver.application.model.values.SchemaName;
import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class DatabaseSettings {

    SchemaName schema;
}
