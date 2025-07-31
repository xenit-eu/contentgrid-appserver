package com.contentgrid.appserver.rest.assembler;

public interface JsonViews {

    /**
     * The {@link DefaultView} includes all properties without explicit view configured.
     * Properties with a different view will be ignored.
     * <p>
     * This view is required because if no view is configured,
     * {@link com.fasterxml.jackson.annotation.JsonView} annotations are ignored and all properties are included.
     */
    interface DefaultView {}

    /**
     * The {@link HalFormsView} includes all properties of the {@link DefaultView}
     * and the {@code _templates} property of {@link RepresentationModelWithTemplates} model.
     */
    interface HalFormsView extends DefaultView {}
}
