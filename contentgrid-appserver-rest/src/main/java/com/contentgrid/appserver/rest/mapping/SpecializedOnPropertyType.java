package com.contentgrid.appserver.rest.mapping;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller class or method as specialized on a specific property type
 * <p>
 * Specialization of a controller means that its {@link org.springframework.web.bind.annotation.RequestMapping} is
 * further restricted at runtime to only match the specific kinds of properties (attributes or relations) that are given in {@link #type()}.
 * <p>
 * Example: Given an application model:
 * <ul>
 *     <li>Entity 'invoice'
 *     <ul>
 *         <li>Content attribute 'image'</li>
 *         <li>Many-to-one relation 'supplier'</li>
 *     </ul>
 *     </li>
 *     <li>
 *         Entity 'supplier'
 *         <ul>
 *             <li>One-to-one relation 'contact'</li>
 *             <li>One-to-many relation 'invoices'</li>
 *         </ul>
 *     </li>
 * </ul>
 * The declared mapping {@code @RequestMapping("/{entityName}/{id}/{propertyName}")} combined with
 * {@code @SpecializedOnPropertyType(type=PropertyType.TO_ONE_RELATION, entityPathVariable="entityName", propertyPathVariable="propertyName")}
 * will be expanded to: {@code @RequestMapping({"/{entityName:invoice}/{id}/{propertyName:supplier}", "/{entityName:supplier}/{id}/{propertyName:contact}"})}
 * <p>
 * The same declared mapping, combined with
 * {@code @SpecializedOnPropertyType(type=PropertyType.CONTENT_ATTRIBUTE, entityPathVariable="entityName", propertyPathVariable="propertyName")}
 * will be instead be expanded to {@code @RequestMapping("/{entityName:invoice}/{id}/{propertyName:image}")}
 * <p>
 * This is a companion annotation to {@link org.springframework.web.bind.annotation.RequestMapping} and friends.
 * A request mapping containing {@link #entityPathVariable()} and {@link #propertyPathVariable()} is necessary for this annotation to work.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface SpecializedOnPropertyType {

    /**
     * @return The property types for which this controller class or method should be specialized
     */
    PropertyType[] type();

    /**
     * @return Path variable that will be expanded with the entity name
     */
    String entityPathVariable();

    /**
     * @return Path variable that will be expanded with the property name (relation name or attribute name)
     */
    String propertyPathVariable();

    enum PropertyType {
        /**
         * Matches content attributes
         */
        CONTENT_ATTRIBUTE,
        /**
         * Matches one-to-one and many-to-one relations
         */
        TO_ONE_RELATION,
        /**
         * Matches one-to-many and many-to-many relations
         */
        TO_MANY_RELATION,
        ;
    }

}
