# ContentGrid Appserver

This project is the heart of ContentGrid. It will serve as the API server for ContentGrid user applications.

## Project Structure

The project is organized into modules:

- **contentgrid-appserver-actuators**: Custom Contentgrid actuators (e.g. webhooks and policy definitions).
- **contentgrid-appserver-app**: Minimal spring boot application with configuration for an example application in testFixtures.
- **contentgrid-appserver-application-model**: Core domain model for applications including:
  - Entities and attributes
  - Relationships (One-to-One, One-to-Many, Many-to-One, Many-to-Many)
  - Constraints (Required, Unique, Allowed Values)
  - Search filters (Exact, Prefix)
- **contentgrid-appserver-application-model-json**: Serialization and deserialization of Applications.
- **contentgrid-appserver-autoconfigure**: Autoconfiguration for appserver applications.
- **contentgrid-appserver-blueprintartifact-impl-fs**: Implementation of domain SPI, for loading files from filesystem directory, classpath or zip file.
- **contentgrid-appserver-contentstore-api**: Defines interfaces and data structures to query object storage.
- **contentgrid-appserver-contentstore-impl-fs**: Implementation of contentstore API, using filesystem storage.
- **contentgrid-appserver-contentstore-impl-s3**: Implementation of contentstore API, using S3-compatible storage.
- **contentgrid-appserver-contentstore-impl-encryption**: Encryption wrapper around any contentstore implementation
- **contentgrid-appserver-contentstore-impl-utils**: Content utils for dealing with input and output streams.
- **contentgrid-appserver-domain**: Core domain layer defining apis and implementations to deal with entities, relations and content.
- **contentgrid-appserver-domain-spi**: Defines interfaces and datastructures that the domain layer can use.
- **contentgrid-appserver-domain-values**: Defines core data structures for representing input and output data.
- **contentgrid-appserver-events**: Implementation to send change events to RabbitMQ.
- **contentgrid-appserver-platform**: Platform defining dependencies for contentgrid-appserver.
- **contentgrid-appserver-query-engine-api**: Defines interfaces and data structures to query database.
- **contentgrid-appserver-query-engine-impl-jooq**: Implementation of *contentgrid-appserver-query-engine-api* using [JOOQ](https://www.jooq.org/).
- **contentgrid-appserver-rest**: Defines the rest layer for interacting with entities, relations and content.
- **contentgrid-appserver-spring-boot-starter**: Spring boot starter for ContentGrid appserver applications.
- **contentgrid-appserver-webjars**: ContentGrid module to embed and serve webjars like Swagger UI.

## Configure your app

Most properties are prefixed with `contentgrid.appserver`. System identity and event properties use the `contentgrid` prefix.

### Application model

| Property | Description | Default | Required |
|---|---|---|---|
| `contentgrid.appserver.application-model` | Path to an application model JSON file (e.g. `classpath:my-app.json`). When set, loads the model from this resource. When absent, the model is loaded from the blueprint artifact defined by `contentgrid.appserver.blueprint-artifact.location`. | — | No |

### Blueprint artifact

| Property | Description | Default | Required |
|---|---|---|---|
| `contentgrid.appserver.blueprint-artifact.location` | Location of the blueprint artifact that contains the blueprint files. Accepts `classpath:`, `file:` and `zip:` URIs. | `classpath:.` | No |

### Content store

The content store type is selected with `contentgrid.appserver.content-store.type`.

| Value | Description |
|---|---|
| `ephemeral` | Stores files in a temporary directory. Data is lost on restart. Useful for development. |
| `fs` | Stores files on the local filesystem at a configurable path. |
| `s3` | Stores files in an S3-compatible object store (e.g. MinIO or AWS S3). |

#### Filesystem (`type=fs`)

| Property | Description | Default | Required |
|---|---|---|---|
| `contentgrid.appserver.content.fs.path` | Directory path where content files are stored. | — | Yes |

#### S3 (`type=s3`)

| Property | Description | Default | Required |
|---|---|---|---|
| `contentgrid.appserver.content.s3.url` | S3 endpoint URL (e.g. `https://s3.amazonaws.com` or a MinIO URL). | — | Yes |
| `contentgrid.appserver.content.s3.bucket` | Name of the S3 bucket to use. | — | Yes |
| `contentgrid.appserver.content.s3.access-key` | S3 access key ID. | — | No |
| `contentgrid.appserver.content.s3.secret-key` | S3 secret access key. | — | No |
| `contentgrid.appserver.content.s3.region` | AWS region identifier (e.g. `eu-west-1`). | — | No |
| `contentgrid.appserver.content.s3.connection-pool-size` | Maximum number of idle HTTP connections to keep in the pool. | `0` | No |
| `contentgrid.appserver.content.s3.connection-pool-keep-alive-seconds` | How long idle connections are kept alive (in seconds). | `1` | No |

### Content encryption

When enabled, content is transparently encrypted before being written to the content store.

| Property | Description | Default | Required |
|---|---|---|---|
| `contentgrid.appserver.content.encryption.enabled` | Enables transparent content encryption. | `false` | No |
| `contentgrid.appserver.content.encryption.bootstrap-tables` | Controls DEK table lifecycle on startup. `NONE` does nothing, `CREATE` creates the table, `CREATE_DROP` creates on start and drops on stop. | `NONE` | No |
| `contentgrid.appserver.content.encryption.wrapper.algorithms` | List of key wrapping algorithms to use. Supported value: `NONE` (unencrypted symmetric key). | `NONE` | No |
| `contentgrid.appserver.content.encryption.engine.algorithms` | List of Content encryption algorithms to use. Supported values: `AES128_CTR`, `AES192_CTR`, `AES256_CTR`, `ALFRESCO`. | `AES128_CTR` | No |

### Query engine

| Property | Description | Default | Required |
|---|---|---|---|
| `contentgrid.appserver.query-engine.count.timeout` | Maximum time allowed for a count query before falling back to estimate. Accepts Spring `Duration` format (e.g. `500ms`, `5s`). | `500ms` | No |
| `contentgrid.appserver.query-engine.bootstrap-tables` | Controls database table lifecycle on startup. `NONE` does nothing, `CREATE` creates tables, `CREATE_DROP` creates on start and drops on stop. | `NONE` | No |

### System

These properties identify the running deployment and are used by both the actuator and event modules.

| Property | Description | Default | Required |
|---|---|---|---|
| `contentgrid.system.deployment-id` | Unique identifier of the deployment. | — | No |
| `contentgrid.system.application-id` | Unique identifier of the application. | — | No |
| `contentgrid.system.policy-package` | OPA policy package name used by the policy actuator. | — | No |
| `contentgrid.variables` | Arbitrary key-value pairs made available as application variables (e.g. `contentgrid.variables.my-key=value`). | — | No |

### Events

| Property | Description | Default | Required |
|---|---|---|---|
| `contentgrid.events.webhook-config-url` | URL to fetch the webhook configuration from. | — | No |
| `contentgrid.events.rabbitmq.routing-key` | RabbitMQ routing key used when publishing change events. | `contentgrid.events` | No |
| `contentgrid.events.rabbitmq.enabled` | Whether RabbitMQ is enabled. | `true` | No |

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
