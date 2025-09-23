package com.contentgrid.appserver.actuator;

import com.contentgrid.appserver.actuator.policy.PolicyActuator;
import com.contentgrid.appserver.actuator.policy.PolicyVariables;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ActuatorConfiguration {

    // TODO after ACC-2274
    //   Make this an AutoConfiguration

    @Autowired
    ApplicationContext applicationContext;

    @Configuration
    class PolicyActuatorConfiguration {
        @Bean
        PolicyVariables policyVariables(ContentgridApplicationProperties applicationProperties) {
            return PolicyVariables.builder()
                    .policyPackageName(applicationProperties.getSystem().getPolicyPackage())
                    .build();
        }

        @Bean
        PolicyActuator policyActuator(PolicyVariables policyVariables) {
            return new PolicyActuator(applicationContext.getResource("classpath:rego/policy.rego"), policyVariables);
        }

    }

    @Bean
    @ConfigurationProperties(prefix = "contentgrid")
    ContentgridApplicationProperties contentgridApplicationProperties() {
        return new ContentgridApplicationProperties();
    }

}
