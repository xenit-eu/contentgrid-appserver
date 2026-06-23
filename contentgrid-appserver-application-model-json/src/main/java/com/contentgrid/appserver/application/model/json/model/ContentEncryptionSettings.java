package com.contentgrid.appserver.application.model.json.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ContentEncryptionSettings implements ApplicationSettings {

    private List<String> encryptionEngineAlgorithms;
    private List<String> keyWrapperAlgorithms;
}
