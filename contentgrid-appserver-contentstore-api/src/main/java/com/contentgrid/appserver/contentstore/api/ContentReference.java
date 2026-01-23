package com.contentgrid.appserver.contentstore.api;

import java.io.Serializable;
import lombok.NonNull;
import lombok.Value;

/**
 * Reference to a content object
 */
@Value(staticConstructor = "of")
public class ContentReference implements Serializable {

    /**
     * Static value used when the referenced content object is unknown
     */
    public static final ContentReference UNKNOWN = ContentReference.of("<UNKNOWN>");

    @NonNull
    String value;
}
