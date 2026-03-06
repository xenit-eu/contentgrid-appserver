package com.contentgrid.appserver.content.lifecycle;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.contentstore.api.ContentReference;
import com.contentgrid.appserver.contentstore.api.ContentStore;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.OffsetDateTime;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/**
 * Deletes content objects from the {@link ContentStore} that have been marked for deletion and whose grace period
 * has elapsed. Intended to be invoked as a K8s CronJob via Spring Boot's {@link ApplicationRunner} mechanism.
 *
 * <p>For each candidate:
 * <ol>
 *   <li>Performs a safety check to confirm the content is truly unreferenced (drift detection).
 *   <li>If unreferenced: deletes from the content store and removes the tracking row.
 *   <li>If still referenced (drift): clears the deletion mark and records a drift metric.
 *   <li>On any failure: records a failure metric and continues to the next candidate.
 * </ol>
 */
@Slf4j
public class ContentDeletionJob implements ApplicationRunner {

    private final DSLContext dslContext;
    private final ContentStore contentStore;
    private final ContentReferenceVerificationQuery verificationQuery;
    private final Application application;
    private final ContentLifecycleProperties properties;
    private final Counter successCounter;
    private final Counter failureCounter;
    private final Counter driftCounter;

    public ContentDeletionJob(
            DSLContext dslContext,
            ContentStore contentStore,
            ContentReferenceVerificationQuery verificationQuery,
            Application application,
            MeterRegistry meterRegistry,
            ContentLifecycleProperties properties) {
        this.dslContext = dslContext;
        this.contentStore = contentStore;
        this.verificationQuery = verificationQuery;
        this.application = application;
        this.properties = properties;
        this.successCounter = Counter.builder("content.deletion.success")
                .description("Number of content objects successfully deleted")
                .register(meterRegistry);
        this.failureCounter = Counter.builder("content.deletion.failure")
                .description("Number of content deletion attempts that failed")
                .register(meterRegistry);
        this.driftCounter = Counter.builder("content.deletion.drift")
                .description("Number of content objects marked for deletion but found to still be referenced")
                .register(meterRegistry);
    }

    @Override
    public void run(ApplicationArguments args) {
        var cutoff = OffsetDateTime.now().minus(properties.getDeletion().getGracePeriod());

        var candidates = dslContext
                .select(ContentReferenceTable.CONTENT_ID)
                .from(ContentReferenceTable.TABLE)
                .where(ContentReferenceTable.MARKED_FOR_DELETION_AT.lessOrEqual(cutoff))
                .limit(properties.getDeletion().getBatchSize())
                .fetch(ContentReferenceTable.CONTENT_ID);

        log.info("Processing {} content deletion candidates", candidates.size());

        for (var contentId : candidates) {
            processDeletionCandidate(ContentReference.of(contentId));
        }
    }

    private void processDeletionCandidate(ContentReference ref) {
        try {
            if (verificationQuery.isReferenced(application, ref)) {
                dslContext.update(ContentReferenceTable.TABLE)
                        .set(ContentReferenceTable.MARKED_FOR_DELETION_AT, (OffsetDateTime) null)
                        .where(ContentReferenceTable.CONTENT_ID.eq(ref.getValue()))
                        .execute();
                driftCounter.increment();
                log.warn("Drift detected: content {} is still referenced; clearing deletion mark", ref.getValue());
            } else {
                contentStore.remove(ref);
                dslContext.deleteFrom(ContentReferenceTable.TABLE)
                        .where(ContentReferenceTable.CONTENT_ID.eq(ref.getValue()))
                        .execute();
                successCounter.increment();
                log.debug("Deleted content {}", ref.getValue());
            }
        } catch (Exception e) {
            failureCounter.increment();
            log.error("Failed to process content deletion for {}", ref.getValue(), e);
        }
    }
}
