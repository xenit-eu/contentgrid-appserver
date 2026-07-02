package com.contentgrid.appserver.domain.uritemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.contentgrid.appserver.domain.uritemplate.ParameterizedUriTemplate.ExpressionUriTemplatePart;
import com.contentgrid.appserver.domain.uritemplate.ParameterizedUriTemplate.ExpressionUriTemplatePart.Operator;
import com.contentgrid.appserver.domain.uritemplate.ParameterizedUriTemplate.ExpressionUriTemplatePart.VariableDefinition;
import com.contentgrid.appserver.domain.uritemplate.ParameterizedUriTemplate.LiteralUriTemplatePart;
import com.contentgrid.appserver.domain.uritemplate.ParameterizedUriTemplate.SubstitutionUriTemplatePart;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ParameterizedUriTemplateParserTest {

    @RequiredArgsConstructor
    @Getter
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    enum TestSubstitutionParameters implements SubstitutionVariableDefinition {
        APPLICATION_ID("application.id");
        String name;
    }

    private final ParameterizedUriTemplateParser<TestSubstitutionParameters> parser = new ParameterizedUriTemplateParser<>(
            EnumSet.allOf(TestSubstitutionParameters.class)
    );

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void standardUriTemplatePatterns(
            String pattern,
            ParameterizedUriTemplate<?> template
    ) throws InvalidUriTemplateException {
        var parsed = parser.parse(pattern);

        assertThat(parsed).isEqualTo(template);

        assertThat(parsed.toTemplate()).isEqualTo(pattern);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void brokenUriTemplatePatterns(
            String pattern,
            int position
    ) {
        assertThatThrownBy(() -> parser.parse(pattern))
                .isInstanceOfSatisfying(InvalidUriTemplateException.class, ex -> {
                    assertThat(ex.getPosition()).isEqualTo(position);
                });
    }

    @Test
    void mixedTemplate() throws InvalidUriTemplateException {
        assertThat(parser.parse("/abc%20/%{application.id}{?var_1,var%2F2}#final")).isEqualTo(
                new ParameterizedUriTemplate<>(List.of(
                        new LiteralUriTemplatePart("/abc%20/"),
                        new SubstitutionUriTemplatePart<>(TestSubstitutionParameters.APPLICATION_ID),
                        new ExpressionUriTemplatePart(Operator.FORM_STYLE_PARAM,
                                List.of(new VariableDefinition("var_1"), new VariableDefinition("var%2F2"))),
                        new LiteralUriTemplatePart("#final")
                ))
        );
    }

    @Test
    void expand() throws InvalidUriTemplateException {
        var template = parser.parseUnchecked("https://example.com/test?app=%{application.id}");
        assertThat(template.expand(new ParameterReplacer<TestSubstitutionParameters>() {
            @Override
            public String replace(TestSubstitutionParameters substitutionVariable) {
                return "test123";
            }
        })).isEqualTo("https://example.com/test?app=test123");
    }

    @Test
    void expandUrlEncoded() throws InvalidUriTemplateException {
        var template = parser.parseUnchecked("https://example.com/test?app=%{application.id}");
        assertThat(template.expand(new ParameterReplacer<TestSubstitutionParameters>() {
            @Override
            public String replace(TestSubstitutionParameters substitutionVariable) {
                return "&#$/=";
            }
        })).isEqualTo("https://example.com/test?app=%26%23%24%2F%3D");
    }

    public static Stream<Arguments> standardUriTemplatePatterns() {
        return Stream.of(
                Arguments.of("{var}",
                        new ParameterizedUriTemplate<>(List.of(new ExpressionUriTemplatePart(Operator.SIMPLE, "var")))),
                Arguments.of("{+var}",
                        new ParameterizedUriTemplate<>(
                                List.of(new ExpressionUriTemplatePart(Operator.RESERVED, "var")))),
                Arguments.of("{+path}/here", new ParameterizedUriTemplate<>(
                        List.of(new ExpressionUriTemplatePart(Operator.RESERVED, "path"),
                                new LiteralUriTemplatePart("/here")))),
                Arguments.of("X{#var}", new ParameterizedUriTemplate<>(List.of(new LiteralUriTemplatePart("X"),
                        new ExpressionUriTemplatePart(Operator.FRAGMENT, "var")))),
                Arguments.of("map{?x,y}", new ParameterizedUriTemplate<>(List.of(new LiteralUriTemplatePart("map"),
                        new ExpressionUriTemplatePart(Operator.FORM_STYLE_PARAM,
                                List.of(new VariableDefinition("x"), new VariableDefinition("y")))))),
                Arguments.of("{x,hello,y}", new ParameterizedUriTemplate<>(
                        List.of(new ExpressionUriTemplatePart(Operator.SIMPLE,
                                List.of(new VariableDefinition("x"), new VariableDefinition("hello"),
                                        new VariableDefinition("y")))))),
                Arguments.of("X{.var}", new ParameterizedUriTemplate<>(List.of(new LiteralUriTemplatePart("X"),
                        new ExpressionUriTemplatePart(Operator.LABEL_DOT_PREFIX, "var")))),
                Arguments.of("X{.x,y}", new ParameterizedUriTemplate<>(List.of(new LiteralUriTemplatePart("X"),
                        new ExpressionUriTemplatePart(Operator.LABEL_DOT_PREFIX,
                                List.of(new VariableDefinition("x"), new VariableDefinition("y")))))),
                Arguments.of("{/var}", new ParameterizedUriTemplate<>(
                        List.of(new ExpressionUriTemplatePart(Operator.PATH_SEGMENT, "var")))),
                Arguments.of("{/var,x}", new ParameterizedUriTemplate<>(
                        List.of(new ExpressionUriTemplatePart(Operator.PATH_SEGMENT,
                                List.of(new VariableDefinition("var"), new VariableDefinition("x")))))),
                Arguments.of("{;x,y}", new ParameterizedUriTemplate<>(
                        List.of(new ExpressionUriTemplatePart(Operator.PATH_STYLE_PARAM,
                                List.of(new VariableDefinition("x"), new VariableDefinition("y")))))),
                Arguments.of("?fixed=yes{&x}", new ParameterizedUriTemplate<>(
                        List.of(new LiteralUriTemplatePart("?fixed=yes"),
                                new ExpressionUriTemplatePart(Operator.FORM_STYLE_CONTINUATION, "x")))),
                Arguments.of("{&x,y,empty}", new ParameterizedUriTemplate<>(
                        List.of(new ExpressionUriTemplatePart(Operator.FORM_STYLE_CONTINUATION,
                                List.of(new VariableDefinition("x"), new VariableDefinition("y"),
                                        new VariableDefinition("empty")))))),
                Arguments.of("{var:3}", new ParameterizedUriTemplate<>(List.of(new ExpressionUriTemplatePart(
                        Operator.SIMPLE, List.of(new VariableDefinition("var", 3, false)))))),
                Arguments.of("{list*}", new ParameterizedUriTemplate<>(List.of(new ExpressionUriTemplatePart(
                        Operator.SIMPLE, List.of(new VariableDefinition("list", null, true)))))),
                Arguments.of("{+path:6}", new ParameterizedUriTemplate<>(List.of(new ExpressionUriTemplatePart(
                        Operator.RESERVED, List.of(new VariableDefinition("path", 6, false)))))),
                Arguments.of("{+list*}", new ParameterizedUriTemplate<>(List.of(new ExpressionUriTemplatePart(
                        Operator.RESERVED, List.of(new VariableDefinition("list", null, true)))))),
                Arguments.of("{#path:6}", new ParameterizedUriTemplate<>(List.of(new ExpressionUriTemplatePart(
                        Operator.FRAGMENT, List.of(new VariableDefinition("path", 6, false)))))),
                Arguments.of("{#list*}", new ParameterizedUriTemplate<>(List.of(new ExpressionUriTemplatePart(
                        Operator.FRAGMENT, List.of(new VariableDefinition("list", null, true)))))),
                Arguments.of("X{.path:6}", new ParameterizedUriTemplate<>(
                        List.of(new LiteralUriTemplatePart("X"), new ExpressionUriTemplatePart(
                                Operator.LABEL_DOT_PREFIX, List.of(new VariableDefinition("path", 6, false)))))),
                Arguments.of("X{.list*}", new ParameterizedUriTemplate<>(
                        List.of(new LiteralUriTemplatePart("X"), new ExpressionUriTemplatePart(
                                Operator.LABEL_DOT_PREFIX, List.of(new VariableDefinition("list", null, true)))))),
                Arguments.of("{/var:1,var}", new ParameterizedUriTemplate<>(List.of(new ExpressionUriTemplatePart(
                        Operator.PATH_SEGMENT,
                        List.of(new VariableDefinition("var", 1, false), new VariableDefinition("var")))))),
                Arguments.of("{/list*,path:4}", new ParameterizedUriTemplate<>(List.of(new ExpressionUriTemplatePart(
                        Operator.PATH_SEGMENT, List.of(new VariableDefinition("list", null, true),
                        new VariableDefinition("path", 4, false)))))),

                Arguments.of("{nested.access}", new ParameterizedUriTemplate<>(
                        List.of(new ExpressionUriTemplatePart(Operator.SIMPLE, "nested.access")))),
                Arguments.of("{underscore_var}", new ParameterizedUriTemplate<>(
                        List.of(new ExpressionUriTemplatePart(Operator.SIMPLE, "underscore_var")))),
                Arguments.of("%{application.id}", new ParameterizedUriTemplate<>(
                        List.of(new SubstitutionUriTemplatePart<>(TestSubstitutionParameters.APPLICATION_ID)))),
                Arguments.of("/just/a/static/path",
                        new ParameterizedUriTemplate<>(List.of(new LiteralUriTemplatePart("/just/a/static/path"))))
        );
    }


    public static Stream<Arguments> brokenUriTemplatePatterns() {
        return Stream.of(
                Arguments.of("{", 1),
                Arguments.of("{%xbc}", 1),
                Arguments.of("{}", 1),
                Arguments.of("{ab/cd}", 3),
                Arguments.of("{ab:4*}", 5),
                Arguments.of("{ab:xyz}", 4),
                Arguments.of("{ab:85xyz}", 6),
                Arguments.of("{?.abc}", 1),
                Arguments.of("{?abc", 5),
                Arguments.of("{?./", 1),
                Arguments.of("%{invalid_variable}", 2)

        );
    }
}