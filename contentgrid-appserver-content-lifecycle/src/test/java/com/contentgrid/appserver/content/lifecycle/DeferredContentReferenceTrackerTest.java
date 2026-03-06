package com.contentgrid.appserver.content.lifecycle;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.contentgrid.appserver.contentstore.api.ContentReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class DeferredContentReferenceTrackerTest {

    @Mock
    ContentReferenceTracker delegate;

    @Test
    void incrementReference_alwaysDelegatesDirectly() {
        var tracker = new DeferredContentReferenceTracker(delegate);
        var ref = ContentReference.of("test-content");

        TransactionSynchronizationManager.initSynchronization();
        try {
            tracker.incrementReference(ref);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        verify(delegate).incrementReference(ref);
    }

    @Test
    void decrementReference_outsideTransaction_callsDirectly() {
        var tracker = new DeferredContentReferenceTracker(delegate);
        var ref = ContentReference.of("test-content");

        tracker.decrementReference(ref);

        verify(delegate).decrementReference(ref);
    }

    @Test
    void decrementReference_insideTransaction_deferredToAfterCommit() {
        var tracker = new DeferredContentReferenceTracker(delegate);
        var ref = ContentReference.of("test-content");

        TransactionSynchronizationManager.initSynchronization();
        try {
            tracker.decrementReference(ref);

            // Should NOT be called yet — still inside transaction
            verify(delegate, never()).decrementReference(ref);

            // Simulate commit: invoke afterCommit on all synchronizations
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(s -> s.afterCommit());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        // Should be called now
        verify(delegate).decrementReference(ref);
    }

    @Test
    void decrementReference_insideTransaction_notCalledOnRollback() {
        var tracker = new DeferredContentReferenceTracker(delegate);
        var ref = ContentReference.of("test-content");

        TransactionSynchronizationManager.initSynchronization();
        try {
            tracker.decrementReference(ref);
        } finally {
            // Simulate rollback: just clear synchronizations without invoking afterCommit
            TransactionSynchronizationManager.clearSynchronization();
        }

        verify(delegate, never()).decrementReference(ref);
    }
}
