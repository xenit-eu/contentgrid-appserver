package com.contentgrid.appserver.domain.data.mapper;

import com.contentgrid.appserver.contentstore.api.ContentAccessor;
import com.contentgrid.appserver.contentstore.api.ContentStore;
import com.contentgrid.appserver.contentstore.api.UnwritableContentException;
import com.contentgrid.appserver.domain.data.DataEntry.FileDataEntry.InputStreamSupplier;
import com.contentgrid.appserver.domain.data.Lazy;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Function;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages content uploads done by {@link ContentUploadAttributeMapper}
 * <p>
 * <ol>
 * <li>Uploads of multiple content attributes are performed in parallel
 * <li>Uploads can be cancelled from any state when required, including after their upload was finished. (This will remove the content from the store again)
 * </ol>
 */
@RequiredArgsConstructor
@Slf4j
public class ContentUploadInteractionManager implements ExternalInteractionManager {
    @NonNull
    private final ContentStore contentStore;
    @NonNull
    private final Executor asyncExecutor;
    private final List<Upload> uploads = new ArrayList<>();

    /**
     * Schedule an upload to be performed when {@link #performInteractions()} is called, or when the returned {@link Lazy} is resolved
     * @param inputStreamSupplier Supplier of an input stream
     * @return A lazy value that contains the upload result. Resolving the lazy value will result in the upload being performed immediately, and waiting for it to complete
     */
    @Synchronized
    public Lazy<UploadResult> scheduleUpload(@NonNull InputStreamSupplier inputStreamSupplier) {
        var pendingUpload = new Upload(inputStreamSupplier);
        uploads.add(pendingUpload);
        return Lazy.of(pendingUpload::waitForResult).map(Upload.CompletedState::getResult);
    }

    @Override
    @Synchronized
    public void performInteractions() {
        uploads.forEach(Upload::start);
        uploads.forEach(Upload::waitForResult);
    }

    @Override
    @Synchronized
    public void commitInteractions() {
        uploads.removeIf(Upload::isFinished);
    }

    @Override
    @Synchronized
    public void rollbackUncommittedInteractions() {
        boolean wasInterrupted = false;
        try {
            List<Upload> cancelledUploads = new ArrayList<>();
            for (var pendingUpload : uploads) {
                try {
                    pendingUpload.cancel();
                    cancelledUploads.add(pendingUpload);
                } catch (InterruptedException e) {
                    wasInterrupted = true;
                }
            }
            uploads.removeAll(cancelledUploads);
        } finally {
            if (wasInterrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public record UploadResult(
            ContentAccessor accessor,
            long size
    ) {

    }

    /**
     * An upload that can be pending, in progress or completed (successfully, failed or cancelled)
     */
    private class Upload {
        private State state;

        public Upload(InputStreamSupplier inputStream) {
            this.state = new NotStartedState(inputStream);
        }

        public void start() {
            advanceState(NotStartedState.class, NotStartedState::retrieveInputStream);
            advanceState(InputStreamRetrievedState.class, streamRetrieved -> streamRetrieved.start(contentStore, asyncExecutor));
        }

        public void cancel() throws InterruptedException {
            state = state.cancel(contentStore);
        }

        private <T extends State> void advanceState(Class<T> stateClass, Function<T, State> mapper) {
            if (stateClass.isInstance(state)) {
                state = mapper.apply(state.as(stateClass));
            }
        }

        @SneakyThrows
        private CompletedState waitForResult() {
            advanceState(NotStartedState.class, NotStartedState::retrieveInputStream);
            advanceState(InputStreamRetrievedState.class, streamRetrieved -> {
                log.warn("Starting upload for {} during wait for result", this);
                return streamRetrieved.start(contentStore, asyncExecutor);
            });
            advanceState(UploadingState.class, UploadingState::waitForCompletion);

            return state.as(CompletedState.class);
        }

        public boolean isFinished() {
            return state instanceof CompletedState;
        }

        /**
         * Holds the state of an upload
         * <p>
         * In each step during an upload, different variables are active, and different actions can be taken.
         * These actions are specific for each state and require different parameters
         */
        private sealed interface State {
            default <T extends State> T as(Class<T> stateClass) {
                if(!stateClass.isInstance(this)) {
                    throw new IllegalStateException("Upload must be in %s, but is %s".formatted(stateClass.getSimpleName(), this.getClass().getSimpleName()));
                }
                return stateClass.cast(this);
            }

            CancelledState cancel(ContentStore contentStore) throws InterruptedException;
        }

        /**
         * A completed state of an upload
         * <p>
         * When the upload is completed, the result of the operation can be retrieved
         */
        private sealed interface CompletedState extends State {

            /**
             * Retrieves the result of the upload, or throws an exception that occurred while uploading
             * @return The result of the upload
             */
            UploadResult getResult();
        }

        record NotStartedState(
                InputStreamSupplier inputStreamSupplier
        ) implements State {
            public State retrieveInputStream() {
                try {
                    return new InputStreamRetrievedState(inputStreamSupplier.getInputStream());
                } catch (Exception e) {
                    return new UploadFailedState(e);
                }
            }

            @Override
            public CancelledState cancel(ContentStore contentStore) {
                return new CancelledState();
            }
        }

        record InputStreamRetrievedState(
                InputStream inputStream
        ) implements State {
            public UploadingState start(ContentStore contentStore, Executor executor) {
                var countingInputStream = new CountingInputStream(inputStream);
                var future = CompletableFuture.supplyAsync(() -> {
                    try {
                        return contentStore.writeContent(countingInputStream);
                    } catch (UnwritableContentException e) {
                        throw new RuntimeException(e);
                    }
                }, executor);
                return new UploadingState(
                        countingInputStream,
                        future
                );
            }

            @Override
            public CancelledState cancel(ContentStore contentStore) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    log.warn("Failed to clean up upload stream", e);
                }
                return new CancelledState();
            }
        }

        record UploadingState(
                CountingInputStream inputStream,
                CompletableFuture<ContentAccessor> future
        ) implements State {

            public CompletedState waitForCompletion() {
                try {
                    var result = future.join();
                    return new SuccessState(inputStream, result);
                } catch (CompletionException e) {
                    return new UploadFailedState(e.getCause());
                }
            }

            @Override
            public CancelledState cancel(ContentStore contentStore) throws InterruptedException {
                return waitForCompletion().cancel(contentStore);
            }
        }

        record UploadFailedState(
                Throwable failure
        ) implements State, CompletedState {

            public UploadFailedState(Throwable failure) {
                this.failure = failure;
                if (failure instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }

            @Override
            public CancelledState cancel(ContentStore contentStore) {
                return new CancelledState();
            }

            @Override
            @SneakyThrows
            public UploadResult getResult() {
                throw failure;
            }
        }

        record SuccessState(
                CountingInputStream inputStream,
                ContentAccessor accessor
        ) implements State, CompletedState {

            @Override
            public CancelledState cancel(ContentStore contentStore) {
                try {
                    contentStore.remove(accessor.getReference());
                } catch (Exception e) {
                    log.warn("Failed to clean up uploaded content {}", accessor.getDescription(), e);
                    if (e instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                }
                return new CancelledState();

            }

            @Override
            public UploadResult getResult() {
                return new UploadResult(accessor, inputStream.getSize());
            }
        }

        record CancelledState() implements State, CompletedState {

            @Override
            public CancelledState cancel(ContentStore contentStore) {
                return this;
            }

            @Override
            public UploadResult getResult() {
                throw new IllegalStateException("Upload has been cancelled");
            }
        }

    }


}
