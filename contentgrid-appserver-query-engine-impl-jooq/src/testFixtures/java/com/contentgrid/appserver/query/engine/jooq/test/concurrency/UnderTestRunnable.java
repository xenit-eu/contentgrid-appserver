package com.contentgrid.appserver.query.engine.jooq.test.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.With;

public interface UnderTestRunnable<P, T> {
    P prepare();
    T test(P preparation);
    void verify(T result);
    void cleanup();

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
        private final Runnable cleanup;

        @With(value = AccessLevel.PRIVATE)
        @NonNull
        private final Consumer<T> verification;

        private UnderTestRunnableBuilder(@NonNull Supplier<P> prepare, @NonNull Function<P, T> test) {
            this(prepare, test, () -> {}, t -> {});
        }

        public UnderTestRunnableBuilder<P, T> verify(@NonNull Consumer<T> verification) {
            return withVerification(this.verification.andThen(verification));
        }

        public UnderTestRunnableBuilder<P, T> cleanup(@NonNull Runnable cleanup) {
            var oldCleanup = this.cleanup;
            return withCleanup(() -> {
                oldCleanup.run();
                cleanup.run();
            });
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
        public void verify(T result) {
            assertThat(result)
                    .satisfies(verification);
        }

        @Override
        public void cleanup() {
            cleanup.run();
        }
    }
}
