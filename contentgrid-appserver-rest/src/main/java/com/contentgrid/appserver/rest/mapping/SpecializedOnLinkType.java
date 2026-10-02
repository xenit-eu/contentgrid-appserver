package com.contentgrid.appserver.rest.mapping;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller class or method as specialized on a specific link type
 * <p>
 * Specialization of a controller means that its {@link org.springframework.web.bind.annotation.RequestMapping} is
 * further restricted at runtime to only match the specific types of stored links that are given in {@link #type()}
 * A request mapping containing {@link #entityPathVariable()} and {@link #linkPathVariable()} is necessary for this annotation to work.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface SpecializedOnLinkType {

    /**
     * @return The stored link types for which this controller class or method should be specialized
     */
    StoredLinkType[] type() default {StoredLinkType.TEXT, StoredLinkType.CONTENT};

    /**
     * @return Path variable that will be expanded with the entity name
     */
    String entityPathVariable();

    /**
     * @return Path variable that will be expanded with the link path
     */
    String linkPathVariable();


    enum StoredLinkType {
        TEXT,
        CONTENT
    }

}
