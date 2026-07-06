package com.contentgrid.appserver.application.model.propertypath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.contentgrid.appserver.application.model.propertypath.CompositeRelationPath.CompositeRelationPathToAttribute;
import com.contentgrid.appserver.application.model.propertypath.CompositeRelationPath.CompositeRelationPathToRelation;
import com.contentgrid.appserver.application.model.propertypath.PropertyPath.CrossesAttribute;
import com.contentgrid.appserver.application.model.propertypath.PropertyPath.CrossesRelation;
import com.contentgrid.appserver.application.model.propertypath.PropertyPath.ResolvesToAttribute;
import com.contentgrid.appserver.application.model.propertypath.PropertyPath.ResolvesToRelation;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.PropertyName;
import com.contentgrid.appserver.application.model.values.RelationName;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PropertyPathTest {

    private static final AttributeName ATTRIBUTE = AttributeName.of("attribute");
    private static final AttributeName NESTED_ATTRIBUTE = AttributeName.of("nested");
    private static final RelationName RELATION = RelationName.of("relation");
    private static final RelationName OTHER_RELATION = RelationName.of("other_relation");

    @Nested
    class Factory {

        @Test
        void singleAttribute() {
            var path = PropertyPath.of(ATTRIBUTE);

            assertThat(path).isInstanceOfSatisfying(SimpleAttributePath.class, attributePath -> {
                assertThat(attributePath.getFirst()).isEqualTo(ATTRIBUTE);
                assertThat(attributePath.getRest()).isNull();
            });
        }

        @Test
        void singleRelation() {
            var path = PropertyPath.of(RELATION);

            assertThat(path).isInstanceOfSatisfying(SimpleRelationPath.class, relationPath -> {
                assertThat(relationPath.getFirst()).isEqualTo(RELATION);
                assertThat(relationPath.getRest()).isNull();
            });
        }

        @Test
        void attributeInAttribute() {
            var path = PropertyPath.of(ATTRIBUTE, NESTED_ATTRIBUTE);

            assertThat(path).isInstanceOfSatisfying(CompositeAttributePath.class, attributePath -> {
                assertThat(attributePath.getFirst()).isEqualTo(ATTRIBUTE);
                assertThat(attributePath.getRest()).isEqualTo(new SimpleAttributePath(NESTED_ATTRIBUTE));
            });
        }

        @Test
        void attributeInRelation() {
            var path = PropertyPath.of(RELATION, ATTRIBUTE);

            assertThat(path).isInstanceOfSatisfying(CompositeRelationPathToAttribute.class, relationPath -> {
                assertThat(relationPath.getFirst()).isEqualTo(RELATION);
                assertThat(relationPath.getRest()).isEqualTo(new SimpleAttributePath(ATTRIBUTE));
            });
        }

        @Test
        void relationInRelation() {
            var path = PropertyPath.of(RELATION, OTHER_RELATION);

            assertThat(path).isInstanceOfSatisfying(CompositeRelationPathToRelation.class, relationPath -> {
                assertThat(relationPath.getFirst()).isEqualTo(RELATION);
                assertThat(relationPath.getRest()).isEqualTo(new SimpleRelationPath(OTHER_RELATION));
            });
        }

        @Test
        void nestedAttributeAcrossRelations() {
            var path = PropertyPath.of(RELATION, OTHER_RELATION, ATTRIBUTE, NESTED_ATTRIBUTE);

            assertThat(path).isInstanceOfSatisfying(CompositeRelationPathToAttribute.class, relationPath -> {
                assertThat(relationPath.getFirst()).isEqualTo(RELATION);
                assertThat(relationPath.getRest())
                        .isInstanceOfSatisfying(CompositeRelationPathToAttribute.class, nestedRelationPath -> {
                            assertThat(nestedRelationPath.getFirst()).isEqualTo(OTHER_RELATION);
                            assertThat(nestedRelationPath.getRest())
                                    .isInstanceOfSatisfying(CompositeAttributePath.class, attributePath -> {
                                        assertThat(attributePath.getFirst()).isEqualTo(ATTRIBUTE);
                                        assertThat(attributePath.getRest())
                                                .isEqualTo(new SimpleAttributePath(NESTED_ATTRIBUTE));
                                    });
                        });
            });
        }

        @Test
        void relationInAttribute_throws() {
            assertThatThrownBy(() -> PropertyPath.of(ATTRIBUTE, RELATION))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> PropertyPath.of(ATTRIBUTE, RELATION, NESTED_ATTRIBUTE))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void emptyPath_throws() {
            assertThatThrownBy(PropertyPath::of)
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> PropertyPath.of(List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void ofList() {
            var path = PropertyPath.of(List.of(RELATION, ATTRIBUTE));

            assertThat(path).isEqualTo(PropertyPath.of(RELATION, ATTRIBUTE));
        }

        @Test
        void toAttribute_attributeNames() {
            AttributePath path = PropertyPath.toAttribute(ATTRIBUTE, NESTED_ATTRIBUTE);

            assertThat(path).isEqualTo(PropertyPath.of(ATTRIBUTE, NESTED_ATTRIBUTE));
        }

        @Test
        void toAttribute_propertyNames() throws InvalidPropertyPathException {
            ResolvesToAttribute path = PropertyPath.toAttribute(RELATION, ATTRIBUTE);

            assertThat(path).isEqualTo(PropertyPath.of(RELATION, ATTRIBUTE));
        }

        @Test
        void toAttribute_resolvingToRelation_throws() {
            assertThatThrownBy(() -> PropertyPath.toAttribute(RELATION, OTHER_RELATION))
                    .isInstanceOf(InvalidPropertyPathException.class);
        }

        @Test
        void toAttributeUnchecked() throws InvalidPropertyPathException {
            var path = PropertyPath.toAttributeUnchecked(RELATION, ATTRIBUTE);

            assertThat(path).isEqualTo(PropertyPath.toAttribute(RELATION, ATTRIBUTE));
        }

        @Test
        void toAttributeUnchecked_resolvingToRelation_throws() {
            assertThatThrownBy(() -> PropertyPath.toAttributeUnchecked(RELATION, OTHER_RELATION))
                    .isInstanceOf(InvalidPropertyPathException.class);
        }

        @Test
        void compositeRelationPathOf() {
            assertThat(CompositeRelationPath.of(RELATION, new SimpleAttributePath(ATTRIBUTE)))
                    .isInstanceOf(CompositeRelationPathToAttribute.class);
            assertThat(CompositeRelationPath.of(RELATION, new SimpleRelationPath(OTHER_RELATION)))
                    .isInstanceOf(CompositeRelationPathToRelation.class);
        }
    }

    @Nested
    class TypedViews {

        @Test
        void attributePathViews() {
            var path = PropertyPath.of(ATTRIBUTE, NESTED_ATTRIBUTE);

            assertThat(path)
                    .isInstanceOf(CrossesAttribute.class)
                    .isInstanceOf(ResolvesToAttribute.class)
                    .isInstanceOf(AttributePath.class);
        }

        @Test
        void relationToAttributeViews() {
            var path = PropertyPath.of(RELATION, ATTRIBUTE);

            assertThat(path)
                    .isInstanceOf(CrossesRelation.class)
                    .isInstanceOf(ResolvesToAttribute.class);
        }

        @Test
        void relationToRelationViews() {
            var path = PropertyPath.of(RELATION, OTHER_RELATION);

            assertThat(path)
                    .isInstanceOf(CrossesRelation.class)
                    .isInstanceOf(ResolvesToRelation.class);
        }

        @Test
        void terminalRelationViews() {
            var path = PropertyPath.of(RELATION);

            assertThat(path)
                    .isInstanceOf(CrossesRelation.class)
                    .isInstanceOf(ResolvesToRelation.class);
        }
    }

    @Nested
    class As {

        @Test
        void matchingType() throws InvalidPropertyPathException {
            var path = PropertyPath.of(RELATION, ATTRIBUTE);

            assertThat(path.as(ResolvesToAttribute.class)).isSameAs(path);
            assertThat(path.as(CrossesRelation.class)).isSameAs(path);
            assertThat(path.as(CompositeRelationPathToAttribute.class)).isSameAs(path);
        }

        @Test
        void mismatchedResolveTarget() {
            var path = PropertyPath.of(RELATION, OTHER_RELATION);

            assertThatThrownBy(() -> path.as(ResolvesToAttribute.class))
                    .isInstanceOf(InvalidPropertyPathException.class)
                    .hasMessage("expected path that resolves to attribute, but path 'relation.other_relation' is path that resolves to relation");
        }

        @Test
        void relationPathAsAttributePath() {
            var path = PropertyPath.of(RELATION, ATTRIBUTE);

            assertThatThrownBy(() -> path.as(AttributePath.class))
                    .isInstanceOf(InvalidPropertyPathException.class)
                    .hasMessage("expected attribute path, but path 'relation.attribute' is path that crosses relation");
        }

        @Test
        void mismatchedFirstComponent() {
            var path = PropertyPath.of(ATTRIBUTE);

            assertThatThrownBy(() -> path.as(CrossesRelation.class))
                    .isInstanceOf(InvalidPropertyPathException.class)
                    .hasMessage("expected path that crosses relation, but path 'attribute' is path that crosses attribute");
        }

        @Test
        void mismatchedConcreteType() {
            var path = PropertyPath.of(ATTRIBUTE);

            assertThatThrownBy(() -> path.as(CompositeAttributePath.class))
                    .isInstanceOf(InvalidPropertyPathException.class)
                    .hasMessage("expected CompositeAttributePath, but path 'attribute' is SimpleAttributePath");
        }
    }

    @Nested
    class WithSuffix {

        @Test
        void onSimpleAttributePath() {
            var path = new SimpleAttributePath(ATTRIBUTE).withSuffix(NESTED_ATTRIBUTE);

            assertThat(path).isEqualTo(PropertyPath.of(ATTRIBUTE, NESTED_ATTRIBUTE));
        }

        @Test
        void onCompositeAttributePath() {
            var path = PropertyPath.toAttribute(ATTRIBUTE, NESTED_ATTRIBUTE).withSuffix(AttributeName.of("deep"));

            assertThat(path).isEqualTo(PropertyPath.of(ATTRIBUTE, NESTED_ATTRIBUTE, AttributeName.of("deep")));
        }
    }

    static Stream<Arguments> paths() {
        return Stream.of(
                Arguments.of(PropertyPath.of(ATTRIBUTE), List.of("attribute"), "attribute"),
                Arguments.of(PropertyPath.of(RELATION), List.of("relation"), "relation"),
                Arguments.of(PropertyPath.of(ATTRIBUTE, NESTED_ATTRIBUTE), List.of("attribute", "nested"),
                        "attribute.nested"),
                Arguments.of(PropertyPath.of(RELATION, ATTRIBUTE), List.of("relation", "attribute"),
                        "relation.attribute"),
                Arguments.of(PropertyPath.of(RELATION, OTHER_RELATION), List.of("relation", "other_relation"),
                        "relation.other_relation"),
                Arguments.of(PropertyPath.of(RELATION, OTHER_RELATION, ATTRIBUTE, NESTED_ATTRIBUTE),
                        List.of("relation", "other_relation", "attribute", "nested"),
                        "relation.other_relation.attribute.nested")
        );
    }

    @ParameterizedTest
    @MethodSource("paths")
    void toList(PropertyPath path, List<String> expected) {
        assertThat(path.toList()).isEqualTo(expected);
    }

    @ParameterizedTest
    @MethodSource("paths")
    void toStringRepresentation(PropertyPath path, List<String> expectedList, String expected) {
        assertThat(path).hasToString(expected);
    }

    @ParameterizedTest
    @MethodSource("paths")
    void equality(PropertyPath path) {
        var copy = PropertyPath.of(collectNames(path));

        assertThat(copy).isEqualTo(path);
        assertThat(copy).hasSameHashCodeAs(path);
    }

    @ParameterizedTest
    @MethodSource("paths")
    void serializationRoundTrip(PropertyPath path) throws Exception {
        var buffer = new ByteArrayOutputStream();
        try (var output = new ObjectOutputStream(buffer)) {
            output.writeObject(path);
        }
        try (var input = new ObjectInputStream(new ByteArrayInputStream(buffer.toByteArray()))) {
            assertThat(input.readObject()).isEqualTo(path);
        }
    }

    @Test
    void unequalPaths() {
        assertThat(PropertyPath.of(ATTRIBUTE)).isNotEqualTo(PropertyPath.of(NESTED_ATTRIBUTE));
        assertThat(PropertyPath.of(ATTRIBUTE)).isNotEqualTo(PropertyPath.of(RELATION, ATTRIBUTE));
        assertThat(PropertyPath.of(RELATION, ATTRIBUTE)).isNotEqualTo(PropertyPath.of(RELATION, OTHER_RELATION));
    }

    private static List<PropertyName> collectNames(PropertyPath path) {
        var names = new java.util.ArrayList<PropertyName>();
        for (var current = path; current != null; current = current.getRest()) {
            names.add(current.getFirst());
        }
        return names;
    }
}
