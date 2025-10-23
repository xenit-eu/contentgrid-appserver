package com.contentgrid.appserver.autoconfigure.events;

import com.contentgrid.appserver.events.EventHandlerConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@ConditionalOnWebApplication
@ConditionalOnClass(EventHandlerConfiguration.class)
@Import(EventHandlerConfiguration.class)
public class ContentGridEventsAutoConfiguration {
}
