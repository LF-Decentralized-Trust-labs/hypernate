# Getting Started

## Overview

Hypernate is an entity framework for Hyperledger Fabric.

If Fabric allows you to keep your familiar programming language, then Hypernate will allow you to
keep your familiar programming style.

No more low-level boilerplate code for key-value storage operations and other housekeeping tasks.
Take advantage of Hypernate's high abstraction level, aspect-oriented approaches, and extensibility
to keep critical business logic as clean as possible.

Enhance your chaincode with features like:

- Object-oriented CRUD (create, read, update, delete) operations with explicit semantics
- Declarative and flexible configuration of entity keys
- An extensible chain of middleware processors handling non-business concerns

Get access to these features using a simple incantation:

```java
public class MyBusinessContract implements HypernateContract
```

More features are on the way:

- Partial and range queries based on non-key attributes
- Range query support for composite keys
- Overall friendlier query support
- OpenTelemetry integration
- Support for data schemas

## Setup Flow

For complete examples, refer to the
[hypernate-samples](https://github.com/LF-Decentralized-Trust-labs/hypernate-samples)
repository.

The typical setup flow is:

1. Include the Hypernate library as a dependency in your project.
2. Use Hypernate annotations on your entities (DTOs or POJOs).
3. Use `HypernateContract` as the base class for your business `Contract` implementation.
4. Use the `Registry` class to handle annotated entities in a friendly way.

The easiest integration path is using `HypernateContext` as your transaction `Context`
implementation so it can wire the registry and middleware setup automatically.

## Next Steps

- Continue to the [Concepts overview](../concepts/index.md) for design details.
- Follow the [Quickstart](quickstart.md) once available.
- Read [Installation](installation.md) for environment setup details.

## License

Hypernate uses the Apache License Version 2.0.
For details, see `NOTICES.md`, `MAINTAINERS.md`, and `LICENSE` in the repository root.
