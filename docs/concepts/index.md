# Concepts

## Core Concepts

Hypernate builds on Hyperledger Fabric by adding a higher-level programming model focused on
entities, typed operations, and reusable middleware.

## Declarative Entity Keys

Hypernate uses annotations to define key construction declaratively.

### Declaring Primary Keys

Use `@PrimaryKey` with one or more `@AttributeInfo` entries.

```java
@FieldNameConstants
@PrimaryKey(@AttributeInfo(name = Asset.Fields.assetID))
public record Asset(
    String assetID,
    String color,
    int size,
    int appraisedValue,
    String owner) {}
```

### Key Mappers

Fabric keys are strings, so key-part mapping matters for sorting and range scans.

If numeric IDs are mapped with plain `toString`, lexical ordering can become semantically wrong.
For example, `9`, `10`, `11` become `"9"`, `"10"`, `"11"`, and lexical ordering puts `"10"`
before `"9"`.

Hypernate supports mapper classes to preserve ordering semantics. Available mappers include:

- `IntegerZeroPadder`
- `IntegerFlipperAndZeroPadder`
- `LongZeroPadder`
- `LongFlipperAndZeroPadder`
- `ObjectToString`

### Composite Keys

Hypernate supports ordered composite keys with multiple attributes.

```java
@FieldNameConstants
@PrimaryKey({
    @AttributeInfo(name = Asset.Fields.owner),
    @AttributeInfo(name = Asset.Fields.assetID, mapper = IntegerZeroPadder.class)
})
public record Asset(
    String owner,
    int assetID,
    String color,
    int size,
    int appraisedValue) {}
```

## CRUD Semantics with Registry

Use `Registry` through `HypernateContext` for object-oriented ledger access.

Methods use explicit semantics:

- `must*` methods fail with exceptions when invariants are not satisfied.
- `try*` methods return graceful outcomes for non-fatal paths.

```java
@Transaction(intent = EVALUATE)
public boolean AssetExists(final HypernateContext ctx, final String assetID) {
    return ctx.getRegistry().tryRead(Asset.class, assetID) != null;
}
```

```java
Asset toDelete = reg.mustRead(Asset.class, assetID);
ctx.getRegistry().mustDelete(toDelete);
```

## Middleware Chain

Hypernate middleware wraps `ChaincodeStub` behavior in reusable processors.

Common middleware includes:

- `LoggingStubMiddleware`
- `WriteBackCachedStubMiddleware`

You can chain middleware with `@MiddlewareInfo`:

```java
@MiddlewareInfo({
  LoggingStubMiddleware.class,
  WriteBackCachedStubMiddleware.class
})
public class MyBusinessContract implements HypernateContract
```

This allows call interception for logging, caching, and other cross-cutting behavior while
keeping business code focused.

> Hypernate context and middleware instances are specific to individual transaction executions.
