package com.contentgrid.appserver.application.model.propertypath;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class InvalidPropertyPathException extends Exception {
    private static final Map<Class<? extends PropertyPath>, String> DESCRIPTIONS = Map.of(
            PropertyPath.class, "property path",
            PropertyPath.ResolvesToAttribute.class, "path that resolves to attribute",
            PropertyPath.ResolvesToRelation.class, "path that resolves to relation",
            PropertyPath.CrossesAttribute.class, "path that crosses attribute",
            PropertyPath.CrossesRelation.class, "path that crosses relation",
            AttributePath.class, "attribute path"
    );

    private record Alternate(
            Class<? extends PropertyPath> requiredType,
            List<Class<? extends PropertyPath>> conflictingTypes
    ) {
        public boolean matches(PropertyPath path, Class<? extends PropertyPath> requested) {
            return requiredType.isAssignableFrom(requested) && conflictingTypes.stream().anyMatch(t -> t.isInstance(path));
        }
    }
    private static final List<Alternate> ALTERNATES = List.of(
            new Alternate(AttributePath.class, List.of(PropertyPath.ResolvesToRelation.class, PropertyPath.CrossesRelation.class)),
            new Alternate(PropertyPath.ResolvesToAttribute.class, List.of(PropertyPath.ResolvesToRelation.class)),
            new Alternate(PropertyPath.ResolvesToRelation.class, List.of(PropertyPath.ResolvesToAttribute.class)),
            new Alternate(PropertyPath.CrossesAttribute.class, List.of(PropertyPath.CrossesRelation.class)),
            new Alternate(PropertyPath.CrossesRelation.class, List.of(PropertyPath.CrossesAttribute.class))
    );

    private static String createMismatchMessage(PropertyPath path, Class<? extends PropertyPath> requested) {
        return ALTERNATES.stream()
                .filter(alt -> alt.matches(path, requested))
                .map(alt -> {
                    var conflicting = alt.conflictingTypes.stream().filter(t -> t.isInstance(path))
                            .map(InvalidPropertyPathException::createDescription)
                            .collect(Collectors.joining(", "));
                    return "expected %s, but path '%s' is %s".formatted(createDescription(alt.requiredType), path, conflicting);
                })
                .findFirst()
                .orElseGet(() -> "expected %s, but path '%s' is %s".formatted(createDescription(requested), path, createDescription(path.getClass())));
    }

    private static String createDescription(Class<? extends PropertyPath> cls) {
        return DESCRIPTIONS.getOrDefault(cls, cls.getSimpleName());
    }

    public InvalidPropertyPathException(PropertyPath path, Class<? extends PropertyPath> expectedType) {
        super(createMismatchMessage(path, expectedType));
    }
}
