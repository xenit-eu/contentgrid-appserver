# Multi-Store Content Management

## Overview

The ContentGrid AppServer now supports using multiple content stores simultaneously. This allows you to:

- **Read from multiple stores**: Access content stored in different locations (e.g., legacy storage, new storage, archive storage)
- **Write to a single active store**: All new content is written to one designated "write store"
- **Migrate between stores**: Gradually migrate content from old to new storage systems without downtime
- **Store-aware references**: Each content object includes a reference to its storage location

## How It Works

### Content Reference Format

Content references now support an optional store identifier prefix:

- **Without store ID** (legacy format): `abc123def456`
- **With store ID**: `mystore:abc123def456`

When a content reference doesn't specify a store ID, the system uses the current write store for backward compatibility.

### ContentStoreRegistry

The `ContentStoreRegistry` manages multiple content stores:

- **Multiple stores**: Register any number of content stores with unique IDs
- **One write store**: Exactly one store is designated as the active write store
- **Automatic routing**: Reads are automatically routed to the correct store based on the content reference

## Configuration

### Single Store (Default Behavior)

By default, the system creates a registry with a single store named "default":

```yaml
contentgrid:
  appserver:
    content-store:
      type: fs  # or s3, ephemeral
    content:
      fs:
        path: /var/contentgrid/content
```

This configuration is automatically wrapped in a `DefaultContentStoreRegistry`.

### Multiple Stores

To use multiple stores, create a custom `ContentStoreRegistry` bean:

```java
@Configuration
public class MultiStoreConfiguration {
    
    @Bean
    @Primary
    public ContentStoreRegistry contentStoreRegistry(
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

### Dynamic Store Registration

You can also register stores dynamically at runtime:

```java
@Autowired
private DefaultContentStoreRegistry contentStoreRegistry;

public void addNewStore(String storeId, ContentStore store) {
    contentStoreRegistry.registerStore(storeId, store);
}

public void switchWriteStore(String newStoreId) {
    contentStoreRegistry.setWriteStore(newStoreId);
}
```

## Usage Examples

### Writing Content

New content is always written to the active write store:

```java
@Autowired
private ContentStoreRegistry registry;

public void uploadContent(InputStream content) {
    // Writes to the active write store
    var accessor = registry.getWriteStore().writeContent(content);
    
    // The reference includes the write store ID
    var reference = ContentReference.of(
        registry.getWriteStoreId(),
        accessor.getReference().getValue()
    );
    
    // Store reference as: "primary:abc123def456"
    String storedValue = reference.toStorageFormat();
}
```

### Reading Content

Reading automatically uses the correct store based on the reference:

```java
public InputStream readContent(String storedReference) {
    // Parse the stored reference
    var reference = ContentReference.parse(storedReference);
    
    // Get the appropriate store (based on store ID in reference)
    var store = registry.getStoreForReading(reference);
    
    // Read the content
    var reader = store.getReader(reference, contentRange);
    return reader.getContentInputStream();
}
```

### Migration Example

When migrating from an old storage system to a new one:

```java
@Configuration
public class MigrationConfiguration {
    
    @Bean
    @Primary
    public ContentStoreRegistry contentStoreRegistry() {
        var oldStore = new FilesystemContentStore(Paths.get("/old/storage"));
        var newStore = new S3ContentStore(/* ... */);
        
        var stores = Map.of(
            "old-fs", oldStore,
            "new-s3", newStore
        );
        
        // New content goes to "new-s3"
        // Old content can still be read from "old-fs"
        return new DefaultContentStoreRegistry("new-s3", stores);
    }
}
```

Now:
- New uploads go to the S3 store with references like `new-s3:abc123`
- Old content references without store IDs are read from `new-s3` (current write store)
- Old content references with `old-fs:` prefix are read from the filesystem store
- You can migrate content gradually in the background

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
// Get a specific store
Optional<ContentStore> getStore(String storeId)

// Get the active write store
ContentStore getWriteStore()
String getWriteStoreId()

// Get store for reading a reference
ContentStore getStoreForReading(ContentReference ref)
```

### DefaultContentStoreRegistry

```java
// Create with single store
new DefaultContentStoreRegistry("store-id", contentStore)

// Create with multiple stores
new DefaultContentStoreRegistry("write-store-id", storeMap)

// Register additional store
registerStore("store-id", contentStore)

// Change write store
setWriteStore("new-write-store-id")

// Get all store IDs
Set<String> getStoreIds()
```

## Backward Compatibility

The implementation is fully backward compatible:

- **Old references** (without store ID) continue to work
- **Single store** configuration works unchanged
- **Legacy code** doesn't need updates
- **Database migration** is not required

Old content references without a store ID are automatically routed to the current write store, ensuring seamless operation during and after migration.