package com.contentgrid.appserver.application.model;

import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.exceptions.InvalidEntityDataException;
import com.contentgrid.appserver.application.model.values.AttributeName;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.experimental.UtilityClass;

/**
 * Validates entity instance data against the entity's attribute definitions. Ensures that all required attributes are
 * present and have the correct data types.
 */
@UtilityClass
public class EntityDataValidator {

    /**
     * Validates data against an entity's attribute definitions.
     *
     * @param entity The entity definition to validate against
     * @param data The data to validate
     * @return The validated and converted data
     * @throws InvalidEntityDataException if validation fails
     */
    public static Map<String, Object> validate(Entity entity, Map<String, Object> data) {
        Map<AttributeName, String> validationErrors = new HashMap<>();
        Map<String, Object> validatedData = new HashMap<>();

        // Check for unknown attributes
        for (String attributeKey : data.keySet()) {
            AttributeName attrName = AttributeName.of(attributeKey);
            if (entity.getAttributeByName(attrName).isEmpty()) {
                validationErrors.put(attrName, "Unknown attribute");
            }
        }

        // Validate each attribute
        for (Attribute attribute : entity.getAttributes()) {
            AttributeName attributeName = attribute.getName();
            String key = attributeName.getValue();
            Object value = data.get(key);

            // Skip primary key validation if not provided (will be generated)
            if (entity.getPrimaryKey().getName().equals(attributeName) && value == null) {
                continue;
            }

            if (value == null) {
                // For now, treat all attributes as optional
                continue;
            }

            if (attribute instanceof SimpleAttribute simpleAttribute) {
                try {
                    Object convertedValue = validateSimpleAttributeValue(simpleAttribute, value);
                    validatedData.put(key, convertedValue);
                } catch (IllegalArgumentException e) {
                    validationErrors.put(attributeName, e.getMessage());
                }
            }
        }

        if (!validationErrors.isEmpty()) {
            throw new InvalidEntityDataException(entity, validationErrors);
        }

        return validatedData;
    }

    private static Object validateSimpleAttributeValue(SimpleAttribute attribute, Object value) {
        return switch (attribute.getType()) {
            case TEXT -> validateStringAttributeValue(value);
            case LONG -> validateLongAttributeValue(value);
            case DOUBLE -> validateDoubleAttributeValue(value);
            case BOOLEAN -> validateBooleanAttributeValue(value);
            case DATETIME -> validateDatetimeAttributeValue(value);
            case UUID -> validateUuidAttributeValue(value);
        };
    }

    private static UUID validateUuidAttributeValue(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        } else if (value instanceof String str) {
            try {
                return UUID.fromString(str);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid UUID format: " + str);
            }
        }
        throw new IllegalArgumentException("Expected UUID value, got: " + value.getClass().getSimpleName());
    }

    private static Instant validateDatetimeAttributeValue(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        } else if (value instanceof String str) {
            try {
                return Instant.parse(str);
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Invalid datetime format: " + str, e);
            }
        }
        throw new IllegalArgumentException("Expected datetime value, got: " + value.getClass().getSimpleName());
    }

    private static boolean validateBooleanAttributeValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        } else if (value instanceof String str) {
            if (str.equalsIgnoreCase("true") || str.equalsIgnoreCase("false")) {
                return Boolean.parseBoolean(str);
            }
            throw new IllegalArgumentException("Invalid boolean value: " + str);
        }
        throw new IllegalArgumentException("Expected boolean value, got: " + value.getClass().getSimpleName());
    }

    private static double validateDoubleAttributeValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        } else if (value instanceof String str) {
            try {
                return Double.parseDouble(str);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid decimal format: " + str);
            }
        }
        throw new IllegalArgumentException("Expected decimal value, got: " + value.getClass().getSimpleName());
    }

    private static long validateLongAttributeValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        } else if (value instanceof String str) {
            try {
                return Long.parseLong(str);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid number format: " + str);
            }
        }
        throw new IllegalArgumentException("Expected numeric value, got: " + value.getClass().getSimpleName());
    }

    private static String validateStringAttributeValue(Object value) {
        if (!(value instanceof String)) {
            throw new IllegalArgumentException("Expected text value, got: " + value.getClass().getSimpleName());
        }
        return (String) value;
    }
}