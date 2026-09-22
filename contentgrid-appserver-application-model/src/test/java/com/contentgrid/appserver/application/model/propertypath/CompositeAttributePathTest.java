package com.contentgrid.appserver.application.model.propertypath;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.appserver.application.model.values.AttributeName;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CompositeAttributePathTest {

    private static final AttributeName NAME_ROOT = AttributeName.of("root");
    private static final AttributeName NAME_BRANCH = AttributeName.of("branch");
    private static final AttributeName NAME_LEAF = AttributeName.of("leaf");
    private static final SimpleAttributePath LEAF = new SimpleAttributePath(NAME_LEAF);
    private static final CompositeAttributePath BRANCH = new CompositeAttributePath(NAME_BRANCH, LEAF);
    private static final CompositeAttributePath ROOT = new CompositeAttributePath(NAME_ROOT, BRANCH);

    private static Stream<Arguments> testGetAsList() {
        return Stream.of(
                Arguments.of(ROOT, List.of(new SimpleAttributePath(NAME_ROOT), new CompositeAttributePath(NAME_ROOT, new SimpleAttributePath(NAME_BRANCH)), ROOT)),
                Arguments.of(BRANCH, List.of(new SimpleAttributePath(NAME_BRANCH), BRANCH)),
                Arguments.of(LEAF, List.of(LEAF))
        );
    }

    @ParameterizedTest
    @MethodSource
    void testGetAsList(AttributePath in, List<AttributePath> expected) {
        assertThat(in.getAsList()).containsExactlyInAnyOrder(expected.toArray(expected.toArray(new AttributePath[0])));
    }
}
