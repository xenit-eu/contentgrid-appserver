package com.contentgrid.appserver.domain.uritemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.experimental.NonFinal;

/**
 * An RFC6570 URI template with additional (non-URI template) substitution parameters.
 * <p>
 * The URI template portion is compliant with RFC6570. The additional substitution parameters use a separate syntax,
 * which is NOT valid in URI templates. This is to ensure that values are not accidentally replaced.
 * <p>
 * Example: <code>/application/%{application.id}/{entity}/history{?id}</code> In this example;
 * <code>%{application.id}</code> is a substitution parameter that has to be filled in before the URI template itself
 * can be rendered. On the other hand, <code>{?id}</code> is a URI template expression, which is not filled in before
 * the URI template is rendered
 */
@EqualsAndHashCode
public class ParameterizedUriTemplate<S extends Enum<S> & SubstitutionVariableDefinition> {

    sealed interface UriTemplatePart<S extends Enum<S> & SubstitutionVariableDefinition> {

        String toTemplate();

        String expand(ParameterReplacer<S> replacer);
    }

    /**
     * A static part of a URI template, not an expression or a substitution parameter
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc6570#section-2.1">RFC6570 Section 2.1</a>
     */
    @Value
    static class LiteralUriTemplatePart<S extends Enum<S> & SubstitutionVariableDefinition> implements
            UriTemplatePart<S> {

        @NonFinal
        String value;

        @Override
        public String toTemplate() {
            return value;
        }

        @Override
        public String expand(ParameterReplacer<S> replacer) {
            return value;
        }
    }

    /**
     * A URI template expression.
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc6570#section-2.2">RFC6570 Section 2.2</a>
     */
    @Value
    @RequiredArgsConstructor
    static class ExpressionUriTemplatePart<S extends Enum<S> & SubstitutionVariableDefinition> implements
            UriTemplatePart<S> {

        Operator operator;

        List<VariableDefinition> variables;

        public ExpressionUriTemplatePart(Operator operator, String variable) {
            this(operator, List.of(new VariableDefinition(variable, null, false)));
        }

        @Override
        public String toTemplate() {
            var sb = new StringBuilder();
            sb.append('{').append(operator.operator);

            boolean isFirst = true;
            for (var variable : variables) {
                if (!isFirst) {
                    sb.append(',');
                }
                isFirst = false;
                variable.appendTo(sb);
            }

            sb.append('}');
            return sb.toString();
        }

        @Override
        public String expand(ParameterReplacer<S> replacer) {
            return toTemplate();
        }

        /**
         * Operator used for template expressions
         *
         * @see <a href="https://www.rfc-editor.org/rfc/rfc6570#section-2.2">RFC6570 Section 2.2</a>
         */
        @RequiredArgsConstructor
        enum Operator {
            SIMPLE(""),
            RESERVED("+"),
            FRAGMENT("#"),
            LABEL_DOT_PREFIX("."),
            PATH_SEGMENT("/"),
            PATH_STYLE_PARAM(";"),
            FORM_STYLE_PARAM("?"),
            FORM_STYLE_CONTINUATION("&"),
            ;

            private final String operator;

            private static final Map<String, Operator> LOOKUP;

            static {
                Map<String, Operator> lookup = new HashMap<>(values().length);
                for (var value : values()) {
                    lookup.put(value.operator, value);
                }
                LOOKUP = Collections.unmodifiableMap(lookup);
            }

            public static Operator forString(String operatorStr) {
                return LOOKUP.get(operatorStr);
            }


        }

        @Value
        @RequiredArgsConstructor
        static class VariableDefinition {

            String variable;

            Integer maxLength;

            boolean explode;

            public VariableDefinition(String variable) {
                this(variable, null, false);
            }

            public void appendTo(StringBuilder stringBuilder) {
                stringBuilder.append(variable);
                if (maxLength != null) {
                    stringBuilder.append(':').append(maxLength);
                }
                if (explode) {
                    stringBuilder.append('*');
                }
            }
        }
    }

    @Value
    static class SubstitutionUriTemplatePart<S extends Enum<S> & SubstitutionVariableDefinition> implements
            UriTemplatePart<S> {

        S variable;

        @Override
        public String toTemplate() {
            return "%{" + variable.getName() + "}";
        }

        @Override
        public String expand(ParameterReplacer<S> replacer) {
            return URLEncoder.encode(replacer.replace(variable), StandardCharsets.UTF_8);
        }
    }

    private final List<UriTemplatePart<S>> parts;

    ParameterizedUriTemplate(List<UriTemplatePart<S>> parts) {
        this.parts = List.copyOf(parts);
    }

    public String toTemplate() {
        return parts.stream().map(UriTemplatePart::toTemplate).collect(Collectors.joining());
    }

    public String expand(ParameterReplacer<S> replacer) {
        return parts.stream()
                .map(part -> part.expand(replacer))
                .collect(Collectors.joining());
    }

    public Collection<String> getTemplateVariables() {
        Set<String> variables = new HashSet<>();

        for (var part : parts) {
            if (part instanceof ExpressionUriTemplatePart<?> expressionPart) {
                for (var variableDefinition : expressionPart.getVariables()) {
                    variables.add(variableDefinition.variable);
                }
            }
        }

        return variables;
    }

    public Collection<S> getSubstitutionVariables() {
        Set<S> variables = new HashSet<>();

        for (var part : parts) {
            if (part instanceof SubstitutionUriTemplatePart<?> substitutionUriTemplatePart) {
                variables.add((S) substitutionUriTemplatePart.variable);
            }
        }

        return Collections.unmodifiableCollection(variables);
    }

}
