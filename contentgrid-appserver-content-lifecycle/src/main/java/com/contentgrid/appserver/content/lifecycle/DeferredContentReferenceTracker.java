package com.contentgrid.appserver.content.lifecycle;

import com.contentgrid.appserver.contentstore.api.ContentReference;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Wraps a {@link ContentReferenceTracker} to defer {@link #decrementReference} until after the current transaction
 * commits. If no transaction is active, the decrement is called directly.
 * <p>
 * This prevents premature deletion markers when the entity transaction rolls back.
 */
@RequiredArgsConstructor
public class DeferredContentReferenceTracker implements ContentReferenceTracker {

    private final ContentReferenceTracker delegate;

    @Override
    public void incrementReference(ContentReference ref) {
        delegate.incrementReference(ref);
    }

    @Override
    public void decrementReference(ContentReference ref) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    delegate.decrementReference(ref);
                }
            });
        } else {
            delegate.decrementReference(ref);
        }
    }
}
