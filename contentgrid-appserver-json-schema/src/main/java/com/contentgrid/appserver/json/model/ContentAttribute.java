package com.contentgrid.appserver.json.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"type", "name", "description", "pathSegment", "idColumn", "fileNameColumn", "mimeTypeColumn", "lengthColumn", "flags"})
public final class ContentAttribute extends Attribute {

    @NonNull
    private String pathSegment;

    @NonNull
    private String idColumn;

    @NonNull
    private String fileNameColumn;

    @NonNull
    private String mimeTypeColumn;

    @NonNull
    private String lengthColumn;
}
