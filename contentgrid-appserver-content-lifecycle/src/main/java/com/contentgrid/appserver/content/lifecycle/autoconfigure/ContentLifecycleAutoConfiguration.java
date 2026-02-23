package com.contentgrid.appserver.content.lifecycle.autoconfigure;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.content.lifecycle.ContentDeletionJob;
import com.contentgrid.appserver.content.lifecycle.ContentLifecycleProperties;
import com.contentgrid.appserver.content.lifecycle.ContentReferenceTracker;
import com.contentgrid.appserver.content.lifecycle.ContentReferenceVerificationQuery;
import com.contentgrid.appserver.content.lifecycle.DeferredContentReferenceTracker;
import com.contentgrid.appserver.content.lifecycle.JooqContentReferenceTracker;
import com.contentgrid.appserver.contentstore.api.ContentStore;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.function.Supplier;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnBean({DSLContext.class, ContentStore.class})
@ConditionalOnProperty(prefix = "contentgrid.content.lifecycle", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ContentLifecycleProperties.class)
public class ContentLifecycleAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JooqContentReferenceTracker jooqContentReferenceTracker(DSLContext dslContext, ContentLifecycleProperties properties) {
        var tracker = new JooqContentReferenceTracker(dslContext, properties.getDeletion().getGracePeriod());
        tracker.setupTables();
        return tracker;
    }

    @Bean
    @ConditionalOnMissingBean(ContentReferenceTracker.class)
    public DeferredContentReferenceTracker contentReferenceTracker(JooqContentReferenceTracker delegate) {
        return new DeferredContentReferenceTracker(delegate);
    }

    @Bean
    @ConditionalOnMissingBean
    public ContentReferenceVerificationQuery contentReferenceVerificationQuery(
            DSLContext dslContext,
            @Autowired Supplier<Application> applicationSupplier
    ) {
        return new ContentReferenceVerificationQuery(dslContext, applicationSupplier);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "contentgrid.content.lifecycle.deletion", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ContentDeletionJob contentDeletionJob(
            DSLContext dslContext,
            ContentStore contentStore,
            ContentReferenceVerificationQuery verificationQuery,
            ContentLifecycleProperties properties,
            MeterRegistry meterRegistry
    ) {
        return new ContentDeletionJob(dslContext, contentStore, verificationQuery, properties, meterRegistry);
    }
}
