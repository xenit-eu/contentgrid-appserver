package com.contentgrid.appserver.query.engine.jooq.test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@SpringBootTest(
        classes = {TestApplication.class},
        properties = {
                "spring.datasource.url=jdbc:tc:postgresql:15:///",
                "logging.level.org.jooq.tools.LoggerListener=DEBUG"
        }
)
public @interface JooqTest {

}
