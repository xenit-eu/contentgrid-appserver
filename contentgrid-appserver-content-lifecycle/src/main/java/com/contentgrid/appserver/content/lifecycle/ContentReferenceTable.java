package com.contentgrid.appserver.content.lifecycle;

import java.time.OffsetDateTime;
import org.jooq.Allow;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

@Allow.PlainSQL
final class ContentReferenceTable {

    static final Table<?> TABLE = DSL.table("_content_references");

    static final Field<String> CONTENT_ID = DSL.field("content_id", SQLDataType.VARCHAR.nullable(false));
    static final Field<Integer> REFERENCE_COUNT = DSL.field("reference_count", SQLDataType.INTEGER.nullable(false));
    static final Field<OffsetDateTime> FIRST_REFERENCED_AT = DSL.field("first_referenced_at", SQLDataType.TIMESTAMPWITHTIMEZONE.nullable(false));
    static final Field<OffsetDateTime> LAST_DEREFERENCED_AT = DSL.field("last_dereferenced_at", SQLDataType.TIMESTAMPWITHTIMEZONE.nullable(true));
    static final Field<OffsetDateTime> MARKED_FOR_DELETION_AT = DSL.field("marked_for_deletion_at", SQLDataType.TIMESTAMPWITHTIMEZONE.nullable(true));

    private ContentReferenceTable() {}
}
