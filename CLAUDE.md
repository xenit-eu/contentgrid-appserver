# contentgrid-appserver

## Testing

Always run `./gradlew check` before pushing. This includes all module tests, javadoc, Maven Central requirement checks, and the integration tests (which spin up PostgreSQL via Testcontainers and catch runtime SQL errors that unit tests with mocks cannot).
