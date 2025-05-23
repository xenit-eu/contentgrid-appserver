package com.contentgrid.appserver.json.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"type", "name", "description", "pathSegment", "idColumn", "fileNameColumn", "mimeTypeColumn", "lengthColumn", "flags"})
public final class ContentAttribute extends Attribute {
    private String pathSegment;
    private String idColumn;
    private String fileNameColumn;
    private String mimeTypeColumn;
    private String lengthColumn;
}
