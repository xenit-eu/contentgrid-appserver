package com.contentgrid.appserver.content.lifecycle;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.then;

import com.contentgrid.appserver.contentstore.api.ContentReference;
import java.time.OffsetDateTime;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JooqContentReferenceTrackerTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    DSLContext dslContext;

    JooqContentReferenceTracker tracker;

    @BeforeEach
    void setup() {
        tracker = new JooqContentReferenceTracker(dslContext);
    }

    @Test
    void incrementReference_insertsOrIncrementsRow() {
        var ref = ContentReference.of("content-abc");

        tracker.incrementReference(ref);

        // Verify the upsert chain was called with the content_id
        then(dslContext).should().insertInto(ContentReferenceTable.TABLE);
    }

    @Test
    void decrementReference_updatesRow() {
        var ref = ContentReference.of("content-abc");

        tracker.decrementReference(ref);

        // Verify the update chain was invoked
        then(dslContext).should().update(ContentReferenceTable.TABLE);
    }
}
