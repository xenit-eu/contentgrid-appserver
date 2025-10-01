package com.contentgrid.appserver.rest.mapping;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.core.annotation.AliasFor;

/**
 * Marks a controller class or method as specialized on an Entity
 * <p>
 * Specialization of a controller means that its {@link org.springframework.web.bind.annotation.RequestMapping} is
 * further restricted at runtime to only match if its pathVariable is "{entityName}"
 */
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface SpecializedOnEntity {

    /**
     * @return Path variable that will be expanded with the entity name
     */
    String value();

}
