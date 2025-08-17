# Project Guidelines

## Project Overview
Fluxtion Server is a Java-based event-driven server framework built around Fluxtion's composable services and agents. It provides infrastructure for configuring, composing, and running event processors and services with support for scheduling, batching, dispatching, and server control.

Core components visible in this repository include:
- Configuration (com.fluxtion.server.config) to bootstrap and wire services.
- Service extensions (com.fluxtion.server.service.extension) for event sources and agent-hosted services.
- Dispatch, duty-cycle, scheduler, and admin services under com.fluxtion.server.service.*.
- Internal runners and injectors (com.fluxtion.server.internal) for composing and running agents/processors.
- Example, integration, benchmark, and stress tests under src/test/java/com/fluxtion/server.

## Repository Structure
- src/main/java: Production code under package com.fluxtion.server and subpackages.
- src/test/java: Unit, integration, and performance-related tests.
- docs/: Architecture notes, standards, and sequence diagrams.
- pom.xml: Maven build configuration.

## How Junie should work on this project
- Prefer minimal, targeted code changes per issue.
- When making Java changes, run related tests to ensure nothing breaks.
- Keep documentation changes simple and focused unless the issue requests broader edits.

## Build and Test
- Build with Maven: mvn -q -DskipTests package
- Run all tests: mvn -q test
- Run a specific test class: mvn -q -Dtest=FullyQualifiedTestName test
- In this environment, Junie can also use the test runner tool to execute tests by path or FQN when needed.

## Code Style and Conventions
- Follow existing package structure and naming conventions.
- Keep methods cohesive; prefer small, single-responsibility helpers in internal utilities.
- Add Javadoc or comments where behavior is non-obvious.

## Contribution Tips
- Search for existing utilities in com.fluxtion.server.internal and service packages before adding new helpers.
- Update or add tests alongside behavior changes.
- For configuration-related edits, check AppConfig and ServerConfigurator for wiring impacts.
