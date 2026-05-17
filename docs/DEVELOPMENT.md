# Development Notes

OrderFlow currently has a Spring Boot backend foundation for the synchronous order happy path. This document records the public development commands and conventions that are real in the current codebase.

## Repository Status

- Backend runtime code has landed under `backend/`.
- Public documentation should distinguish implemented backend behavior from planned later milestones.
- The current runnable service depends on PostgreSQL.
- Internal planning material belongs in ignored private documentation and should not be referenced from public-facing files.

## Expected Local Development Flow

- Keep changes scoped to the active milestone.
- Use `./gradlew test` for the backend test suite.
- Use `docker compose up --build` for the local PostgreSQL plus backend runtime.
- Prefer reproducible local dependencies through Docker Compose.
- Keep public docs in sync with implemented behavior, not aspirational behavior.

## Testing Expectations

- Add tests with each feature rather than saving coverage for the end.
- Use integration tests when behavior depends on database transactions, message brokers, cache behavior, or concurrency.
- The current integration test uses Testcontainers with PostgreSQL and verifies the create-order happy path.
- On Docker Desktop for macOS, `DOCKER_HOST=unix://$HOME/.docker/run/docker.sock ./gradlew test --no-daemon` may be needed when the default socket is not detected.
- Keep benchmark and comparison modes runnable in the current codebase when they are used to prove an engineering mechanism.
- Baseline modes should stay isolated to test, benchmark, or evaluation profiles. Default runtime paths should use the improved implementation.

## PR And Milestone Workflow

- Work should move in logical milestone-sized changes.
- Each milestone should have a clear done state, validation notes, and any known limitations.
- Public PR descriptions should focus on engineering behavior, validation, and remaining risks.
- Private milestone notes may capture additional planning context, but public files should remain project-neutral.

## Public And Private Documentation Boundary

- Public files must be written in English and should only describe the engineering project.
- Private planning, local strategy, and non-public decision context must stay in ignored internal files.
- Public documentation must not claim planned features, benchmark numbers, or deployment paths as complete before they are implemented and verified.
