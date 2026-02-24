package com.contentgrid.appserver.content.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.appserver.contentstore.api.ContentReference;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class InMemoryContentReferenceTracker implements ContentReferenceTracker {
    private final AtomicInteger count = new AtomicInteger(0);
    private final List<String> decrementedRefs = new ArrayList<>();

    @Override
    public void incrementReference(ContentReference ref) {
        count.incrementAndGet();
    }

    @Override
    public void decrementReference(ContentReference ref) {
        count.decrementAndGet();
        decrementedRefs.add(ref.getValue());
    }

    public int getCount() {
        return count.get();
    }

    public List<String> getDecrementedRefs() {
        return List.copyOf(decrementedRefs);
    }
}

class InMemoryDeferredContentReferenceTracker implements ContentReferenceTracker {
    private final InMemoryContentReferenceTracker delegate;
    private boolean transactionActive = false;
    private final List<String> pendingDecrements = new java.util.ArrayList<>();

    public InMemoryDeferredContentReferenceTracker(InMemoryContentReferenceTracker delegate) {
        this.delegate = delegate;
    }

    @Override
    public void incrementReference(ContentReference ref) {
        delegate.incrementReference(ref);
    }

    @Override
    public void decrementReference(ContentReference ref) {
        if (transactionActive) {
            pendingDecrements.add(ref.getValue());
        } else {
            delegate.decrementReference(ref);
        }
    }

    public void setTransactionActive(boolean active) {
        this.transactionActive = active;
    }

    public InMemoryContentReferenceTracker getDelegate() {
        return delegate;
    }

    public void flushPendingDecrements() {
        for (var ref : pendingDecrements) {
            delegate.decrementReference(ContentReference.of(ref));
        }
        pendingDecrements.clear();
    }
}

class ContentReferenceTrackerBehaviorTest {

    @Nested
    class InMemoryTracker {
        @Test
        void incrementIncreasesCount() {
            var tracker = new InMemoryContentReferenceTracker();
            
            tracker.incrementReference(ContentReference.of("content-1"));
            
            assertThat(tracker.getCount()).isEqualTo(1);
        }

        @Test
        void decrementDecreasesCount() {
            var tracker = new InMemoryContentReferenceTracker();
            tracker.incrementReference(ContentReference.of("content-1"));
            
            tracker.decrementReference(ContentReference.of("content-1"));
            
            assertThat(tracker.getCount()).isEqualTo(0);
        }

        @Test
        void multipleIncrementsAndDecrements() {
            var tracker = new InMemoryContentReferenceTracker();
            
            tracker.incrementReference(ContentReference.of("content-1"));
            tracker.incrementReference(ContentReference.of("content-2"));
            tracker.incrementReference(ContentReference.of("content-3"));
            
            tracker.decrementReference(ContentReference.of("content-2"));
            
            assertThat(tracker.getCount()).isEqualTo(2);
        }

        @Test
        void decrementTracksReferences() {
            var tracker = new InMemoryContentReferenceTracker();
            
            tracker.incrementReference(ContentReference.of("content-1"));
            tracker.decrementReference(ContentReference.of("content-1"));
            
            assertThat(tracker.getDecrementedRefs()).containsExactly("content-1");
        }
    }

@Nested
        class DeferredTracker {
        @Test
        void delegatesWhenNoTransaction() {
            var inner = new InMemoryContentReferenceTracker();
            var deferred = new InMemoryDeferredContentReferenceTracker(inner);
            
            deferred.incrementReference(ContentReference.of("content-1"));
            deferred.decrementReference(ContentReference.of("content-1"));
            
            assertThat(inner.getCount()).isEqualTo(0);
        }

        @Test
        void doesNotDecrementWhenTransactionActive() {
            var inner = new InMemoryContentReferenceTracker();
            var deferred = new InMemoryDeferredContentReferenceTracker(inner);
            
            deferred.incrementReference(ContentReference.of("content-1"));
            deferred.setTransactionActive(true);
            deferred.decrementReference(ContentReference.of("content-1"));
            
            assertThat(inner.getCount()).isEqualTo(1);
        }
    }
}
