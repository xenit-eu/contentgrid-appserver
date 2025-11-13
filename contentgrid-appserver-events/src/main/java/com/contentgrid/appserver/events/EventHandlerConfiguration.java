package com.contentgrid.appserver.events;

import com.contentgrid.appserver.rest.RestEntityFormatter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class EventHandlerConfiguration {
    @Bean
    @ConfigurationProperties(prefix = "contentgrid")
    ContentGridEventHandlerProperties contentgridEventHandlerProperties() {
        return new ContentGridEventHandlerProperties();
    }

    @Bean
    @ConditionalOnBooleanProperty(value = "contentgrid.events.rabbitmq.enabled", matchIfMissing = true)
    @ConditionalOnBean({RestEntityFormatter.class})
    RabbitMqEventHandlers rabbitEventHandlers(
            RabbitTemplate rabbitTemplate,
            ContentGridEventHandlerProperties contentGridProps,
            RestEntityFormatter formatter,
            ObjectMapper objectMapper
    ) {
        return new RabbitMqEventHandlers(rabbitTemplate, contentGridProps, formatter, objectMapper);
    }
}
