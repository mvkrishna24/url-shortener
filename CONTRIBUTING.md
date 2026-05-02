# Contributing

Thank you for considering contributing to the Distributed URL Shortener! 

## Branching Strategy
*   `main` is protected and strictly deployable.
*   Create feature branches from `main` using the format `feature/your-feature-name` or `bugfix/issue-description`.

## Development Workflow
1.  Fork the repo and clone it locally.
2.  Ensure Docker is running and run `make up` to provision the database and cache.
3.  Make your changes.
4.  Ensure all unit and integration tests pass:
    ```bash
    mvn clean test
    ```
5.  Ensure no degradation in application startup or performance.

## Pull Requests
*   Provide a clear and descriptive PR title.
*   Include a summary of changes and the motivation behind them.
*   Ensure the CI pipeline is passing (Testcontainers will run the integration tests in CI).
*   If adding a new endpoint, please update the OpenAPI annotations and `docs/api.md`.