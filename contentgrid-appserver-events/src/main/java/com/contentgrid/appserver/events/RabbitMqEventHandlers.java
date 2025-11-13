package com.contentgrid.appserver.events;

import com.contentgrid.appserver.domain.events.FormattedEventDispatcher;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@RequiredArgsConstructor
public class RabbitMqEventHandlers implements FormattedEventDispatcher {

    private final RabbitTemplate rabbitTemplate;
    private final ContentGridEventHandlerProperties contentGridProps;
    private final ObjectMapper objectMapper;

    public void dispatchCreate(@NonNull String entity, @NonNull TreeNode newData) {
        send(EntityChangeEventPayload.forCreate(newData), "create", entity);
    }

    public void dispatchUpdate(@NonNull String entity, @NonNull TreeNode oldData, @NonNull TreeNode newData) {
        send(EntityChangeEventPayload.forUpdate(oldData, newData), "update", entity);
    }

    public void dispatchDelete(@NonNull String entity, @NonNull TreeNode oldData) {
        send(EntityChangeEventPayload.forDelete(oldData), "delete", entity);
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
