package com.contentgrid.appserver.rest.test;

import com.contentgrid.appserver.application.model.fixtures.ModelTestFixtures;
import com.contentgrid.appserver.registry.ApplicationResolver;
import com.contentgrid.appserver.registry.SingleApplicationResolver;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class TestApplication {

    @Bean
    ApplicationResolver testApplicationResolver() {
        return new SingleApplicationResolver(ModelTestFixtures.APPLICATION);
    }
}
