# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**codspeed-jvm** is a CodSpeed integration for JVM-based projects that use JMH (Java Microbenchmark Harness). It provides walltime benchmarking support and performance tracking in CI.

### Key Components

- **`jmh-fork/`**: Forked OpenJDK JMH with CodSpeed walltime result collection. Maven multi-module project used as a composite build dependency.
- **`examples/example-gradle/`**: Gradle-based example benchmarks using the custom JMH fork
- **`examples/example-maven/`**: Maven-based example benchmarks
- **`examples/TheAlgorithms-Java/`**: TheAlgorithms submodule with additional benchmarks

## Build & Development

### Prerequisites

- **Java**: JDK 21+ (for main project), JDK 11+ (for jmh-fork modules)
- **Maven**: 3.9.9+ (for jmh-fork)
- **Gradle**: Uses gradlew wrapper (no separate installation needed)

### Common Commands

#### Building

```bash
# Build and run Gradle example benchmarks
./gradlew :examples:example-gradle:jmh

# Build JAR artifact only (useful for local profiling)
./gradlew :examples:example-gradle:jmhJar

# Build and install JMH fork to local Maven repo (required before Maven example)
cd jmh-fork && mvn clean install -DskipTests -q

# Build Maven example (after installing JMH fork)
cd examples/example-maven && mvn package -q
java -jar examples/example-maven/target/example-maven-1.0-SNAPSHOT.jar
```

#### Testing

```bash
# Run CodSpeed tests for the JMH fork integration
cd jmh-fork && mvn test -pl jmh-core -Dtest="io.codspeed.*.*"

# Run with CodSpeed locally (walltime mode)
codspeed run --mode walltime --skip-upload -- ./gradlew :examples:example-gradle:jmh

# Run with specific pattern filtering
CODSPEED_LOG=trace codspeed run --mode walltime --skip-upload -- java -jar examples/example-gradle/build/libs/example-gradle-1.0-SNAPSHOT-jmh.jar ".*BacktrackingBenchmark.*"
```

#### Code Quality

```bash
# Run pre-commit hooks manually (linting and formatting)
pre-commit run --all-files
# Uses Google Java Formatter v1.25.2
```

### Build Structure

- **Root**: Gradle root project (`settings.gradle.kts`) includes:
  - `examples:example-gradle` as a regular subproject
  - `jmh-fork` as a composite build (allows independent versioning)
- **jmh-fork**: Maven multi-module project with submodules `jmh-core`, `jmh-generator-*`, etc.

### Publishing JMH Fork

The JMH fork uses versioning scheme `1.37.0-codspeed.N`. When updating versions:

```bash
cd jmh-fork && mvn versions:set -DnewVersion=1.37.0-codspeed.2
```

Then update the version reference in root `build.gradle.kts`.

## Architecture & Key Patterns

### Multi-Build Structure

The repository combines Maven and Gradle via Gradle's composite build feature:
- **jmh-fork** (Maven multi-module) is built as a composite dependency for independent versioning
- Example projects depend on the forked JMH packages

### CodSpeed Integration Points

1. **Walltime Collection**: Modified JMH fork in `jmh-fork/jmh-core` collects walltime metrics during benchmark execution
2. **JNI Bindings**: Native code hooks (C) for precise instrumentation in `jmh-fork/jmh-core/src/main/java/io/codspeed/`
3. **CI Integration**: GitHub Actions workflows trigger CodSpeed measurement runs on `codspeed-macro` runners

### Benchmark Architecture

- Benchmarks use standard JMH annotations (`@Benchmark`, `@Fork`, etc.)
- The `gradle-jmh-plugin` (v0.7.2) generates benchmark JARs from sources
- Both Gradle (`me.champeau.jmh`) and Maven profiles support building executable benchmark JARs

## CI/CD & Testing

### GitHub Actions Workflows (`.github/workflows/ci.yml`)

- **lint**: Pre-commit hooks (Java formatting, YAML validation, file endings)
- **test**: CodSpeed-specific unit tests (jmh-core integration tests)
- **build-and-run-gradle**: Matrix test across Linux, macOS, Windows
- **build-and-run-maven**: Matrix test for Maven example, including publishToMavenLocal step
- **walltime-benchmarks**: Runs on CodSpeed infrastructure for real performance tracking

## Modifying JMH Fork

1. Make changes in `jmh-fork/`
2. Bump version: `cd jmh-fork && mvn versions:set -DnewVersion=X.Y.Z-codspeed.N`
3. Publish locally: `cd jmh-fork && mvn clean install -DskipTests -q`
4. Update version reference in root `build.gradle.kts` if needed
5. Test both Gradle and Maven examples to verify compatibility

## Profiling & Debugging

Use `just` commands for quick profiling workflows (see `Justfile`):
- `just profile-codspeed` — walltime profiling via CodSpeed runner
- `just profile-perf` — Linux perf + flamegraph (requires Linux + JDK perf integration)
- `just profile-asprof` — async-profiler flamegraph

## Notes

- **Submodules**: Repository uses Git submodules (`.gitmodules`). Clone with `--recurse-submodules`.
- **Java Versions**: Root project uses Java 21; jmh-fork runs on Java 11+.
- **Pre-commit Exclusions**: jmh-fork is mostly excluded from pre-commit hooks except for `jmh-core/src/main/java/io/codspeed/`.
- **CodSpeed Credentials**: CI uses OIDC for authentication.
