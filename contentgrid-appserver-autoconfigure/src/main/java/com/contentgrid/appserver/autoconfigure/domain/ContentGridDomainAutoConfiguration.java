package com.contentgrid.appserver.autoconfigure.domain;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.autoconfigure.events.ContentGridEventsAutoConfiguration;
import com.contentgrid.appserver.contentstore.api.ContentStore;
import com.contentgrid.appserver.domain.ContentApi;
import com.contentgrid.appserver.domain.ContentApiImpl;
import com.contentgrid.appserver.domain.DatamodelApiImpl;
import com.contentgrid.appserver.domain.DomainEventDispatcher;
import com.contentgrid.appserver.domain.automations.BlueprintArtifactAutomationsModelResolver;
import com.contentgrid.appserver.domain.automations.AutomationsModelResolver;
import com.contentgrid.appserver.domain.automations.CachingAutomationsModelResolver;
import com.contentgrid.appserver.domain.data.EntityInstance;
import com.contentgrid.appserver.domain.paging.cursor.CursorCodec;
import com.contentgrid.appserver.domain.paging.cursor.RequestIntegrityCheckCursorCodec;
import com.contentgrid.appserver.domain.paging.cursor.SimplePageBasedCursorCodec;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifact;
import com.contentgrid.appserver.query.engine.api.QueryEngine;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
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
    DatamodelApiImpl datamodelApi(QueryEngine queryEngine, ContentStore contentStore, DomainEventDispatcher dispatcher,
            CursorCodec cursorCodec, Clock clock) {
        return new DatamodelApiImpl(queryEngine, contentStore, dispatcher, cursorCodec, clock);
    }

    @Bean
    ContentApi contentApi(DatamodelApiImpl datamodelApi, ContentStore contentStore) {
        return new ContentApiImpl(datamodelApi, contentStore);
    }

    @Bean
    CursorCodec cursorCodec() {
        return new RequestIntegrityCheckCursorCodec(new SimplePageBasedCursorCodec());
    }

    @Bean
    @ConditionalOnMissingBean
    AutomationsModelResolver blueprintArtifactAutomationsResolver(BlueprintArtifact blueprintArtifact) {
        return new CachingAutomationsModelResolver(new BlueprintArtifactAutomationsModelResolver(blueprintArtifact));
    }
}
