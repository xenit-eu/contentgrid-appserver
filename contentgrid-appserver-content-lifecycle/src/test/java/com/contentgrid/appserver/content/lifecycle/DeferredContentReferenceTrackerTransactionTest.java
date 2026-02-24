package com.contentgrid.appserver.content.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.contentgrid.appserver.contentstore.api.ContentReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class DeferredContentReferenceTrackerTransactionTest {

    private ContentReferenceTracker delegate;
    private DeferredContentReferenceTracker tracker;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
        delegate = new ContentReferenceTracker() {
            @Override
            public void incrementReference(ContentReference ref) {}
            @Override
            public void decrementReference(ContentReference ref) {}
        };
        tracker = new DeferredContentReferenceTracker(delegate);
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void decrementReference_registersSynchronization_whenTransactionActive() {
        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isTrue();
        
        tracker.decrementReference(ContentReference.of("test-id"));
        
        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
    }

    @Test
    void decrementReference_doesNotCallDelegateImmediately() {
        var delegateSpy = new ContentReferenceTracker() {
            private boolean called = false;
            @Override
            public void incrementReference(ContentReference ref) {}
            @Override
            public void decrementReference(ContentReference ref) {
                called = true;
            }
            public boolean wasCalled() { return called; }
        };
        var spyTracker = new DeferredContentReferenceTracker(delegateSpy);
        
        spyTracker.decrementReference(ContentReference.of("test-id"));
        
        assertThat(delegateSpy.wasCalled()).isFalse();
    }

    @Test
    void incrementReference_doesNotRegisterSynchronization() {
        tracker.incrementReference(ContentReference.of("test-id"));
        
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
    }
}
