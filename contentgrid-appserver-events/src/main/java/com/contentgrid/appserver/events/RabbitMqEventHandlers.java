package com.contentgrid.appserver.events;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.domain.DomainEventDispatcher;
import com.contentgrid.appserver.domain.data.EntityInstance;
import com.contentgrid.appserver.rest.RestEntityFormatter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@RequiredArgsConstructor
public class RabbitMqEventHandlers implements DomainEventDispatcher {

    private final RabbitTemplate rabbitTemplate;
    private final ContentGridEventHandlerProperties contentGridProps;
    private final RestEntityFormatter formatter;
    private final ObjectMapper objectMapper;

    @Override
    public void dispatchCreate(@NonNull Application application, @NonNull EntityInstance instance) {
        var entity = instance.getIdentity().getEntityName();
        var newData = formatter.format(application, instance);

        send(EntityChangeEventPayload.forCreate(newData), "create", entity.getValue());
    }

    @Override
    public void dispatchUpdate(@NonNull Application application, @NonNull EntityInstance oldInstance,
            @NonNull EntityInstance newInstance) {
        var entity = oldInstance.getIdentity().getEntityName();
        var oldData = formatter.format(application, oldInstance);
        var newData = formatter.format(application, newInstance);

        send(EntityChangeEventPayload.forUpdate(oldData, newData), "update", entity.getValue());
    }


    @Override
    public void dispatchDelete(@NonNull Application application, @NonNull EntityInstance instance) {
        var entity = instance.getIdentity().getEntityName();
        var oldData = formatter.format(application, instance);

        send(EntityChangeEventPayload.forDelete(oldData), "delete", entity.getValue());
    }

    private void send(EntityChangeEventPayload payload, String trigger, String entity) {
        var payloadString = objectMapper.valueToTree(payload).toString();
        Message message = MessageBuilder
                .withBody(payloadString.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setHeader("trigger", trigger)
                .setHeader("entity", entity)
                .build();
        var routingKey = contentGridProps.getEvents().getRabbitmq().getRoutingKey();
        rabbitTemplate.send(routingKey, message);
    }
}
