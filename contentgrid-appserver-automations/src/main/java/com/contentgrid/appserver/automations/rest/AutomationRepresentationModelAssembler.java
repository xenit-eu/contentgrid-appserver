package com.contentgrid.appserver.automations.rest;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.contentgrid.appserver.automations.rest.AutomationsModel.AutomationModel;
import com.contentgrid.appserver.rest.links.factory.LinkFactoryProvider;
import com.contentgrid.hateoas.spring.server.RepresentationModelContextAssembler;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.hateoas.CollectionModel;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AutomationRepresentationModelAssembler implements
        RepresentationModelContextAssembler<AutomationModel, AutomationRepresentationModel, LinkFactoryProvider> {

    @NonNull
    private final AutomationAnnotationRepresentationModelAssembler annotationAssembler;

    @Override
    public AutomationRepresentationModel toModel(AutomationModel automation, LinkFactoryProvider context) {
        return toModel(automation, context, false);
    }

    public AutomationRepresentationModel toModel(AutomationModel automation, LinkFactoryProvider context, boolean expandAnnotations) {
        AutomationRepresentationModel result;
        if (expandAnnotations) {
            result = AutomationRepresentationModel.expandedFrom(automation, annotationAssembler.toCollectionModel(
                    automation.getAnnotations(), context));
        } else {
            result = AutomationRepresentationModel.from(automation);
        }

        result.add(linkTo(methodOn(AutomationsRestController.class).getAutomation(context, automation.getId())).withSelfRel());

        return result;
    }

    @Override
    public CollectionModel<AutomationRepresentationModel> toCollectionModel(Iterable<? extends AutomationModel> automations, LinkFactoryProvider context) {
        var result = RepresentationModelContextAssembler.super.toCollectionModel(automations, context);
        result.add(linkTo(methodOn(AutomationsRestController.class).getAutomations(context)).withSelfRel());
        return result;
    }
}
