# Content Deletion Design

## Overview

ContentGrid previously did not delete content from the ContentStore when:
- Entity was deleted
- Content property was set to null

This design implements a **verified reference counting** approach to safely delete orphaned content.

## Design Decisions

| Aspect | Decision | Rationale |
|--------|----------|-----------|
| Tracking | Reference counting table `_content_references` | Incremental tracking, supports multi-entity sharing |
| Safety check | Query each entity table before deletion | Catches count drift, prevents premature deletion |
| Drift handling | Alert only (log + metrics) | Manual intervention required |
| Deletion trigger | K8s CronJob (one-shot) | Full app context, scales independently |
| Grace period | Configurable (default 7 days) | Allows recovery, ensures backup coverage |

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                     contentgrid-appserver-app                        │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │              HTTP API Layer (Controllers)                     │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                              │                                      │
│  ┌───────────────────────────┼───────────────────────────────────┐  │
│  │                      domain module                             │  │
│  │  ┌─────────────────────┐  │  ┌─────────────────────────────┐  │  │
│  │  │ContentUploadMapper  │  │  │ContentModificationValidator│  │  │
│  │  │(upload → increment) │  │  │(null → decrement)          │  │  │
│  │  └─────────────────────┘  │  └─────────────────────────────┘  │  │
│  └───────────────────────────┼───────────────────────────────────┘  │
│                              │                                      │
│  ┌───────────────────────────┼───────────────────────────────────┐  │
│  │              content-lifecycle module                          │  │
│  │                          │                                     │  │
│  │  ┌───────────────────────┴───────────────────────────────┐   │  │
│  │  │         ContentReferenceTracker                        │   │  │
│  │  │  - incrementReference()                                │   │  │
│  │  │  - decrementReference() (afterCommit)                  │   │  │
│  │  └───────────────────────────────────────────────────────┘   │  │
│  │                          │                                     │  │
│  │  ┌───────────────────────┴───────────────────────────────┐   │  │
│  │  │       _content_references table                        │   │  │
│  │  │  content_id | ref_count | last_dereferenced | marked   │   │  │
│  │  └───────────────────────────────────────────────────────┘   │  │
│  └───────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘

                    K8s CronJob (configurable schedule)
                           │
┌──────────────────────────┼──────────────────────────────────────────┐
│           content-lifecycle module (ContentDeletionJob)             │
│  ┌───────────────────────┴───────────────────────────────┐          │
│  │  1. Find candidates: marked_for_deletion_at <= now    │          │
│  │  2. Safety check: query all entity tables             │          │
│  │  3a. If referenced: clear mark, log drift alert       │          │
│  │  3b. If orphaned: delete from ContentStore, DEKs,     │          │
│  │                    _content_references                 │          │
│  └───────────────────────────────────────────────────────┘          │
└─────────────────────────────────────────────────────────────────────┘
```

## Database Schema

```sql
CREATE TABLE _content_references (
    content_id VARCHAR(36) PRIMARY KEY,
    reference_count INTEGER NOT NULL DEFAULT 1,
    first_referenced_at TIMESTAMP NOT NULL DEFAULT NOW(),
    last_dereferenced_at TIMESTAMP,
    marked_for_deletion_at TIMESTAMP
);
```

## Components

### ContentReferenceTracker (interface)
Location: `contentgrid-appserver-content-lifecycle`

```java
public interface ContentReferenceTracker {
    void incrementReference(ContentReference ref);
    void decrementReference(ContentReference ref);
}
```

### JooqContentReferenceTracker
- Implements reference counting with JOOQ
- On increment: INSERT or UPDATE (upsert), clear deletion marker
- On decrement: Reduce count, mark for deletion if count reaches 0

### DeferredContentReferenceTracker
- Wraps `ContentReferenceTracker` with after-commit callback
- Uses `TransactionSynchronizationManager` to ensure decrements only happen after successful commit
- Prevents premature deletion if transaction rolls back

### ContentReferenceVerificationQuery
- Safety check before deletion
- Queries each entity table with content attributes
- Returns true if content_id is still referenced anywhere

### ContentDeletionJob
- One-shot job for K8s CronJob
- Finds content past grace period
- Verifies not referenced, then deletes
- Metrics: `content.deletion.success`, `content.deletion.failure`, `content.deletion.drift`

## Integration Points

### 1. Content Upload (`ContentUploadAttributeMapper`)
After `contentStore.writeContent()`:
```java
if (contentReferenceTracker != null) {
    contentReferenceTracker.incrementReference(contentAccessor.getReference());
}
```

### 2. Content Dereference (`ContentAttributeModificationValidator`)
When content is set to null:
```java
// In validate() method - track dereferenced content IDs
if (hasContent && dataEntry instanceof NullDataEntry) {
    // Extract content_id and add to dereferencedContentIds list
}
```

Then in `DatamodelApiImpl.update()` and `updatePartial()`:
```java
for (String contentId : contentModificationValidator.getDereferencedContentIds()) {
    if (contentReferenceTracker != null) {
        contentReferenceTracker.decrementReference(ContentReference.of(contentId));
    }
}
```

### 3. Entity Delete (`DatamodelApiImpl.deleteEntity()`)
After entity deletion:
```java
if (contentReferenceTracker != null) {
    for (var contentAttr : entity.getContentAttributes()) {
        // Extract content_id from deleted entity
        // Call decrementReference()
    }
}
```

## Configuration

```yaml
contentgrid:
  content:
    lifecycle:
      enabled: true
      deletion:
        enabled: true
        grace-period: P7D  # ISO-8601 Duration
        batch-size: 100
```

## Metrics

| Metric | Description |
|--------|-------------|
| `content.deletion.success` | Successfully deleted content |
| `content.deletion.failure` | Failed to delete content |
| `content.deletion.drift` | Count drift detected (marked but still referenced) |

## Edge Cases

| Scenario | Behavior |
|----------|----------|
| Content uploaded then immediately deleted | Count → 1 → 0, marked for deletion after grace period |
| Transaction rollback after upload | No increment (never tracked) |
| Transaction rollback after delete | Decrement in afterCommit, so no decrement |
| Content shared across entities | Each entity increments count |
| Content resurrected (deleted, then re-uploaded with same ID) | Increment clears `marked_for_deletion_at` |
| Count drift detected | Clear deletion marker, log warning, increment drift counter |
| Content without entry in `_content_references` | Safety check finds reference, no deletion |

## Migration

Before enabling the deletion job, populate `_content_references` from existing data:

```sql
INSERT INTO _content_references (content_id, reference_count, first_referenced_at)
SELECT content_id, 1, NOW()
FROM (
    SELECT content_id FROM invoices WHERE content_id IS NOT NULL
    UNION ALL
    SELECT attachment_id FROM documents WHERE attachment_id IS NOT NULL
    -- ... all content columns
) AS all_content_ids
GROUP BY content_id;
```

## Future Considerations

1. **Content sharing API**: Currently content sharing is database-only. API support could allow explicit content reuse.

2. **Bulk operations**: Batch deletion for efficiency with large content volumes.

3. **Soft delete**: Move to trash location before final deletion for additional safety.

4. **Deduplication**: If same content uploaded multiple times, could share content_id to save storage.

## Files Changed

- `settings.gradle` - Added new module
- `contentgrid-appserver-content-lifecycle/` - New module
- `contentgrid-appserver-domain/build.gradle` - Added dependency
- `contentgrid-appserver-domain/.../DatamodelApiImpl.java` - Integration
- `contentgrid-appserver-domain/.../ContentUploadAttributeMapper.java` - Integration
- `contentgrid-appserver-domain/.../ContentAttributeModificationValidator.java` - Integration
- `contentgrid-appserver-autoconfigure/.../AutoConfiguration.imports` - Registered auto-config
