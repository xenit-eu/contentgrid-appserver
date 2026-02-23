package com.contentgrid.appserver.content.lifecycle;

import com.contentgrid.appserver.contentstore.api.ContentReference;

public interface ContentReferenceTracker {
    void incrementReference(ContentReference ref);
    void decrementReference(ContentReference ref);
}
