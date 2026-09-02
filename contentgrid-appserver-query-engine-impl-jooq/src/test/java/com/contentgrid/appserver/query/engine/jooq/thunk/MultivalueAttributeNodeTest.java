package com.contentgrid.appserver.query.engine.jooq.thunk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.MultivalueAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.attributes.flags.ReadOnlyFlag;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.LinkName;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.application.model.values.TableName;
import com.contentgrid.appserver.query.engine.api.exception.InvalidThunkExpressionException;
import com.contentgrid.appserver.query.engine.jooq.JOOQUtils;
import com.contentgrid.thunx.predicates.model.SymbolicReference;
import java.util.List;
import org.junit.jupiter.api.Test;

class MultivalueAttributeNodeTest {

    private static final MultivalueAttribute TAGS = MultivalueAttribute.builder()
            .name(AttributeName.of("tags"))
            .column(ColumnName.of("tags"))
            .itemType(Type.TEXT)
            .build();

    private static final Entity DOCUMENT = Entity.builder()
            .name(EntityName.of("document"))
            .table(TableName.of("document"))
            .pathSegment(PathSegmentName.of("documents"))
            .linkName(LinkName.of("documents"))
            .primaryKey(SimpleAttribute.builder()
                    .name(AttributeName.of("id"))
                    .column(ColumnName.of("id"))
                    .type(Type.UUID)
                    .flag(ReadOnlyFlag.INSTANCE)
                    .build())
            .attribute(TAGS)
            .build();

    private static final Application APPLICATION = Application.builder()
            .name(ApplicationName.of("MultivalueAttributeNodeTest"))
            .entity(DOCUMENT)
            .build();

    @Test
    void symbolicReferenceResolvesToTheAliasedArrayField() {
        var resolver = new JOOQSymbolicReferenceResolver(APPLICATION, DOCUMENT.getName());

        var field = resolver.resolvePath(List.of(SymbolicReference.path("tags")));

        assertEquals(JOOQUtils.resolveField(TableName.of("d0"), TAGS), field);
    }

    @Test
    void pathThroughMultivalueAttributeIsRejected() {
        var resolver = new JOOQSymbolicReferenceResolver(APPLICATION, DOCUMENT.getName());
        var path = List.of(SymbolicReference.path("tags"), SymbolicReference.path("nested"));

        assertThrows(InvalidThunkExpressionException.class, () -> resolver.resolvePath(path));
    }
}
