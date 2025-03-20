package com.contentgrid.appserver.application.model.attributes;

import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import java.util.List;
import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;

@Value
@Builder
public class CompositeAttribute implements Attribute {

    @NonNull
    AttributeName name;

    @Singular
    List<Attribute> attributes;

    @Override
    public List<ColumnName> getColumns() {
        return attributes.stream()
                .map(Attribute::getColumns)
                .flatMap(List::stream)
                .toList();
    }
}
