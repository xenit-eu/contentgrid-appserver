package com.contentgrid.appserver.application.model.attributes;

import com.contentgrid.appserver.application.model.Constraint;
import com.contentgrid.appserver.application.model.Constraint.RegexPatternConstraint;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.attributes.flags.AttributeFlag;
import com.contentgrid.appserver.application.model.attributes.flags.IgnoredFlag;
import com.contentgrid.appserver.application.model.attributes.flags.ReadOnlyFlag;
import com.contentgrid.appserver.application.model.i18n.ConfigurableTranslatable;
import com.contentgrid.appserver.application.model.i18n.ResourceBundleTranslatable;
import com.contentgrid.appserver.application.model.i18n.Translatable;
import com.contentgrid.appserver.application.model.i18n.TranslatableImpl;
import com.contentgrid.appserver.application.model.i18n.TranslationBuilderSupport;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.LinkName;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;
import lombok.experimental.Delegate;

@Value
public class ContentAttribute implements CompositeAttribute {

    // package-private for testing
    static final RegexPatternConstraint MIMETYPE_PATTERN_CONSTRAINT = Constraint.pattern(MediaTypeABNF.MEDIA_TYPE);

    @NonNull
    AttributeName name;

    @EqualsAndHashCode.Exclude
    @NonNull
    @Delegate
    @Getter(value = AccessLevel.NONE)
    Translatable<AttributeTranslations> translations;

    @NonNull
    PathSegmentName pathSegment;

    @NonNull
    LinkName linkName;

    Set<AttributeFlag> flags;

    @NonNull
    SimpleAttribute id;

    @NonNull
    SimpleAttribute filename;

    @NonNull
    SimpleAttribute mimetype;

    @NonNull
    SimpleAttribute length;

    @Builder
    ContentAttribute(
            @NonNull AttributeName name,
            @NonNull ConfigurableTranslatable<AttributeTranslations, ConfigurableAttributeTranslations> translations,
            @Singular Set<AttributeFlag> flags,
            @NonNull PathSegmentName pathSegment,
            @NonNull LinkName linkName,
            @NonNull ColumnName idColumn,
            @NonNull ColumnName filenameColumn,
            @NonNull ColumnName mimetypeColumn,
            @NonNull ColumnName lengthColumn
    ) {
        this.name = name;
        this.translations = translations.withTranslationsBy(Locale.ROOT, t -> {
            if(t.getName() == null) {
                t = t.withName(name.getValue());
            }
            return t;
        });
        this.flags = flags;
        this.pathSegment = pathSegment;
        this.linkName = linkName;
        var resourceBundleTranslations = ResourceBundleTranslatable.<AttributeTranslations, ConfigurableAttributeTranslations>builder(ConfigurableAttributeTranslations::new)
                .bundleName(getClass().getName())
                .mapping("name", ConfigurableAttributeTranslations::withName)
                .mapping("description", ConfigurableAttributeTranslations::withDescription)
                .build();
        this.id = SimpleAttribute.builder().name(AttributeName.of("id")).column(idColumn)
                .type(Type.TEXT)
                .flag(IgnoredFlag.INSTANCE)
                .build();
        this.filename = SimpleAttribute.builder().name(AttributeName.of("filename")).column(filenameColumn)
                .translations(resourceBundleTranslations.withPrefix("filename.").asConfigurable())
                .type(Type.TEXT).build();
        this.mimetype = SimpleAttribute.builder().name(AttributeName.of("mimetype")).column(mimetypeColumn)
                .translations(resourceBundleTranslations.withPrefix("mimetype.").asConfigurable())
                .type(Type.TEXT)
                .constraint(MIMETYPE_PATTERN_CONSTRAINT)
                .build();
        this.length = SimpleAttribute.builder().name(AttributeName.of("length")).column(lengthColumn)
                .translations(resourceBundleTranslations.withPrefix("length.").asConfigurable())
                .type(Type.LONG)
                .flag(ReadOnlyFlag.INSTANCE)
                .build();

        for (var flag : this.flags) {
            flag.checkSupported(this);
        }
    }

    @Override
    public List<Attribute> getAttributes() {
        return Stream.of(id, filename, mimetype, length).collect(Collectors.toUnmodifiableList());
    }

    public static ContentAttributeBuilder builder() {
        return new ContentAttributeBuilder()
                .translations(new TranslatableImpl<>(ConfigurableAttributeTranslations::new));

    }

    public static class ContentAttributeBuilder extends TranslationBuilderSupport<AttributeTranslations, ConfigurableAttributeTranslations, ContentAttributeBuilder> {
        {
            getTranslations = () -> translations;
        }

        public ContentAttributeBuilder description(String description) {
            return translationsBy(Locale.ROOT, t -> t.withDescription(description));
        }

    }

    /**
     * Constructs the media-type validation regex based on RFC9110 ABNF definitions
     */
    private static class MediaTypeABNF {
        // https://www.rfc-editor.org/rfc/rfc5234#appendix-B.1
        private static final ABNFCharRange DQUOTE = ABNFCharRange.of(0x22);
        private static final ABNFCharRange HTAB = ABNFCharRange.of(0x09);
        private static final ABNFCharRange SP = ABNFCharRange.of(0x20);
        private static final ABNFCharRange VCHAR = ABNFCharRange.range(0x21, 0x7e);
        private static final ABNFCharRange DIGIT = ABNFCharRange.range(0x30, 0x39);
        private static final ABNFCharRange ALPHA = new ABNFCharCompositeRange(
                ABNFCharRange.range(0x41, 0x5A),
                ABNFCharRange.range(0x61, 0x7A)
        );

        // https://www.rfc-editor.org/rfc/rfc9110.html#name-field-values
        private static final ABNFCharRange OBS_TEXT = ABNFCharRange.range(0x80, 0xff);

        // https://www.rfc-editor.org/rfc/rfc9110.html#name-tokens
        private static final ABNFCharRange TCHAR = new ABNFCharCompositeRange(
                ABNFCharRange.of('!', '#', '$', '%', '&', '*', '+', '-', '.', '^', '_', '`', '|', '~'),
                DIGIT,
                ALPHA
        );
        private static final String TOKEN = TCHAR+"+";

        // https://www.rfc-editor.org/rfc/rfc9110.html#name-whitespace
        private static final String OWS = new ABNFCharCompositeRange(SP, HTAB)+"*";

        // https://www.rfc-editor.org/rfc/rfc9110.html#name-quoted-strings
        private static final ABNFCharRange QDTEXT = new ABNFCharCompositeRange(HTAB, SP, ABNFCharRange.of(0x21), ABNFCharRange.range(0x23, 0x5B), ABNFCharRange.range(0x5D, 0x7E), OBS_TEXT);
        private static final String QUOTED_PAIR = "(?:"+ABNFCharRange.of('\\')+new ABNFCharCompositeRange(HTAB, SP, VCHAR, OBS_TEXT)+")";

        private static final String QUOTED_STRING = "(?:"+DQUOTE + "(?:"+QDTEXT +"|"+QUOTED_PAIR+")*"+DQUOTE+")";

        // https://www.rfc-editor.org/rfc/rfc9110.html#parameter
        private static final String PARAMETER_NAME = TOKEN;
        private static final String PARAMETER_VALUE = "(?:"+TOKEN+"|"+QUOTED_STRING+")";
        private static final String PARAMETER = PARAMETER_NAME+ABNFCharRange.of('=')+PARAMETER_VALUE;
        private static final String PARAMETERS = "(?:"+OWS+ABNFCharRange.of(';')+OWS+"(?:"+PARAMETER+")?)*";

        //  https://www.rfc-editor.org/rfc/rfc9110.html#name-media-type
        public static final String MEDIA_TYPE = TOKEN+"/"+TOKEN+PARAMETERS;

        private sealed interface ABNFCharRange {
            static ABNFCharRange of(char character) {
                return range(character, character);
            }

            static ABNFCharRange of(int character) {
                return of((char)character);
            }

            static ABNFCharRange of(char ...characters) {
                var ranges = new ABNFCharRange[characters.length];
                for (int i = 0; i < characters.length; i++) {
                    ranges[i] = ABNFCharRange.of(characters[i]);
                }
                return new ABNFCharCompositeRange(ranges);
            }

            static ABNFCharRange range(int start, int endInclusive) {
                if(endInclusive < start) {
                    // This is an empty range
                    return new ABNFCharCompositeRange();
                }
                return new ABNFCharSingleRange(start, endInclusive);
            }

            boolean isEmpty();

            String toRegexCharacterClass();
        }

        private record ABNFCharCompositeRange(ABNFCharRange ...ranges) implements ABNFCharRange {

            @Override
            public boolean isEmpty() {
                return Arrays.stream(ranges).allMatch(ABNFCharRange::isEmpty);
            }

            @Override
            public String toRegexCharacterClass() {
                return Arrays.stream(ranges)
                        .filter(Predicate.not(ABNFCharRange::isEmpty))
                        .map(ABNFCharRange::toRegexCharacterClass)
                        .collect(Collectors.joining());
            }

            @Override
            public String toString() {
                return "["+toRegexCharacterClass()+"]";
            }
        }

        private record ABNFCharSingleRange(int start, int endInclusive) implements ABNFCharRange {

            @Override
            public boolean isEmpty() {
                return endInclusive < start;
            }

            private static String encodeCharacter(int character) {
                var hexString = Integer.toHexString(character);
                return switch (hexString.length()) {
                    case 1 -> "\\x0" + hexString;
                    case 2 -> "\\x" + hexString;
                    case 3 -> "\\u0" + hexString;
                    case 4 -> "\\u" + hexString;
                    default -> "\\x{" + hexString + "}";
                };
            }

            @Override
            public String toRegexCharacterClass() {
                if(start == endInclusive) {
                    return encodeCharacter(start);
                }
                return encodeCharacter(start)+"-"+ encodeCharacter(endInclusive);
            }

            @Override
            public String toString() {
                if(start == endInclusive) {
                    return encodeCharacter(start);
                }
                return "["+toRegexCharacterClass()+"]";
            }
        }
    }
}
