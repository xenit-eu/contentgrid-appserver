package com.contentgrid.appserver.query.engine.jooq.test.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.With;

/**
 * Simple interface that holds a bunch of functions that are used to run during a concurrency test
 * <p>
 * A concurrency test exists of multiple independent runs.
 * Every run consists of 4 phases, that are executed in order:
 * <ol>
 * <li>{@link #prepare()}: Set up state of the database to prepare for a test run
 * <li>{@link #test(Object)}: Performs the actual test run. Executes the main code that is under test. Interfering, concurrently running code is also executed at some point while this function is executing
 * <li>{@link #verify(Object, Object)}: Verifies the results of the test run
 * <li>{@link #cleanup(Object, Object)}: Clean up database changes done during {@link #prepare()} and {@link #test(Object)}, so the next run can be performed from a clean starting point
 * </ol>
 *
 * @param <P> Result from preparation. Used to pass some information (usually generated entity IDs) to later stages
 * @param <T> Result from test. Used to pass some information (entity IDs, thrown exceptions, ...) to later stages
 */
public interface UnderTestRunnable<P, T> {
    P prepare();
    T test(P preparation);
    void verify(P preparation, T result);
    void cleanup(P preparation, T result);

    static <P, T> UnderTestRunnableBuilder<P, T> test(Supplier<P> prepare, Function<P, T> test) {
        return new UnderTestRunnableBuilder<>(prepare, test);
    }

    static <T> UnderTestRunnableBuilder<Void, T> test(Supplier<T> test) {
        return test(() -> {}, test);
    }

    static <T> UnderTestRunnableBuilder<Void, T> test(Runnable prepare, Supplier<T> test) {
        return test(() -> {
            prepare.run();
            return null;
        }, p -> test.get());
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    class UnderTestRunnableBuilder<P, T> implements UnderTestRunnable<P, T> {
        @NonNull
        private final Supplier<P> prepare;

        @NonNull
        private final Function<P, T> test;

        @NonNull
        @With(value = AccessLevel.PRIVATE)
        private final BiConsumer<P, T> cleanup;

        @With(value = AccessLevel.PRIVATE)
        @NonNull
        private final BiConsumer<P, T> verification;

        private UnderTestRunnableBuilder(@NonNull Supplier<P> prepare, @NonNull Function<P, T> test) {
            this(prepare, test, (p, t) -> {}, (p, t) -> {});
        }

        public UnderTestRunnableBuilder<P, T> verify(@NonNull Consumer<T> verification) {
            return verify((p, t) -> verification.accept(t));
        }

        public UnderTestRunnableBuilder<P, T> verify(@NonNull BiConsumer<P, T> verification) {
            return withVerification(this.verification.andThen(verification));
        }

        public UnderTestRunnableBuilder<P, T> cleanup(@NonNull BiConsumer<P, T> cleanup) {
            return withCleanup(this.cleanup.andThen(cleanup));
        }

        public UnderTestRunnableBuilder<P, T> cleanup(@NonNull Runnable cleanup) {
            return cleanup((p, t) -> cleanup.run());
        }

        @Override
        public P prepare() {
            return prepare.get();
        }

        @Override
        public T test(P preparation) {
            return test.apply(preparation);
        }

        @Override
        public void verify(P preparation, T result) {
            assertThat(result)
                    .satisfies(r -> verification.accept(preparation, r));
        }

        @Override
        public void cleanup(P preparation, T result) {
            cleanup.accept(preparation, result);
        }
    }
}
