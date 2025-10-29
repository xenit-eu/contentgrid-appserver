package com.contentgrid.appserver.autoconfigure.events;

import com.contentgrid.appserver.autoconfigure.query.engine.JOOQQueryEngineAutoConfiguration;
import com.contentgrid.appserver.domain.events.DomainEventDispatcher;
import com.contentgrid.appserver.domain.events.EntityFormatter;
import com.contentgrid.appserver.events.RabbitMqEventHandlers;
import org.springframework.beans.factory.ObjectProvider;
import com.contentgrid.appserver.events.EventHandlerConfiguration;
import com.contentgrid.appserver.query.engine.api.EventConsumer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@AutoConfiguration(before = {
        JOOQQueryEngineAutoConfiguration.class,
}, after = {
        RabbitAutoConfiguration.class,
})
@ConditionalOnWebApplication
@ConditionalOnClass(EventHandlerConfiguration.class)
@Import(EventHandlerConfiguration.class)
public class ContentGridEventsAutoConfiguration {

    @Bean
    EventConsumer eventConsumer(EntityFormatter formatter, ObjectProvider<RabbitMqEventHandlers> rabbitProvider) {
        var rabbit = rabbitProvider.getIfAvailable();
        return (rabbit != null) ? new DomainEventDispatcher(formatter, rabbit) : new DomainEventDispatcher(formatter);
    }
}
