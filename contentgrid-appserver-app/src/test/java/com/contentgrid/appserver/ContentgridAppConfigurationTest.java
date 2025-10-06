package com.contentgrid.appserver;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "contentgrid.appserver.content-store.type=ephemeral")
class ContentgridAppConfigurationTest {
    @Test
    void contextLoads() {}

}