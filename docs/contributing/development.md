# Working with the Code

This page covers everything you need to build, test, and format Hypernate locally.

## Prerequisites

- **JDK 17** — Hypernate targets Java 17 (the build declares a Java 17 toolchain). CI uses the
  [Temurin](https://adoptium.net/) distribution.
- **Git** — for cloning and contributing.
- No separate Gradle installation is required; the project ships with the Gradle wrapper
  (`./gradlew`).

The library uses [Lombok](https://projectlombok.org/) and AspectJ post-compile weaving. If you use
an IDE, enable annotation processing and install Lombok support so the code resolves correctly:

- **IntelliJ IDEA** — install the *Lombok* plugin and enable *Annotation Processing*.
- **Eclipse** — run the Lombok installer against your IDE.

## Getting the Source

```bash
git clone https://github.com/LF-Decentralized-Trust-labs/hypernate.git
cd hypernate
```

If you plan to open a pull request,
[fork](https://github.com/LF-Decentralized-Trust-labs/hypernate/fork) the repository first and
clone your fork.

## Building and Testing

The project is a single Gradle module (`lib`). The most common tasks are:

| Command | What it does |
| ------- | ------------ |
| `./gradlew build` | Full build: formatting check, compilation, and tests (everything CI runs). |
| `./gradlew assemble` | Compile and package without running tests. |
| `./gradlew test` | Run the JUnit 5 test suite. |
| `./gradlew spotlessApply` | Auto-format the code (see [Code Style](#code-style)). |
| `./gradlew spotlessCheck` | Verify formatting without changing files. |

Running `./gradlew build` before you push is the best way to catch problems early — it mirrors the
[CI pipeline](../guides/cicd.md).

### Test Reports

After running tests, an HTML report is written to `lib/build/reports/tests/test/index.html`. Open
it in a browser to inspect failures with full stack traces.

## Code Style

Formatting is enforced by [Spotless](https://github.com/diffplug/spotless) using
[Google Java Format](https://github.com/google/google-java-format). CI fails if code is not
formatted, so always run:

```bash
./gradlew spotlessApply
```

Spotless also orders imports, removes unused imports, and inserts the required SPDX license header
on every source file:

```java
/* SPDX-License-Identifier: Apache-2.0 */
```

You do not need to add the header by hand — `spotlessApply` inserts it for you.

## Project Layout

The source lives under `lib/src/main/java/hu/bme/mit/ftsrg/hypernate/`, organized by package:

| Package | Responsibility |
| ------- | -------------- |
| `annotations` | Declarative entity-key and query-index annotations (`@PrimaryKey`, `@AttributeInfo`, …). |
| `context` | The Hypernate context carrying per-transaction state. |
| `contract` | Integration with the Fabric contract / chaincode entry points. |
| `mappers` | Attribute-to-key mappers (zero-padding, ordering, and serialization helpers). |
| `middleware` | The stub middleware chain (logging, caching, throttling) and its notifications. |
| `registry` | The entity registry — CRUD operations and the related exceptions. |
| `util` | Internal utilities such as JSON serialization. |

Tests mirror this structure under `lib/src/test/java/`.

## What to Work On

Looking for a place to start? Browse the
[open issues](https://github.com/LF-Decentralized-Trust-labs/hypernate/issues), especially those
labeled `design/approved`. Every change — even a trivial one — needs an approved issue before you
begin work; see [Issue Triage and the Design Workflow](issues.md#issue-triage-and-the-design-workflow).
