package com.contentgrid.appserver.automations.rest;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.automations.model.AutomationsModel.AutomationModel;
import com.contentgrid.appserver.rest.links.factory.LinkFactoryProvider;
import com.contentgrid.hateoas.spring.server.RepresentationModelContextAssembler;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AutomationRepresentationModelAssembler implements
        RepresentationModelContextAssembler<AutomationModel, AutomationRepresentationModel, AutomationRepresentationModelContext> {

    @NonNull
    private final AutomationAnnotationRepresentationModelAssembler annotationAssembler;

    @Override
    public AutomationRepresentationModel toModel(AutomationModel automation, AutomationRepresentationModelContext context) {
        return toModel(automation, context, false);
    }

    public AutomationRepresentationModel toModel(AutomationModel automation, AutomationRepresentationModelContext context, boolean expandAnnotations) {
        AutomationRepresentationModel result;
        if (expandAnnotations) {
            result = AutomationRepresentationModel.expandedFrom(automation, annotationAssembler.toCollectionModel(
                    automation.getAnnotations(), context.linkFactoryProvider()));
        } else {
            result = AutomationRepresentationModel.from(automation);
        }

        result.add(linkTo(methodOn(AutomationsRestController.class).getAutomation(context.application(), context.linkFactoryProvider(), automation.getId())).withSelfRel());

        return result;
    }

    @Override
    public CollectionModel<AutomationRepresentationModel> toCollectionModel(Iterable<? extends AutomationModel> automations, AutomationRepresentationModelContext context) {
        var result = RepresentationModelContextAssembler.super.toCollectionModel(automations, context);
        result.add(linkTo(methodOn(AutomationsRestController.class).getAutomations(context.application(), context.linkFactoryProvider())).withSelfRel());
        return result;
    }

    public RepresentationModelAssembler<AutomationModel, AutomationRepresentationModel> withContext(
            Application application, LinkFactoryProvider linkFactoryProvider
    ) {
        return withContext(new AutomationRepresentationModelContext(application, linkFactoryProvider));
    }
}
