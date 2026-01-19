# Multi-Store Content Implementation Summary

## Overview

This implementation adds support for using multiple content stores simultaneously in the ContentGrid AppServer. Previously, only a single content store could be used at a time. Now, multiple stores can be configured with one designated as the active write store, while others remain available for reading existing content.

**Key Design**: `ContentStoreRegistry` implements the `ContentStore` interface, providing a transparent drop-in replacement. No code changes are required in existing components - they continue to use `ContentStore` while benefiting from multi-store support.

## Key Features

- **Transparent Design**: `ContentStoreRegistry` implements `ContentStore` - no code changes needed
- **Multiple Content Stores**: Support for any number of content stores registered with unique identifiers
- **Single Write Store**: One store is designated as active for all write operations
- **Store-Aware References**: Content references now include the store ID where the content is located
- **Backward Compatible**: Existing code and content references continue to work without modification
- **Flexible Configuration**: Stores can be added dynamically at runtime
- **Automatic Routing**: Read/write/delete operations automatically routed to correct stores

## Components Changed

### 1. ContentReference (Enhanced)

**Location**: `contentgrid-appserver-contentstore-api/src/main/java/com/contentgrid/appserver/contentstore/api/ContentReference.java`

**Changes**:
- Added optional `storeId` field to track which store contains the content
- Added factory method: `ContentReference.of(String storeId, String value)`
- Added `parse()` method to parse from storage format: `"storeId:value"`
- Added `toStorageFormat()` method to serialize: returns `"storeId:value"` or just `"value"` if no store ID
- Backward compatible: references without store IDs are treated as having `null` storeId

### 2. ContentStoreRegistry (New)

**Location**: `contentgrid-appserver-contentstore-api/src/main/java/com/contentgrid/appserver/contentstore/api/ContentStoreRegistry.java`

**Purpose**: Extends `ContentStore` interface to manage multiple content stores with transparent routing

**Key Design**: Implements `ContentStore` interface, providing default implementations that route operations to the appropriate underlying stores.

**Key Methods**:
- **ContentStore methods** (inherited):
  - `getReader(ContentReference, ResolvedContentRange)`: Routes to correct store based on reference
  - `writeContent(InputStream)`: Delegates to active write store
  - `remove(ContentReference)`: Routes to correct store based on reference
- **Registry-specific methods**:
  - `getStore(String storeId)`: Retrieve a specific store by ID
  - `getWriteStore()`: Get the active write store
  - `getWriteStoreId()`: Get the ID of the active write store
  - `getStoreForReading(ContentReference)`: Get store for reading a specific reference

### 3. DefaultContentStoreRegistry (New)

**Location**: `contentgrid-appserver-contentstore-api/src/main/java/com/contentgrid/appserver/contentstore/api/DefaultContentStoreRegistry.java`

**Purpose**: Default implementation of `ContentStoreRegistry`

**Key Methods**:
- Constructor with single store
- Constructor with multiple stores
- `registerStore(String, ContentStore)`: Add stores dynamically
- `setWriteStore(String)`: Change the active write store
- `getStoreIds()`: List all registered store IDs

### 4. ContentUploadAttributeMapper (No Changes Required)

**Location**: `contentgrid-appserver-domain/src/main/java/com/contentgrid/appserver/domain/data/mapper/ContentUploadAttributeMapper.java`

**Changes**: None - continues to use `ContentStore` interface

**How it works**:
- Writes content using `contentStore.writeContent()` - no changes needed
- The registry (injected as `ContentStore`) automatically writes to the active store
- The returned `ContentAccessor` has a reference with store ID automatically included

### 5. ContentApiImpl (No Changes Required)

**Location**: `contentgrid-appserver-domain/src/main/java/com/contentgrid/appserver/domain/ContentApiImpl.java`

**Changes**: None - continues to use `ContentStore` interface

**How it works**:
- Parses stored references using `ContentReference.parse()`
- Reads content using `contentStore.getReader()` - no changes needed
- The registry (injected as `ContentStore`) automatically routes to the correct store

### 6. DatamodelApiImpl (No Changes Required)

**Location**: `contentgrid-appserver-domain/src/main/java/com/contentgrid/appserver/domain/DatamodelApiImpl.java`

**Changes**: None - continues to use `ContentStore` interface

**How it works**:
- Constructor accepts `ContentStore` as before
- The registry is injected as `ContentStore`, providing transparent multi-store support

### 7. ContentGridDomainAutoConfiguration (Updated)

**Location**: `contentgrid-appserver-autoconfigure/src/main/java/com/contentgrid/appserver/autoconfigure/domain/ContentGridDomainAutoConfiguration.java`

**Changes**:
- Creates a `@Primary` `ContentStore` bean that is actually a `DefaultContentStoreRegistry`
- Wraps the original `ContentStore` with store ID "default"
- The registry is injected everywhere as `ContentStore`, providing transparent multi-store support
- All existing components receive the registry without knowing about it

## Storage Format

Content references are stored in the database as strings using one of two formats:

1. **With store ID**: `"storeId:contentValue"` (e.g., `"primary:abc123def456"`)
2. **Without store ID** (legacy): `"contentValue"` (e.g., `"abc123def456"`)

When reading, references without a store ID default to the current write store for backward compatibility.

## Backward Compatibility

The implementation is fully backward compatible and transparent:

1. **Existing Code**: Zero changes required - registry implements `ContentStore` interface
2. **Existing Data**: Content references without store IDs continue to work
3. **Single Store**: Default configuration creates a registry with one store named "default"
4. **API Compatibility**: All existing ContentStore methods remain unchanged
5. **Transparent Operation**: Components don't know they're using a registry - it's injected as `ContentStore`
6. **Domain Layer**: Unchanged - continues using `ContentStore` interface

## Migration Scenarios

### Scenario 1: Adding a New Store

```java
// Register an additional store for reading
contentStoreRegistry.registerStore("archive", archiveStore);

// Old content references without store ID → read from "primary" (write store)
// New content references with store ID → read from specified store
```

### Scenario 2: Switching Write Store

```java
// Initially writing to "primary"
contentStoreRegistry.setWriteStore("primary");

// Switch to new store
contentStoreRegistry.setWriteStore("new-store");

// All new uploads go to "new-store"
// Old content remains readable from "primary"
```

### Scenario 3: Read-Only Legacy Store

```java
var stores = Map.of(
    "new-s3", newS3Store,      // Active write store
    "old-fs", oldFilesystemStore  // Read-only legacy store
);

var registry = new DefaultContentStoreRegistry("new-s3", stores);

// New content → "new-s3"
// Legacy content with "old-fs:..." → readable from old store
```

## Testing

### Unit Tests

- **ContentReferenceTest**: Tests parsing, formatting, equality, and round-tripping
- **ContentStoreRegistryTest**: Tests store management, routing, and error handling

### Integration Tests

- **MultiStoreContentIntegrationTest**: Demonstrates multi-store scenarios including:
  - Writing to active store
  - Reading from multiple stores
  - Migration workflows
  - Backward compatibility
  - Dynamic store registration

## Configuration Example

```java
@Configuration
public class MultiStoreConfiguration {
    
    @Bean
    @Primary
    public ContentStore contentStoreRegistry(
            @Qualifier("primaryStore") ContentStore primaryStore,
            @Qualifier("archiveStore") ContentStore archiveStore,
            @Qualifier("legacyStore") ContentStore legacyStore
    ) {
        var stores = Map.of(
            "primary", primaryStore,
            "archive", archiveStore,
            "legacy", legacyStore
        );
        
        // Return as ContentStore - it's a transparent drop-in replacement
        return new DefaultContentStoreRegistry("primary", stores);
    }
    
    @Bean
    @Qualifier("primaryStore")
    public ContentStore primaryStore() {
        return new S3ContentStore(/* config */);
    }
    
    @Bean
    @Qualifier("archiveStore")
    public ContentStore archiveStore() {
        return new S3ContentStore(/* config */);
    }
}
```

## Benefits

1. **Zero-Downtime Migration**: Migrate between storage systems without service interruption
2. **Cost Optimization**: Move old content to cheaper storage (e.g., Glacier)
3. **Performance**: Use different stores for hot/cold data
4. **Multi-Tenancy**: Isolate content for different tenants
5. **Disaster Recovery**: Replicate content across multiple stores

## Design Decisions

### Why Have ContentStoreRegistry Implement ContentStore?

- **Zero Code Changes**: Existing code continues to work without modification
- **Transparent Operation**: Components don't need to know about multi-store support
- **Type Safety**: Strong typing ensures correct usage
- **Gradual Migration**: Can switch to multi-store without refactoring
- **Encapsulation**: Multi-store complexity hidden behind familiar interface

### Why Include Store ID in Reference?

- **Explicit Routing**: No ambiguity about where content is stored
- **Migration Safety**: Content references remain valid even after changing write store
- **Auditability**: Can track which store contains which content
- **Automatic Routing**: Registry uses store ID to route operations automatically

### Why Parse Format "storeId:value"?

- **Simple**: Easy to parse and understand
- **Compact**: Minimal storage overhead
- **Safe**: Colon is not typically used in content IDs
- **Human Readable**: Easy to identify which store contains content

### Why Default to Write Store for Missing Store ID?

- **Backward Compatibility**: Existing references continue to work
- **Migration Path**: Gradual migration without breaking existing data
- **Simplicity**: No special handling needed for legacy data
- **Transparent Upgrade**: Old references work immediately after enabling multi-store

## Future Enhancements

Potential future improvements (not implemented):

1. **Store Health Checks**: Monitor store availability
2. **Automatic Failover**: Fall back to replica if primary store fails
3. **Content Replication**: Automatically replicate content across stores
4. **Store Policies**: Rules for which content goes to which store
5. **Migration Tools**: Utilities to migrate content between stores
6. **Store Metrics**: Per-store usage statistics and monitoring

## Documentation

See `MULTI_STORE_CONTENT.md` for detailed usage guide and examples.