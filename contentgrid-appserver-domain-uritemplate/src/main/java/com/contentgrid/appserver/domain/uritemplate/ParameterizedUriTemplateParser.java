package com.contentgrid.appserver.domain.uritemplate;

import com.contentgrid.appserver.domain.uritemplate.ParameterizedUriTemplate.ExpressionUriTemplatePart;
import com.contentgrid.appserver.domain.uritemplate.ParameterizedUriTemplate.ExpressionUriTemplatePart.Operator;
import com.contentgrid.appserver.domain.uritemplate.ParameterizedUriTemplate.ExpressionUriTemplatePart.VariableDefinition;
import com.contentgrid.appserver.domain.uritemplate.ParameterizedUriTemplate.LiteralUriTemplatePart;
import com.contentgrid.appserver.domain.uritemplate.ParameterizedUriTemplate.SubstitutionUriTemplatePart;
import com.contentgrid.appserver.domain.uritemplate.StringParser.NamedPattern;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.With;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class ParameterizedUriTemplateParser<S extends Enum<S> & SubstitutionVariableDefinition> {
    private final Map<String, S> substitutionVariables;

    @With
    private final boolean allowTemplateVariables;

    // rfc6570 Section 2.3
    private static final String CHAR_PATTERN = "([a-z0-9_]|%[0-9a-f]{2})";
    private static final NamedPattern VARIABLE_PATTERN = NamedPattern.compile("variable name",
            CHAR_PATTERN + "+(\\." + CHAR_PATTERN + "+)*", Pattern.CASE_INSENSITIVE);
    private static final NamedPattern NUMBER = NamedPattern.compile("number", "[0-9]+");

    private static final NamedPattern END_OF_STATIC = NamedPattern.compile("end of static part", "%?\\{|$");

    public ParameterizedUriTemplateParser(Set<S> substitutionVariables) {
        this(
                substitutionVariables.stream()
                        .collect(Collectors.toUnmodifiableMap(
                                SubstitutionVariableDefinition::getName,
                                Function.identity()
                        )),
                true
        );
    }

    public ParameterizedUriTemplate<S> parse(String template) throws InvalidUriTemplateException {
        var parser = new StringParser(template);

        List<ParameterizedUriTemplate.UriTemplatePart<S>> parts = new ArrayList<>();

        do {
            if (parser.consumeMatching("%{")) { // dynamic
                parts.add(parseSubstitutionPart(parser));
                parser.swallow("}");
            } else if (parser.consumeMatching("{")) { // variable
                parts.add(parseExpressionPart(parser));
                parser.swallow("}");
            } else { // static
                parts.add(new LiteralUriTemplatePart<S>(parser.consumeUntilBefore(END_OF_STATIC)));
            }
        } while (parser.hasMore());

        return new ParameterizedUriTemplate<>(parts);
    }

    @SneakyThrows(InvalidUriTemplateException.class)
    public ParameterizedUriTemplate<S> parseUnchecked(String template) {
        return parse(template);
    }

    private SubstitutionUriTemplatePart<S> parseSubstitutionPart(StringParser parser) throws InvalidUriTemplateException {
        var variable = parser.consumeUntilBefore("}");

        var substitutionVariable = substitutionVariables.get(variable);

        if (substitutionVariable == null) {
            throw parser.errorPrevious("Substitution must be any of %s".formatted(
                    substitutionVariables.keySet().stream().map(s -> "'" + s + "'").collect(Collectors.joining(", "))));
        }

        return new SubstitutionUriTemplatePart<>(substitutionVariable);

    }

    private ExpressionUriTemplatePart<S> parseExpressionPart(StringParser parser) throws InvalidUriTemplateException {
        if(!allowTemplateVariables) {
            throw parser.error("URI template expressions are not allowed");
        }

        var operator = parseOperator(parser);

        List<VariableDefinition> variables = new ArrayList<>();
        do {
            // Section 2.3: Variables
            var variable = parser.consumeMatching(VARIABLE_PATTERN)
                    .orElseThrow(() -> parser.error("Invalid variable name"))
                    .group();

            Integer maxLength = null;
            if (parser.consumeMatching(":")) { // Section 2.4.1: Prefix values
                var maxLenStr = parser.consumeMatching(NUMBER)
                        .orElseThrow(() -> parser.error("Invalid prefix max-length (must be number)"))
                        .group();
                maxLength = Integer.parseInt(maxLenStr);
            }

            boolean explode = false;
            if (parser.consumeMatching("*")) { // Section 2.4.2: Composite values
                if (maxLength != null) {
                    throw parser.errorPrevious("Prefix max-length and composite value can not be combined");
                }
                explode = true;
            }
            variables.add(new VariableDefinition(variable, maxLength, explode));

        } while (parser.consumeMatching(","));

        return new ExpressionUriTemplatePart<>(operator, variables);
    }

    private Operator parseOperator(StringParser parser) throws InvalidUriTemplateException {
        var operatorStr = parser.consumeUntilBefore(VARIABLE_PATTERN);
        var operator = Operator.forString(operatorStr);

        if (operator == null) {
            throw parser.errorPrevious("Unsupported operator '%s'".formatted(operatorStr));
        }

        return operator;
    }

}
