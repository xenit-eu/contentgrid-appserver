package com.contentgrid.appserver.rest;

import com.contentgrid.appserver.query.engine.api.data.EntityId;
import com.contentgrid.appserver.registry.ApplicationNameExtractor;
import com.contentgrid.appserver.registry.ApplicationResolver;
import java.text.ParseException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.Formatter;
import org.springframework.format.FormatterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Component
@RequiredArgsConstructor
public class ArgumentResolverConfigurer implements WebMvcConfigurer {
    private final ApplicationResolver applicationResolver;
    private final ApplicationNameExtractor applicationNameExtractor;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new ApplicationArgumentResolver(applicationResolver, applicationNameExtractor));
    }

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addFormatter(new Formatter<EntityId>() {
            @Override
            public EntityId parse(String text, Locale locale) throws ParseException {
                return EntityId.of(UUID.fromString(text));
            }

            @Override
            public String print(EntityId entityId, Locale locale) {
                return entityId.getValue().toString();
            }
        });
    }
}
