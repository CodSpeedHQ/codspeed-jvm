<div align="center">
<h1>codspeed-jvm</h1>

[![CI](https://github.com/CodSpeedHQ/codspeed-jvm/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/CodSpeedHQ/codspeed-jvm/actions/workflows/ci.yml)
[![Discord](https://img.shields.io/badge/chat%20on-discord-7289da.svg)](https://discord.com/invite/MxpaCfKSqF)
[![CodSpeed Badge](https://img.shields.io/endpoint?url=https://codspeed.io/badge.json)](https://codspeed.io/CodSpeedHQ/codspeed-jvm)

</div>

This repo contains the CodSpeed integration for JVM-based projects using [JMH](https://github.com/openjdk/jmh):

- [`jmh-fork`](./jmh-fork/): Forked JMH with CodSpeed walltime result collection
- [`examples`](./examples/): Example JMH benchmarks (Gradle and Maven)

## Usage

Add the CodSpeed JMH fork to your project and write benchmarks as you normally would with JMH. When running your benchmarks in CI with CodSpeed, the results will be automatically collected and reported.

For information on how to integrate it, see the [CodSpeed documentation](https://codspeed.io/docs). If you need further information to integrate CodSpeed to your project, please feel free to open an issue or ask for help on our [Discord server](https://discord.com/invite/MxpaCfKSqF).

## Local Usage

### Prerequisites

- JDK 21+
- A C toolchain (Linux only, to build the native libraries)

### Setup

1. Build and install the JMH fork to your local Maven repository. Use Gradle: only the Gradle build compiles and bundles the native libraries needed for CodSpeed measurements.
```bash
git submodule update --init --recursive
./gradlew -p jmh-fork publishToMavenLocal
```

2. Run the example benchmarks:
```bash
./gradlew :examples:example-gradle:jmh
```

### Running with CodSpeed locally

To run the benchmarks with CodSpeed locally, you need to install the [CodSpeed runner](https://codspeed.io/docs):

```bash
codspeed run --mode walltime -- ./gradlew :examples:example-gradle:jmh
```
