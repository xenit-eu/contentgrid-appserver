package com.contentgrid.appserver.rest.hal.forms;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Constraint.AllowedValuesConstraint;
import com.contentgrid.appserver.application.model.Constraint.RequiredConstraint;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.CompositeAttribute;
import com.contentgrid.appserver.application.model.attributes.ContentAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.UserAttribute;
import com.contentgrid.appserver.application.model.relations.ManyToManyRelation;
import com.contentgrid.appserver.application.model.relations.OneToManyRelation;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.application.model.relations.flags.HiddenEndpointFlag;
import com.contentgrid.appserver.application.model.searchfilters.AttributeSearchFilter;
import com.contentgrid.appserver.application.model.sortable.SortableField;
import com.contentgrid.appserver.query.engine.api.data.SortData.Direction;
import com.contentgrid.appserver.rest.EntityRestController;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.Value;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.mediatype.hal.forms.HalFormsOptions;
import org.springframework.hateoas.mediatype.html.HtmlInputType;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

public class HalFormsTemplateGenerator {

    public HalFormsTemplate generateCreateTemplate(Application application, Entity entity) {
        List<HalFormsProperty> properties = new ArrayList<>();
        for (var attribute : entity.getAttributes()) {
            properties.addAll(attributeToCreateProperties("", attribute));
        }
        for (var relation : application.getRelationsForSourceEntity(entity)) {
            relationToProperty(application, relation).ifPresent(properties::add);
        }

        var hasFiles = properties.stream().anyMatch(prop -> Objects.equals(HtmlInputType.FILE_VALUE, prop.getType()));

        return HalFormsTemplate.builder()
                .key(IanaLinkRelations.CREATE_FORM_VALUE)
                .httpMethod(HttpMethod.POST)
                .contentType(hasFiles? MediaType.MULTIPART_FORM_DATA_VALUE:MediaType.APPLICATION_JSON_VALUE)
                .properties(properties)
                .target(getCollectionSelfLink(application, entity).toString())
                .build();
    }

    public HalFormsTemplate generateUpdateTemplate(Application application, Entity entity) {
        List<HalFormsProperty> properties = new ArrayList<>();
        for (var attribute : entity.getAttributes()) {
            properties.addAll(attributeToUpdateProperty("", attribute));
        }

        return HalFormsTemplate.builder()
                .key(HalFormsTemplate.DEFAULT_KEY)
                .httpMethod(HttpMethod.PUT)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .properties(properties)
                .build();
    }

    public HalFormsTemplate generateSearchTemplate(Application application, Entity entity) {
        List<HalFormsProperty> properties = new ArrayList<>();
        for (var searchFilter : entity.getSearchFilters()) {
            if (Objects.requireNonNull(searchFilter) instanceof AttributeSearchFilter attributeSearchFilter) {
                var property = searchFilterToSearchProperty(attributeSearchFilter);
                var attribute = application.resolvePropertyPath(entity, attributeSearchFilter.getAttributePath());
                properties.add(addAllowedValues(property, attribute, true));
            } else {
                throw new IllegalStateException("Unexpected value: " + searchFilter);
            }
        }
        entityToSortProperty(entity).ifPresent(properties::add);

        return HalFormsTemplate.builder()
                .key(IanaLinkRelations.SEARCH_VALUE)
                .httpMethod(HttpMethod.GET)
                .properties(properties)
                .target(getCollectionSelfLink(application, entity).toString())
                .build();
    }

    public List<HalFormsTemplate> generateRelationTemplates(Application application, Relation relation, String relationLink) {
        var maybeProperty = relationToProperty(application, relation);
        if (maybeProperty.isEmpty()) {
            return List.of();
        }

        var result = new ArrayList<HalFormsTemplate>();
        if (relation instanceof OneToManyRelation || relation instanceof ManyToManyRelation) {
            result.add(HalFormsTemplate.builder()
                    .key("add-" + relation.getSourceEndPoint().getLinkName())
                    .httpMethod(HttpMethod.POST)
                    .target(relationLink)
                    .contentType("text/uri-list")
                    .property(maybeProperty.get())
                    .build());
        } else {
            result.add(HalFormsTemplate.builder()
                    .key("set-" + relation.getSourceEndPoint().getLinkName())
                    .httpMethod(HttpMethod.PUT)
                    .target(relationLink)
                    .contentType("text/uri-list")
                    .property(maybeProperty.get())
                    .build());
        }
        if (!relation.getSourceEndPoint().isRequired() && !relation.getTargetEndPoint().isRequired()) {
            // A relation that is required on any side can't be cleared, because it would give a constraint violation error
            result.add(HalFormsTemplate.builder()
                    .key("clear-" + relation.getSourceEndPoint().getLinkName())
                    .httpMethod(HttpMethod.DELETE)
                    .target(relationLink)
                    .build());
        }
        return result;
    }

    public List<HalFormsTemplate> generateContentTemplates(Application application, Entity entity, ContentAttribute content, String contentLink) {
        return List.of(); // no templates yet
    }

    private URI getCollectionSelfLink(Application application, Entity entity) {
        return linkTo(methodOn(EntityRestController.class).listEntity(application, entity.getPathSegment(), 0, null, Map.of())).toUri();
    }


    private List<HalFormsProperty> attributeToCreateProperties(String prefix, Attribute attribute) {
        if (attribute.isIgnored() || attribute.isReadOnly()) {
            return List.of();
        }
        var result = new ArrayList<HalFormsProperty>();
        switch (attribute) {
            case SimpleAttribute simpleAttribute -> {
                result.add(simpleAttributeToProperty(prefix, simpleAttribute));
            }
            case UserAttribute userAttribute -> {
                // how to provide user attributes at creation?
            }
            case ContentAttribute contentAttribute -> {
                result.add(contentToCreateProperty(prefix, contentAttribute));
            }
            case CompositeAttribute compositeAttribute -> {
                var newPrefix = prefix + compositeAttribute.getName() + ".";
                for (var attr : compositeAttribute.getAttributes()) {
                    result.addAll(attributeToCreateProperties(newPrefix, attr));
                }
            }
        }
        return result;
    }

    private List<HalFormsProperty> attributeToUpdateProperty(String prefix, Attribute attribute) {
        if (attribute.isIgnored() || attribute.isReadOnly()) {
            return List.of();
        }
        var result = new ArrayList<HalFormsProperty>();
        switch (attribute) {
            case SimpleAttribute simpleAttribute -> {
                result.add(simpleAttributeToProperty(prefix, simpleAttribute));
            }
            case UserAttribute userAttribute -> {
                // how to update user attributes?
            }
            // handle ContentAttributes like CompositeAttributes
            case CompositeAttribute compositeAttribute -> {
                var newPrefix = prefix + compositeAttribute.getName() + ".";
                for (var attr : compositeAttribute.getAttributes()) {
                    result.addAll(attributeToUpdateProperty(newPrefix, attr));
                }
            }
        }
        return result;
    }

    private HalFormsProperty simpleAttributeToProperty(String prefix, SimpleAttribute attribute) {
        var property = HalFormsProperty.named(prefix + attribute.getName().getValue())
                .withAttributeType(attribute.getType())
                .withRequired(attribute.hasConstraint(RequiredConstraint.class));
        return addAllowedValues(property, attribute, false);
    }

    private HalFormsProperty addAllowedValues(HalFormsProperty property, SimpleAttribute attribute, boolean unbounded) {
        return attribute.getConstraint(AllowedValuesConstraint.class)
                .map(AllowedValuesConstraint::getValues)
                .map(HalFormsOptions::inline)
                .map(options -> options.withMinItems(property.isRequired() ? 1L : 0L))
                .map(options -> options.withMaxItems(unbounded ? null : 1L))
                .map(property::withOptions)
                .orElse(property);
    }

    private Optional<HalFormsProperty> relationToProperty(Application application, Relation relation) {
        if (relation.getSourceEndPoint().hasFlag(HiddenEndpointFlag.class)) {
            return Optional.empty();
        }
        var required = relation.getSourceEndPoint().isRequired();
        var url = linkTo(methodOn(EntityRestController.class)
                .listEntity(application, relation.getTargetEndPoint().getEntity().getPathSegment(), 0, null, Map.of()))
                .toUri().toString();
        var options = HalFormsOptions.remote(url)
                .withMinItems(required ? 1L : 0L)
                .withMaxItems(relation instanceof OneToManyRelation || relation instanceof ManyToManyRelation ? null : 1L)
                .withValueField("/_links/self/href");
        return Optional.of(HalFormsProperty.named(relation.getSourceEndPoint().getName().getValue())
                .withType(HtmlInputType.URL_VALUE)
                .withRequired(required)
                .withOptions(options));
    }

    private HalFormsProperty contentToCreateProperty(String prefix, ContentAttribute attribute) {
        return HalFormsProperty.named(prefix + attribute.getName().getValue())
                .withType(HtmlInputType.FILE_VALUE);
    }

    private HalFormsProperty searchFilterToSearchProperty(AttributeSearchFilter attributeSearchFilter) {
        return HalFormsProperty.named(attributeSearchFilter.getName().getValue())
                .withAttributeType(attributeSearchFilter.getAttributeType());
    }

    private Optional<HalFormsProperty> entityToSortProperty(Entity entity) {
        var sortOptions = new ArrayList<SortOption>();
        for (var sortableField : entity.getSortableFields()) {
            sortableFieldToSortOptions(sortableField).forEachOrdered(sortOptions::add);
        }
        if (sortOptions.isEmpty()) {
            return Optional.empty();
        }
        var options = HalFormsOptions.inline(sortOptions)
                .withMinItems(0L)
                .withPromptField("prompt")
                .withValueField("value");
        return Optional.of(HalFormsProperty.named(EntityRestController.SORT_NAME)
                .withType(HtmlInputType.TEXT_VALUE)
                .withOptions(options));
    }

    private Stream<SortOption> sortableFieldToSortOptions(SortableField sortableField) {
        return Stream.of(Direction.ASC, Direction.DESC)
                .map(direction -> direction.name().toLowerCase())
                .map(direction -> {
                    var value = sortableField.getName().getValue() + "," + direction;
                    return new SortOption(sortableField.getPropertyPath().toString(), direction, null, value);
                });
    }

    @Value
    private static class SortOption {
        String property;
        String direction;
        String prompt;
        String value;
    }
}
