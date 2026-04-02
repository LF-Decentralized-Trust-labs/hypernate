# Contributing

## Development Setup

Use Java 17 and Gradle Wrapper.

```bash
./gradlew :lib:check
```

## Pull Request Requirements

Before opening or updating a PR, ensure these pass locally:

```bash
./gradlew --no-daemon :lib:spotlessCheck :lib:test :lib:integrationTest
```

## Commit and PR Guidelines

- Keep changes focused and atomic.
- Include tests for behavior changes.
- Update docs when changing developer workflow or release behavior.
- Ensure SPDX header remains present in source files.
