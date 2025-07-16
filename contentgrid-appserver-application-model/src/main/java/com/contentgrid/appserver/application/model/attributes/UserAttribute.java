package com.contentgrid.appserver.application.model.attributes;

import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.attributes.flags.AttributeFlag;
import com.contentgrid.appserver.application.model.attributes.flags.IgnoredFlag;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;

@Value
public class UserAttribute implements CompositeAttribute {

    @NonNull
    AttributeName name;

    String description;

    Set<AttributeFlag> flags;

    @NonNull
    SimpleAttribute id;

    @NonNull
    SimpleAttribute namespace;

    @NonNull
    SimpleAttribute username;

    @Builder
    UserAttribute(@NonNull AttributeName name, String description, @Singular Set<AttributeFlag> flags,
            @NonNull ColumnName idColumn, @NonNull ColumnName namespaceColumn, @NonNull ColumnName usernameColumn) {
        this.name = name;
        this.description = description;
        this.flags = flags;
        this.id = SimpleAttribute.builder().name(AttributeName.of("id")).column(idColumn)
                .type(Type.TEXT)
                .flag(IgnoredFlag.INSTANCE)
                .build();
        this.namespace = SimpleAttribute.builder().name(AttributeName.of("namespace")).column(namespaceColumn)
                .type(Type.TEXT)
                .flag(IgnoredFlag.INSTANCE)
                .build();
        this.username = SimpleAttribute.builder().name(AttributeName.of("name")).column(usernameColumn)
                .type(Type.TEXT).build();

        for (var flag : this.flags) {
            flag.checkSupported(this);
        }
    }

    @Override
    public List<Attribute> getAttributes() {
        return Stream.of(id, namespace, username).collect(Collectors.toUnmodifiableList());
    }
}
