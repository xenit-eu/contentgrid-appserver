package com.contentgrid.appserver.automations;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.contentgrid.appserver.automations.rest.AutomationAnnotationRepresentationModelAssembler;
import com.contentgrid.appserver.automations.rest.AutomationRepresentationModelAssembler;
import com.contentgrid.appserver.automations.rest.AutomationsRestController;
import com.contentgrid.appserver.rest.assembler.EmptyRepresentationModel;
import com.contentgrid.appserver.rest.links.curie.CurieProviderCustomizer;
import com.contentgrid.thunx.spring.security.AbacContextSupplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ResourceLoader;
import org.springframework.hateoas.server.RepresentationModelProcessor;

@Import({
        AutomationRepresentationModelAssembler.class,
        AutomationAnnotationRepresentationModelAssembler.class,
})
@Configuration(proxyBeanMethods = false)
public class ContentGridAutomationsConfiguration {

    private static final String AUTOMATIONS_RESOURCE = "classpath:automation/automations.json";

    @Bean
    AutomationsRestController automationsRestController(
            ResourceLoader resourceLoader,
            AutomationRepresentationModelAssembler assembler,
            AbacContextSupplier abacContextSupplier
    ) {
        return new AutomationsRestController(
                resourceLoader.getResource(AUTOMATIONS_RESOURCE),
                assembler,
                abacContextSupplier
        );
    }

    @Bean
    CurieProviderCustomizer automationCurieProvider() {
        return CurieProviderCustomizer.register(AutomationLinkRelations.CURIE, AutomationLinkRelations.TEMPLATE);
    }

    @Bean
    RepresentationModelProcessor<EmptyRepresentationModel> automationRepositoryLinksRepresentationModelProcessor() {
        // This must be a class instead of a lambda so the generic parameter can be determined by spring-hateoas
        return new RepresentationModelProcessor<EmptyRepresentationModel>() {
            @Override
            public EmptyRepresentationModel process(EmptyRepresentationModel model) {
                return model.add(
                        linkTo(methodOn(AutomationsRestController.class).getAutomations(null))
                                .withRel(AutomationLinkRelations.REGISTRATIONS)
                );
            }
        };
    }

}
