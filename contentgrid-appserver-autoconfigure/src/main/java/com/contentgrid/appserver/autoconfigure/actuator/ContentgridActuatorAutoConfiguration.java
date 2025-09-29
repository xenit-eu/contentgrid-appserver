package com.contentgrid.appserver.autoconfigure.actuator;

import com.contentgrid.appserver.actuator.ActuatorConfiguration;
import com.contentgrid.appserver.autoconfigure.security.DefaultSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.annotation.Import;

@AutoConfiguration(before = DefaultSecurityAutoConfiguration.class)
@ConditionalOnClass({ActuatorConfiguration.class})
@ConditionalOnWebApplication(type = Type.SERVLET)
@Import(ActuatorConfiguration.class)
public class ContentgridActuatorAutoConfiguration {
}
