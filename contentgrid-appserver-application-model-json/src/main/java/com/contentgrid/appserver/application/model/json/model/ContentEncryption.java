package com.contentgrid.appserver.application.model.json.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContentEncryption {

    private boolean enabled;
    private List<String> encryptionEngineAlgorithms;
    private List<String> keyWrapperAlgorithms;
}
