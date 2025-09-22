# ContentGrid Appserver

This project is the heart of ContentGrid. It will serve as the API server for ContentGrid user applications.

## Project Structure

The project is organized into modules:

- **contentgrid-appserver-app**: Configuration for an example application.
- **contentgrid-appserver-application-model**: Core domain model for applications including:
  - Entities and attributes
  - Relationships (One-to-One, One-to-Many, Many-to-One, Many-to-Many)
  - Constraints (Required, Unique, Allowed Values)
  - Search filters (Exact, Prefix)
- **contentgrid-appserver-contentstore-api**: Defines interfaces and data structures to query object storage.
- **contentgrid-appserver-contentstore-impl-fs**: Implementation of contentstore API, using filesystem storage.
- **contentgrid-appserver-contentstore-impl-s3**: Implementation of contentstore API, using S3-compatible storage.
- **contentgrid-appserver-contentstore-impl-encryption**: Encryption wrapper around any contentstore implementation
- **contentgrid-appserver-contentstore-impl-utils**: Content utils for dealing with input and output streams.
- **contentgrid-appserver-domain**: Core domain layer defining apis and implementations to deal with entities, relations and content.
- **contentgrid-appserver-domain-values**: Defines core data structures for representing input and output data.
- **contentgrid-appserver-json-schema**: Serialization and deserialization of Applications.
- **contentgrid-appserver-platform**: Platform defining dependencies for contentgrid-appserver.
- **contentgrid-appserver-query-engine-api**: Defines interfaces and data structures to query database.
- **contentgrid-appserver-query-engine-impl-jooq**: Implementation of *contentgrid-appserver-query-engine-api* using [JOOQ](https://www.jooq.org/).
- **contentgrid-appserver-rest**: Defines the rest layer for interacting with entities, relations and content.

## Development

### Building the Project

```bash
./gradlew build
```

### Running Tests

```bash
./gradlew test
```

### Code Coverage

Code coverage reports are generated using JaCoCo and can be found in the `build/reports/jacoco` directory after running tests.