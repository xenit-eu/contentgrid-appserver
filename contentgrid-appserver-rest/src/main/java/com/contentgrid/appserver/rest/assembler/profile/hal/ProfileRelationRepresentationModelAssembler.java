package com.contentgrid.appserver.rest.assembler.profile.hal;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.relations.ManyToManyRelation;
import com.contentgrid.appserver.application.model.relations.ManyToOneRelation;
import com.contentgrid.appserver.application.model.relations.OneToManyRelation;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.application.model.relations.flags.HiddenEndpointFlag;
import com.contentgrid.appserver.rest.ProfileRestController;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.hateoas.Link;

@RequiredArgsConstructor
public class ProfileRelationRepresentationModelAssembler {

    public Optional<ProfileRelationRepresentationModel> toModel(Application application, Relation relation) {
        var sourceEndPoint = relation.getSourceEndPoint();
        if (sourceEndPoint.hasFlag(HiddenEndpointFlag.class)) {
            // ignore hidden endpoints
            return Optional.empty();
        }

        var isManySourcePerTarget = relation instanceof ManyToOneRelation || relation instanceof ManyToManyRelation;
        var isManyTargetPerSource = relation instanceof OneToManyRelation || relation instanceof ManyToManyRelation;

        var relationModel = ProfileRelationRepresentationModel.builder()
                .name(sourceEndPoint.getName().getValue())
                .title(readTitle(application, relation))
                .description(sourceEndPoint.getDescription())
                .manySourcePerTarget(isManySourcePerTarget)
                .manyTargetPerSource(isManyTargetPerSource)
                .required(sourceEndPoint.isRequired())
                .build();

        relationModel.add(getTargetProfileLink(application, relation));

        return Optional.of(relationModel);
    }

    private String readTitle(Application application, Relation relation) {
        return null; // TODO: resolve title (ACC-2230)
    }

    private Link getTargetProfileLink(Application application, Relation relation) {
        return linkTo(methodOn(ProfileRestController.class)
                .getEntityProfile(application, relation.getTargetEndPoint().getEntity().getPathSegment()))
                .withRel(BlueprintLinkRelations.TARGET_ENTITY);
    }

}
