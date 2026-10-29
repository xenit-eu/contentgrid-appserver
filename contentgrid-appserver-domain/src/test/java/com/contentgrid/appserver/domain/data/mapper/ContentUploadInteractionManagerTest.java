package com.contentgrid.appserver.domain.data.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.contentgrid.appserver.contentstore.api.ContentAccessor;
import com.contentgrid.appserver.contentstore.api.ContentReference;
import com.contentgrid.appserver.contentstore.api.ContentStore;
import com.contentgrid.appserver.contentstore.api.UnwritableContentException;
import com.contentgrid.appserver.domain.data.DataEntry.FileDataEntry.InputStreamSupplier;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
class ContentUploadInteractionManagerTest {

    private static final String CONTENT_A = "Hello world";
    private static final String CONTENT_B = "Some other content";
    private static final String FAILING_CONTENT = "Failing content";

    @Mock
    private ContentStore contentStore;

    private ExecutorService executor;

    /**
     * References of all content that has been successfully written to the {@link #contentStore}
     */
    private final List<ContentReference> written = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setup() {
        executor = Executors.newCachedThreadPool();
    }

    @AfterEach
    void teardown() throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    private static InputStreamSupplier supplierOf(String content) {
        return () -> new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Content is "stored" by using the content itself as the reference, so assertions on the reference also check the content
     */
    private static ContentReference referenceOf(String content) {
        return ContentReference.of(content);
    }

    private void stubWriteContent() throws UnwritableContentException {
        stubWriteContent(data -> {
        });
    }

    private void stubWriteContent(BeforeWrite beforeWrite) throws UnwritableContentException {
        lenient().when(contentStore.writeContent(any())).thenAnswer((Answer<ContentAccessor>) invocation -> {
            InputStream inputStream = invocation.getArgument(0);
            var data = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            beforeWrite.run(data);
            var reference = referenceOf(data);
            written.add(reference);
            var accessor = mock(ContentAccessor.class);
            lenient().when(accessor.getReference()).thenReturn(reference);
            lenient().when(accessor.getDescription()).thenReturn(data);
            return accessor;
        });
    }

    @FunctionalInterface
    private interface BeforeWrite {

        void run(String data) throws Exception;
    }

    @Nested
    class Upload {

        @Test
        void scheduleUpload_doesNotUploadImmediately() {
            var supplier = mock(InputStreamSupplier.class);
            var manager = new ContentUploadInteractionManager(contentStore, executor);

            manager.scheduleUpload(supplier);

            verifyNoInteractions(supplier, contentStore);
        }

        @Test
        void performInteractions_uploadsAllContent() throws UnwritableContentException {
            stubWriteContent();
            var manager = new ContentUploadInteractionManager(contentStore, executor);

            var resultA = manager.scheduleUpload(supplierOf(CONTENT_A));
            var resultB = manager.scheduleUpload(supplierOf(CONTENT_B));

            manager.performInteractions();

            verify(contentStore, times(2)).writeContent(any());

            assertThat(resultA.get().size()).isEqualTo(CONTENT_A.length());
            assertThat(resultA.get().accessor().getReference()).isEqualTo(referenceOf(CONTENT_A));

            assertThat(resultB.get().size()).isEqualTo(CONTENT_B.length());
            assertThat(resultB.get().accessor().getReference()).isEqualTo(referenceOf(CONTENT_B));
        }

        @Test
        void performInteractions_uploadsInParallel() throws UnwritableContentException {
            var bothStarted = new CountDownLatch(2);
            stubWriteContent(data -> {
                bothStarted.countDown();
                // Only succeeds when both uploads are running at the same time
                if (!bothStarted.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Uploads were not running in parallel");
                }
            });
            var manager = new ContentUploadInteractionManager(contentStore, executor);

            var resultA = manager.scheduleUpload(supplierOf(CONTENT_A));
            var resultB = manager.scheduleUpload(supplierOf(CONTENT_B));

            manager.performInteractions();

            assertThat(resultA.get().size()).isEqualTo(CONTENT_A.length());
            assertThat(resultB.get().size()).isEqualTo(CONTENT_B.length());
        }

        @Test
        void resolvingLazy_uploadsWithoutPerformInteractions() throws UnwritableContentException {
            stubWriteContent();
            var manager = new ContentUploadInteractionManager(contentStore, executor);

            var resultA = manager.scheduleUpload(supplierOf(CONTENT_A));
            var resultB = manager.scheduleUpload(supplierOf(CONTENT_B));

            assertThat(resultA.get().accessor().getReference()).isEqualTo(referenceOf(CONTENT_A));

            // Only the resolved upload is performed
            assertThat(written).containsExactly(referenceOf(CONTENT_A));

            manager.performInteractions();

            // The already completed upload is not performed again
            assertThat(written).containsExactly(referenceOf(CONTENT_A), referenceOf(CONTENT_B));
            assertThat(resultB.get().size()).isEqualTo(CONTENT_B.length());
        }

        @Test
        void performInteractions_multipleTimes_uploadsOnlyOnce() throws UnwritableContentException {
            stubWriteContent();
            var manager = new ContentUploadInteractionManager(contentStore, executor);

            var result = manager.scheduleUpload(supplierOf(CONTENT_A));

            manager.performInteractions();
            manager.performInteractions();

            verify(contentStore, times(1)).writeContent(any());
            assertThat(result.get().size()).isEqualTo(CONTENT_A.length());
        }

        @Test
        void inputStreamSupplierFailure_isThrownFromResult() throws UnwritableContentException {
            stubWriteContent();
            var manager = new ContentUploadInteractionManager(contentStore, executor);
            var exception = new IOException("Can not open stream");

            var failing = manager.scheduleUpload(() -> {
                throw exception;
            });
            var succeeding = manager.scheduleUpload(supplierOf(CONTENT_A));

            assertThatCode(manager::performInteractions).doesNotThrowAnyException();

            assertThatThrownBy(failing::get).isSameAs(exception);
            assertThat(succeeding.get().size()).isEqualTo(CONTENT_A.length());
            verify(contentStore, times(1)).writeContent(any());
        }

        @Test
        void contentStoreFailure_isThrownFromResult() throws UnwritableContentException {
            when(contentStore.writeContent(any())).thenThrow(new UnwritableContentException(ContentReference.UNKNOWN));
            var manager = new ContentUploadInteractionManager(contentStore, executor);

            var result = manager.scheduleUpload(supplierOf(CONTENT_A));

            assertThatCode(manager::performInteractions).doesNotThrowAnyException();

            assertThatThrownBy(result::get).hasRootCauseInstanceOf(UnwritableContentException.class);
        }
    }

    @Nested
    class Commit {

        @Test
        void commit_keepsUploadedContent() throws UnwritableContentException {
            stubWriteContent();
            var manager = new ContentUploadInteractionManager(contentStore, executor);

            var result = manager.scheduleUpload(supplierOf(CONTENT_A));
            manager.performInteractions();
            manager.commitInteractions();

            // Rollback after commit has no effect on committed uploads
            manager.rollbackUncommittedInteractions();

            verify(contentStore, never()).remove(any());
            assertThat(result.get().accessor().getReference()).isEqualTo(referenceOf(CONTENT_A));
        }

        @Test
        void rollback_afterCommit_onlyAffectsNewUploads() throws UnwritableContentException {
            stubWriteContent();
            var manager = new ContentUploadInteractionManager(contentStore, executor);

            manager.scheduleUpload(supplierOf(CONTENT_A));
            manager.performInteractions();
            manager.commitInteractions();

            manager.scheduleUpload(supplierOf(CONTENT_B));
            manager.performInteractions();
            manager.rollbackUncommittedInteractions();

            verify(contentStore).remove(referenceOf(CONTENT_B));
            verify(contentStore, never()).remove(referenceOf(CONTENT_A));
        }

        @Test
        void commit_leavesUploadsScheduledAfterPerformInteractionsPending() throws UnwritableContentException {
            stubWriteContent();
            var manager = new ContentUploadInteractionManager(contentStore, executor);

            manager.scheduleUpload(supplierOf(CONTENT_A));
            manager.performInteractions();

            var lateSupplier = mock(InputStreamSupplier.class);
            manager.scheduleUpload(lateSupplier);

            manager.commitInteractions();

            // The upload scheduled after performInteractions is not started by commit
            verifyNoInteractions(lateSupplier);
            assertThat(written).containsExactly(referenceOf(CONTENT_A));
        }

        @Test
        void commit_uploadsScheduledAfterPerformInteractions_arePerformedByNextPerformInteractions()
                throws UnwritableContentException {
            stubWriteContent();
            var manager = new ContentUploadInteractionManager(contentStore, executor);

            manager.scheduleUpload(supplierOf(CONTENT_A));
            manager.performInteractions();
            var late = manager.scheduleUpload(supplierOf(CONTENT_B));
            manager.commitInteractions();

            manager.performInteractions();
            manager.commitInteractions();
            manager.rollbackUncommittedInteractions();

            assertThat(written).containsExactly(referenceOf(CONTENT_A), referenceOf(CONTENT_B));
            assertThat(late.get().accessor().getReference()).isEqualTo(referenceOf(CONTENT_B));
            verify(contentStore, never()).remove(any());
        }

        @Test
        void rollback_cancelsUploadsScheduledAfterPerformInteractions() throws Exception {
            stubWriteContent();
            var manager = new ContentUploadInteractionManager(contentStore, executor);

            manager.scheduleUpload(supplierOf(CONTENT_A));
            manager.performInteractions();
            var lateSupplier = mock(InputStreamSupplier.class);
            var late = manager.scheduleUpload(lateSupplier);
            manager.commitInteractions();

            manager.rollbackUncommittedInteractions();

            // The committed upload is kept, the late upload is never performed
            verify(contentStore, never()).remove(any());
            verifyNoInteractions(lateSupplier);
            assertThatThrownBy(late::get)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Upload has been cancelled");
        }

        @Test
        void commit_afterPerformInteractions_allUploadsFinished() throws UnwritableContentException {
            var uploadsFinished = new AtomicInteger();
            stubWriteContent(data -> {
                // Slow uploads, to make sure that performInteractions waits for them
                Thread.sleep(50);
                uploadsFinished.incrementAndGet();
            });
            var manager = new ContentUploadInteractionManager(contentStore, executor);

            manager.scheduleUpload(supplierOf(CONTENT_A));
            manager.scheduleUpload(supplierOf(CONTENT_B));

            manager.performInteractions();
            manager.commitInteractions();

            assertThat(uploadsFinished).hasValue(2);
            assertThat(written).containsExactlyInAnyOrder(referenceOf(CONTENT_A), referenceOf(CONTENT_B));
        }
    }

    @Nested
    class Rollback {

        @Test
        void rollback_beforeStart_doesNotUpload() {
            var supplier = mock(InputStreamSupplier.class);
            var manager = new ContentUploadInteractionManager(contentStore, executor);

            var result = manager.scheduleUpload(supplier);

            manager.rollbackUncommittedInteractions();

            verifyNoInteractions(supplier, contentStore);
            assertThatThrownBy(result::get)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Upload has been cancelled");
        }

        @Test
        void rollback_beforeStart_doesNotUploadOnPerformInteractions() {
            var supplier = mock(InputStreamSupplier.class);
            var manager = new ContentUploadInteractionManager(contentStore, executor);

            manager.scheduleUpload(supplier);
            manager.rollbackUncommittedInteractions();
            manager.performInteractions();

            verifyNoInteractions(supplier, contentStore);
        }

        @Test
        void rollback_afterUpload_removesContent() throws UnwritableContentException {
            stubWriteContent();
            var manager = new ContentUploadInteractionManager(contentStore, executor);

            manager.scheduleUpload(supplierOf(CONTENT_A));
            manager.scheduleUpload(supplierOf(CONTENT_B));
            manager.performInteractions();

            manager.rollbackUncommittedInteractions();

            verify(contentStore).remove(referenceOf(CONTENT_A));
            verify(contentStore).remove(referenceOf(CONTENT_B));
        }

        @Test
        void rollback_duringUpload_waitsForUploadAndRemovesContent() throws Exception {
            var uploadStarted = new CountDownLatch(1);
            var allowUpload = new CountDownLatch(1);
            stubWriteContent(data -> {
                uploadStarted.countDown();
                allowUpload.await();
            });
            var manager = new ContentUploadInteractionManager(contentStore, executor);
            var result = manager.scheduleUpload(supplierOf(CONTENT_A));

            // Start the upload without waiting for it to complete
            executor.execute(manager::performInteractions);
            assertThat(uploadStarted.await(5, TimeUnit.SECONDS)).isTrue();

            var rollbackDone = new CountDownLatch(1);
            executor.execute(() -> {
                manager.rollbackUncommittedInteractions();
                rollbackDone.countDown();
            });

            // Rollback must wait until the upload has completed
            assertThat(rollbackDone.await(200, TimeUnit.MILLISECONDS)).isFalse();

            allowUpload.countDown();
            assertThat(rollbackDone.await(5, TimeUnit.SECONDS)).isTrue();

            verify(contentStore).remove(referenceOf(CONTENT_A));
            assertThatThrownBy(result::get)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Upload has been cancelled");
        }

    }

    /**
     * Stages of an upload where an exception can be thrown
     */
    enum FailureStage {
        SUPPLIER_IO_EXCEPTION,
        SUPPLIER_RUNTIME_EXCEPTION,
        STREAM_READ_IO_EXCEPTION,
        STORE_WRITE_UNWRITABLE_EXCEPTION,
        STORE_WRITE_RUNTIME_EXCEPTION,
        STORE_REMOVE_UNWRITABLE_EXCEPTION,
        STORE_REMOVE_RUNTIME_EXCEPTION,
    }

    static Stream<Arguments> failureStages() {
        return Stream.of(FailureStage.values())
                .flatMap(stage -> Stream.of(
                        Arguments.of(stage, true),
                        Arguments.of(stage, false)
                ));
    }

    @Nested
    class FailureHandling {

        /**
         * Configures the content store to fail at the given stage for {@link #FAILING_CONTENT}
         *
         * @return supplier for the upload that will fail
         */
        private InputStreamSupplier setupFailure(FailureStage stage) throws UnwritableContentException {
            stubWriteContent(data -> {
                if (data.equals(FAILING_CONTENT)) {
                    switch (stage) {
                        case STORE_WRITE_UNWRITABLE_EXCEPTION ->
                                throw new UnwritableContentException(ContentReference.UNKNOWN);
                        case STORE_WRITE_RUNTIME_EXCEPTION -> throw new IllegalStateException("Store failure");
                        default -> {
                        }
                    }
                }
            });
            switch (stage) {
                case STORE_REMOVE_UNWRITABLE_EXCEPTION ->
                        lenient().doThrow(new UnwritableContentException(referenceOf(FAILING_CONTENT)))
                                .when(contentStore).remove(referenceOf(FAILING_CONTENT));
                case STORE_REMOVE_RUNTIME_EXCEPTION -> lenient().doThrow(new IllegalStateException("Remove failure"))
                        .when(contentStore).remove(referenceOf(FAILING_CONTENT));
                default -> {
                }
            }

            return switch (stage) {
                case SUPPLIER_IO_EXCEPTION -> () -> {
                    throw new IOException("Supplier failure");
                };
                case SUPPLIER_RUNTIME_EXCEPTION -> () -> {
                    throw new IllegalStateException("Supplier failure");
                };
                case STREAM_READ_IO_EXCEPTION -> () -> new InputStream() {
                    @Override
                    public int read() throws IOException {
                        throw new IOException("Read failure");
                    }
                };
                default -> supplierOf(FAILING_CONTENT);
            };
        }

        private ContentUploadInteractionManager scheduleUploads(FailureStage stage, boolean failingFirst)
                throws UnwritableContentException {
            var manager = new ContentUploadInteractionManager(contentStore, executor);
            var failingSupplier = setupFailure(stage);
            if (failingFirst) {
                manager.scheduleUpload(failingSupplier);
                manager.scheduleUpload(supplierOf(CONTENT_A));
            } else {
                manager.scheduleUpload(supplierOf(CONTENT_A));
                manager.scheduleUpload(failingSupplier);
            }
            return manager;
        }

        private static void performIgnoringFailure(ContentUploadInteractionManager manager) {
            try {
                manager.performInteractions();
            } catch (Exception e) {
                // Failure while performing is allowed, but must not affect commit or rollback
            }
        }

        @ParameterizedTest(name = "{0}, failing upload first: {1}")
        @MethodSource("com.contentgrid.appserver.domain.data.mapper.ContentUploadInteractionManagerTest#failureStages")
        void commit_afterFailure(FailureStage stage, boolean failingFirst) throws UnwritableContentException {
            var manager = scheduleUploads(stage, failingFirst);

            performIgnoringFailure(manager);

            assertThatCode(manager::commitInteractions).doesNotThrowAnyException();

            // The successful upload has finished, even when a failure prevented performInteractions from starting it
            assertThat(written).contains(referenceOf(CONTENT_A));

            // Nothing is rolled back anymore after commit
            assertThatCode(manager::rollbackUncommittedInteractions).doesNotThrowAnyException();
            verify(contentStore, never()).remove(any());
        }

        @ParameterizedTest(name = "{0}, failing upload first: {1}")
        @MethodSource("com.contentgrid.appserver.domain.data.mapper.ContentUploadInteractionManagerTest#failureStages")
        void rollback_afterFailure(FailureStage stage, boolean failingFirst) throws UnwritableContentException {
            var manager = scheduleUploads(stage, failingFirst);

            performIgnoringFailure(manager);

            assertThatCode(manager::rollbackUncommittedInteractions).doesNotThrowAnyException();
            assertThat(Thread.currentThread().isInterrupted()).isFalse();

            // Removal is attempted for all content that was written
            for (var reference : written) {
                verify(contentStore).remove(reference);
            }

            // Rolling back again does not attempt to remove content again
            assertThatCode(manager::rollbackUncommittedInteractions).doesNotThrowAnyException();
            verify(contentStore, times(written.size())).remove(any());
        }

        @ParameterizedTest(name = "{0}, failing upload first: {1}")
        @MethodSource("com.contentgrid.appserver.domain.data.mapper.ContentUploadInteractionManagerTest#failureStages")
        void rollback_afterFailureFromLazyResult_cleansUpAllUploads(FailureStage stage, boolean failingFirst)
                throws UnwritableContentException {
            var manager = new ContentUploadInteractionManager(contentStore, executor);
            var failingSupplier = setupFailure(stage);
            var failing = manager.scheduleUpload(failingSupplier);
            var succeeding = manager.scheduleUpload(supplierOf(CONTENT_A));

            performIgnoringFailure(manager);

            // Resolve the results as a consumer would, which may throw for the failing upload
            var results = failingFirst ? List.of(failing, succeeding) : List.of(succeeding, failing);
            for (var result : results) {
                try {
                    result.get();
                } catch (Exception e) {
                    // The consumer handles the failure by rolling back
                    break;
                }
            }

            assertThatCode(manager::rollbackUncommittedInteractions).doesNotThrowAnyException();

            // Every upload that was written, including the successful one, has been cleaned up
            for (var reference : written) {
                verify(contentStore).remove(reference);
            }
        }

        @ParameterizedTest(name = "{0}, failing upload first: {1}")
        @MethodSource("com.contentgrid.appserver.domain.data.mapper.ContentUploadInteractionManagerTest#failureStages")
        void managerIsReusable_afterRollbackOfFailure(FailureStage stage, boolean failingFirst)
                throws UnwritableContentException {
            var manager = scheduleUploads(stage, failingFirst);

            performIgnoringFailure(manager);
            try {
                manager.rollbackUncommittedInteractions();
            } catch (Exception e) {
                // Ignored here, rollback failures are checked by rollback_afterFailure
            }

            var result = manager.scheduleUpload(supplierOf(CONTENT_B));
            manager.performInteractions();
            manager.commitInteractions();
            manager.rollbackUncommittedInteractions();

            assertThat(result.get().accessor().getReference()).isEqualTo(referenceOf(CONTENT_B));
            verify(contentStore, never()).remove(referenceOf(CONTENT_B));
        }
    }
}
