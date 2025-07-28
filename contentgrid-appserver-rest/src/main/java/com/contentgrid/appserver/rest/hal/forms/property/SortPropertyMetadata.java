package com.contentgrid.appserver.rest.hal.forms.property;

import com.contentgrid.appserver.application.model.sortable.SortableField;
import com.contentgrid.appserver.query.engine.api.data.SortData.Direction;
import com.contentgrid.appserver.rest.EntityRestController;
import com.contentgrid.hateoas.spring.affordances.property.BasicPropertyMetadata;
import java.util.List;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.Value;
import org.springframework.core.ResolvableType;
import org.springframework.hateoas.AffordanceModel.PropertyMetadata;
import org.springframework.hateoas.mediatype.hal.forms.HalFormsOptions;
import org.springframework.hateoas.mediatype.html.HtmlInputType;
import org.springframework.util.Assert;

@Getter
public class SortPropertyMetadata extends PropertyMetadataWithOptions {

    private static final PropertyMetadata SORT =
            new BasicPropertyMetadata(EntityRestController.SORT_NAME, ResolvableType.forClass(Object.class))
                    .withRequired(false)
                    .withReadOnly(false)
                    .withInputType(HtmlInputType.TEXT_VALUE);

    private final List<SortOption> sortOptions;

    public SortPropertyMetadata(List<SortableField> sortableFields) {
        super(SORT);
        Assert.notEmpty(sortableFields, "sortableFields can not be empty");
        this.sortOptions = sortableFields.stream()
                .flatMap(field -> Stream.of(Direction.ASC, Direction.DESC)
                        .map(direction -> direction.name().toLowerCase())
                        .map(direction -> new SortOption(field.getPropertyPath().toString(), direction, null, field.getName().getValue() + "," + direction)))
                .toList();
    }

    @Override
    public HalFormsOptions getOptions() {
        return HalFormsOptions.inline(sortOptions)
                .withMinItems(0L)
                .withPromptField("prompt")
                .withValueField("value");
    }

    @Value
    private static class SortOption {
        String property;
        String direction;
        String prompt;
        String value;
    }
}
