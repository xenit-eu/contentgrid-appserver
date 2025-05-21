package com.contentgrid.appserver.json.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContentAttribute extends Attribute {
    private String pathSegment;
    private String idColumn;
    private String fileNameColumn;
    private String mimeTypeColumn;
    private String lengthColumn;
}
