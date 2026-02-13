# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
# Build all modules
./gradlew build

# Run all tests
./gradlew test

# Build and test (includes all checks)
./gradlew check

# Run a single test class
./gradlew :<module-name>:test --tests "com.contentgrid.appserver.ClassName"

# Run a single test method
./gradlew :<module-name>:test --tests "com.contentgrid.appserver.ClassName.methodName"

# Generate test coverage reports (JaCoCo)
./gradlew jacocoTestReport

# Run SonarCloud analysis
./gradlew sonar
```

## Project Structure

Multi-module Gradle project using Java 21, Spring Boot 3.5.10, and JOOQ for database access.

**Key Modules:**

- **contentgrid-appserver-application-model**: Core domain model defining entities, attributes, relationships, and constraints
- **contentgrid-appserver-domain**: Business logic APIs for entities, relations, and content (DatamodelApi, ContentApi)
- **contentgrid-appserver-rest**: REST controllers providing dynamic HATEOAS endpoints based on application model
- **contentgrid-appserver-query-engine-api**: Database abstraction interfaces (QueryEngine)
- **contentgrid-appserver-query-engine-impl-jooq**: JOOQ-based implementation of query engine
- **contentgrid-appserver-contentstore-api**: Content storage interfaces (ContentStore)
- **contentgrid-appserver-contentstore-impl-fs**: Filesystem-based content storage
- **contentgrid-appserver-contentstore-impl-s3**: S3-compatible content storage
- **contentgrid-appserver-contentstore-impl-encryption**: Encryption wrapper for content stores
- **contentgrid-appserver-autoconfigure**: Spring Boot auto-configuration
- **contentgrid-appserver-integration-test**: Integration tests with Testcontainers

## Architecture Overview

### Schema-Driven Dynamic API

The application is **schema-driven**: an `Application` model defines entities, attributes, relationships, and constraints. The REST layer dynamically generates endpoints based on this model.

**Application Model Loading:**
```
JSON Schema File → ApplicationSchemaConverter → Application Domain Object → ApplicationResolver → Injected into Controllers
```

**Core Domain Model:**
- `Application`: Top-level aggregate containing entities and relations with validation
- `Entity`: Maps to database table, contains attributes and search filters
- `Attribute` (sealed interface): `SimpleAttribute`, `CompositeAttribute`, `ContentAttribute`
- `Relation` (sealed abstract class): `ManyToOneRelation`, `OneToManyRelation`, `OneToOneRelation`, `ManyToManyRelation`
- `Constraint`: `RequiredConstraint`, `UniqueConstraint`, `AllowedValuesConstraint`

The model uses **immutable value objects** and **sealed interfaces** for type safety.

### Layered Architecture

```
REST Layer (EntityRestController, XToOneRelationRestController, XToManyRelationRestController, ContentRestController)
    ↓
Domain Layer (DatamodelApi, ContentApi)
    ↓
Query Engine API (QueryEngine interface)
    ↓
Query Engine Implementation (JOOQQueryEngine)
    ↓
Database / Content Store
```

Each layer depends on abstractions above, not implementations below.

### REST Controllers & Dynamic Routing

Controllers use `@SpecializedOnEntity(entityPathVariable = "entityName")` to dynamically resolve entities from URL paths.

**Request Processing Pipeline:**
1. `ApplicationArgumentResolver`: Injects Application from ApplicationResolver
2. `AuthorizationContextArgumentResolver`: Extracts authorization context
3. `EncodedCursorPaginationHandlerMethodArgumentResolver`: Parses pagination parameters
4. `LinkProviderArgumentResolver`: Injects HAL link builders
5. Controller method invokes `DatamodelApi`
6. `EntityDataRepresentationModelAssembler`: Converts response to HAL+JSON

**Endpoint Mapping:**
- `/{entityName}` → List/Create entities (EntityRestController)
- `/{entityName}/{id}` → Get/Update/Delete entity (EntityRestController)
- `/{entityName}/{id}/{relationName}` → Manage relations (XToOneRelationRestController, XToManyRelationRestController)
- `/{entityName}/{id}/{contentName}` → Upload/Download content (ContentRestController)

### Query Engine Abstraction

The `QueryEngine` interface provides:
- `findAll()`: Query with predicates, sorting, pagination
- `findById()`: Fetch single entity
- `create()`, `update()`, `delete()`: CRUD operations
- `linkRelation()`, `unlinkRelation()`: Relationship management

**JOOQ Implementation Flow:**
```
Search Filters → ThunkExpression<Boolean> → JOOQThunkExpressionVisitor → JOOQ Condition → SQL Query → EntityData
```

Key components:
- `JOOQThunkExpressionVisitor`: Converts filter expressions to SQL WHERE clauses
- `EntityDataMapper`: Maps JOOQ Record to EntityData DTOs
- `JOOQRelationStrategyFactory`: Polymorphic strategies for relation types
- `TransactionalQueryEngine`: Wraps JOOQQueryEngine with Spring transactions

### Content Storage

Content storage uses a **strategy pattern** with pluggable implementations:

**`ContentStore` Interface:**
- `getReader()`: Stream content for download
- `writeContent()`: Upload content
- `remove()`: Delete content

**Implementations:**
- `FilesystemContentStore`: File-based storage
- `S3ContentStore`: S3-compatible cloud storage
- `EncryptedContentStore`: Decorator for encryption

### Event-Driven Callbacks

Query operations accept consumer callbacks for domain events:
- `CreateEventConsumer`, `UpdateEventConsumer`, `DeleteEventConsumer`
- `LinkEventConsumer`, `UnlinkEventConsumer`

This decouples persistence logic from event handling.

### Key Architectural Patterns

1. **Type Safety**: Sealed interfaces (`Constraint`, `Attribute`, `Relation`) ensure exhaustive pattern matching
2. **Value Objects**: `EntityName`, `AttributeName`, `EntityId`, `PropertyPath`, etc. provide type-safe identifiers
3. **Immutability**: `@Value` classes and unmodifiable collections prevent state mutations
4. **Builder Pattern**: Lombok `@Builder` with `@Singular` for complex object construction
5. **Delegation**: `Entity` delegates `Translatable` interface to reduce boilerplate
6. **Decorator Pattern**: `EncryptedContentStore` wraps any `ContentStore` implementation
7. **Strategy Pattern**: Content storage and relation handling use pluggable strategies
8. **Visitor Pattern**: `ThunkExpression` uses visitor for predicate evaluation

## Code Style & Conventions

### Language Features
- Java 21 with preview features enabled
- Use `var` for local variable declarations
- Maximum line length: ~120 characters
- 4-space indentation, no tabs

### Import Ordering
1. `java.*` imports
2. `lombok.*` imports
3. Third-party libraries (Spring, etc.)
4. Project imports (`com.contentgrid.appserver.*`)

### Naming Conventions
- Classes: PascalCase (e.g., `EntityRestController`)
- Methods/fields: camelCase (e.g., `getEntityOrThrow`)
- Constants: UPPER_SNAKE_CASE (e.g., `PRIMARY_KEY`)
- Test classes: `*Test.java` (not `*Tests`)
- Test methods: descriptive camelCase (e.g., `createEntity_withValidData_returns201`)
- Packages: lowercase with `com.contentgrid.appserver` prefix

### Lombok Usage
- `@Getter` / `@Setter` for accessors
- `@RequiredArgsConstructor` for constructor injection
- `@Builder` for complex object construction
- `@NonNull` for null-safety
- `@Data` for data containers
- `@Value` for immutable classes
- Use `lombok.val` for immutable local variables

### Exception Handling
- Create domain-specific exceptions in `exception` or `exceptions` packages
- Extend appropriate base exceptions:
  - `QueryEngineException` for database errors
  - `ApplicationModelException` for model validation
  - `InvalidDataException` for input validation
- Exception names: `<Condition>Exception` (e.g., `EntityIdNotFoundException`)

### Testing
- JUnit 5 (Jupiter) with `@Test`, `@ParameterizedTest`, `@Nested`
- Spring Boot tests: `@SpringBootTest` + `@AutoConfigureMockMvc`
- AssertJ for assertions: `assertThat()`
- Hamcrest for matchers: `is()`, `containsString()`
- Integration tests use Testcontainers (PostgreSQL, RabbitMQ, MinIO)

### REST API Guidelines
- Use Spring HATEOAS for hypermedia responses
- Return `Optional<T>` for queries that may not find results
- Use `ResponseEntity` to control HTTP status codes and headers
- ProblemDetails (RFC 7807) for error responses
- ETags for optimistic locking

### Documentation
- Javadoc for public APIs with `@param`, `@return`, and `@throws` tags
- Focus on "why" rather than "what" in comments

## Key Files Reference

**Core Model:**
- `contentgrid-appserver-application-model/src/main/java/com/contentgrid/appserver/application/model/Application.java`
- `contentgrid-appserver-application-model/src/main/java/com/contentgrid/appserver/application/model/Entity.java`
- `contentgrid-appserver-application-model/src/main/java/com/contentgrid/appserver/application/model/Attribute.java`
- `contentgrid-appserver-application-model/src/main/java/com/contentgrid/appserver/application/model/Relation.java`

**REST Layer:**
- `contentgrid-appserver-rest/src/main/java/com/contentgrid/appserver/rest/EntityRestController.java`
- `contentgrid-appserver-rest/src/main/java/com/contentgrid/appserver/rest/ContentRestController.java`

**Query Engine:**
- `contentgrid-appserver-query-engine-api/src/main/java/com/contentgrid/appserver/query/engine/api/QueryEngine.java`
- `contentgrid-appserver-query-engine-impl-jooq/src/main/java/com/contentgrid/appserver/query/engine/jooq/JOOQQueryEngine.java`

**Domain Layer:**
- `contentgrid-appserver-domain/src/main/java/com/contentgrid/appserver/domain/DatamodelApi.java`
- `contentgrid-appserver-domain/src/main/java/com/contentgrid/appserver/domain/ContentApi.java`

**Auto-Configuration:**
- `contentgrid-appserver-autoconfigure/src/main/java/com/contentgrid/appserver/autoconfigure/`

## Request Flow Example

**GET /entities/persons?first_name=John&page=1**

1. Request arrives at `EntityRestController.listEntity()`
2. `ApplicationArgumentResolver` resolves Application from registry
3. `AuthorizationContextArgumentResolver` extracts auth context
4. `EncodedCursorPaginationHandlerMethodArgumentResolver` parses pagination
5. Controller calls `datamodelApi.findAll()`
6. `DatamodelApiImpl` converts query params to `ThunkExpression` predicates
7. `QueryEngine.findAll()` called with predicates
8. `JOOQThunkExpressionVisitor` converts to JOOQ Condition
9. Query builder creates SQL SELECT with WHERE, ORDER BY, LIMIT OFFSET
10. Results mapped via `EntityDataMapper`
11. `EntityDataRepresentationModelAssembler` converts to HAL+JSON
12. Response includes `_links` (self, next, prev, relation links)

**PUT /entities/persons/{id}**

1. Request body deserialized via `RequestInputDataJacksonModule`
2. `ConversionService` converts strings to proper types
3. `DatamodelApi.update()` called
4. `QueryEngine.update()` performs version check, authorization, validation, and database UPDATE
5. `UpdateEventConsumer` called with old/new EntityInstance
6. HAL response with updated entity and links
