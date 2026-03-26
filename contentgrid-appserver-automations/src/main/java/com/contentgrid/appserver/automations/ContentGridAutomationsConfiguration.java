package com.contentgrid.appserver.automations;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.contentgrid.appserver.automations.rest.AutomationAnnotationRepresentationModelAssembler;
import com.contentgrid.appserver.automations.rest.AutomationRepresentationModelAssembler;
import com.contentgrid.appserver.automations.rest.AutomationsRestController;
import com.contentgrid.appserver.rest.assembler.RootRepresentationModel;
import com.contentgrid.appserver.rest.links.curie.CurieProviderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.hateoas.server.RepresentationModelProcessor;

@Import({
        AutomationRepresentationModelAssembler.class,
        AutomationAnnotationRepresentationModelAssembler.class,
        AutomationsRestController.class,
})
@Configuration(proxyBeanMethods = false)
public class ContentGridAutomationsConfiguration {

    @Bean
    CurieProviderCustomizer automationCurieProvider() {
        return CurieProviderCustomizer.register(AutomationLinkRelations.CURIE, AutomationLinkRelations.TEMPLATE);
    }

    @Bean
    RepresentationModelProcessor<RootRepresentationModel> automationRepositoryLinksRepresentationModelProcessor() {
        // This must be a class instead of a lambda so the generic parameter can be determined by spring-hateoas
        return new RepresentationModelProcessor<RootRepresentationModel>() {
            @Override
            public RootRepresentationModel process(RootRepresentationModel model) {
                // TODO: application unknown, but static resource /.contentgrid/automations
                return model.add(
                        linkTo(methodOn(AutomationsRestController.class).getAutomations(null, null))
                                .withRel(AutomationLinkRelations.REGISTRATIONS)
                );
            }
        };
    }

}
