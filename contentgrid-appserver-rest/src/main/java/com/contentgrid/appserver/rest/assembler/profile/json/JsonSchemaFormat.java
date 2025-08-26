/*
 * Copyright 2015-2025 the original author or authors.
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

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * An enum to represent JSON Schema pre-defined formats.
 *
 * @author Oliver Gierke
 * @see <a href="https://github.com/spring-projects/spring-data-rest/blob/bfb2a5bea703ee10f19f85e5b5e8c77f24017940/spring-data-rest-core/src/main/java/org/springframework/data/rest/core/config/JsonSchemaFormat.java">
 *     org.springframework.data.rest.core.config.JsonSchemaFormat</a>
 */
public enum JsonSchemaFormat {

    EMAIL, DATE_TIME, HOSTNAME, IPV4, IPV6, URI, UUID;

    @JsonValue
    @Override
    public String toString() {
        return name().toLowerCase(Locale.US).replace("_", "-");
    }
}
