package com.contentgrid.appserver.rest.profile.assembler;

import com.contentgrid.appserver.rest.hal.links.curie.CurieProviderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BlueprintLinkRelationsConfiguration {

    @Bean
    CurieProviderCustomizer blueprintCurieProviderCustomizer() {
        return CurieProviderCustomizer.register(BlueprintLinkRelations.CURIE, BlueprintLinkRelations.TEMPLATE);
    }
}
