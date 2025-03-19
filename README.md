# ContentGrid Appserver

This project is the heart of ContentGrid. It will serve as the API server for ContentGrid user applications.

## Project Structure

The project is organized into modules:

- **contentgrid-appserver-application-model**: Core domain model for applications including:
  - Entities and attributes
  - Relationships (One-to-One, One-to-Many, Many-to-One, Many-to-Many)
  - Constraints (Required, Unique, Allowed Values)
  - Search filters (Exact, Prefix)

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