package com.contentgrid.appserver.example;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("initContainer")
public class ContentgridAppConfigurationInitContainerTest {
    @Test
    void contextLoads() {}

}
