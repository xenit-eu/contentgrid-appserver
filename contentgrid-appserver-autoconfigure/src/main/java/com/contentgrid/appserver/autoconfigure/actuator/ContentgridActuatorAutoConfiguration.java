package com.contentgrid.appserver.autoconfigure.actuator;

import com.contentgrid.appserver.actuator.ActuatorConfiguration;
import com.contentgrid.appserver.actuator.policy.PolicyActuator;
import com.contentgrid.appserver.autoconfigure.security.DefaultSecurityConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@ConditionalOnClass({PolicyActuator.class})
@ConditionalOnWebApplication(type = Type.SERVLET)
@AutoConfigureAfter({DefaultSecurityConfiguration.class})
@Import(ActuatorConfiguration.class)
public class ContentgridActuatorAutoConfiguration {
}
