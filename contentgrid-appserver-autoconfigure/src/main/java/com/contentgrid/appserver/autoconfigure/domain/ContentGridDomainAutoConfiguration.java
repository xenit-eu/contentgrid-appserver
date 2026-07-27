package com.contentgrid.appserver.autoconfigure.domain;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.RelationName;
import com.contentgrid.appserver.autoconfigure.events.ContentGridEventsAutoConfiguration;
import com.contentgrid.appserver.domain.ConfigurationProperties;
import com.contentgrid.appserver.domain.ConfigurationPropertiesFactory;
import com.contentgrid.appserver.domain.LinkUriProviderFactory;
import com.contentgrid.appserver.domain.content.ContentStoreResolver;
import com.contentgrid.appserver.domain.ContentApi;
import com.contentgrid.appserver.domain.ContentApiImpl;
import com.contentgrid.appserver.domain.DatamodelApiImpl;
import com.contentgrid.appserver.domain.DomainEventDispatcher;
import com.contentgrid.appserver.domain.automations.AutomationsModelResolver;
import com.contentgrid.appserver.domain.automations.BlueprintArtifactAutomationsModelResolver;
import com.contentgrid.appserver.domain.automations.CachingAutomationsModelResolver;
import com.contentgrid.appserver.domain.data.EntityInstance;
import com.contentgrid.appserver.domain.paging.cursor.CursorCodec;
import com.contentgrid.appserver.domain.paging.cursor.RequestIntegrityCheckCursorCodec;
import com.contentgrid.appserver.domain.paging.cursor.SimplePageBasedCursorCodec;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifact;
import com.contentgrid.appserver.domain.LinkUriProvider;
import com.contentgrid.appserver.domain.values.EntityIdentity;
import com.contentgrid.appserver.query.engine.api.QueryEngine;
import java.net.URI;
import java.time.Clock;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after={ContentGridEventsAutoConfiguration.class})
@ConditionalOnClass({DatamodelApiImpl.class})
public class ContentGridDomainAutoConfiguration {

    @Autowired
    private ApplicationContext applicationContext;

    @Bean
    @ConditionalOnMissingBean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    DomainEventDispatcher noopDomainEventDispatcher() {
        return new DomainEventDispatcher() {
            @Override
            public void dispatchCreate(Application application, EntityInstance instance) {}

            @Override
            public void dispatchUpdate(Application application, EntityInstance oldInstance, EntityInstance newInstance) {}

            @Override
            public void dispatchDelete(Application application, EntityInstance instance) {}
        };
    }

    @Bean
    DatamodelApiImpl datamodelApi(
            QueryEngine queryEngine,
            ContentStoreResolver contentStoreResolver,
            DomainEventDispatcher dispatcher,
            ObjectProvider<LinkUriProviderFactory> linkUriProviderFactory,
            ConfigurationPropertiesFactory configurationPropertiesFactory,
            CursorCodec cursorCodec,
            Clock clock) {
        return new DatamodelApiImpl(
                queryEngine,
                contentStoreResolver,
                dispatcher,
                linkUriProviderFactory.getIfUnique(() -> app -> new NoneLinkUriProvider()),
                configurationPropertiesFactory,
                cursorCodec,
                clock
        );
    }

    @Bean
    ContentApi contentApi(DatamodelApiImpl datamodelApi, ContentStoreResolver contentStoreResolver) {
        return new ContentApiImpl(datamodelApi, contentStoreResolver);
    }

    @Bean
    CursorCodec cursorCodec() {
        return new RequestIntegrityCheckCursorCodec(new SimplePageBasedCursorCodec());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(BlueprintArtifact.class)
    AutomationsModelResolver blueprintArtifactAutomationsResolver(BlueprintArtifact blueprintArtifact) {
        return new CachingAutomationsModelResolver(new BlueprintArtifactAutomationsModelResolver(blueprintArtifact));
    }

    @Bean
    ConfigurationPropertiesFactory contentGridConfigurationPropertiesFactory(
            @Value("${contentgrid.system.application-id:-}") String applicationId,
            ContentgridAutomationProperties automationProperties
    ) {
        return app -> new ConfigurationProperties() {
            @Override
            public String getApplicationId() {
                return applicationId;
            }

            @Override
            public Optional<URI> getAutomationSystemBaseUrl(String automationSystemId, String basePathName) {
                return automationProperties.getRegistration(automationSystemId)
                        .flatMap(reg -> reg.getBasePath(basePathName));
            }
        };

    }

    @Bean
    @org.springframework.boot.context.properties.ConfigurationProperties(prefix = "contentgrid.automation")
    ContentgridAutomationProperties contentgridAutomationProperties() {
        return new ContentgridAutomationProperties();
    }

    private static class NoneLinkUriProvider implements LinkUriProvider {

        @Override
        public String createEntityLink(EntityIdentity entityIdentity) {
            return null;
        }

        @Override
        public String createAttributeLink(EntityIdentity entityIdentity, AttributeName attributeName) {
            return null;
        }

        @Override
        public String createRelationLink(EntityIdentity entityIdentity, RelationName relationName) {
            return null;
        }
    }
}
