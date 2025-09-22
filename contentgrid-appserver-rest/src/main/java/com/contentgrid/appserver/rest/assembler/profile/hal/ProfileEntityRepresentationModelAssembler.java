package com.contentgrid.appserver.rest.assembler.profile.hal;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.i18n.UserLocales;
import com.contentgrid.appserver.rest.EntityRestController;
import com.contentgrid.appserver.rest.assembler.profile.hal.ProfileEntityRepresentationModelAssembler.Context;
import com.contentgrid.appserver.rest.hal.forms.HalFormsTemplate;
import com.contentgrid.appserver.rest.hal.forms.HalFormsTemplateGenerator;
import com.contentgrid.appserver.rest.links.factory.LinkFactoryProvider;
import com.contentgrid.appserver.rest.links.factory.LinkFactoryProvider.CollectionParameters;
import com.contentgrid.hateoas.spring.server.RepresentationModelContextAssembler;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.Link;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProfileEntityRepresentationModelAssembler implements RepresentationModelContextAssembler<Entity, ProfileEntityRepresentationModel, Context> {

    private final ProfileAttributeRepresentationModelAssembler attributeAssembler = new ProfileAttributeRepresentationModelAssembler();
    private final ProfileRelationRepresentationModelAssembler relationAssembler = new ProfileRelationRepresentationModelAssembler();

    public record Context(Application application, UserLocales userLocales, LinkFactoryProvider linkFactoryProvider) {
        public HalFormsTemplateGenerator templateGenerator() {
            return new HalFormsTemplateGenerator(application(), linkFactoryProvider());
        }
    }

    @Override
    public ProfileEntityRepresentationModel toModel(Entity entity, Context context) {
        var translation = entity.getTranslations(context.userLocales());
        var result = ProfileEntityRepresentationModel.builder()
                .name(entity.getName().getValue())
                .title(translation.getSingularName())
                .description(translation.getDescription())
                .attributes(entity.getAllAttributes().stream()
                        .map(attribute -> attributeAssembler.toModel(context, entity, attribute))
                        .flatMap(Optional::stream)
                        .toList())
                .relations(context.application().getRelationsForSourceEntity(entity).stream()
                        .map(relation -> relationAssembler.toModel(context, relation))
                        .flatMap(Optional::stream)
                        .toList())
                .build();
        var collectionLink = context.linkFactoryProvider().toCollection(entity.getName(), CollectionParameters.defaults())
                .withRel(IanaLinkRelations.DESCRIBES)
                .withName(IanaLinkRelations.COLLECTION_VALUE);

        // Add links
        result.add(context.linkFactoryProvider().toProfile(entity.getName()).withSelfRel())
                .add(collectionLink)
                .add(getEntityItemLink(entity, context));

        // Add default template
        result.addTemplate(HalFormsTemplate.builder()
                .httpMethod(HttpMethod.HEAD)
                .target(collectionLink.getHref())
                .build());

        // Add search and create templates
        result.addTemplate(context.templateGenerator().generateSearchTemplate(entity.getName()))
                .addTemplate(context.templateGenerator().generateCreateTemplate(entity.getName()));
        return result;
    }

    private Link getEntityItemLink(Entity entity, Context context) {
        return linkTo(methodOn(EntityRestController.class)
                .getEntity(context.application(), entity.getPathSegment(), null, null, null))
                .withRel(IanaLinkRelations.DESCRIBES)
                .withName(IanaLinkRelations.ITEM_VALUE)
                .withTitle(entity.getTranslations(context.userLocales()).getSingularName());
    }

}
