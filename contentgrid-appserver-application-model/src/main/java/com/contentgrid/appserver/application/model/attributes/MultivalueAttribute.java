package com.contentgrid.appserver.application.model.attributes;

import com.contentgrid.appserver.application.model.Constraint;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.attributes.flags.AttributeFlag;
import com.contentgrid.appserver.application.model.exceptions.InvalidAttributeTypeException;
import com.contentgrid.appserver.application.model.exceptions.InvalidConstraintException;
import com.contentgrid.appserver.application.model.i18n.ConfigurableTranslatable;
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
 * Represents a multi-value attribute (field) of an entity.
 *
 * A MultivalueAttribute holds an unordered set of values of a single item type,
 * stored in a single array-typed database column.
 */
@Value
public class MultivalueAttribute implements Attribute {

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
     * The data type of the elements of this attribute.
     */
    @NonNull
    Type itemType;

    Set<AttributeFlag> flags;

    /**
     * The list of constraints applied to this attribute.
     */
    List<Constraint> constraints;

    @Builder
    MultivalueAttribute(@NonNull AttributeName name, ConfigurableTranslatable<AttributeTranslations, ConfigurableAttributeTranslations> translations, @NonNull ColumnName column,
            @NonNull Type itemType, @Singular Set<AttributeFlag> flags, @Singular List<Constraint> constraints) {
        this.name = name;
        this.translations = translations.withTranslationsBy(Locale.ROOT, t -> {
            if(t.getName() == null) {
                t = t.withName(name.getValue());
            }
            return t;
        });
        this.column = column;
        if (itemType != Type.TEXT) {
            throw new InvalidAttributeTypeException(
                    "Item type %s is not supported for multi-value attributes".formatted(itemType));
        }
        this.itemType = itemType;
        this.flags = flags;
        this.constraints = constraints;
        for (var flag : this.flags) {
            flag.checkSupported(this);
        }
        for (var constraint : this.constraints) {
            if (!(constraint instanceof Constraint.AllowedValuesConstraint)) {
                throw new InvalidConstraintException(
                        "Constraint %s is not supported for multi-value attributes"
                                .formatted(constraint.getClass().getSimpleName()));
            }
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

    public static MultivalueAttributeBuilder builder() {
        return new MultivalueAttributeBuilder()
                .translations(new TranslatableImpl<>(ConfigurableAttributeTranslations::new));
    }

    public static class MultivalueAttributeBuilder extends TranslationBuilderSupport<AttributeTranslations, ConfigurableAttributeTranslations, MultivalueAttributeBuilder> {
        {
            getTranslations = () -> translations;
        }

        public MultivalueAttributeBuilder description(String description) {
            return translationsBy(Locale.ROOT, t -> t.withDescription(description));
        }

    }

}
