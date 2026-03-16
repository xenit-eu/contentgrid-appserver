package com.contentgrid.appserver.content.lifecycle;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.contentstore.api.ContentReference;

/**
 * Verifies whether a content object is still referenced by any entity in the application data model.
 * Used as a safety check before physically deleting content from the content store.
 */
public interface ContentReferenceVerificationQuery {

    /**
     * Returns true if the given content reference is still referenced by at least one entity in any entity table.
     */
    boolean isReferenced(Application application, ContentReference ref);
}
