package com.contentgrid.appserver.integration.test.fixture.invoicing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

@SpringBootApplication
public class InvoicingApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(InvoicingApiApplication.class, args);
	}

    @TestConfiguration(proxyBeanMethods = false)
    static class TestDatabaseConfiguration {

        @Bean
        @ServiceConnection
        PostgreSQLContainer postgreSQLContainer() {
            return new PostgreSQLContainer("postgres:15");
        }

        @Bean
        @ServiceConnection
        RabbitMQContainer rabbitMQContainer() {
            return new RabbitMQContainer("rabbitmq:4");
        }
    }

}
