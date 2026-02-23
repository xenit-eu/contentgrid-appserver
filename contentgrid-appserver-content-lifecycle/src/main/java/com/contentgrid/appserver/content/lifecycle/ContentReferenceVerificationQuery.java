package com.contentgrid.appserver.content.lifecycle;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.selectOne;
import static org.jooq.impl.DSL.table;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.ContentAttribute;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Select;

@RequiredArgsConstructor
public class ContentReferenceVerificationQuery {
    private final DSLContext dslContext;
    private final Supplier<Application> applicationSupplier;

    public boolean isStillReferenced(String contentId) {
        Application app = applicationSupplier.get();
        
        for (Entity entity : app.getEntities()) {
            for (ContentAttribute contentAttr : entity.getContentAttributes()) {
                if (isReferencedIn(entity, contentAttr, contentId)) {
                    return true;
                }
            }
        }
        
        return false;
    }

    private boolean isReferencedIn(Entity entity, ContentAttribute contentAttr, String contentId) {
        String tableName = entity.getTable().getValue();
        String contentIdColumn = contentAttr.getId().getColumn().getValue();
        
        return dslContext.fetchExists(
            dslContext.selectOne()
                .from(table(name(tableName)))
                .where(field(name(contentIdColumn)).eq(contentId))
        );
    }
}
