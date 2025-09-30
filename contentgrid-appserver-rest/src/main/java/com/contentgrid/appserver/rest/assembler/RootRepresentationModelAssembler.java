package com.contentgrid.appserver.rest.assembler;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.rest.assembler.RootRepresentationModelAssembler.Context;
import com.contentgrid.appserver.rest.links.ContentGridLinkRelations;
import com.contentgrid.appserver.rest.links.factory.LinkFactoryProvider;
import com.contentgrid.appserver.rest.links.factory.LinkFactoryProvider.CollectionParameters;
import com.contentgrid.hateoas.spring.server.RepresentationModelContextAssembler;
import lombok.NonNull;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.server.RepresentationModelAssembler;

public class RootRepresentationModelAssembler implements
        RepresentationModelContextAssembler<Application, EmptyRepresentationModel, Context> {

    public RepresentationModelAssembler<Application, EmptyRepresentationModel> withContext(
            LinkFactoryProvider linkFactoryProvider) {
        return withContext(new Context(linkFactoryProvider));
    }

    public record Context(LinkFactoryProvider linkFactoryProvider) {

    }

    @Override
    public EmptyRepresentationModel toModel(@NonNull Application application, @NonNull Context context) {
        var model = new EmptyRepresentationModel();
        model.add(context.linkFactoryProvider().toRoot().withSelfRel())
                .add(context.linkFactoryProvider().toProfileRoot().withRel(IanaLinkRelations.PROFILE));
        for (var entity : application.getEntities()) {
            model.add(context.linkFactoryProvider().toCollection(entity.getName(), CollectionParameters.defaults()).withRel(ContentGridLinkRelations.ENTITY));
        }
        return model;
    }
}
