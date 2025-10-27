package com.contentgrid.appserver.events;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.query.engine.api.data.EntityData;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;

@RequiredArgsConstructor
class RabbitMqEventHandlers implements EventHandlers {

    private final RabbitTemplate rabbitTemplate;
    private final RabbitProperties rabbitProperties;
    private final ContentGridEventHandlerProperties contentGridProps;
    private final EventMapper eventMapper;

    @Override
    public void dispatchCreate(@NonNull Application application, @NonNull EntityName entity, @NonNull EntityData data) {
        var event = EntityChangeEvent.builder()
                .trigger(EntityChangeEvent.ChangeKind.CREATE)
                .application(application)
                .entity(entity)
                .newData(data)
                .build();
        send(event);
    }

    @Override
    public void dispatchUpdate(@NonNull Application application, @NonNull EntityName entity, @NonNull EntityData oldData, @NonNull EntityData newData) {
        var event = EntityChangeEvent.builder()
                .trigger(EntityChangeEvent.ChangeKind.UPDATE)
                .application(application)
                .entity(entity)
                .oldData(oldData)
                .newData(newData)
                .build();
        send(event);
    }

    @Override
    public void dispatchDelete(@NonNull Application application, @NonNull EntityName entity, @NonNull EntityData oldData) {
        var event = EntityChangeEvent.builder()
                .trigger(EntityChangeEvent.ChangeKind.DELETE)
                .application(application)
                .entity(entity)
                .oldData(oldData)
                .build();
        send(event);
    }

    private void send(EntityChangeEvent event) {
        var objectMapper = new ObjectMapper();
        var payload = objectMapper.valueToTree(eventMapper.map(event)).toString();
        var exchange = rabbitProperties.getTemplate() != null ? rabbitProperties.getTemplate().getExchange() : null;
        var routingKey = contentGridProps.getEvents().getRabbitmq().getRoutingKey();
        if (exchange == null || exchange.isBlank()) {
            rabbitTemplate.convertAndSend(routingKey, payload); // default exchange from template
        } else {
            rabbitTemplate.convertAndSend(exchange, routingKey, payload);
        }
    }
}
