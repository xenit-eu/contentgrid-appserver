package com.contentgrid.appserver.domain.data;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.NonNull;

/**
 * A decorator for {@link RequestInputData} that tracks which keys are present but unused.
 * <p>
 * This implementation wraps another {@link RequestInputData} instance and maintains a set of unused keys.
 * When keys are accessed through the various getter methods, they are marked as used and removed from
 * the unused keys set. This allows callers to determine which keys from the input data were not consumed.
 * <p>
 * The tracking extends to nested {@link RequestInputData} objects, where nested keys are tracked using
 * dot notation (e.g., "parent.child").
 */
public class UsageTrackingRequestInputData implements RequestInputData {
    @NonNull
    private final RequestInputData delegate;

    @NonNull
    private final Set<String> unusedKeys;

    @NonNull
    private final Consumer<String> markKeyAsUsed;

    public UsageTrackingRequestInputData(@NonNull RequestInputData delegate) {
        this(delegate, s -> {});
    }

    private UsageTrackingRequestInputData(
            @NonNull RequestInputData delegate,
            @NonNull Consumer<String> markKeyAsUsed
    ) {
        this.delegate = delegate;
        this.unusedKeys = delegate.keys().collect(Collectors.toSet());
        this.markKeyAsUsed = markKeyAsUsed.andThen(unusedKeys::remove);
    }

    public Set<String> getUnusedKeys() {
        return Collections.unmodifiableSet(unusedKeys);
    }

    @Override
    public Stream<String> keys() {
        return delegate.keys();
    }

    @Override
    public DataEntry get(String key, Class<? extends DataEntry> typeHint) throws InvalidDataException {
        markKeyAsUsed.accept(key);
        return delegate.get(key, typeHint);
    }

    @Override
    public Result<List<? extends DataEntry>> getList(String key, Class<? extends DataEntry> entryTypeHint)
            throws InvalidDataException {
        markKeyAsUsed.accept(key);
        return delegate.getList(key, entryTypeHint);
    }

    @Override
    public Result<RequestInputData> nested(String key) throws InvalidDataException {
        markKeyAsUsed.accept(key);
        return delegate.nested(key)
                .map(nested -> {
                    nested.keys().forEach(k -> unusedKeys.add(key+"."+k));
                    return new UsageTrackingRequestInputData(nested, s -> unusedKeys.remove(key+"."+s));
                });
    }
}
