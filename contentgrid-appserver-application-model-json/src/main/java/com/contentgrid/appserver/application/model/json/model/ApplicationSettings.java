package com.contentgrid.appserver.application.model.json.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = ContentEncryptionSettings.class, name = "contentEncryption"),
})
public sealed interface ApplicationSettings permits ContentEncryptionSettings {

}
