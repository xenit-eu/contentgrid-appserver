package com.contentgrid.appserver.autoconfigure.opa;

import com.contentgrid.appserver.security.opa.authorization.AppserverOpaInputProvider;
import com.contentgrid.thunx.pdp.opa.OpaInputProvider;
import com.contentgrid.thunx.webmvc.autoconfigure.WebMvcAbacAutoConfiguration;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.Authentication;

/**
 * Registered {@code before} {@link WebMvcAbacAutoConfiguration} so this bean definition exists by the time
 * {@code WebMvcAbacAutoConfiguration.servletOpaInputProvider()}'s {@code @ConditionalOnMissingBean} is evaluated,
 * making it back off in favor of the appserver-specific provider instead of registering its generic default.
 */
@AutoConfiguration(before = WebMvcAbacAutoConfiguration.class)
@ConditionalOnClass(OpaInputProvider.class)
@ConditionalOnWebApplication(type = Type.SERVLET)
public class OpaInputProviderAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    OpaInputProvider<Authentication, HttpServletRequest> appserverOpaInputProvider() {
        return new AppserverOpaInputProvider();
    }
}
