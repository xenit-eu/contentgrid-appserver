package com.contentgrid.appserver.rest.hal.forms.property;

import java.util.List;
import lombok.Getter;
import lombok.NonNull;
import org.springframework.hateoas.AffordanceModel.PropertyMetadata;
import org.springframework.hateoas.mediatype.hal.forms.HalFormsOptions;

@Getter
public class PropertyMetadataWithInlineValues extends PropertyMetadataWithOptions {

    @NonNull
    private final List<?> inlineValues;
    private final boolean unbounded;

    public PropertyMetadataWithInlineValues(PropertyMetadata delegate, List<?> inlineValues, boolean unbounded) {
        super(delegate);
        this.inlineValues = inlineValues;
        this.unbounded = unbounded;
    }

    @Override
    public HalFormsOptions getOptions() {
        return HalFormsOptions.inline(this.getInlineValues())
                .withMinItems(this.isRequired() ? 1L : 0L)
                .withMaxItems(this.isUnbounded() ? null : 1L);
    }
}
