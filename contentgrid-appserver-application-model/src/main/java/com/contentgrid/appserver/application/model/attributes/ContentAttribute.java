package com.contentgrid.appserver.application.model.attributes;

import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.attributes.flags.AttributeFlag;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.LinkRel;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;

@Value
public class ContentAttribute implements CompositeAttribute {

    @NonNull
    AttributeName name;

    String description;

    @NonNull
    PathSegmentName pathSegment;

    @NonNull
    LinkRel linkRel;

    Set<AttributeFlag> flags;

    @NonNull
    SimpleAttribute id;

    @NonNull
    SimpleAttribute filename;

    @NonNull
    SimpleAttribute mimetype;

    @NonNull
    SimpleAttribute length;

    @Builder
    ContentAttribute(
            @NonNull AttributeName name,
            String description,
            @Singular Set<AttributeFlag> flags,
            @NonNull PathSegmentName pathSegment,
            @NonNull LinkRel linkRel,
            @NonNull ColumnName idColumn,
            @NonNull ColumnName filenameColumn,
            @NonNull ColumnName mimetypeColumn,
            @NonNull ColumnName lengthColumn
    ) {
        this.name = name;
        this.description = description;
        this.flags = flags;
        this.pathSegment = pathSegment;
        this.linkRel = linkRel;
        this.id = SimpleAttribute.builder().name(AttributeName.of("id")).column(idColumn)
                .type(Type.TEXT).build();
        this.filename = SimpleAttribute.builder().name(AttributeName.of("filename")).column(filenameColumn)
                .type(Type.TEXT).build();
        this.mimetype = SimpleAttribute.builder().name(AttributeName.of("mimetype")).column(mimetypeColumn)
                .type(Type.TEXT).build();
        this.length = SimpleAttribute.builder().name(AttributeName.of("length")).column(lengthColumn)
                .type(Type.LONG).build();

        for (var flag : this.flags) {
            flag.checkSupported(this);
        }
    }

    @Override
    public List<Attribute> getAttributes() {
        return Stream.of(id, filename, mimetype, length).collect(Collectors.toUnmodifiableList());
    }
}
