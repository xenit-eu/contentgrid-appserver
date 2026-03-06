## Why

When content is uploaded, it is written to the `ContentStore` and a `ContentReference` is stored in the entity. However,
when that content is later replaced, cleared from an attribute, or the owning entity is deleted, the underlying storage
bytes are never removed — causing unbounded storage growth. Additionally, the system needs to support multiple entities
pointing to the same content object (content sharing), and deletions must not be immediate: a configurable grace period
is required so that backups can be made before content is permanently removed.

## What Changes

- A new `contentgrid-appserver-content-lifecycle` module is introduced to own all content lifecycle tracking and
  deletion logic.
- A `_content_references` table tracks reference counts, first-referenced time, last-dereferenced time, and deletion
  markers for every content object.
- When content is uploaded, its reference count is incremented. When content is dereferenced (replaced, cleared, or
  entity deleted), its reference count is decremented — but only after the database transaction commits successfully.
- When the reference count reaches zero, the content is marked for deletion with a timestamp. It is **not** deleted
  immediately.
- A K8s CronJob runs the `ContentDeletionJob`, which finds content past the grace period, performs a safety verification
  against all entity tables, and only then deletes from the `ContentStore`.
- **BREAKING**: `contentgrid-appserver-domain` gains an optional dependency on the content lifecycle module.

## Capabilities

### New Capabilities

- `content-lifecycle`: Reference-counted tracking of content objects, grace-period-based deletion scheduling, and a
  verified deletion job.

### Modified Capabilities

## Impact

- `contentgrid-appserver-content-lifecycle`: New module with `ContentReferenceTracker`, `JooqContentReferenceTracker`,
  `DeferredContentReferenceTracker`, `ContentReferenceVerificationQuery`, `ContentDeletionJob`, and auto-configuration.
- `contentgrid-appserver-domain`: `ContentUploadAttributeMapper` increments on upload; `DatamodelApiImpl` decrements on
  entity delete; the content cleared path decrements via `ContentAttributeModificationValidator`.
- `settings.gradle`: New module registered.
- `contentgrid-appserver-autoconfigure`: Auto-configuration registered.
- Content store implementations (S3, filesystem): `ContentStore.remove()` will now be called by the deletion job.
