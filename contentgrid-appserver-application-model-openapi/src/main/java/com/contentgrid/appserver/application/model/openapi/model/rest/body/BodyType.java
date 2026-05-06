package com.contentgrid.appserver.application.model.openapi.model.rest.body;

/**
 * Describes the role of a REST body, used to drive which attributes are included and how
 * nullability is determined when mapping an entity to a {@link BodyValue} tree.
 */
public enum BodyType {

    /**
     * GET response
     * <p>all non-ignored attributes including read-only / primary key
     * <p>all fields are mandatory (because they are all present in the response)
     */
    RESPONSE,

    /**
     * POST (create) request
     * <p>non-readonly attributes + relation fields
     */
    POST,

    /**
     * PUT (full-update) request
     * <p>non-readonly attributes; no relations
     */
     PUT,

    /**
     * PATCH (partial-update) request
     * <p>non-readonly attributes; no relations
     * <p>No fields are mandatory
     */
    PATCH;

}
