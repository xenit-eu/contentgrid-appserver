# Multi-Store Content Management

## Overview

The ContentGrid AppServer now supports using multiple content stores simultaneously. This allows you to:

- **Transparent multi-store support**: `ContentStoreRegistry` implements `ContentStore` interface - no code changes needed
- **Read from multiple stores**: Access content stored in different locations (e.g., legacy storage, new storage, archive storage)
- **Write to a single active store**: All new content is written to one designated "write store"
- **Migrate between stores**: Gradually migrate content from old to new storage systems without downtime
- **Store-aware references**: Each content object includes a reference to its storage location

## How It Works

### Transparent ContentStore Implementation

`ContentStoreRegistry` implements the `ContentStore` interface, acting as a transparent facade over multiple stores:

- **Drop-in replacement**: No code changes needed - existing code works as-is
- **Automatic routing**: Read/write/delete operations are automatically routed to the correct store
- **Write operations**: Delegated to the active write store, with store ID automatically included in references
- **Read operations**: Automatically routed based on store ID in the content reference
- **Delete operations**: Routed to the store specified in the content reference

### Content Reference Format

Content references now support an optional store identifier prefix:

- **Without store ID** (legacy format): `abc123def456`
- **With store ID**: `mystore:abc123def456`

When a content reference doesn't specify a store ID, the system uses the current write store for backward compatibility.

### ContentStoreRegistry

The `ContentStoreRegistry` extends `ContentStore` and manages multiple content stores:

- **Multiple stores**: Register any number of content stores with unique IDs
- **One write store**: Exactly one store is designated as the active write store
- **Transparent operation**: All ContentStore methods work seamlessly across multiple stores

## Configuration

### Single Store (Default Behavior)

By default, the system automatically wraps your configured ContentStore in a registry with store ID "default":

```yaml
contentgrid:
  appserver:
    content-store:
      type: fs  # or s3, ephemeral
    content:
      fs:
        path: /var/contentgrid/content
```

This configuration is automatically wrapped in a `DefaultContentStoreRegistry` as the primary `ContentStore` bean. Your existing code continues to work without any changes.

### Multiple Stores

To use multiple stores, create a custom `ContentStoreRegistry` bean as the primary `ContentStore`:

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
        
        // "primary" is the active write store
        // This registry implements ContentStore, so it's a drop-in replacement
        return new DefaultContentStoreRegistry("primary", stores);
    }
    
    @Bean
    @Qualifier("primaryStore")
    public ContentStore primaryStore() {
        return new S3ContentStore(/* ... */);
    }
    
    @Bean
    @Qualifier("archiveStore")
    public ContentStore archiveStore() {
        return new S3ContentStore(/* ... */);
    }
    
    @Bean
    @Qualifier("legacyStore")
    public ContentStore legacyStore() {
        return new FilesystemContentStore(Paths.get("/legacy/content"));
    }
}
```

**Important**: The registry is injected as a `ContentStore`, so all existing code using `ContentStore` works without modification.

### Dynamic Store Registration

You can also register stores dynamically at runtime. Since the registry is typically injected as `ContentStore`, you need to cast it:

```java
@Autowired
private ContentStore contentStore;

public void addNewStore(String storeId, ContentStore store) {
    if (contentStore instanceof DefaultContentStoreRegistry registry) {
        registry.registerStore(storeId, store);
    }
}

public void switchWriteStore(String newStoreId) {
    if (contentStore instanceof DefaultContentStoreRegistry registry) {
        registry.setWriteStore(newStoreId);
    }
}
```

## Usage Examples

### Writing Content

Writing content works exactly as before - no code changes needed:

```java
@Autowired
private ContentStore contentStore;

public void uploadContent(InputStream content) {
    // Writes to the active write store
    // The registry automatically includes the store ID in the reference
    var accessor = contentStore.writeContent(content);
    
    // The reference automatically includes the write store ID
    var reference = accessor.getReference();
    
    // Store reference as: "primary:abc123def456"
    String storedValue = reference.toStorageFormat();
}
```

### Reading Content

Reading content also works exactly as before - routing is automatic:

```java
@Autowired
private ContentStore contentStore;

public InputStream readContent(String storedReference) {
    // Parse the stored reference
    var reference = ContentReference.parse(storedReference);
    
    // Read the content - registry automatically routes to correct store
    var reader = contentStore.getReader(reference, contentRange);
    return reader.getContentInputStream();
}
```

**Key Point**: Your existing code using `ContentStore` continues to work without any changes. The registry transparently handles routing to multiple stores.

### Migration Example

When migrating from an old storage system to a new one:

```java
@Configuration
public class MigrationConfiguration {
    
    @Bean
    @Primary
    public ContentStore contentStoreRegistry() {
        var oldStore = new FilesystemContentStore(Paths.get("/old/storage"));
        var newStore = new S3ContentStore(/* ... */);
        
        var stores = Map.of(
            "old-fs", oldStore,
            "new-s3", newStore
        );
        
        // New content goes to "new-s3"
        // Old content can still be read from "old-fs"
        // Return as ContentStore for transparent operation
        return new DefaultContentStoreRegistry("new-s3", stores);
    }
}
```

Now all your existing code continues to work:
- New uploads automatically go to the S3 store with references like `new-s3:abc123`
- Old content references without store IDs are read from `new-s3` (current write store)
- Old content references with `old-fs:` prefix are automatically read from the filesystem store
- You can migrate content gradually in the background using standard ContentStore operations

## Migration Strategies

### Strategy 1: Gradual Background Migration

1. Configure multiple stores with new store as write store
2. New content goes to new store
3. Old content remains readable from old store
4. Background job migrates old content:
   - Read from old store
   - Write to new store
   - Update database reference
5. Once complete, remove old store

### Strategy 2: Dual-Write During Migration

1. Create a custom `ContentStore` that writes to both stores
2. Set as write store temporarily
3. Verify data consistency
4. Switch to new store only
5. Clean up old store

### Strategy 3: Lazy Migration

1. Configure both stores
2. On read, check if content is in old store
3. If yes, copy to new store and update reference
4. Eventually all accessed content is migrated
5. Archive remaining cold data

## Best Practices

### Store Naming

Use descriptive, consistent store IDs:
- ✅ Good: `production-s3`, `archive-glacier`, `legacy-nfs`
- ❌ Bad: `store1`, `s`, `temp`

### Store ID Stability

Store IDs should be stable and never change:
- Don't rename stores after content is stored
- Don't reuse store IDs
- Document store IDs in your operations runbook

### Monitoring

Monitor each store independently:
- Storage capacity and growth
- Read/write latency per store
- Error rates per store
- Content distribution across stores

### Backup and Recovery

- Ensure each store has appropriate backup/recovery procedures
- Test restore procedures for each store
- Document which content is in which store

### Security

- Apply appropriate security policies per store
- Use encryption for sensitive content stores
- Audit access per store

## Troubleshooting

### Content Not Found

If you get `UnreadableContentException: Content store 'xyz' not found`:
- Verify the store ID is registered in the registry
- Check the content reference format is correct
- Ensure the store hasn't been removed

### Wrong Store Being Used

If content is read from the wrong store:
- Check the content reference includes the correct store ID
- Verify the reference parsing is working correctly
- Ensure store IDs in the registry match the references

### Migration Issues

If content migration fails:
- Verify both source and target stores are accessible
- Check permissions on both stores
- Ensure content references are being updated atomically
- Use transactions where possible

## API Reference

### ContentReference

```java
// Create reference without store ID (legacy)
ContentReference.of("content-id")

// Create reference with store ID
ContentReference.of("store-id", "content-id")

// Parse from storage format
ContentReference.parse("store-id:content-id")

// Get storage format
reference.toStorageFormat()  // Returns "store-id:content-id"

// Access components
reference.getValue()    // Returns "content-id"
reference.getStoreId()  // Returns "store-id" or null
```

### ContentStoreRegistry

```java
// Implements ContentStore interface
ContentReader getReader(ContentReference ref, ResolvedContentRange range)
ContentAccessor writeContent(InputStream inputStream)
void remove(ContentReference contentReference)

// Additional registry-specific methods
Optional<ContentStore> getStore(String storeId)
ContentStore getWriteStore()
String getWriteStoreId()
ContentStore getStoreForReading(ContentReference ref)
```

### DefaultContentStoreRegistry

```java
// Create with single store (implements ContentStore)
ContentStore registry = new DefaultContentStoreRegistry("store-id", contentStore)

// Create with multiple stores (implements ContentStore)
ContentStore registry = new DefaultContentStoreRegistry("write-store-id", storeMap)

// Cast to access registry-specific methods
if (contentStore instanceof DefaultContentStoreRegistry registry) {
    // Register additional store
    registry.registerStore("store-id", contentStore)
    
    // Change write store
    registry.setWriteStore("new-write-store-id")
    
    // Get all store IDs
    Set<String> storeIds = registry.getStoreIds()
}
```

## Backward Compatibility

The implementation is fully backward compatible and transparent:

- **Zero code changes**: Existing code using `ContentStore` works as-is
- **Old references** (without store ID) continue to work
- **Single store** configuration works unchanged - automatically wrapped in registry
- **Legacy code** doesn't need updates - registry implements `ContentStore` interface
- **Database migration** is not required
- **Transparent operation**: All routing happens automatically behind the scenes

Old content references without a store ID are automatically routed to the current write store, ensuring seamless operation during and after migration.