package com.contentgrid.appserver.application.model.links;

import com.contentgrid.hateoas.uritemplate.ParameterizedUriTemplate;
import com.contentgrid.hateoas.uritemplate.SubstitutionVariableDefinition;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;

public sealed interface UriTemplateDefinition {

    ParameterizedUriTemplate<EntityLinkSubstitutionVariables> getTemplate();

    @RequiredArgsConstructor
    @Getter
    enum EntityLinkSubstitutionVariables implements SubstitutionVariableDefinition {
        APPLICATION_ID("application.id"),
        ENTITY_ID("entity.id"),
        ENTITY_LINK("entity.link"),
        ENTITY_NAME("entity.name"),
        OWNER_NAME("owner.name"),
        OWNER_VALUE("owner.value"),
        OWNER_LINK("owner.link"),
        ;

        private final String name;
    }

    @Value
    class SimpleUriTemplateDefinition implements UriTemplateDefinition {
        @NonNull
        ParameterizedUriTemplate<EntityLinkSubstitutionVariables> template;
    }

    @Value
    class AutomationUriTemplateDefinition implements UriTemplateDefinition {
        @NonNull
        String automationSystem;
        @NonNull
        String basePathName;

        @NonNull
        ParameterizedUriTemplate<EntityLinkSubstitutionVariables> template;

    }

}
