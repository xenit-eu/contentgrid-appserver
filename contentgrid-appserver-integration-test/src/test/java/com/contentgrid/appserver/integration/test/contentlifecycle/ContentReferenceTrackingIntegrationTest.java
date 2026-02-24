package com.contentgrid.appserver.integration.test.contentlifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.appserver.content.lifecycle.ContentReferenceTracker;
import com.contentgrid.appserver.content.lifecycle.JooqContentReferenceTracker;
import com.contentgrid.appserver.contentstore.api.ContentReference;
import com.contentgrid.appserver.contentstore.api.ContentStore;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Table;

@SpringBootTest(
        webEnvironment = WebEnvironment.NONE,
        properties = {
                "contentgrid.security.unauthenticated.allow = true",
                "contentgrid.events.rabbitmq.enabled=false",
                "contentgrid.appserver.content-store.type = ephemeral",
                "contentgrid.thunx.abac.source = none",
                "spring.datasource.url=jdbc:tc:postgresql:15:///",
                "contentgrid.content.lifecycle.enabled=true",
                "contentgrid.content.lifecycle.deletion.grace-period=PT1H"
        })
@TestPropertySource(properties = {
        "contentgrid.content.lifecycle.enabled=true",
        "contentgrid.content.lifecycle.deletion.grace-period=PT1H"
})
class ContentReferenceTrackingIntegrationTest {

    @Autowired
    DSLContext dslContext;

    @Autowired
    ContentStore contentStore;

    private ContentReferenceTracker tracker;

    private static final Table<Record> CONTENT_REFERENCES = 
            org.jooq.impl.DSL.table("_content_references");
    private static final org.jooq.Field<String> CONTENT_ID = 
            org.jooq.impl.DSL.field("content_id", org.jooq.impl.SQLDataType.VARCHAR);
    private static final org.jooq.Field<Integer> REFERENCE_COUNT = 
            org.jooq.impl.DSL.field("reference_count", org.jooq.impl.SQLDataType.INTEGER);

    @BeforeEach
    void setUp() {
        dslContext.execute("""
            CREATE TABLE IF NOT EXISTS _content_references (
                content_id VARCHAR(255) PRIMARY KEY,
                reference_count INTEGER NOT NULL DEFAULT 0,
                first_referenced_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                last_dereferenced_at TIMESTAMP WITH TIME ZONE,
                marked_for_deletion_at TIMESTAMP WITH TIME ZONE
            )
            """);
        dslContext.deleteFrom(CONTENT_REFERENCES).execute();
        tracker = new JooqContentReferenceTracker(dslContext, Duration.ofHours(1));
    }

    @Test
    void incrementReference_createsNewRecord() {
        String contentId = UUID.randomUUID().toString();
        
        tracker.incrementReference(ContentReference.of(contentId));
        
        var record = dslContext.selectFrom(CONTENT_REFERENCES)
                .where(CONTENT_ID.eq(contentId))
                .fetchOne();
        
        assertThat(record).isNotNull();
        assertThat(record.get(CONTENT_ID)).isEqualTo(contentId);
        assertThat(record.get(REFERENCE_COUNT)).isEqualTo(1);
    }

    @Test
    void incrementReference_incrementsExistingCount() {
        String contentId = UUID.randomUUID().toString();
        
        tracker.incrementReference(ContentReference.of(contentId));
        tracker.incrementReference(ContentReference.of(contentId));
        tracker.incrementReference(ContentReference.of(contentId));
        
        var record = dslContext.selectFrom(CONTENT_REFERENCES)
                .where(CONTENT_ID.eq(contentId))
                .fetchOne();
        
        assertThat(record).isNotNull();
        assertThat(record.get(REFERENCE_COUNT)).isEqualTo(3);
    }

    @Test
    void decrementReference_decrementsCount() {
        String contentId = UUID.randomUUID().toString();
        
        tracker.incrementReference(ContentReference.of(contentId));
        tracker.incrementReference(ContentReference.of(contentId));
        tracker.decrementReference(ContentReference.of(contentId));
        
        var record = dslContext.selectFrom(CONTENT_REFERENCES)
                .where(CONTENT_ID.eq(contentId))
                .fetchOne();
        
        assertThat(record).isNotNull();
        assertThat(record.get(REFERENCE_COUNT)).isEqualTo(1);
    }

    @Test
    void decrementReference_setsMarkedForDeletion_whenCountReachesZero() {
        String contentId = UUID.randomUUID().toString();
        
        tracker.incrementReference(ContentReference.of(contentId));
        tracker.decrementReference(ContentReference.of(contentId));
        
        var record = dslContext.selectFrom(CONTENT_REFERENCES)
                .where(CONTENT_ID.eq(contentId))
                .fetchOne();
        
        assertThat(record).isNotNull();
        assertThat(record.get(REFERENCE_COUNT)).isEqualTo(0);
        assertThat(record.get("marked_for_deletion_at", java.sql.Timestamp.class)).isNotNull();
    }

    @Test
    void incrementReference_clearsMarkedForDeletion() {
        String contentId = UUID.randomUUID().toString();
        
        tracker.incrementReference(ContentReference.of(contentId));
        tracker.decrementReference(ContentReference.of(contentId));
        
        var recordBefore = dslContext.selectFrom(CONTENT_REFERENCES)
                .where(CONTENT_ID.eq(contentId))
                .fetchOne();
        assertThat(recordBefore.get("marked_for_deletion_at", java.sql.Timestamp.class)).isNotNull();
        
        tracker.incrementReference(ContentReference.of(contentId));
        
        var recordAfter = dslContext.selectFrom(CONTENT_REFERENCES)
                .where(CONTENT_ID.eq(contentId))
                .fetchOne();
        assertThat(recordAfter.get("marked_for_deletion_at", java.sql.Timestamp.class)).isNull();
        assertThat(recordAfter.get(REFERENCE_COUNT)).isEqualTo(1);
    }

    @SpringBootApplication
    static class TestApplication {
        @Bean
        public ContentReferenceTracker contentReferenceTracker(DSLContext dslContext) {
            return new JooqContentReferenceTracker(dslContext, Duration.ofHours(1));
        }
    }
}