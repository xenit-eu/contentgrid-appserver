package com.contentgrid.appserver.content.lifecycle;

import static org.jooq.impl.DSL.currentTimestamp;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;
import static org.jooq.impl.DSL.val;

import com.contentgrid.appserver.contentstore.api.ContentReference;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.impl.SQLDataType;

@RequiredArgsConstructor
public class JooqContentReferenceTracker implements ContentReferenceTracker {
    private final DSLContext dslContext;
    private final Duration gracePeriod;

    private static final String TABLE_NAME = "_content_references";
    private static final org.jooq.Table<Record> CONTENT_REFERENCES = table(name(TABLE_NAME));
    private static final Field<String> CONTENT_ID = field(name(TABLE_NAME, "content_id"), SQLDataType.VARCHAR);
    private static final Field<Integer> REFERENCE_COUNT = field(name(TABLE_NAME, "reference_count"), SQLDataType.INTEGER);
    private static final Field<java.sql.Timestamp> FIRST_REFERENCED_AT = field(name(TABLE_NAME, "first_referenced_at"), SQLDataType.TIMESTAMP);
    private static final Field<java.sql.Timestamp> LAST_DEREFERENCED_AT = field(name(TABLE_NAME, "last_dereferenced_at"), SQLDataType.TIMESTAMP);
    private static final Field<java.sql.Timestamp> MARKED_FOR_DELETION_AT = field(name(TABLE_NAME, "marked_for_deletion_at"), SQLDataType.TIMESTAMP);

    public void setupTables() {
        dslContext.createTableIfNotExists(TABLE_NAME)
            .column(CONTENT_ID)
            .column(REFERENCE_COUNT)
            .column(FIRST_REFERENCED_AT)
            .column(LAST_DEREFERENCED_AT)
            .column(MARKED_FOR_DELETION_AT)
            .primaryKey(CONTENT_ID)
            .execute();
    }

    public void dropTables() {
        dslContext.dropTableIfExists(TABLE_NAME).execute();
    }

    @Override
    public void incrementReference(ContentReference ref) {
        dslContext.insertInto(CONTENT_REFERENCES)
            .columns(CONTENT_ID, REFERENCE_COUNT, FIRST_REFERENCED_AT)
            .values(val(ref.getValue()), val(1), currentTimestamp())
            .onConflict(CONTENT_ID)
            .doUpdate()
            .set(REFERENCE_COUNT, REFERENCE_COUNT.plus(1))
            .setNull(MARKED_FOR_DELETION_AT)
            .execute();
    }

    @Override
    public void decrementReference(ContentReference ref) {
        dslContext.transaction(configuration -> {
            var txDsl = configuration.dsl();
            
            txDsl.update(CONTENT_REFERENCES)
                .set(REFERENCE_COUNT, REFERENCE_COUNT.minus(1))
                .set(LAST_DEREFERENCED_AT, currentTimestamp())
                .where(CONTENT_ID.eq(ref.getValue()))
                .and(REFERENCE_COUNT.gt(0))
                .execute();
            
            txDsl.update(CONTENT_REFERENCES)
                .set(MARKED_FOR_DELETION_AT, currentTimestamp().plus((int) gracePeriod.toSeconds()))
                .where(CONTENT_ID.eq(ref.getValue()))
                .and(REFERENCE_COUNT.le(0))
                .execute();
        });
    }
}
