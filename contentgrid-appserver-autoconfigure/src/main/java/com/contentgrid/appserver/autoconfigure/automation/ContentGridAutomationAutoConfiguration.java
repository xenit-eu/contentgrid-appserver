package com.contentgrid.appserver.autoconfigure.automation;

import com.contentgrid.appserver.autoconfigure.rest.ContentGridRestAutoConfiguration;
import com.contentgrid.appserver.automations.ContentGridAutomationsConfiguration;
import com.contentgrid.appserver.automations.model.AutomationsModel;
import com.contentgrid.appserver.automations.model.AutomationsModelResolver;
import com.contentgrid.appserver.automations.model.SingleAutomationsModelResolver;
import com.contentgrid.thunx.predicates.model.ThunkExpression;
import com.contentgrid.thunx.spring.security.AbacContextSupplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;

@AutoConfiguration(
        after = ContentGridRestAutoConfiguration.class
)
@ConditionalOnWebApplication(type = Type.SERVLET)
@ConditionalOnClass({ContentGridAutomationsConfiguration.class, AbacContextSupplier.class, ThunkExpression.class})
@Import(ContentGridAutomationsConfiguration.class)
public class ContentGridAutomationAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    AutomationsModelResolver automationsResolver(@Value("${contentgrid.appserver.automation-model:}") Resource resource) {
        var model = AutomationsModel.fromConfig(resource);
        return new SingleAutomationsModelResolver(model);
    }
}
