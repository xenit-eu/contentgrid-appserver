package com.contentgrid.appserver.application.model.attributes;

import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.attributes.flags.AttributeFlag;
import com.contentgrid.appserver.application.model.exceptions.DuplicateElementException;
import com.contentgrid.appserver.application.model.exceptions.InvalidFlagException;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;
import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;

@Value
public class UserAttribute implements Attribute {

    @NonNull
    AttributeName name;

    String description;

    List<AttributeFlag> flags;

    @NonNull
    Attribute id;

    @NonNull
    Attribute namespace;

    @NonNull
    Attribute username;

    @Builder
    UserAttribute(@NonNull AttributeName name, String description, ColumnName columnPrefix,
            @Singular List<AttributeFlag> flags, Attribute id, Attribute namespace, Attribute username) {
        this.name = name;
        this.description = description;
        this.flags = flags;
        columnPrefix = columnPrefix == null ? name.toColumnName().withSuffix("__") : columnPrefix;
        this.id = id == null ? SimpleAttribute.builder().name(AttributeName.of("id"))
                .column(columnPrefix.withSuffix("id")).type(Type.TEXT).build() : id;
        this.namespace = namespace == null ? SimpleAttribute.builder().name(AttributeName.of("namespace"))
                .column(columnPrefix.withSuffix("ns")).type(Type.TEXT).build() : namespace;
        this.username = username == null ? SimpleAttribute.builder().name(AttributeName.of("name"))
                .column(columnPrefix.withSuffix("name")).type(Type.TEXT).build() : username;

        // Check for duplicate attribute names, (duplicate column names are checked on the entity)
        var attributes = new HashSet<AttributeName>();
        for (var attribute : List.of(this.id, this.namespace, this.username)) {
            if (!attributes.add(attribute.getName())) {
                throw new DuplicateElementException("Duplicate attribute named %s".formatted(attribute.getName()));
            }
        }
        for (var flag : this.flags) {
            if (!flag.isSupported(this)) {
                throw new InvalidFlagException("Flag %s is not supported".formatted(flag.getClass().getSimpleName()));
            }
        }
    }

    @Override
    public List<ColumnName> getColumns() {
        return Stream.of(id.getColumns(), namespace.getColumns(), username.getColumns())
                .flatMap(List::stream)
                .toList();
    }
}
