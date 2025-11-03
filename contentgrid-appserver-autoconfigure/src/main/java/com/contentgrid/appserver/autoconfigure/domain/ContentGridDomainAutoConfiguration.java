package com.contentgrid.appserver.autoconfigure.domain;

import com.contentgrid.appserver.contentstore.api.ContentStore;
import com.contentgrid.appserver.domain.ContentApi;
import com.contentgrid.appserver.domain.ContentApiImpl;
import com.contentgrid.appserver.domain.DatamodelApiImpl;
import com.contentgrid.appserver.domain.paging.cursor.CursorCodec;
import com.contentgrid.appserver.domain.paging.cursor.RequestIntegrityCheckCursorCodec;
import com.contentgrid.appserver.domain.paging.cursor.SimplePageBasedCursorCodec;
import com.contentgrid.appserver.query.engine.api.QueryEngine;
import java.time.Clock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass({DatamodelApiImpl.class})
public class ContentGridDomainAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    DatamodelApiImpl datamodelApi(QueryEngine queryEngine, ContentStore contentStore, CursorCodec cursorCodec, Clock clock) {
        return new DatamodelApiImpl(queryEngine, contentStore, cursorCodec, clock);
    }

    @Bean
    ContentApi contentApi(DatamodelApiImpl datamodelApi, ContentStore contentStore) {
        return new ContentApiImpl(datamodelApi, contentStore);
    }

    @Bean
    CursorCodec cursorCodec() {
        return new RequestIntegrityCheckCursorCodec(new SimplePageBasedCursorCodec());
    }
}
