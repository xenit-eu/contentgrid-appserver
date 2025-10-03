package com.contentgrid.appserver.autoconfigure.webjars;

import com.contentgrid.appserver.webjars.WebjarsRestConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@ConditionalOnWebApplication
@ConditionalOnClass(WebjarsRestConfiguration.class)
@Import(WebjarsRestConfiguration.class)
public class WebjarsRestAutoConfiguration {

}

