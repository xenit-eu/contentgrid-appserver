package com.contentgrid.appserver.application.model.values;

import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Value;
import lombok.experimental.Delegate;

@Value(staticConstructor = "of")
public class PropertyPath {
    @Delegate(types = DelegateToList.class)
    List<PropertyName> value;

    public static PropertyPath of(PropertyName... elements) {
        return new PropertyPath(List.of(elements));
    }

    public String toString() {
        return value.stream().map(PropertyName::getValue).collect(Collectors.joining("."));
    }

    private interface DelegateToList {
        Stream<PropertyName> stream();
        int size();
        boolean isEmpty();
        PropertyName getFirst();
        PropertyName getLast();
        PropertyName get(int i);
        boolean contains(Object o);
        int indexOf(Object o);
        Iterator<PropertyName> iterator();
    }
}
