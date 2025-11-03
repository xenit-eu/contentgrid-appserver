package com.contentgrid.appserver.domain.events;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.domain.data.EntityInstance;
import com.fasterxml.jackson.databind.JsonNode;

public interface EntityFormatter {
    JsonNode format(Application application, EntityInstance entityInstance);
}
