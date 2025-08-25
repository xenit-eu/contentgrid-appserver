/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.contentgrid.appserver.rest.assembler.profile.json;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import lombok.Getter;
import org.springframework.util.Assert;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

/**
 * Model class to render JSON schema documents.
 *
 * @author Jon Brisbin
 * @author Oliver Gierke
 * @see <a href="https://github.com/spring-projects/spring-data-rest/blob/bfb2a5bea703ee10f19f85e5b5e8c77f24017940/spring-data-rest-webmvc/src/main/java/org/springframework/data/rest/webmvc/json/JsonSchema.java">
 *     org.springframework.data.rest.webmvc.json.JsonSchema</a>
 */
@JsonInclude(Include.NON_EMPTY)
public class JsonSchema {

    private final String title;
    private final String description;
    private final PropertiesContainer container;
    private final Definitions definitions;

    /**
     * Creates a new {@link JsonSchema} instance for the given title, description, {@link AbstractJsonSchemaProperty}s and
     * {@link Definitions}.
     *
     * @param title must not be {@literal null} or empty.
     * @param description can be {@literal null}.
     * @param properties must not be {@literal null}.
     * @param definitions must not be {@literal null}.
     */
    public <T extends AbstractJsonSchemaProperty<T>> JsonSchema(String title, String description, Collection<T> properties,
            Definitions definitions) {

        Assert.hasText(title, "Title must not be null or empty");
        Assert.notNull(properties, "Properties must not be null");
        Assert.notNull(definitions, "Definitions must not be null");

        this.title = title;
        this.description = description;
        this.container = new PropertiesContainer(properties);
        this.definitions = definitions;
    }

    @JsonProperty("$schema")
    public String getSchema() {
        return "https://json-schema.org/draft/2020-12/schema";
    }

    public String getType() {
        return JsonSchemaType.OBJECT.toString();
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    @JsonUnwrapped
    public PropertiesContainer getContainer() {
        return container;
    }

    @JsonUnwrapped
    public Definitions getDefinitions() {
        return definitions;
    }

    /**
     * A JSON Schema item.
     *
     * @author Oliver Gierke
     */
    @Getter
    static class Item {

        private final JsonSchemaType type;
        private final @JsonUnwrapped PropertiesContainer properties;

        /**
         * Creates a new {@link Item} for the given {@link Class} and properties.
         *
         * @param type must not be {@literal null}.
         * @param properties must not be {@literal null}.
         */
        public <T extends AbstractJsonSchemaProperty<T>> Item(JsonSchemaType type, Collection<T> properties) {

            this.type = type;
            this.properties = new PropertiesContainer(properties);
        }
    }

    /**
     * Value object to represent a generic container of properties.
     *
     * @author Oliver Gierke
     */
    @JsonInclude(Include.NON_EMPTY)
    static class PropertiesContainer {

        public final Map<String, AbstractJsonSchemaProperty<?>> properties;
        public final @JsonProperty("required") Collection<String> requiredProperties;

        /**
         * Creates a new {@link PropertiesContainer} for the given {@link AbstractJsonSchemaProperty}s.
         *
         * @param properties must not be {@literal null}.
         */
        public <T extends AbstractJsonSchemaProperty<T>> PropertiesContainer(Collection<T> properties) {

            Assert.notNull(properties, "Properties must not be null");

            this.properties = new HashMap<String, JsonSchema.AbstractJsonSchemaProperty<?>>();
            this.requiredProperties = new ArrayList<String>();

            for (AbstractJsonSchemaProperty<?> property : properties) {
                this.properties.put(property.getName(), property);

                if (property.isRequired()) {
                    this.requiredProperties.add(property.name);
                }
            }
        }
    }

    /**
     * Value object to abstract a {@link Map} of JSON Schema definitions.
     *
     * @author Oliver Gierke
     */
    @Getter
    public static class Definitions {

        private final @JsonProperty("$defs") Map<String, Item> definitions;

        public Definitions() {
            this.definitions = new HashMap<String, Item>();
        }

        boolean hasDefinitionFor(JsonSchemaReference reference) {
            return this.definitions.containsKey(reference.getName());
        }

        void addDefinition(JsonSchemaReference reference, Item item) {
            this.definitions.put(reference.getName(), item);
        }
    }

    /**
     * Base class for all property implementations.
     *
     * @author Oliver Gierke
     */
    @Getter
    @JsonInclude(Include.NON_EMPTY)
    abstract static class AbstractJsonSchemaProperty<T extends AbstractJsonSchemaProperty<T>> {

        private final @JsonIgnore String name;
        private final String title;
        private final @JsonIgnore boolean required;

        private boolean readOnly;

        protected AbstractJsonSchemaProperty(String name, boolean required) {
            this(name, null, required);
        }

        protected AbstractJsonSchemaProperty(String name, String title, boolean required) {

            this.name = name;
            this.title = title;
            this.required = required;
            this.readOnly = false;
        }

        @SuppressWarnings("unchecked")
        protected T withReadOnly() {
            this.readOnly = true;
            return (T) this;
        }
    }

    /**
     * A JSON Schema property
     *
     * @author Oliver Gierke
     */
    @Getter
    public static class JsonSchemaProperty extends AbstractJsonSchemaProperty<JsonSchemaProperty> {

        private final String description;
        private JsonSchemaType type;
        private JsonSchemaFormat format;
        private String pattern;
        private Boolean uniqueItems;
        private @JsonProperty("$ref") JsonSchemaReference reference;
        private Map<String, String> items;
        private @JsonUnwrapped PropertiesContainer properties;

        JsonSchemaProperty(String name, String title, String description, boolean required) {

            super(name, title, required);

            this.description = description;
        }

        /**
         * Configures the {@link JsonSchemaProperty} to reflect the given type.
         *
         * @param type must not be {@literal null}.
         * @return
         */
        public JsonSchemaProperty withType(JsonSchemaType type) {

            Assert.notNull(type, "Type must not be null");

            this.type = type;
            return this;
        }

        /**
         * Configures the given {@link JsonSchemaFormat} to be exposed on the current {@link JsonSchemaProperty}.
         *
         * @param format must not be {@literal null}.
         * @return
         */
        public JsonSchemaProperty withFormat(JsonSchemaFormat format) {

            Assert.notNull(format, "Format must not be null");

            this.format = format;
            return withType(JsonSchemaType.STRING);
        }

        /**
         * Configures the {@link JsonSchemaProperty} to require the given regular expression as pattern.
         *
         * @param regex must not be {@literal null}.
         * @return
         */
        public JsonSchemaProperty withRegex(String regex) {

            Assert.hasText(regex, "Regular expression must not be null or empty");
            return withPattern(Pattern.compile(regex));
        }

        /**
         * Configures the {@link JsonSchemaProperty} to require the given {@link Pattern}.
         *
         * @param pattern must not be {@literal null}.
         * @return
         */
        public JsonSchemaProperty withPattern(Pattern pattern) {

            Assert.notNull(pattern, "Pattern must not be null");

            this.pattern = pattern.toString();
            return withType(JsonSchemaType.STRING);
        }

        /**
         * Turns the current {@link JsonSchemaProperty} into an association.
         *
         * @return
         */
        public JsonSchemaProperty asAssociation(boolean collectionLike) {
            if (collectionLike) {
                this.items = Map.of(
                        "type", JsonSchemaType.STRING.toString(),
                        "format", JsonSchemaFormat.URI.toString()
                );

                this.uniqueItems = true;

                return withType(JsonSchemaType.ARRAY);
            }

            this.items = null;
            this.uniqueItems = null;

            return withFormat(JsonSchemaFormat.URI);
        }

        public JsonSchemaProperty withReference(JsonSchemaReference reference) {
            this.reference = reference;
            return this;
        }

        public JsonSchemaProperty withArrayReference(JsonSchemaReference reference, boolean uniqueItems) {
            if (uniqueItems) {
                this.uniqueItems = true;
            }

            this.type = JsonSchemaType.ARRAY;
            this.items = Collections.singletonMap("$ref", reference.toString());

            return this;
        }

        public <T extends AbstractJsonSchemaProperty<T>> JsonSchemaProperty withProperties(Collection<T> properties) {

            Assert.notNull(properties, "Properties must not be null");

            this.properties = new PropertiesContainer(properties);
            return withType(JsonSchemaType.OBJECT);
        }
    }

    /**
     * A {@link JsonSchemaProperty} representing enumerations. Will cause all valid values to be rendered in a nested
     * {@literal enum} property.
     *
     * @author Oliver Gierke
     */
    public static class EnumProperty extends JsonSchemaProperty {

        private List<String> values;

        public EnumProperty(String name, String title, Class<?> type, String description, boolean required) {

            this(name, title, toValues(type), description, required);
        }

        public EnumProperty(String name, String title, List<String> values, String description, boolean required) {

            super(name, title, description, required);

            this.values = Collections.unmodifiableList(values);
        }

        @JsonProperty("enum")
        public List<String> getValues() {
            return values;
        }

        /**
         * Returns the current {@link EnumProperty} exposing the given values.
         *
         * @param values must not be {@literal null}.
         * @return
         */
        public EnumProperty withValues(List<String> values) {

            Assert.notNull(values, "Values must not be null");

            this.values = Collections.unmodifiableList(values);
            return this;
        }

        private static List<String> toValues(Class<?> type) {

            List<String> values = new ArrayList<String>();

            for (Object value : type.getEnumConstants()) {
                values.add(value.toString());
            }

            return values;
        }
    }
}
