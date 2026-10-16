package com.contentgrid.appserver.rest.entity.assembler;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.i18n.UserLocales;
import com.contentgrid.appserver.application.model.links.LinkIdentity.NamedLink;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.domain.data.EntityLinkInstance;
import com.contentgrid.appserver.domain.paging.cursor.EncodedCursorPagination;
import com.contentgrid.appserver.rest.entity.assembler.EntityDataRepresentationModelAssembler.EntityContext;
import com.contentgrid.appserver.rest.hal.links.factory.LinkFactoryProvider;
import com.contentgrid.appserver.rest.hal.serializer.RenderAsLinkRelation;
import com.contentgrid.hateoas.spring.server.RepresentationModelContextAssembler;
import java.util.Map;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;

@Component
@RequiredArgsConstructor
public class EntityLinkDataRepresentationModelAssembler implements
        RepresentationModelContextAssembler<EntityLinkInstance, EntityLinkDataRepresentationModel, EntityContext> {

    @Override
    public EntityLinkDataRepresentationModel toModel(
            @NonNull EntityLinkInstance entityLinkInstance,
            @NonNull EntityContext context) {
        var linkIdentity =entityLinkInstance.getEntityLinkData().getIdentity();
        var model = EntityLinkDataRepresentationModel.from(entityLinkInstance);
        var halLink = Link.of(entityLinkInstance.getEntityLinkData().getHref(), linkIdentity.rel().toASCIIString()); // TODO ACC-3004 probs need to clean up identity for the 3 types
        if (linkIdentity instanceof NamedLink namedLink) {
            halLink = halLink
                    .withRel(RenderAsLinkRelation.array(halLink.getRel()))
                    .withName(namedLink.name());
        }
        if (entityLinkInstance.getEntityLinkData().getProfile() != null) {
            halLink = halLink.withProfile(entityLinkInstance.getEntityLinkData().getProfile().toASCIIString());
        }
        model.add(halLink);
        return model.addTemplate(
                context.templateGenerator().generateUpdateTemplate(entityLinkInstance.getEntityIdentity().getEntityName())
        );
    }

    public RepresentationModelAssembler<EntityLinkInstance, EntityLinkDataRepresentationModel> withContext(
            Application application,
            EntityName entityName,
            UserLocales userLocales,
            LinkFactoryProvider linkFactoryProvider) {
        return withContext(
                application,
                entityName,
                userLocales,
                linkFactoryProvider,
                MultiValueMap.fromSingleValue(Map.of()),
                null);
    }

    public RepresentationModelAssembler<EntityLinkInstance, EntityLinkDataRepresentationModel> withContext(
            Application application,
            EntityName entityName,
            UserLocales userLocales,
            LinkFactoryProvider linkFactoryProvider,
            MultiValueMap<String, String> params,
            EncodedCursorPagination pagination) {
        return withContext(new EntityContext(application, entityName, userLocales, linkFactoryProvider, params, pagination));
    }

    @Override
    public RepresentationModelAssembler<EntityLinkInstance, EntityLinkDataRepresentationModel> withContext(
            EntityContext context) {
        return RepresentationModelContextAssembler.super.withContext(context);
    }
}
