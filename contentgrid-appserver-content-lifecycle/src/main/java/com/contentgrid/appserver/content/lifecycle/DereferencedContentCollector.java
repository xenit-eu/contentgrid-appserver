package com.contentgrid.appserver.content.lifecycle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DereferencedContentCollector {
    private final List<String> contentIds = new ArrayList<>();

    public void add(String contentId) {
        if (contentId != null && !contentId.isEmpty()) {
            contentIds.add(contentId);
        }
    }

    public List<String> getContentIds() {
        return Collections.unmodifiableList(contentIds);
    }

    public boolean isEmpty() {
        return contentIds.isEmpty();
    }

    public void clear() {
        contentIds.clear();
    }
}
