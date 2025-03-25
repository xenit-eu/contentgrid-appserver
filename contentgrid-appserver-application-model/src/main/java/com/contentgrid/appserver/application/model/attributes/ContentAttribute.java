package com.contentgrid.appserver.application.model.attributes;

import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.attributes.flags.AttributeFlag;
import com.contentgrid.appserver.application.model.exceptions.DuplicateElementException;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import java.util.HashSet;
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
    ContentAttribute(@NonNull AttributeName name, String description, ColumnName columnPrefix,
            @Singular Set<AttributeFlag> flags, Attribute id, Attribute filename, Attribute mimetype, Attribute length) {
        this.name = name;
        this.description = description;
        this.flags = flags;
        columnPrefix = columnPrefix == null ? name.toColumnName().withSuffix("__") : columnPrefix;
        this.id = id == null ? SimpleAttribute.builder().name(AttributeName.of("id"))
                .column(columnPrefix.withSuffix("id")).type(Type.TEXT).build() : id;
        this.filename = filename == null ? SimpleAttribute.builder().name(AttributeName.of("filename"))
                .column(columnPrefix.withSuffix("filename")).type(Type.TEXT).build() : filename;
        this.mimetype  = mimetype == null ? SimpleAttribute.builder().name(AttributeName.of("mimetype"))
                .column(columnPrefix.withSuffix("mimetype")).type(Type.TEXT).build() : mimetype;
        this.length  = length == null ? SimpleAttribute.builder().name(AttributeName.of("length"))
                .column(columnPrefix.withSuffix("length")).type(Type.LONG).build() : length;

        // Check for duplicate attribute names, (duplicate column names are checked on the entity)
        var attributes = new HashSet<AttributeName>();
        for (var attribute : List.of(this.id, this.filename, this.mimetype, this.length)) {
            if (!attributes.add(attribute.getName())) {
                throw new DuplicateElementException("Duplicate attribute named %s".formatted(attribute.getName()));
            }
        }
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
