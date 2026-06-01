package com.contentgrid.appserver.rest.automations;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.domain.automations.AutomationsModel.AutomationModel;
import com.contentgrid.appserver.rest.hal.links.factory.LinkFactoryProvider;
import com.contentgrid.hateoas.spring.server.RepresentationModelContextAssembler;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AutomationRepresentationModelAssembler implements
        RepresentationModelContextAssembler<AutomationModel, AutomationRepresentationModel, AutomationRepresentationModelAssembler.AutomationRepresentationModelContext> {

    @NonNull
    private final AutomationAnnotationRepresentationModelAssembler annotationAssembler;

    @Override
    public AutomationRepresentationModel toModel(AutomationModel automation, AutomationRepresentationModelContext context) {
        AutomationRepresentationModel result;
        if (context.expandAnnotations()) {
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
            Application application, LinkFactoryProvider linkFactoryProvider, boolean expandAnnotations
    ) {
        return withContext(new AutomationRepresentationModelContext(application, linkFactoryProvider, expandAnnotations));
    }

    record AutomationRepresentationModelContext(Application application, LinkFactoryProvider linkFactoryProvider, boolean expandAnnotations) {}
}
