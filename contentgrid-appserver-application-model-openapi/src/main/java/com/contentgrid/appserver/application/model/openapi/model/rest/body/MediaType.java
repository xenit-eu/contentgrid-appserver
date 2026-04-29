package com.contentgrid.appserver.application.model.openapi.model.rest.body;

/**
 * Describes the media type of a REST body, used to drive which attributes are included when
 * mapping an entity to a {@link BodyValue} tree.
 */
public enum MediaType {

    /** {@code application/json} */
    JSON,

    /** {@code application/x-www-form-urlencoded} */
    FORM,

    /** {@code multipart/form-data} — the only media type that includes file ({@link ContentBodyValue}) fields. */
    MULTIPART_FORM;

}
