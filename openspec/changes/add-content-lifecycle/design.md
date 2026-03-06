## Context

The `ContentStore` stores binary content objects identified by a `ContentReference`. Currently, `ContentStore.remove()`
is never called in production — content objects accumulate indefinitely. Two additional requirements shape this design
beyond simple deletion:

1. **Content sharing**: Multiple entities may reference the same content object (same `ContentReference`). Deleting
   content when one entity is removed must not affect others still pointing to it.
2. **Grace period**: Content must not be deleted immediately after dereference — a configurable delay (default 7 days)
   ensures backups are taken before permanent removal.

The domain layer already has all required integration points:

- `ContentUploadAttributeMapper`: writes content to the store (increment point)
- `DatamodelApiImpl.updatePartial()` / `update()`: entity content replaced or cleared (decrement point)
- `DatamodelApiImpl.deleteEntity()`: entity removed (decrement point for all content attributes)
- `ContentAttributeModificationValidator`: detects when a content attribute is being set to null

## Goals / Non-Goals

**Goals:**

- Track reference counts for every content object in a `_content_references` table.
- Increment on upload; decrement after a successful transaction commit when content is dereferenced.
- Mark content for deletion when its reference count reaches zero; do not delete immediately.
- A separately-triggered deletion job verifies references and removes content past the grace period.
- Support content sharing: multiple entities pointing to the same `ContentReference`.

**Non-Goals:**

- Retroactive cleanup of orphaned content from before this feature.
- Exposing a content-sharing API (future consideration).
- Two-phase commit or distributed transaction guarantees between the DB and the content store.
- Deduplication of identical content on upload.

## Decisions

### Decision 1: Reference counting in a dedicated `_content_references` table

**Chosen:** A new table tracks `content_id`, `reference_count`, `first_referenced_at`, `last_dereferenced_at`, and
`marked_for_deletion_at`.

**Alternatives considered:**

- *Immediate deletion in the domain layer (no tracking table)*: Doesn't support content sharing or grace periods.
- *Scan all entity tables on every deletion*: Correct but slow — N queries per deletion where N = number of entity types
  with content attributes. Acceptable for the deletion job (runs infrequently), not acceptable for request-time use.

**Rationale:** A reference count table makes increment/decrement O(1) at request time. The deletion job uses it to find
candidates and the per-entity scan is only done as a safety check before final deletion.

### Decision 2: New module `contentgrid-appserver-content-lifecycle`

**Chosen:** All tracking and deletion logic lives in a new module. The domain module gains an optional dependency on it
via Spring auto-configuration.

**Alternatives considered:**

- *Inline in `contentgrid-appserver-domain`*: Couples database lifecycle concerns with the domain logic; harder to
  disable or replace.

**Rationale:** Keeping lifecycle tracking in its own module makes it independently deployable and configurable (
`contentgrid.content.lifecycle.enabled`). The domain module uses the `ContentReferenceTracker` interface and tolerates a
null/no-op implementation when the module is absent.

### Decision 3: Decrement-after-commit via `TransactionSynchronizationManager`

**Chosen:** `DeferredContentReferenceTracker` wraps `ContentReferenceTracker` and registers a
`TransactionSynchronizationAdapter` to call `decrementReference()` in the `afterCommit()` callback.

**Alternatives considered:**

- *Decrement inline (before commit)*: If the transaction rolls back, the decrement would incorrectly reduce the
  reference count for a content object that is still referenced.
- *Async event after commit*: Introduces messaging infrastructure for a synchronous concern; adds complexity and failure
  modes.

**Rationale:** `TransactionSynchronizationManager.afterCommit()` is the standard Spring pattern for "do this only if the
transaction succeeded." The increment (on upload) does not need deferral because a rolled-back upload is simply an
orphaned object — it gets cleaned up by the same grace-period job.

### Decision 4: Safety verification before deletion (verified reference counting)

**Chosen:** Before the deletion job removes a content object, it queries every entity table that has content attributes
to confirm the content ID is truly not referenced anywhere. If drift is detected (count says zero but a live reference
is found), the deletion marker is cleared and the drift is logged and counted in metrics.

**Alternatives considered:**

- *Trust the reference count, skip verification*: Any count drift (e.g., from a bug, manual DB operation, migration)
  would cause data loss.

**Rationale:** Content deletion is irreversible. The safety query is cheap (runs infrequently in a batch job, not on
every request) and prevents silent data loss from drift.

### Decision 5: K8s CronJob for deletion (not a scheduled Spring task)

**Chosen:** A `ContentDeletionJob` is a one-shot Spring Boot job triggered by a K8s CronJob.

**Alternatives considered:**

- *`@Scheduled` inside the running app*: Runs in every replica; requires leader election to avoid duplicate deletions;
  harder to observe and operate independently.

**Rationale:** A separate K8s CronJob runs in a single pod, has full application context (DB + ContentStore), is
independently scalable, and is easy to trigger or disable without redeploying the main app.

## Risks / Trade-offs

- **Count drift from bugs or manual DB changes** → Safety verification in the deletion job catches this; drift is logged
  with metrics for alerting. No data loss occurs, but the content is not deleted until drift is resolved.
- **Increment on upload has no rollback** → If an upload transaction rolls back after `writeContent()` but before the
  increment is persisted, there is an orphaned content object with no tracking entry. The safety query will find no
  references to it and the deletion job can clean it up (the missing reference count entry is treated as "no references,
  safe to delete" only after the grace period — but without a `marked_for_deletion_at` it would never be found). *
  *Mitigation:** The increment should be persisted in the same transaction as the entity write, so both commit or both
  roll back together.
- **Race between decrement and CronJob** → If a CronJob run coincides with a decrement that hasn't fully committed yet,
  the safety check catches it.
- **Grace period delays storage reclamation** → By design; default 7 days. Configurable.

## Migration Plan

1. Deploy the new module. `JOOQTableCreator.createTables()` now also creates the `_content_references` table (via the
   JOOQ DSL — no Flyway migration; schema management is handled by an external project that calls `createTables()`).
2. Run the backfill SQL in the external schema management project to populate reference counts from existing entity data:
   ```sql
   INSERT INTO _content_references (content_id, reference_count, first_referenced_at)
   SELECT content_id, COUNT(*), NOW()
   FROM (
       -- one UNION ALL per entity/attribute combination with a content reference
   ) AS all_refs
   GROUP BY content_id;
   ```
3. Enable the deletion job in configuration (`contentgrid.content.lifecycle.deletion.enabled: true`) after the backfill
   has been verified.
4. Rollback: Set `contentgrid.content.lifecycle.enabled: false` — the domain module treats the tracker as absent and
   falls back to no-op behavior. No data is lost by disabling.

## Open Questions

- Should content upload increment be in the same JOOQ transaction as the entity write, or in a separate transaction? (
  Affects the rollback-on-upload risk above.)
- Is there a need to expose the `_content_references` table via an actuator endpoint for operational visibility?
