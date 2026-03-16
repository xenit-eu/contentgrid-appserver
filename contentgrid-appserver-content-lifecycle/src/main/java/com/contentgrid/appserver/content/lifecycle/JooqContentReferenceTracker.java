package com.contentgrid.appserver.content.lifecycle;

import com.contentgrid.appserver.contentstore.api.ContentReference;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

@RequiredArgsConstructor
public class JooqContentReferenceTracker implements ContentReferenceTracker {

    private final DSLContext dslContext;

    @Override
    public void incrementReference(ContentReference ref) {
        dslContext.insertInto(ContentReferenceTable.TABLE)
                .set(ContentReferenceTable.CONTENT_ID, ref.getValue())
                .set(ContentReferenceTable.REFERENCE_COUNT, 1)
                .set(ContentReferenceTable.FIRST_REFERENCED_AT, OffsetDateTime.now())
                .onConflict(ContentReferenceTable.CONTENT_ID)
                .doUpdate()
                .set(ContentReferenceTable.REFERENCE_COUNT,
                        DSL.field(DSL.name("_content_references", "reference_count"), Integer.class).add(1))
                .set(ContentReferenceTable.MARKED_FOR_DELETION_AT, (OffsetDateTime) null)
                .execute();
    }

    @Override
    public void decrementReference(ContentReference ref) {
        dslContext.update(ContentReferenceTable.TABLE)
                .set(ContentReferenceTable.REFERENCE_COUNT, ContentReferenceTable.REFERENCE_COUNT.subtract(1))
                .set(ContentReferenceTable.LAST_DEREFERENCED_AT, OffsetDateTime.now())
                .set(ContentReferenceTable.MARKED_FOR_DELETION_AT,
                        DSL.when(ContentReferenceTable.REFERENCE_COUNT.subtract(1).eq(0), OffsetDateTime.now())
                                .otherwise(ContentReferenceTable.MARKED_FOR_DELETION_AT))
                .where(ContentReferenceTable.CONTENT_ID.eq(ref.getValue()))
                .execute();
    }
}
