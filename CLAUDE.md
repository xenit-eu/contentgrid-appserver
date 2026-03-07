# contentgrid-appserver

## Testing

Always run both unit/module tests **and** integration tests before pushing.

```bash
# Module checks (compile, unit tests, javadoc, Maven Central requirements)
./gradlew check

# Integration tests (requires Docker — spins up PostgreSQL via Testcontainers)
./gradlew :contentgrid-appserver-integration-test:check
```

Running only `./gradlew check` is not sufficient. The integration tests catch runtime failures (e.g. SQL errors against a real PostgreSQL instance) that unit tests with mocks cannot.
