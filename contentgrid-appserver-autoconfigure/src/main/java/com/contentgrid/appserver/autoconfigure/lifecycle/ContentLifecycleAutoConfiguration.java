package com.contentgrid.appserver.autoconfigure.lifecycle;

import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.autoconfigure.json.schema.ApplicationResolverAutoConfiguration;
import com.contentgrid.appserver.content.lifecycle.ContentDeletionJob;
import com.contentgrid.appserver.content.lifecycle.ContentLifecycleProperties;
import com.contentgrid.appserver.content.lifecycle.ContentReferenceTracker;
import com.contentgrid.appserver.content.lifecycle.ContentReferenceVerificationQuery;
import com.contentgrid.appserver.content.lifecycle.DeferredContentReferenceTracker;
import com.contentgrid.appserver.content.lifecycle.JooqContentReferenceTracker;
import com.contentgrid.appserver.content.lifecycle.JooqContentReferenceVerificationQuery;
import com.contentgrid.appserver.contentstore.api.ContentStore;
import com.contentgrid.appserver.registry.ApplicationResolver;
import io.micrometer.core.instrument.MeterRegistry;
import org.jooq.DSLContext;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@AutoConfiguration(after = ApplicationResolverAutoConfiguration.class)
@ConditionalOnClass(JooqContentReferenceTracker.class)
@EnableConfigurationProperties(ContentLifecycleProperties.class)
public class ContentLifecycleAutoConfiguration {

    @Bean
    JooqContentReferenceTracker jooqContentReferenceTracker(DSLContext dslContext) {
        return new JooqContentReferenceTracker(dslContext);
    }

    @Bean
    @Primary
    ContentReferenceTracker contentReferenceTracker(JooqContentReferenceTracker jooqTracker) {
        return new DeferredContentReferenceTracker(jooqTracker);
    }

    @Bean
    ContentReferenceVerificationQuery contentReferenceVerificationQuery(DSLContext dslContext) {
        return new JooqContentReferenceVerificationQuery(dslContext);
    }

    @Bean
    @ConditionalOnBean({ApplicationResolver.class, ContentStore.class, MeterRegistry.class})
    ContentDeletionJob contentDeletionJob(
            DSLContext dslContext,
            ContentStore contentStore,
            ContentReferenceVerificationQuery verificationQuery,
            ApplicationResolver applicationResolver,
            MeterRegistry meterRegistry,
            ContentLifecycleProperties properties) {
        return new ContentDeletionJob(
                dslContext,
                contentStore,
                verificationQuery,
                applicationResolver.resolve(ApplicationName.of("default")),
                meterRegistry,
                properties);
    }
}
