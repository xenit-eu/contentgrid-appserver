package com.contentgrid.appserver.rest;

import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.CompositeAttribute;
import com.contentgrid.appserver.application.model.attributes.ContentAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.UserAttribute;
import com.contentgrid.appserver.rest.exception.AttributesValidationException;
import com.contentgrid.appserver.rest.exception.InvalidEntityDataException;
import com.contentgrid.appserver.application.model.values.AttributeName;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.experimental.UtilityClass;
import org.springframework.util.InvalidMimeTypeException;
import org.springframework.util.MimeType;

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
        Map<String, String> validationErrors = new HashMap<>();
        Map<String, Object> validatedData = new HashMap<>();

        // Check for unknown attributes
        for (String attributeKey : data.keySet()) {
            AttributeName attrName = AttributeName.of(attributeKey);
            if (entity.getAttributeByName(attrName).isEmpty()) {
                validationErrors.put(attrName.getValue(), "Unknown attribute");
            }
        }

        // Validate each attribute
        for (Attribute attribute : entity.getAllAttributes()) {
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

            try {
                Object convertedValue = validate(attribute, value);
                validatedData.put(key, convertedValue);
            } catch (AttributesValidationException ave) {
                for (Map.Entry<String, String> error : ave.getValidationErrors().entrySet()) {
                    String errorKey = attribute.getName().getValue() + "." + error.getKey();
                    validationErrors.put(errorKey, error.getValue());
                }
            } catch (IllegalArgumentException e) {
                validationErrors.put(attribute.getName().getValue(), e.getMessage());
            }
        }

        if (!validationErrors.isEmpty()) {
            throw new InvalidEntityDataException(entity, validationErrors);
        }

        return validatedData;
    }

    private Object validate(Attribute attribute, Object value) {
        return switch (attribute) {
            case SimpleAttribute simpleAttribute -> validateSimpleAttributeValue(simpleAttribute, value);
            case CompositeAttribute compositeAttribute -> validateCompositeAttributeValue(compositeAttribute, value);
            case ContentAttribute contentAttribute -> validateContentAttributeValue(contentAttribute, value);
            case UserAttribute userAttribute -> validateUserAttributeValue(userAttribute, value);
        };
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

    private static MimeType validateMimetypeAttributeValue(Object value) {
        if (value instanceof MimeType mimeType) {
            return mimeType;
        } else if (value instanceof String str) {
            try {
                return MimeType.valueOf(str);
            } catch (InvalidMimeTypeException e) {
                throw new IllegalArgumentException("Invalid mimetype format: " + str);
            }
        }
        throw new IllegalArgumentException("Expected mimetype value, got: " + value.getClass().getSimpleName());
    }

    private static Map<AttributeName, Object> validateCompositeAttributeValue(CompositeAttribute attribute, Object value) {
        Map<AttributeName, Object> validatedData = new HashMap<>();
        Map<String, String> validationErrors = new HashMap<>();

        if (value instanceof Map map) {
            for (Attribute subAttribute : attribute.getAttributes()) {
                Object subAttributeValue = map.get(subAttribute.getName().getValue());

                try {
                    validatedData.put(attribute.getName(), validate(subAttribute, subAttributeValue));
                } catch (AttributesValidationException ave) {
                    for (Map.Entry<String, String> error : ave.getValidationErrors().entrySet()) {
                        String errorKey = subAttribute.getName().getValue() + "." + error.getKey();
                        validationErrors.put(errorKey, error.getValue());
                    }
                } catch (IllegalArgumentException e) {
                    validationErrors.put(subAttribute.getName().getValue(), e.getMessage());
                }
            }
        } else {
            // TODO add required check
            throw new IllegalArgumentException("Expected map, got: " + value.getClass().getSimpleName());
        }

        if (!validationErrors.isEmpty()) {
            throw new AttributesValidationException(validationErrors);
        }

        return validatedData;
    }

    private static Map<AttributeName, Object> validateContentAttributeValue(ContentAttribute attribute, Object value) {
        Map<AttributeName, Object> validatedData = new HashMap<>();
        Map<String, String> validationErrors = new HashMap<>();

        if (value instanceof Map map) {
            // Filename
            try {
                var filename = map.get(attribute.getFilename().getName().getValue());
                validatedData.put(attribute.getFilename().getName(), validateStringAttributeValue(filename));
            } catch(IllegalArgumentException e) {
                validationErrors.put(attribute.getFilename().getName().getValue(), e.getMessage());
            }

            // Mimetype
            try {
                var mimetype = map.get(attribute.getMimetype().getName().getValue());
                validatedData.put(attribute.getMimetype().getName(), validateMimetypeAttributeValue(mimetype));
            } catch(IllegalArgumentException e) {
                validationErrors.put(attribute.getMimetype().getName().getValue(), e.getMessage());
            }

            // User doesn't get to set id or length
        }
        // TODO add required check

        if (!validationErrors.isEmpty()) {
            throw new AttributesValidationException(validationErrors);
        }
        return validatedData;
    }

    private static Map<AttributeName, Object> validateUserAttributeValue(UserAttribute attribute, Object value) {
        Map<AttributeName, Object> validatedData = new HashMap<>();
        Map<String, String> validationErrors = new HashMap<>();

        if (value instanceof Map map) {
            // Id
            try {
                var id = map.get(attribute.getId().getName().getValue());
                validatedData.put(attribute.getId().getName(), validateStringAttributeValue(id));
            } catch(IllegalArgumentException e) {
                validationErrors.put(attribute.getId().getName().getValue(), e.getMessage());
            }

            // Username
            try {
                var filename = map.get(attribute.getUsername().getName().getValue());
                validatedData.put(attribute.getUsername().getName(), validateStringAttributeValue(filename));
            } catch(IllegalArgumentException e) {
                validationErrors.put(attribute.getUsername().getName().getValue(), e.getMessage());
            }

            // Namespace
            try {
                var namespace = map.get(attribute.getNamespace().getName().getValue());
                validatedData.put(attribute.getNamespace().getName(), validateStringAttributeValue(namespace));
            } catch(IllegalArgumentException e) {
                validationErrors.put(attribute.getNamespace().getName().getValue(), e.getMessage());
            }
        }
        // TODO add required check

        if (!validationErrors.isEmpty()) {
            throw new AttributesValidationException(validationErrors);
        }
        return validatedData;
    }
}