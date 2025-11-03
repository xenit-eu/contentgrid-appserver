package com.contentgrid.appserver.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
    @ConditionalOnProperty(value = {"spring.rabbitmq.host"})
    RabbitMqEventHandlers rabbitEventHandlers(RabbitTemplate rabbitTemplate,
            RabbitProperties rabbitProperties,
            ContentGridEventHandlerProperties contentGridProps,
            ObjectMapper objectMapper
    ) {
        return new RabbitMqEventHandlers(rabbitTemplate, rabbitProperties, contentGridProps, objectMapper);
    }

}
