package com.contentgrid.appserver.application.model.attributes;

import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.attributes.flags.AttributeFlag;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;

@Value
public class ContentAttribute implements Attribute {

    @NonNull
    AttributeName name;

    String description;

    Set<AttributeFlag> flags;

    @NonNull
    Attribute id;

    @NonNull
    Attribute filename;

    @NonNull
    Attribute mimetype;

    @NonNull
    Attribute length;

    @Builder
    ContentAttribute(@NonNull AttributeName name, String description, @Singular Set<AttributeFlag> flags,
            @NonNull ColumnName idColumn, @NonNull ColumnName filenameColumn, @NonNull ColumnName mimetypeColumn,
            @NonNull ColumnName lengthColumn) {
        this.name = name;
        this.description = description;
        this.flags = flags;
        this.id = SimpleAttribute.builder().name(AttributeName.of("id")).column(idColumn)
                .type(Type.TEXT).build();
        this.filename = SimpleAttribute.builder().name(AttributeName.of("filename")).column(filenameColumn)
                .type(Type.TEXT).build();
        this.mimetype  = SimpleAttribute.builder().name(AttributeName.of("mimetype")).column(mimetypeColumn)
                .type(Type.TEXT).build();
        this.length  = SimpleAttribute.builder().name(AttributeName.of("length")).column(lengthColumn)
                .type(Type.LONG).build();

        for (var flag : this.flags) {
            flag.checkSupported(this);
        }
    }

    @Override
    public List<ColumnName> getColumns() {
        return Stream.of(id.getColumns(), filename.getColumns(), mimetype.getColumns(), length.getColumns())
                .flatMap(List::stream)
                .toList();
    }
}
