package com.contentgrid.appserver.autoconfigure.automation;

import com.contentgrid.appserver.autoconfigure.rest.ContentGridRestAutoConfiguration;
import com.contentgrid.appserver.automations.ContentGridAutomationsConfiguration;
import com.contentgrid.thunx.predicates.model.ThunkExpression;
import com.contentgrid.thunx.spring.security.AbacContextSupplier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.annotation.Import;

@AutoConfiguration(
        after = ContentGridRestAutoConfiguration.class
)
@ConditionalOnWebApplication(type = Type.SERVLET)
@ConditionalOnClass({ContentGridAutomationsConfiguration.class, AbacContextSupplier.class, ThunkExpression.class})
@Import(ContentGridAutomationsConfiguration.class)
public class ContentGridAutomationAutoConfiguration {

}
