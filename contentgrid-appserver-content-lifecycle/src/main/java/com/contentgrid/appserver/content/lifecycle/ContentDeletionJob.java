package com.contentgrid.appserver.content.lifecycle;

import static org.jooq.impl.DSL.currentTimestamp;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

import com.contentgrid.appserver.contentstore.api.ContentReference;
import com.contentgrid.appserver.contentstore.api.ContentStore;
import com.contentgrid.appserver.contentstore.api.UnwritableContentException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.jooq.Select;

@Slf4j
public class ContentDeletionJob {
    private static final String TABLE_NAME = "_content_references";
    private static final org.jooq.Table<org.jooq.Record> CONTENT_REFERENCES = table(name(TABLE_NAME));
    private static final org.jooq.Field<String> CONTENT_ID = field(name(TABLE_NAME, "content_id"), org.jooq.impl.SQLDataType.VARCHAR);
    private static final org.jooq.Field<java.sql.Timestamp> MARKED_FOR_DELETION_AT = field(name(TABLE_NAME, "marked_for_deletion_at"), org.jooq.impl.SQLDataType.TIMESTAMP);

    private final DSLContext dslContext;
    private final ContentStore contentStore;
    private final ContentReferenceVerificationQuery verificationQuery;
    private final ContentLifecycleProperties properties;
    private final Counter successCounter;
    private final Counter failureCounter;
    private final Counter driftCounter;

    public ContentDeletionJob(
            DSLContext dslContext,
            ContentStore contentStore,
            ContentReferenceVerificationQuery verificationQuery,
            ContentLifecycleProperties properties,
            MeterRegistry meterRegistry
    ) {
        this.dslContext = dslContext;
        this.contentStore = contentStore;
        this.verificationQuery = verificationQuery;
        this.properties = properties;
        this.successCounter = Counter.builder("content.deletion")
            .tag("result", "success")
            .register(meterRegistry);
        this.failureCounter = Counter.builder("content.deletion")
            .tag("result", "failure")
            .register(meterRegistry);
        this.driftCounter = Counter.builder("content.deletion.drift")
            .register(meterRegistry);
    }

    public void run() {
        log.info("Starting content deletion job");
        int processed = 0;
        int deleted = 0;
        int skipped = 0;

        List<String> candidates = findDeletionCandidates();
        
        for (String contentId : candidates) {
            processed++;
            
            if (verificationQuery.isStillReferenced(contentId)) {
                log.warn("Count drift detected: content_id {} marked for deletion but still referenced", contentId);
                driftCounter.increment();
                clearDeletionMarker(contentId);
                skipped++;
                continue;
            }

            if (deleteContent(contentId)) {
                deleted++;
            }
        }

        log.info("Content deletion job completed: processed={}, deleted={}, skipped={}", processed, deleted, skipped);
    }

    private List<String> findDeletionCandidates() {
        Select<Record1<String>> query = dslContext
            .select(CONTENT_ID)
            .from(CONTENT_REFERENCES)
            .where(MARKED_FOR_DELETION_AT.isNotNull())
            .and(MARKED_FOR_DELETION_AT.le(currentTimestamp()))
            .orderBy(MARKED_FOR_DELETION_AT)
            .limit(properties.getDeletion().getBatchSize());
        
        return query.fetch(CONTENT_ID);
    }

    private void clearDeletionMarker(String contentId) {
        dslContext.update(CONTENT_REFERENCES)
            .setNull(MARKED_FOR_DELETION_AT)
            .where(CONTENT_ID.eq(contentId))
            .execute();
    }

    private boolean deleteContent(String contentId) {
        try {
            contentStore.remove(ContentReference.of(contentId));
            
            dslContext.deleteFrom(CONTENT_REFERENCES)
                .where(CONTENT_ID.eq(contentId))
                .execute();
            
            successCounter.increment();
            log.debug("Deleted content_id {}", contentId);
            return true;
        } catch (UnwritableContentException e) {
            log.error("Failed to delete content_id {}", contentId, e);
            failureCounter.increment();
            return false;
        }
    }
}
