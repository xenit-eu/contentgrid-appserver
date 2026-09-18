package com.contentgrid.appserver.query.engine.api.data;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.appserver.application.model.propertypath.PropertyPath;
import com.contentgrid.appserver.application.model.values.AttributeName;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CompositeAttributeDataTest {

    private static final SimpleAttributeData<String> TOP_LEVEL_ATTRIBUTE = new SimpleAttributeData<>(AttributeName.of("topLevelAttribute"), "top level");
    private static final SimpleAttributeData<String> NESTED_SIMPLE_ATTRIBUTE = new SimpleAttributeData<>(AttributeName.of("nestedSimpleAttribute"), "nestedSimple");
    private static final CompositeAttributeData EMPTY_NESTED_COMPOSITE = new CompositeAttributeData(AttributeName.of("nestedComposite"), List.of());
    private static final CompositeAttributeData COMPOSITE = new CompositeAttributeData(AttributeName.of("composite"), List.of(
            NESTED_SIMPLE_ATTRIBUTE, EMPTY_NESTED_COMPOSITE));
    private static final  CompositeAttributeData ROOT = new CompositeAttributeData(AttributeName.of("data"), List.of(
            TOP_LEVEL_ATTRIBUTE,
            COMPOSITE));

    private AttributeName[] mapToPaths(List<String> strings) {
        return strings.stream()
                .map(AttributeName::of)
                .toArray(AttributeName[]::new);
    }

    private static Stream<Arguments> pathsToEmpty() {
        return Stream.of(
                Arguments.of(List.of("")),
                Arguments.of(List.of("non-existing")),
                Arguments.of(List.of(NESTED_SIMPLE_ATTRIBUTE.getName().getValue())),
                Arguments.of(List.of(COMPOSITE.getName().getValue(), "non-existing"))
        );
    }

    @ParameterizedTest
    @MethodSource("pathsToEmpty")
    void testHasAttributes_returnsEmpty(List<String> path) {
        assertThat(ROOT.getNestedAttributeByPath(PropertyPath.toAttribute(mapToPaths(path)))).isEmpty();
    }

    private static Stream<Arguments> pathsToAttributes() {
        return Stream.of(
                Arguments.argumentSet("Top level simple",
                        List.of(TOP_LEVEL_ATTRIBUTE.getName().getValue()),
                        TOP_LEVEL_ATTRIBUTE),
                Arguments.argumentSet("Nested simple",
                        List.of(COMPOSITE.getName().getValue(), NESTED_SIMPLE_ATTRIBUTE.getName().getValue()),
                        NESTED_SIMPLE_ATTRIBUTE),
                Arguments.argumentSet("Top level composite",
                        List.of(COMPOSITE.getName().getValue()),
                        COMPOSITE),
                Arguments.argumentSet("nested composite",
                        List.of(COMPOSITE.getName().getValue(), EMPTY_NESTED_COMPOSITE.getName().getValue()),
                        EMPTY_NESTED_COMPOSITE)
        );
    }

    @ParameterizedTest
    @MethodSource("pathsToAttributes")
    void testHasAttributes_params(List<String> path, AttributeData expected) {
        assertThat(ROOT.getNestedAttributeByPath(PropertyPath.toAttribute(mapToPaths(path))))
                .isNotEmpty()
                .get()
                .isEqualTo(expected);
    }
}
