package com.contentgrid.appserver.example;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
public class ContentgridApp {
    public static void main(String[] args) {
        log.info("Running Spring application...");
        SpringApplication.run(ContentgridApp.class, args);
    }
}
