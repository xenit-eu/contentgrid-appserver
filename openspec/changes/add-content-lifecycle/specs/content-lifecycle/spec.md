## ADDED Requirements

### Requirement: Reference count is incremented when content is uploaded
When a new content object is written to the `ContentStore`, the system SHALL create a new entry in `_content_references` with `reference_count = 1`. Each upload always produces a unique `ContentReference`, so an existing entry will never be found at upload time.

#### Scenario: New content uploaded creates reference entry
- **WHEN** a file is uploaded to a content attribute on an entity
- **THEN** a new row is created in `_content_references` for that `content_id` with `reference_count = 1`
- **AND** `first_referenced_at` is set to the current time

### Requirement: Reference count is decremented after successful transaction when content is dereferenced
When a content attribute is replaced, cleared, or its owning entity is deleted, the system SHALL decrement the reference count for the old `ContentReference` in `_content_references`, but only after the database transaction has committed successfully.

#### Scenario: Content replaced decrements old reference after commit
- **WHEN** a content attribute is updated with a new file upload
- **AND** the transaction commits successfully
- **THEN** the reference count for the old `ContentReference` is decremented by 1

#### Scenario: Content cleared decrements reference after commit
- **WHEN** a content attribute is set to null
- **AND** the transaction commits successfully
- **THEN** the reference count for the old `ContentReference` is decremented by 1

#### Scenario: Entity deleted decrements all content references after commit
- **WHEN** an entity with one or more populated content attributes is deleted
- **AND** the transaction commits successfully
- **THEN** the reference count for each content attribute's `ContentReference` is decremented by 1

#### Scenario: Transaction rollback suppresses decrement
- **WHEN** an operation that would dereference content is attempted
- **AND** the database transaction rolls back
- **THEN** no decrement is applied to `_content_references`
- **AND** the reference count remains unchanged

#### Scenario: No decrement when attribute had no prior content
- **WHEN** a content attribute that currently holds no content is updated
- **THEN** no decrement is attempted

### Requirement: Content is marked for deletion when its reference count reaches zero
When a decrement causes the `reference_count` in `_content_references` to reach zero, the system SHALL set `marked_for_deletion_at` to the current timestamp. The content object SHALL NOT be removed from the `ContentStore` at this point.

#### Scenario: Content marked for deletion when last reference is removed
- **WHEN** the reference count for a `ContentReference` is decremented to zero
- **THEN** `marked_for_deletion_at` is set to the current timestamp
- **AND** the content object is still readable from the `ContentStore`

### Requirement: Content past the grace period is deleted by the deletion job
The `ContentDeletionJob` SHALL find all `_content_references` entries where `marked_for_deletion_at` is older than the configured grace period, verify they are truly unreferenced, and delete them from the `ContentStore`.

#### Scenario: Orphaned content past grace period is deleted
- **WHEN** the `ContentDeletionJob` runs
- **AND** a `_content_references` entry has `marked_for_deletion_at` older than the grace period
- **AND** the safety verification confirms the `content_id` is not referenced in any entity table
- **THEN** the content object is removed from the `ContentStore`
- **AND** the row is removed from `_content_references`
- **AND** the `content.deletion.success` metric is incremented

#### Scenario: Content within the grace period is not deleted
- **WHEN** the `ContentDeletionJob` runs
- **AND** a `_content_references` entry has `marked_for_deletion_at` set but within the grace period
- **THEN** the content object is NOT deleted

#### Scenario: Content still referenced is not deleted (drift protection)
- **WHEN** the `ContentDeletionJob` runs
- **AND** a `_content_references` entry has `marked_for_deletion_at` set past the grace period
- **AND** the safety verification finds the `content_id` is still referenced in an entity table
- **THEN** the content object is NOT deleted
- **AND** `marked_for_deletion_at` is cleared
- **AND** the `content.deletion.drift` metric is incremented
- **AND** a warning is logged with the `content_id`

#### Scenario: Deletion job continues after individual deletion failure
- **WHEN** the `ContentDeletionJob` runs and `ContentStore.remove()` throws for one content object
- **THEN** the deletion is attempted for all remaining candidates
- **AND** the `content.deletion.failure` metric is incremented for each failure
- **AND** each failure is logged with the `content_id`

### Requirement: Content lifecycle tracking is optional and configurable
The content lifecycle feature SHALL be independently configurable. When disabled, the system SHALL behave as before (no reference tracking, no deletion).

#### Scenario: Lifecycle tracking disabled by configuration
- **WHEN** `contentgrid.content.lifecycle.enabled` is set to `false`
- **THEN** no entries are written to `_content_references` on upload or dereference
- **AND** `ContentStore.remove()` is never called by the lifecycle module
