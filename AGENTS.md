# Repository Guidelines

## Project Structure & Module Organization

This repository is a Gradle multi-module Java/Spring project. Deployable services use the `App-*` prefix; shared libraries use `Lib-*`. Each module follows the standard layout: production code in `src/main/java`, configuration and assets in `src/main/resources`, and tests in `src/test/java`. The root `settings.gradle` lists all modules, `build.gradle` provides shared build rules, and `gradle/libs.versions.toml` centralizes dependency versions. Keep SQL, logging configuration, static files, and environment-specific application configuration in the owning module's resources directory.

## Build, Test, and Development Commands

Run commands from the repository root with the globally installed JDK and Gradle.

- `gradle clean build` compiles every module, runs tests, and creates artifacts.
- `gradle test` runs the complete JUnit test suite.
- `gradle :Lib-Base:test` tests one module during focused development.
- `gradle :App-BusinessProcess-Backend:bootRun` starts a Spring Boot service locally.
- `gradle :App-BusinessProcess-Backend:bootJar` creates its deployable boot jar.

Library modules produce regular jars; application modules produce Spring Boot jars. `application-local.yml` is intentionally excluded from boot jars.

## Coding Style & Naming Conventions

Target Java 25 and use the `cn.bcd` package root. Indent Java and Gradle code with four spaces. Use PascalCase for classes, camelCase for methods and fields, and UPPER_SNAKE_CASE for constants. Follow existing Spring, Lombok, and MapStruct patterns. Name modules consistently, for example `App-Domain-Name` or `Lib-Technology-Name`. Add dependency versions to `gradle/libs.versions.toml` instead of hard-coding them in module builds. No repository-wide formatter is configured, so preserve nearby style.

## Testing Guidelines

Tests use JUnit Jupiter through Gradle's `useJUnitPlatform()`. Place tests under the corresponding module's `src/test/java` tree and name classes `*Test`, such as `DateUtilTest`. Add focused unit tests for library changes and integration tests only when service wiring or external behavior requires them. No coverage threshold is enforced; test changed paths and edge cases. Before submission, run at least the affected module's test task.

## Commit & Pull Request Guidelines

Recent history mainly contains terse messages such as `c` and revert commits; do not copy that ambiguity. Use a concise imperative summary, for example `fix executor shutdown race`. Pull requests should identify affected modules, summarize behavior and configuration or database changes, list verification commands, and link relevant issues. Include screenshots only for web UI changes.

## Agent-Specific Instructions

Because the Windows workspace disk is encrypted, use `cmd` commands for file inspection and manipulation; avoid PowerShell-specific filesystem commands.
