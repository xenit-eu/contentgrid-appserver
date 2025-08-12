package com.contentgrid.appserver.content.api;

import java.io.Serializable;
import lombok.NonNull;
import lombok.Value;

/**
 * Reference to a content object
 */
@Value(staticConstructor = "of")
public class ContentReference implements Serializable {
    @NonNull
    String value;
}
