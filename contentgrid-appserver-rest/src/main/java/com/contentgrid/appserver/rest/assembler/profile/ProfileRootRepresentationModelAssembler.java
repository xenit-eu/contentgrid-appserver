package com.contentgrid.appserver.rest.assembler.profile;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.rest.assembler.EmptyRepresentationModel;
import com.contentgrid.appserver.rest.assembler.profile.ProfileRootRepresentationModelAssembler.Context;
import com.contentgrid.appserver.rest.links.ContentGridLinkRelations;
import com.contentgrid.appserver.rest.links.factory.LinkFactoryProvider;
import com.contentgrid.hateoas.spring.server.RepresentationModelContextAssembler;
import lombok.NonNull;
import org.springframework.hateoas.server.RepresentationModelAssembler;

public class ProfileRootRepresentationModelAssembler implements
        RepresentationModelContextAssembler<Application, EmptyRepresentationModel, Context> {

    public RepresentationModelAssembler<Application, EmptyRepresentationModel> withContext(
            LinkFactoryProvider linkFactoryProvider) {
        return withContext(new Context(linkFactoryProvider));
    }

    public record Context(LinkFactoryProvider linkFactoryProvider) {

    }

    @Override
    public EmptyRepresentationModel toModel(@NonNull Application application, Context context) {
        var result = new EmptyRepresentationModel();
        result.add(context.linkFactoryProvider().toProfileRoot().withSelfRel());
        for (var entity : application.getEntities()) {
            result.add(context.linkFactoryProvider().toProfile(entity.getName()).withRel(ContentGridLinkRelations.ENTITY));
        }
        return result;
    }

}
