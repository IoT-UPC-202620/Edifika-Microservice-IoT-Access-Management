package com.edifika.access.support;

import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));
    }

    @Bean
    @ServiceConnection
    RabbitMQContainer rabbitContainer() {
        return new RabbitMQContainer(DockerImageName.parse("rabbitmq:4-management"));
    }

    @Bean
    TestRestTemplate testRestTemplate(org.springframework.core.env.Environment env) {
        TestRestTemplate template = new TestRestTemplate();
        template.setUriTemplateHandler(new org.springframework.web.util.UriTemplateHandler() {
            @Override
            public java.net.URI expand(String uriTemplate, java.util.Map<String, ?> uriVariables) {
                int port = env.getProperty("local.server.port", Integer.class, 8089);
                return new DefaultUriBuilderFactory("http://localhost:" + port).expand(uriTemplate, uriVariables);
            }

            @Override
            public java.net.URI expand(String uriTemplate, Object... uriVariables) {
                int port = env.getProperty("local.server.port", Integer.class, 8089);
                return new DefaultUriBuilderFactory("http://localhost:" + port).expand(uriTemplate, uriVariables);
            }
        });
        return template;
    }
}
