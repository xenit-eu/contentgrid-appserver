# contentgrid-appserver-content-lifecycle

Tracks content object references and performs safe, grace-period-based deletion from the underlying `ContentStore`.

## How it works

- A `_content_references` table tracks a reference count for every content object.
- When content is uploaded, `ContentReferenceTracker.incrementReference()` is called.
- When content is cleared from an entity (set to null, replaced, or entity deleted), `ContentReferenceTracker.decrementReference()` is called **after the entity transaction commits** (via `DeferredContentReferenceTracker`).
- When the reference count reaches zero, the row is marked with `marked_for_deletion_at = NOW()`.
- `ContentDeletionJob` (an `ApplicationRunner`) processes candidates past the grace period, performs a safety verification query, then deletes from the `ContentStore` and removes the tracking row.

## Running the deletion job

The `ContentDeletionJob` implements Spring Boot's `ApplicationRunner`. It is intended to be invoked as a **K8s CronJob** using a separate Spring Boot process. Configure the grace period and batch size in `application.properties`:

```properties
contentgrid.appserver.content.lifecycle.deletion.grace-period=P7D
contentgrid.appserver.content.lifecycle.deletion.batch-size=100
```

## Backfill for existing data

When deploying this module to an existing database, the `_content_references` table will be empty. Content that was already uploaded before this feature was introduced will never be deleted, which is safe (conservative). If you want to reclaim storage for content that was already dereferenced, a manual backfill is required.

**Chosen approach: manual operation** — no automatic Flyway migration is provided. The backfill is complex and tenant-specific (depends on the entity/column layout), and running it automatically could be disruptive. Perform the backfill as a one-time manual SQL operation if needed.

### Backfill SQL pattern

The following pattern inserts a row into `_content_references` for every content object currently referenced in any entity table. Adjust table and column names to match your application model:

```sql
INSERT INTO _content_references (content_id, reference_count, first_referenced_at)
SELECT content_id, COUNT(*) AS reference_count, NOW() AS first_referenced_at
FROM (
    SELECT invoice_content_id AS content_id FROM invoice WHERE invoice_content_id IS NOT NULL
    UNION ALL
    SELECT document_file_id  AS content_id FROM document  WHERE document_file_id  IS NOT NULL
    -- ... add one UNION ALL per content attribute per entity table
) AS all_refs
GROUP BY content_id
ON CONFLICT (content_id) DO UPDATE
    SET reference_count = EXCLUDED.reference_count;
```

Run this in a maintenance window before enabling the deletion job for the first time.
