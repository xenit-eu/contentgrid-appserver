package com.contentgrid.appserver.application.model.attributes;

import com.contentgrid.appserver.application.model.Constraint;
import com.contentgrid.appserver.application.model.attributes.flags.AttributeFlag;
import com.contentgrid.appserver.application.model.i18n.ManipulatableTranslatable;
import com.contentgrid.appserver.application.model.i18n.Translatable;
import com.contentgrid.appserver.application.model.i18n.TranslatableImpl;
import com.contentgrid.appserver.application.model.i18n.TranslationBuilderSupport;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;
import lombok.experimental.Delegate;

/**
 * Represents an attribute (field) of an entity.
 * 
 * An SimpleAttribute defines a property of an entity, including its name, database column,
 * data type, and constraints. Attributes map to columns in a database table.
 */
@Value
public class SimpleAttribute implements Attribute {

    /**
     * The name of the attribute.
     */
    @NonNull
    AttributeName name;

    @NonNull
    @EqualsAndHashCode.Exclude
    @Delegate
    @Getter(value = AccessLevel.NONE)
    Translatable<AttributeTranslations> translations;

    /**
     * The name of the database column this attribute maps to.
     */
    @NonNull
    ColumnName column;

    /**
     * The data type of this attribute.
     */
    @NonNull
    Type type;

    Set<AttributeFlag> flags;

    /**
     * The list of constraints applied to this attribute.
     */
    List<Constraint> constraints;

    /**
     * Defines the data types supported for attributes.
     */
    public enum Type {
        LONG,
        DOUBLE,
        BOOLEAN,
        TEXT,
        DATETIME,
        UUID;
    }

    @Builder
    SimpleAttribute(@NonNull AttributeName name, ManipulatableTranslatable<AttributeTranslations> translations, @NonNull ColumnName column,
            @NonNull Type type, @Singular Set<AttributeFlag> flags, @Singular List<Constraint> constraints) {
        this.name = name;
        this.translations = translations.withTranslationsBy(Locale.ROOT, t -> {
            if(t.getName() == null) {
                t = t.withName(name.getValue());
            }
            return t;
        });
        this.column = column;
        this.type = type;
        this.flags = flags;
        this.constraints = constraints;
        for (var flag : this.flags) {
            flag.checkSupported(this);
        }
    }


    @Override
    public List<ColumnName> getColumns() {
        return List.of(column);
    }

    /**
     * Returns whether this attribute has a constraint of the specified type.
     *
     * @param constraintClass the class object representing the constraint type
     * @return whether this attribute has the constraint
     */
    public boolean hasConstraint(Class<? extends Constraint> constraintClass) {
        return getConstraints().stream().anyMatch(constraintClass::isInstance);
    }

    /**
     * Finds a constraint of the specified type associated with this attribute.
     *
     * @param <C> the type of constraint to find
     * @param constraintClass the class object representing the constraint type
     * @return an Optional containing the constraint if found, or empty if not found
     */
    public <C extends Constraint> Optional<C> getConstraint(Class<C> constraintClass) {
        return getConstraints().stream()
                .filter(constraintClass::isInstance)
                .map(constraintClass::cast)
                .findAny();
    }

    public static SimpleAttributeBuilder builder() {
        return new SimpleAttributeBuilder()
                .translations(new TranslatableImpl<>(AttributeTranslations::new));
    }

    public static class SimpleAttributeBuilder extends TranslationBuilderSupport<AttributeTranslations, SimpleAttributeBuilder> {
        {
            getTranslations = () -> translations;
        }

        @Deprecated(forRemoval = true)
        public SimpleAttributeBuilder description(String description) {
            return translationsBy(Locale.ROOT, t  -> t.withDescription(description));
        }

    }

}
