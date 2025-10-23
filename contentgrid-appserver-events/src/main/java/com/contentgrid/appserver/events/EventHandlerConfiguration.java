package com.contentgrid.appserver.events;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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


//    @ConditionalOnProperty(value = {"spring.rabbitmq.host"})
//    @Configuration(proxyBeanMethods = false)
//    static class EventsRabbitMqAutoConfiguration {
//
//        @Bean
//        EntityChangeEventHandler messageHandler(RabbitTemplate rabbitTemplate,
//                ContentGridEventHandlerProperties config) {
//            return () -> rabbitTemplate.convertSendAndReceive()
//            return () -> Amqp.outboundAdapter(rabbitTemplate)
//                    .routingKey(config.getEvents().getRabbitmq().getRoutingKey())
//                    .getObject();
//        }
//    }
}
