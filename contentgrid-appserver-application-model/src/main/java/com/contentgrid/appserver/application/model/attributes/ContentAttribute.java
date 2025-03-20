package com.contentgrid.appserver.application.model.attributes;

import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import java.util.List;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Value
@Builder
public class ContentAttribute implements Attribute {

    @NonNull
    AttributeName name;

    @NonNull
    ColumnName filenameColumn;

    @NonNull
    ColumnName mimetypeColumn;

    @NonNull
    ColumnName lengthColumn;

    @Override
    public List<ColumnName> getColumns() {
        return List.of(filenameColumn, mimetypeColumn, lengthColumn);
    }
}
