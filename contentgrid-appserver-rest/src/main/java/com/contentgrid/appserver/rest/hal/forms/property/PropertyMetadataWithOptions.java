package com.contentgrid.appserver.rest.hal.forms.property;

import com.contentgrid.hateoas.spring.affordances.property.CustomPropertyMetadata;
import org.springframework.hateoas.AffordanceModel.PropertyMetadata;
import org.springframework.hateoas.mediatype.hal.forms.HalFormsOptions;

public abstract class PropertyMetadataWithOptions extends CustomPropertyMetadata {

    public PropertyMetadataWithOptions(PropertyMetadata delegate) {
        super(delegate);
    }

    public abstract HalFormsOptions getOptions();
}
