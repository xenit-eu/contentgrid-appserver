package com.contentgrid.appserver.rest.automations;

import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.domain.automations.AutomationsModel.AutomationAnnotationModel;
import com.contentgrid.appserver.rest.hal.links.factory.LinkFactoryProvider;
import com.contentgrid.hateoas.spring.server.RepresentationModelContextAssembler;
import lombok.RequiredArgsConstructor;
import org.springframework.hateoas.Link;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AutomationAnnotationRepresentationModelAssembler implements
        RepresentationModelContextAssembler<AutomationAnnotationModel, AutomationAnnotationRepresentationModel, LinkFactoryProvider> {

    @Override
    public AutomationAnnotationRepresentationModel toModel(AutomationAnnotationModel annotation, LinkFactoryProvider context) {
        return AutomationAnnotationRepresentationModel.from(annotation)
                .add(getTargetEntityLink(annotation, context));
    }

    private Link getTargetEntityLink(AutomationAnnotationModel annotation, LinkFactoryProvider linkFactoryProvider) {
        return linkFactoryProvider.toProfile(EntityName.of(annotation.getSubject().get("entity")))
                .withRel(AutomationLinkRelations.TARGET_ENTITY);
    }
}
