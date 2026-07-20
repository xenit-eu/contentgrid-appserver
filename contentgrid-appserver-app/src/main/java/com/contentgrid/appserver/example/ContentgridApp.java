package com.contentgrid.appserver.example;

import com.contentgrid.appserver.security.opa.authorization.AppserverOpaInputProvider;
import com.contentgrid.thunx.pdp.opa.OpaInputProvider;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.Authentication;

@Slf4j
@SpringBootApplication
public class ContentgridApp {
    public static void main(String[] args) {
        log.info("Running Spring application...");
        SpringApplication.run(ContentgridApp.class, args);
    }

    // Defined here (a bean of the application itself) rather than in an autoconfiguration class,
    // so it always wins over thunx-autoconfigure's WebMvcAbacAutoConfiguration default
    // ServletOpaInputProvider bean without relying on autoconfiguration ordering - mirroring how
    // contentgrid-gateway's GatewayApplication overrides thunx's DefaultOpaInputProvider.
    @Bean
    OpaInputProvider<Authentication, HttpServletRequest> appserverOpaInputProvider() {
        return new AppserverOpaInputProvider();
    }
}
