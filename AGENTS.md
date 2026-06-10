# AGENTS.md

Machine-readable guidance for AI coding agents contributing to Hypernate. This is the terse
version of our [contribution guide](CONTRIBUTING.md); humans should read that (or the
[documentation site](https://lf-decentralized-trust-labs.github.io/hypernate/)) for the rationale.

Hypernate is an entity framework for Hyperledger Fabric chaincode, written in Java 17 and built
with Gradle. Library source lives in the single `lib` module.

## Setup, build, and test

- Use the Gradle wrapper (`./gradlew`); no separate Gradle install is needed. Requires JDK 17.
- `./gradlew build` runs the full build — formatting check, compilation, and tests. **It must pass
  before you propose any code change.**
- `./gradlew test` runs the JUnit 5 test suite.

## Code style

- Formatting is enforced by Spotless (Google Java Format). Run `./gradlew spotlessApply` to format;
  CI fails on unformatted code.
- Do not hand-manage import order or license headers — `spotlessApply` handles them, including the
  required SPDX header `/* SPDX-License-Identifier: Apache-2.0 */` on every source file.

## Commits and sign-off

- Use [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/#summary), e.g.
  `fix: handle missing primary key in Registry.read`.
- **Sign off every commit** with `git commit -s`. A DCO check gates every pull request and rejects
  unsigned commits. Sign-off certifies that a responsible human stands behind the change.

## Scope and the design gate

- An issue labeled `design/approved` must exist before you start — for every change, including
  trivial fixes. **Do not open a pull request without an associated `design/approved` issue**; an
  automated check enforces this and unassociated PRs will fail.
- Keep each pull request focused on one logical change.
- Add or update tests for behavior you change, and update docs under `docs/` when APIs or behavior
  change.

## Documentation

- Docs are Markdown under `docs/` (MkDocs Material). Preview with `mkdocs serve`; validate with
  `mkdocs build --strict` (the site uses strict mode, so broken links fail the build).

## Before opening a pull request

1. `./gradlew build` passes locally.
2. Every commit is signed off (`git commit -s`).
3. The change is linked to a `design/approved` issue (required for every change, including trivial ones).
4. Tests and docs are updated as needed.

## AI-assisted contributions

The human operator is responsible for every contribution, including AI-generated ones. Disclose
substantially AI-generated pull requests, review and test output before submitting, and do not
file bulk or unverified pull requests or issues. Full policy:
[Contributing with AI Agents](docs/contributing/ai-agents.md).
