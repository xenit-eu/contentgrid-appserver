package com.contentgrid.appserver.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
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
    EventHandlers rabbitEventHandlers(RabbitTemplate rabbitTemplate, RabbitProperties rabbitProperties,
            ContentGridEventHandlerProperties contentGridProps, EventMapper eventMapper) {
        return new RabbitMqEventHandlers(rabbitTemplate, rabbitProperties, contentGridProps, eventMapper);
    }

    @Bean
    EventMapper eventMapper(ObjectMapper objectMapper) {
        objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        return new EventMapper(objectMapper);
    }
}
