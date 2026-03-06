package com.contentgrid.appserver.content.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.contentstore.api.ContentReference;
import com.contentgrid.appserver.contentstore.api.ContentStore;
import com.contentgrid.appserver.contentstore.api.UnwritableContentException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.SelectField;
import org.jooq.Table;
import org.mockito.Answers;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.DefaultApplicationArguments;

@ExtendWith(MockitoExtension.class)
class ContentDeletionJobTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    DSLContext dslContext;

    @Mock
    ContentStore contentStore;

    @Mock
    ContentReferenceVerificationQuery verificationQuery;

    @Mock
    Application application;

    SimpleMeterRegistry meterRegistry;
    ContentLifecycleProperties properties;
    ContentDeletionJob job;

    @BeforeEach
    void setup() {
        meterRegistry = new SimpleMeterRegistry();
        properties = new ContentLifecycleProperties();
        job = new ContentDeletionJob(dslContext, contentStore, verificationQuery, application, meterRegistry, properties);
    }

    private void givenCandidates(List<String> contentIds) {
        given(dslContext.select(ArgumentMatchers.<SelectField<String>>any())
                .from(ArgumentMatchers.<Table<?>>any())
                .where(ArgumentMatchers.<Condition>any())
                .limit(anyInt())
                .fetch(ArgumentMatchers.<Field<String>>any()))
                .willReturn(contentIds);
    }

    private double getCounter(String name) {
        Counter counter = meterRegistry.find(name).counter();
        return counter != null ? counter.count() : 0.0;
    }

    @Test
    void candidate_unreferenced_isDeleted() throws Exception {
        var contentId = "content-abc.bin";
        givenCandidates(List.of(contentId));
        given(verificationQuery.isReferenced(any(), any())).willReturn(false);

        job.run(new DefaultApplicationArguments());

        verify(contentStore).remove(ContentReference.of(contentId));
        assertThat(getCounter("content.deletion.success")).isEqualTo(1.0);
        assertThat(getCounter("content.deletion.failure")).isEqualTo(0.0);
        assertThat(getCounter("content.deletion.drift")).isEqualTo(0.0);
    }

    @Test
    void noCandidates_withinGracePeriod_nothingDeleted() throws Exception {
        givenCandidates(List.of());

        job.run(new DefaultApplicationArguments());

        verify(contentStore, never()).remove(any());
        assertThat(getCounter("content.deletion.success")).isEqualTo(0.0);
    }

    @Test
    void candidate_drift_markerCleared_noDelete() throws Exception {
        var contentId = "content-drift.bin";
        givenCandidates(List.of(contentId));
        given(verificationQuery.isReferenced(any(), any())).willReturn(true);

        job.run(new DefaultApplicationArguments());

        verify(contentStore, never()).remove(any());
        assertThat(getCounter("content.deletion.drift")).isEqualTo(1.0);
        assertThat(getCounter("content.deletion.success")).isEqualTo(0.0);
    }

    @Test
    void candidate_contentStoreRemoveFails_continuesAndRecordsFailure() throws Exception {
        var contentId1 = "content-fail.bin";
        var contentId2 = "content-ok.bin";
        givenCandidates(List.of(contentId1, contentId2));
        given(verificationQuery.isReferenced(any(), any())).willReturn(false);
        doThrow(new UnwritableContentException(ContentReference.of(contentId1)))
                .when(contentStore).remove(ContentReference.of(contentId1));

        job.run(new DefaultApplicationArguments());

        verify(contentStore).remove(ContentReference.of(contentId1));
        verify(contentStore).remove(ContentReference.of(contentId2));
        assertThat(getCounter("content.deletion.failure")).isEqualTo(1.0);
        assertThat(getCounter("content.deletion.success")).isEqualTo(1.0);
    }
}
