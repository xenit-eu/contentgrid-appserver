package com.contentgrid.appserver.rest.assembler.profile;

import com.contentgrid.appserver.rest.links.curie.CurieProviderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BlueprintLinkRelationsConfiguration {

    @Bean
    CurieProviderCustomizer BlueprintCurieProviderCustomizer() {
        return CurieProviderCustomizer.register(BlueprintLinkRelations.CURIE, BlueprintLinkRelations.TEMPLATE);
    }
}
