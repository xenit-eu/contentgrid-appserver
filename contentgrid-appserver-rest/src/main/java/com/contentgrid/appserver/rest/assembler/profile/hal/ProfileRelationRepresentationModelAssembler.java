package com.contentgrid.appserver.rest.assembler.profile.hal;

import com.contentgrid.appserver.application.model.relations.ManyToManyRelation;
import com.contentgrid.appserver.application.model.relations.ManyToOneRelation;
import com.contentgrid.appserver.application.model.relations.OneToManyRelation;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.application.model.relations.flags.HiddenEndpointFlag;
import com.contentgrid.appserver.rest.assembler.profile.BlueprintLinkRelations;
import com.contentgrid.appserver.rest.assembler.profile.hal.ProfileEntityRepresentationModelAssembler.Context;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ProfileRelationRepresentationModelAssembler {

    public Optional<ProfileRelationRepresentationModel> toModel(Context context, Relation relation) {
        var sourceEndPoint = relation.getSourceEndPoint();
        if (sourceEndPoint.hasFlag(HiddenEndpointFlag.class)) {
            // ignore hidden endpoints
            return Optional.empty();
        }

        var isManySourcePerTarget = relation instanceof ManyToOneRelation || relation instanceof ManyToManyRelation;
        var isManyTargetPerSource = relation instanceof OneToManyRelation || relation instanceof ManyToManyRelation;

        var relationModel = ProfileRelationRepresentationModel.builder()
                .name(sourceEndPoint.getName().getValue())
                .title(readTitle(context, relation))
                .description(sourceEndPoint.getDescription())
                .manySourcePerTarget(isManySourcePerTarget)
                .manyTargetPerSource(isManyTargetPerSource)
                .required(sourceEndPoint.isRequired())
                .build();

        relationModel.add(
                context.linkFactoryProvider().toProfile(relation.getTargetEndPoint().getEntity())
                        .withRel(BlueprintLinkRelations.TARGET_ENTITY)
                        .withName(null)
        );

        return Optional.of(relationModel);
    }

    private String readTitle(Context context, Relation relation) {
        return null; // TODO: resolve title (ACC-2230)
    }

}
