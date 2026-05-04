package com.contentgrid.appserver.application.model.openapi.model.rest.body;

/**
 * Describes the media type of a REST body, used to drive which attributes are included when
 * mapping an entity to a {@link BodyValue} tree.
 */
public enum MediaType {

    /** {@code application/json} */
    JSON,

    /** {@code application/json}, but flattened to a single level (for HAL-FORMS) */
    FLAT_JSON,

    /** {@code application/x-www-form-urlencoded} */
    FORM,

    /** {@code multipart/form-data} — the only media type that includes file ({@link ContentBodyValue}) fields. */
    MULTIPART_FORM;

    /**
     * @return whether this mediatype has the possibility to transport null values
     */
    public boolean canTransportNulls() {
        return this == JSON || this == FLAT_JSON;
    }

    /**
     * @return whether this mediatype has the possibility to transport file values
     */
    public boolean canTransportContent() {
        return this == MULTIPART_FORM;
    }

    /**
     * @return whether this mediatype has the possibility to transport nested values
     */
    public boolean canTransportNestedObjects() {
        return this == JSON;
    }
}
