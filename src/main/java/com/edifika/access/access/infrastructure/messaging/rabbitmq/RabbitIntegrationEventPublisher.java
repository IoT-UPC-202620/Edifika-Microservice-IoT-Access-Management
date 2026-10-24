package com.edifika.access.access.infrastructure.messaging.rabbitmq;

import com.edifika.access.access.application.internal.outboundservices.messaging.IntegrationEventPublisher;
import com.edifika.access.access.infrastructure.messaging.rabbitmq.configuration.RabbitMqConfiguration;
import com.edifika.access.shared.infrastructure.messaging.IntegrationEnvelope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Publicador de eventos de integración hacia edifika.events.
 */
@Slf4j
@Component
public class RabbitIntegrationEventPublisher implements IntegrationEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public RabbitIntegrationEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publish(String routingKey, IntegrationEnvelope envelope) {
        log.info("Publicando evento {} [{}] en exchange {} con routing key {}",
                envelope.eventType(), envelope.eventId(), RabbitMqConfiguration.EVENTS_EXCHANGE, routingKey);

        rabbitTemplate.convertAndSend(RabbitMqConfiguration.EVENTS_EXCHANGE, routingKey, envelope, message -> {
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
