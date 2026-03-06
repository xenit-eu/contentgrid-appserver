package com.contentgrid.appserver.content.lifecycle;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.contentstore.api.ContentReference;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

@RequiredArgsConstructor
public class JooqContentReferenceVerificationQuery implements ContentReferenceVerificationQuery {

    private final DSLContext dslContext;

    @Override
    public boolean isReferenced(Application application, ContentReference ref) {
        return application.getEntities().stream()
                .anyMatch(entity -> entity.getContentAttributes().stream()
                        .anyMatch(contentAttribute -> dslContext.fetchExists(
                                dslContext.selectOne()
                                        .from(DSL.table(entity.getTable().getValue()))
                                        .where(DSL.field(
                                                contentAttribute.getId().getColumn().getValue(),
                                                String.class
                                        ).eq(ref.getValue()))
                        )));
    }
}
