## 1. New Module Scaffolding

- [x] 1.1 Create `contentgrid-appserver-content-lifecycle` module directory and `build.gradle` with dependencies on
  `contentgrid-appserver-contentstore-api`, `spring-boot-starter`, and `jooq`
- [x] 1.2 Register the new module in `settings.gradle`
- [x] 1.3 Add the module as a required dependency in `contentgrid-appserver-domain/build.gradle`

## 2. Database Schema

- [x] 2.1 Add `createContentReferencesTable(DSLContext)` and `dropContentReferencesTable(DSLContext)` methods to
  `JOOQTableCreator`, using the JOOQ DSL to create/drop the `_content_references` table (`content_id VARCHAR PRIMARY KEY`,
  `reference_count INTEGER NOT NULL`, `first_referenced_at TIMESTAMP NOT NULL`, `last_dereferenced_at TIMESTAMP`,
  `marked_for_deletion_at TIMESTAMP`); call them from `createTables()` and `dropTables()`
- [x] 2.2 Define `_content_references` table and field references as `DSL.table()` / `DSL.field()` constants in the
  new lifecycle module (no codegen — follow the same inline DSL pattern used elsewhere in `contentgrid-appserver-query-engine-impl-jooq`)

## 3. ContentReferenceTracker Interface and Implementations

- [x] 3.1 Define the `ContentReferenceTracker` interface in the new module with `incrementReference(ContentReference)`
  and `decrementReference(ContentReference)` methods
- [x] 3.2 Implement `JooqContentReferenceTracker`: `incrementReference` upserts a row (insert or increment count, clear
  `marked_for_deletion_at`); `decrementReference` decrements count and sets `marked_for_deletion_at = NOW()` when count
  reaches zero, sets `last_dereferenced_at`
- [x] 3.3 Implement `DeferredContentReferenceTracker` that delegates `incrementReference` directly and registers
  `decrementReference` as a `TransactionSynchronizationManager.afterCommit()` callback
- [x] 3.4 Write unit tests for `JooqContentReferenceTracker` covering: first increment (creates row), second increment (
  increments count, clears deletion marker), decrement to non-zero (only decrements), decrement to zero (sets
  `marked_for_deletion_at`)
- [x] 3.5 Write unit tests for `DeferredContentReferenceTracker` verifying that decrement is not called when the
  transaction rolls back

## 4. Domain Integration — Upload (Increment)

- [x] 4.1 Inject `ContentReferenceTracker` into `ContentUploadAttributeMapper`
- [x] 4.2 After `contentStore.writeContent()` succeeds, call `contentReferenceTracker.incrementReference()` with the new
  `ContentReference`
- [x] 4.3 Write tests verifying increment is called on upload

## 5. Domain Integration — Content Cleared (Decrement)

- [x] 5.1 Update `ContentAttributeModificationValidator` to collect content IDs that are being set to null; expose
  them (e.g., return them from `validate()` or accumulate in a field)
- [x] 5.2 In `DatamodelApiImpl.update()` and `updatePartial()`, after the query engine operation, call
  `contentReferenceTracker.decrementReference()` for each collected dereferenced content ID
- [x] 5.3 Write tests verifying decrement is called when content is cleared and not called when attribute was previously
  empty

## 6. Domain Integration — Entity Delete (Decrement)

- [x] 6.1 Add a helper in `DatamodelApiImpl` that extracts all non-null `ContentReference` values from an
  `InternalEntityInstance` by scanning the `Application` model for `ContentAttribute` instances
- [x] 6.2 In `DatamodelApiImpl.deleteEntity()`, after `queryEngine.delete()` returns the deleted entity, call
  `contentReferenceTracker.decrementReference()` for each extracted content reference
- [x] 6.3 Write tests for the delete path: entity with multiple content attributes (all decremented), entity with no
  content attributes (no decrement), entity with mix of populated and empty content attributes

## 7. ContentDeletionJob

- [x] 7.1 Implement `ContentReferenceVerificationQuery` that, given a `content_id`, queries all entity tables with
  content attributes and returns true if any reference exists
- [x] 7.2 Implement `ContentDeletionJob`: query `_content_references` where
  `marked_for_deletion_at <= NOW() - grace_period` (batch-limited); for each candidate, run the safety check; if
  unreferenced delete from `ContentStore` and remove the row, if referenced clear the mark and record drift
- [x] 7.3 Record metrics: `content.deletion.success`, `content.deletion.failure`, `content.deletion.drift`
- [x] 7.4 Expose the job as a Spring Boot `CommandLineRunner` or `ApplicationRunner` for K8s CronJob invocation
- [x] 7.5 Write unit tests for `ContentDeletionJob`: candidate past grace period and unreferenced (deleted), candidate
  within grace period (skipped), candidate with drift detected (marker cleared, metric incremented), failure in
  `ContentStore.remove()` (continues to next candidate)

## 8. Configuration and Auto-Configuration

- [x] 8.1 Define `ContentLifecycleProperties` with `deletion.enabled` (default `true`),
  `deletion.grace-period` (default `P7D`), `deletion.batch-size` (default `100`)
- [x] 8.2 Write `ContentLifecycleAutoConfiguration` that creates `JooqContentReferenceTracker`,
  `DeferredContentReferenceTracker`, `ContentReferenceVerificationQuery`, and `ContentDeletionJob` beans
- [x] 8.3 Register the auto-configuration in
  `contentgrid-appserver-autoconfigure/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

## 9. Migration Script for Existing Data

- [x] 9.1 Document the backfill SQL pattern (UNION ALL across all entity content columns) in the module README or a
  runbook
- [x] 9.2 Consider whether the backfill should be an optional Flyway migration or a manual operation; document the
  chosen approach
