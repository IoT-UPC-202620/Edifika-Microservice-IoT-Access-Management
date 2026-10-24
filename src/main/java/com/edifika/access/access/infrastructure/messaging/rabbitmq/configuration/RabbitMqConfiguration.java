package com.edifika.access.access.infrastructure.messaging.rabbitmq.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuración de RabbitMQ: topología, message converter, RabbitTemplate y listener container factory.
 */
@Configuration
public class RabbitMqConfiguration {

    public static final String EVENTS_EXCHANGE = "edifika.events";
    public static final String COMMANDS_EXCHANGE = "edifika.edge.commands";
    public static final String DLX_EXCHANGE = "edifika.dlx";

    public static final String ACCESS_EDGE_EVENTS_QUEUE = "access.edge-events";
    public static final String ACCESS_EDGE_EVENTS_DLQ = "access.edge-events.dlq";

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange edgeCommandsExchange() {
        return new TopicExchange(COMMANDS_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange dlxExchange() {
        return new TopicExchange(DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue accessEdgeEventsQueue() {
        return QueueBuilder.durable(ACCESS_EDGE_EVENTS_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ACCESS_EDGE_EVENTS_QUEUE)
                .build();
    }

    @Bean
    public Queue accessEdgeEventsDlq() {
        return QueueBuilder.durable(ACCESS_EDGE_EVENTS_DLQ).build();
    }

    @Bean
    public Declarables amqpTopology(
            TopicExchange eventsExchange,
            TopicExchange edgeCommandsExchange,
            TopicExchange dlxExchange,
            Queue accessEdgeEventsQueue,
            Queue accessEdgeEventsDlq) {

        Binding b1 = BindingBuilder.bind(accessEdgeEventsQueue).to(eventsExchange).with("edge.access-attempt");
        Binding b2 = BindingBuilder.bind(accessEdgeEventsQueue).to(eventsExchange).with("edge.credential-changed");
        Binding b3 = BindingBuilder.bind(accessEdgeEventsQueue).to(eventsExchange).with("edge.device-status");
        Binding b4 = BindingBuilder.bind(accessEdgeEventsQueue).to(eventsExchange).with("edge.command-result");

        Binding bDlq = BindingBuilder.bind(accessEdgeEventsDlq).to(dlxExchange).with(ACCESS_EDGE_EVENTS_QUEUE);

        return new Declarables(List.of(
                eventsExchange,
                edgeCommandsExchange,
                dlxExchange,
                accessEdgeEventsQueue,
                accessEdgeEventsDlq,
                b1, b2, b3, b4,
                bDlq
        ));
    }

    @Bean
    public MessageConverter messageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setDefaultRequeueRejected(false);

        var retryInterceptor = RetryInterceptorBuilder.stateless()
                .maxRetries(3)
                .backOffOptions(1000L, 2.0, 4000L)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();
        factory.setAdviceChain(retryInterceptor);

        return factory;
    }
}

