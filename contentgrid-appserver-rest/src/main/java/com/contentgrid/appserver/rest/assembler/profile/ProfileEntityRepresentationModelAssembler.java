package com.contentgrid.appserver.rest.assembler.profile;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.rest.EntityRestController;
import com.contentgrid.appserver.rest.ProfileRestController;
import com.contentgrid.appserver.rest.hal.forms.HalFormsTemplate;
import com.contentgrid.appserver.rest.hal.forms.HalFormsTemplateGenerator;
import com.contentgrid.hateoas.spring.server.RepresentationModelContextAssembler;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.Link;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProfileEntityRepresentationModelAssembler implements RepresentationModelContextAssembler<Entity, ProfileEntityRepresentationModel, Application> {

    private final HalFormsTemplateGenerator templateGenerator;

    @Override
    public ProfileEntityRepresentationModel toModel(Entity entity, Application application) {
        var result = new ProfileEntityRepresentationModel();
        var collectionLink = getEntityCollectionLink(application, entity);

        // Add links
        result.add(getSelfLink(application, entity))
                .add(collectionLink)
                .add(getEntityItemLink(application, entity));

        // Add default template
        result.addTemplate(HalFormsTemplate.builder()
                .httpMethod(HttpMethod.HEAD)
                .target(collectionLink.getHref())
                .build());

        // Add search and create templates
        result.addTemplate(templateGenerator.generateSearchTemplate(application, entity))
                .addTemplate(templateGenerator.generateCreateTemplate(application, entity));
        return result;
    }

    private Link getSelfLink(Application application, Entity entity) {
        return linkTo(methodOn(ProfileRestController.class).getEntityProfile(application, entity.getPathSegment()))
                .withSelfRel();
    }

    private Link getEntityCollectionLink(Application application, Entity entity) {
        return linkTo(methodOn(EntityRestController.class)
                .listEntity(application, entity.getPathSegment(), 0, null, Map.of()))
                .withRel(IanaLinkRelations.DESCRIBES).expand()
                .withName(IanaLinkRelations.COLLECTION_VALUE);
    }

    private Link getEntityItemLink(Application application, Entity entity) {
        return linkTo(methodOn(EntityRestController.class)
                .getEntity(application, entity.getPathSegment(), null))
                .withRel(IanaLinkRelations.DESCRIBES)
                .withName(IanaLinkRelations.ITEM_VALUE);
    }
}
