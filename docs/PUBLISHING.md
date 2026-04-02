# Publishing Policy

This repository publishes the `hypernate` library as a Maven artifact.

## Versioning

- Follow semantic versioning (`MAJOR.MINOR.PATCH`).
- Release tags must use the `v*` format (for example: `v0.1.0`).

## Release Prerequisites

- All pull request checks must pass:
  - Spotless lint check
  - Unit tests
  - Integration tests
- Release changes must be merged into `main`.

## Publishing Workflow

- Publishing is automated by `.github/workflows/publish.yml`.
- The workflow runs on:
  - Push of tags matching `v*`
  - Manual `workflow_dispatch`
- The workflow executes:

```bash
./gradlew --no-daemon :lib:publish
```

## Maven Target

Artifacts are published to GitHub Packages Maven repository for this project:

- `https://maven.pkg.github.com/LF-Decentralized-Trust-labs/hypernate`

## Credentials

Publishing uses these environment variables:

- `GITHUB_ACTOR`
- `GITHUB_TOKEN`

On GitHub Actions these are provided automatically (`github.actor` and `secrets.GITHUB_TOKEN`).

## Local Dry Run

You can verify publication metadata locally with:

```bash
./gradlew :lib:publishToMavenLocal
```
