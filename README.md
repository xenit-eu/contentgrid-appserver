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
- **contentgrid-appserver-blueprintartifact-impl-fs**: Implementation of domain SPI, for loading files from a blueprint artifact on filesystem (e.g. directory, classpath or zip file).
- **contentgrid-appserver-blueprintartifact-impl-s3**: Implementation of domain SPI, for loading files from a blueprint artifact over s3.
- **contentgrid-appserver-blueprintartifact-impl-utils**: Blueprint artifact utilities.
- **contentgrid-appserver-contentstore-api**: Defines interfaces and data structures to query object storage.
- **contentgrid-appserver-contentstore-impl-fs**: Implementation of contentstore API, using filesystem storage.
- **contentgrid-appserver-contentstore-impl-s3**: Implementation of contentstore API, using S3-compatible storage.
- **contentgrid-appserver-contentstore-impl-encryption**: Encryption wrapper around any contentstore implementation
- **contentgrid-appserver-contentstore-impl-utils**: Content utils for dealing with input and output streams.
- **contentgrid-appserver-domain**: Core domain layer defining apis and implementations to deal with entities, relations and content.
- **contentgrid-appserver-domain-spi**: Defines interfaces and datastructures that are used by the domain layer to access its dependencies.
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
| `contentgrid.appserver.blueprint-artifact.location` | Location of the blueprint artifact that contains the blueprint files. Accepts `classpath:`, `file:`, `zip:` and `s3:` URIs. | `classpath:.` | No |

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
| `contentgrid.appserver.content.s3.access-key` | S3 access key ID. | — | Yes |
| `contentgrid.appserver.content.s3.secret-key` | S3 secret access key. | — | Yes |
| `contentgrid.appserver.content.s3.region` | Region identifier (e.g. `eu-west-1`). | `none` | No |
| `contentgrid.appserver.content.s3.path-style-access` | Use path-style access (`https://endpoint/bucket/key`) instead of virtual-host style (`https://bucket.endpoint/key`). | `true` | No |
| `contentgrid.appserver.content.s3.connection-pool-size` | Maximum number of concurrent HTTP connections. `0` disables connection re-use entirely. | `0` | No |
| `contentgrid.appserver.content.s3.connection-pool-keep-alive-seconds` | How long idle connections are kept alive (in seconds). | `1` | No |
| `contentgrid.appserver.content.s3.connection-timeout` | Maximum time to establish a TCP connection. Must be strictly positive. | `2s` | No |
| `contentgrid.appserver.content.s3.tls-negotiation-timeout` | Maximum time for the TLS handshake, independently of the TCP connection timeout. Must be strictly positive. | `5s` | No |
| `contentgrid.appserver.content.s3.connection-acquisition-timeout` | Maximum time to acquire an HTTP connection from the pool. Must be strictly positive. | `10s` | No |
| `contentgrid.appserver.content.s3.read-timeout` | Maximum waiting time for a socket read, not the total download duration. `0ms` disables this timeout; negative values are rejected. | `30s` | No |
| `contentgrid.appserver.content.s3.write-timeout` | Maximum waiting time for a socket write, not the total upload duration. `0ms` disables this timeout; negative values are rejected. | `30s` | No |
| `contentgrid.appserver.content.s3.read-retry.max-retries` | Additional appserver-level read acquisition retries after narrowly typed S3 connection-establishment failures. `0` disables these outer retries. | `1` | No |
| `contentgrid.appserver.content.s3.read-retry.acquisition-timeout` | Total budget for S3 read acquisition attempts and backoff delays. Must be strictly positive. | `10s` | No |
| `contentgrid.appserver.content.s3.read-retry.min-delay` | Minimum delay before a read acquisition retry. Must be nonnegative and no greater than `max-delay`. | `0ms` | No |
| `contentgrid.appserver.content.s3.read-retry.max-delay` | Maximum randomized retry delay. With the default minimum, `0ms` means immediate retry. Set both bounds to the same positive value for a fixed delay. | `250ms` | No |

By default, the retry delay is randomized between `0ms` and `250ms`.
S3 read retries only apply while `getReader` is acquiring the response stream. After the stream is handed to the caller,
large or slow downloads continue outside this acquisition timeout and are not retried by the appserver. Each outer retry
starts a fresh SDK `getObject` operation; the AWS SDK's own retry policy is still active inside each operation, so the
maximum number of underlying HTTP attempts is multiplied by the number of outer operations (`max-retries + 1`).

The transport timeouts apply to the appserver-managed S3 content client, including uploads as well as downloads;
changing them does not change the upload retry policy. Socket read/write timeouts still apply during ongoing transfers,
independently of the acquisition deadline. An externally supplied `S3AsyncClient` keeps its own transport configuration.
Increasing a connection timeout can consume the read acquisition budget before a retry is possible, so adjust the budget
as well when more recovery time is needed. For example, to use a 10-second TCP connection timeout with immediate retry:

```properties
contentgrid.appserver.content.s3.connection-timeout=10s
contentgrid.appserver.content.s3.read-retry.acquisition-timeout=25s
contentgrid.appserver.content.s3.read-retry.min-delay=0ms
contentgrid.appserver.content.s3.read-retry.max-delay=0ms
```

### Content encryption

Whether content is transparently encrypted before being written to the content store is configured per application, with
the `settings.contentEncryption.enabled` property of the application blueprint. The algorithms that are used are
configured on the appserver itself.

| Property | Description | Default | Required |
|---|---|---|---|
| `contentgrid.appserver.content.encryption.wrapper.algorithms` | List of key wrapping algorithms to use. Supported value: `NONE` (unencrypted symmetric key). | `NONE` | No |
| `contentgrid.appserver.content.encryption.engine.algorithms` | List of content encryption algorithms to use. The first algorithm is used for encryption, all algorithms are used for decryption. Supported values: `AES128_CTR`, `AES192_CTR`, `AES256_CTR`, `ALFRESCO` (decryption only). | `AES128_CTR` | No |

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
