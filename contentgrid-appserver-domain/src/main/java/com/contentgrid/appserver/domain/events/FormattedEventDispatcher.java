package com.contentgrid.appserver.domain.events;

import com.fasterxml.jackson.databind.JsonNode;

public interface FormattedEventDispatcher {
    void dispatchCreate(String entity, JsonNode newData);
    void dispatchUpdate(String entity, JsonNode oldData, JsonNode newData);
    void dispatchDelete(String entity, JsonNode oldData);
}
