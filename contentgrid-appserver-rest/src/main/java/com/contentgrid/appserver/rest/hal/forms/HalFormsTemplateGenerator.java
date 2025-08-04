package com.contentgrid.appserver.rest.hal.forms;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.ContentAttribute;
import com.contentgrid.appserver.application.model.relations.ManyToManyRelation;
import com.contentgrid.appserver.application.model.relations.OneToManyRelation;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.rest.EntityRestController;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.mediatype.html.HtmlInputType;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

@RequiredArgsConstructor
public class HalFormsTemplateGenerator {

    private final HalFormsPropertyContributor contributor;

    public HalFormsTemplate generateCreateTemplate(Application application, Entity entity) {
        List<HalFormsProperty> properties = new ArrayList<>();

        contributor.contributeToCreateForm(application, entity)
                .forEachOrdered(properties::add);

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

        contributor.contributeToUpdateForm(application, entity)
                .forEachOrdered(properties::add);

        return HalFormsTemplate.builder()
                .key(HalFormsTemplate.DEFAULT_KEY)
                .httpMethod(HttpMethod.PUT)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .properties(properties)
                .build();
    }

    public HalFormsTemplate generateSearchTemplate(Application application, Entity entity) {
        List<HalFormsProperty> properties = new ArrayList<>();

        contributor.contributeToSearchForm(application, entity)
                .forEachOrdered(properties::add);

        return HalFormsTemplate.builder()
                .key(IanaLinkRelations.SEARCH_VALUE)
                .httpMethod(HttpMethod.GET)
                .properties(properties)
                .target(getCollectionSelfLink(application, entity).toString())
                .build();
    }

    public List<HalFormsTemplate> generateRelationTemplates(Application application, Relation relation, String relationLink) {
        var maybeProperty = contributor.relationToProperty(application, relation);
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
}
