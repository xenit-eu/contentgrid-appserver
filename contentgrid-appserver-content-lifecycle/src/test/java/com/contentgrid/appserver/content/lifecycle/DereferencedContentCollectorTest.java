package com.contentgrid.appserver.content.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DereferencedContentCollectorTest {

    @Nested
    class AddContent {
        @Test
        void addsNonNullContentId() {
            var collector = new DereferencedContentCollector();
            
            collector.add("content-123");
            
            assertThat(collector.getContentIds()).containsExactly("content-123");
        }

        @Test
        void addsMultipleContentIds() {
            var collector = new DereferencedContentCollector();
            
            collector.add("content-1");
            collector.add("content-2");
            collector.add("content-3");
            
            assertThat(collector.getContentIds()).containsExactly("content-1", "content-2", "content-3");
        }

        @Test
        void ignoresNullContentId() {
            var collector = new DereferencedContentCollector();
            
            collector.add(null);
            
            assertThat(collector.getContentIds()).isEmpty();
        }

        @Test
        void ignoresEmptyContentId() {
            var collector = new DereferencedContentCollector();
            
            collector.add("");
            
            assertThat(collector.getContentIds()).isEmpty();
        }
    }

    @Nested
    class GetContentIds {
        @Test
        void returnsUnmodifiableList() {
            var collector = new DereferencedContentCollector();
            collector.add("content-1");
            
            var list = collector.getContentIds();
            
            var thrown = false;
            try {
                list.add("should-fail");
            } catch (UnsupportedOperationException e) {
                thrown = true;
            }
            assertThat(thrown).isTrue();
        }
    }

    @Nested
    class Clear {
        @Test
        void clearsAllContentIds() {
            var collector = new DereferencedContentCollector();
            collector.add("content-1");
            collector.add("content-2");
            
            collector.clear();
            
            assertThat(collector.getContentIds()).isEmpty();
        }
    }

    @Nested
    class IsEmpty {
        @Test
        void returnsTrueWhenEmpty() {
            var collector = new DereferencedContentCollector();
            
            assertThat(collector.isEmpty()).isTrue();
        }

        @Test
        void returnsFalseWhenNotEmpty() {
            var collector = new DereferencedContentCollector();
            collector.add("content-1");
            
            assertThat(collector.isEmpty()).isFalse();
        }
    }
}
