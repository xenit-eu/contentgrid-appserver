package com.contentgrid.appserver.autoconfigure.events;

import com.contentgrid.appserver.autoconfigure.query.engine.JOOQQueryEngineAutoConfiguration;
import com.contentgrid.appserver.domain.DomainEventDispatcher;
import com.contentgrid.appserver.domain.events.EntityFormatter;
import com.contentgrid.appserver.events.EventHandlerConfiguration;
import com.contentgrid.appserver.events.RabbitMqEventHandlers;
import org.springframework.beans.factory.ObjectProvider;
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
@ConditionalOnClass(EventHandlerConfiguration.class)
@Import(EventHandlerConfiguration.class)
public class ContentGridEventsAutoConfiguration {

    @Bean
    DomainEventDispatcher eventDispatcher(ObjectProvider<EntityFormatter> formatterProvider, ObjectProvider<RabbitMqEventHandlers> rabbitProvider) {
        var formatter = formatterProvider.getIfAvailable();
        var rabbit = rabbitProvider.getIfAvailable();
        return new DomainEventDispatcher(formatter, rabbit);
    }
}
