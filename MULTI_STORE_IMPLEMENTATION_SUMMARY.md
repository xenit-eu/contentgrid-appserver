# Multi-Store Content Implementation Summary

## Overview

This implementation adds support for using multiple content stores simultaneously in the ContentGrid AppServer. Previously, only a single content store could be used at a time. Now, multiple stores can be configured with one designated as the active write store, while others remain available for reading existing content.

## Key Features

- **Multiple Content Stores**: Support for any number of content stores registered with unique identifiers
- **Single Write Store**: One store is designated as active for all write operations
- **Store-Aware References**: Content references now include the store ID where the content is located
- **Backward Compatible**: Existing code and content references continue to work without modification
- **Flexible Configuration**: Stores can be added dynamically at runtime

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

**Purpose**: Manages multiple content stores and provides routing logic

**Key Methods**:
- `getStore(String storeId)`: Retrieve a specific store by ID
- `getWriteStore()`: Get the active write store
- `getWriteStoreId()`: Get the ID of the active write store
- `getStoreForReading(ContentReference)`: Automatically route to the correct store based on reference

### 3. DefaultContentStoreRegistry (New)

**Location**: `contentgrid-appserver-contentstore-api/src/main/java/com/contentgrid/appserver/contentstore/api/DefaultContentStoreRegistry.java`

**Purpose**: Default implementation of `ContentStoreRegistry`

**Key Methods**:
- Constructor with single store
- Constructor with multiple stores
- `registerStore(String, ContentStore)`: Add stores dynamically
- `setWriteStore(String)`: Change the active write store
- `getStoreIds()`: List all registered store IDs

### 4. ContentUploadAttributeMapper (Updated)

**Location**: `contentgrid-appserver-domain/src/main/java/com/contentgrid/appserver/domain/data/mapper/ContentUploadAttributeMapper.java`

**Changes**:
- Now uses `ContentStoreRegistry` instead of `ContentStore`
- Writes content to the active write store via `registry.getWriteStore()`
- Stores references in format `"storeId:contentId"` including the write store ID

### 5. ContentApiImpl (Updated)

**Location**: `contentgrid-appserver-domain/src/main/java/com/contentgrid/appserver/domain/ContentApiImpl.java`

**Changes**:
- Now uses `ContentStoreRegistry` instead of `ContentStore`
- Parses stored references using `ContentReference.parse()`
- Routes read requests to correct store via `registry.getStoreForReading()`

### 6. DatamodelApiImpl (Updated)

**Location**: `contentgrid-appserver-domain/src/main/java/com/contentgrid/appserver/domain/DatamodelApiImpl.java`

**Changes**:
- Constructor now accepts `ContentStoreRegistry` instead of `ContentStore`
- Passes registry to `ContentUploadAttributeMapper`

### 7. ContentGridDomainAutoConfiguration (Updated)

**Location**: `contentgrid-appserver-autoconfigure/src/main/java/com/contentgrid/appserver/autoconfigure/domain/ContentGridDomainAutoConfiguration.java`

**Changes**:
- Creates `ContentStoreRegistry` bean wrapping the primary `ContentStore` with ID "default"
- Injects registry into `DatamodelApiImpl` and `ContentApiImpl`

## Storage Format

Content references are stored in the database as strings using one of two formats:

1. **With store ID**: `"storeId:contentValue"` (e.g., `"primary:abc123def456"`)
2. **Without store ID** (legacy): `"contentValue"` (e.g., `"abc123def456"`)

When reading, references without a store ID default to the current write store for backward compatibility.

## Backward Compatibility

The implementation is fully backward compatible:

1. **Existing Code**: No changes required to existing application code
2. **Existing Data**: Content references without store IDs continue to work
3. **Single Store**: Default configuration creates a registry with one store named "default"
4. **API Compatibility**: All existing ContentStore methods remain unchanged

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
    public ContentStoreRegistry contentStoreRegistry(
            @Qualifier("primaryStore") ContentStore primaryStore,
            @Qualifier("archiveStore") ContentStore archiveStore
    ) {
        var stores = Map.of(
            "primary", primaryStore,
            "archive", archiveStore
        );
        
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

### Why Include Store ID in Reference?

- **Explicit Routing**: No ambiguity about where content is stored
- **Migration Safety**: Content references remain valid even after changing write store
- **Auditability**: Can track which store contains which content

### Why Parse Format "storeId:value"?

- **Simple**: Easy to parse and understand
- **Compact**: Minimal storage overhead
- **Safe**: Colon is not typically used in content IDs

### Why Default to Write Store for Missing Store ID?

- **Backward Compatibility**: Existing references continue to work
- **Migration Path**: Gradual migration without breaking existing data
- **Simplicity**: No special handling needed for legacy data

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