package com.edifika.access.access.infrastructure.messaging.rabbitmq;

import com.edifika.access.access.application.internal.outboundservices.messaging.EdgeCommandPublisher;
import com.edifika.access.access.infrastructure.messaging.rabbitmq.configuration.RabbitMqConfiguration;
import com.edifika.access.shared.infrastructure.messaging.IntegrationEnvelope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Publicador de órdenes hacia el edge gateway en edifika.edge.commands.
 */
@Slf4j
@Component
public class RabbitEdgeCommandPublisher implements EdgeCommandPublisher {

    private final RabbitTemplate rabbitTemplate;

    public RabbitEdgeCommandPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publish(String gatewayCode, String command, IntegrationEnvelope envelope) {
        String routingKey = gatewayCode + "." + command;
        log.info("Publicando comando {} [{}] en exchange {} con routing key {}",
                envelope.eventType(), envelope.eventId(), RabbitMqConfiguration.COMMANDS_EXCHANGE, routingKey);

        rabbitTemplate.convertAndSend(RabbitMqConfiguration.COMMANDS_EXCHANGE, routingKey, envelope, message -> {
            var props = message.getMessageProperties();
            props.setMessageId(envelope.eventId());
            props.setType(envelope.eventType());
            props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            props.setContentEncoding("utf-8");
            props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            props.setHeader("x-producer", envelope.producer());
            return message;
        });
    }
}
