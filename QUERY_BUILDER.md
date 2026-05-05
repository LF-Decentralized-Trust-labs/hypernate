## Overview

Hypernate now exposes two explicit query-builder entry points on `Registry`:

- `richQuery(Class<T>)` for CouchDB rich queries built from selector JSON
- `rangeQuery(Class<T>)` for composite-key prefix scans built from `@PrimaryKey`

They are intentionally separate because they do not share the same consistency or execution model.
Rich queries are evaluated by CouchDB against committed world state, while range queries are backed
by Fabric composite-key scans that align with Hypernate's primary-key mapping rules.

Hypernate's query surface is split into two explicit API paths, `richQuery()` and
`rangeQuery()`, because the two backends have fundamentally different semantics, especially within
an active transaction. `WriteBackCachedStubMiddleware` buffers all writes until
`TransactionEnd`. A CouchDB rich query executed mid-transaction therefore operates against
committed ledger state, making any entity created or modified in the current transaction invisible
to the query. Composite key range scans share this limitation at the stub level. Separating the
two paths into named, documented API surfaces turns this behavior into an explicit contract rather
than a hidden footgun.

## Mid-Transaction Consistency Contract

`WriteBackCachedStubMiddleware` buffers `putState` and `delState` calls until transaction end.
That means a mid-transaction rich query executed through `stub.getQueryResult(...)` does not see
pending writes that still live in the middleware cache.

If a rich query is executed while the write-back cache contains dirty entries, Hypernate logs this
warning unless you explicitly opt in:

`Rich query executed with uncommitted writes in cache; results may not reflect pending transaction changes.`

Use `acknowledgeStaleReads()` when the calling code deliberately accepts that contract.

Range queries remain explicit and separate so callers do not accidentally assume they behave like
rich queries or share the same visibility semantics.

## RichQueryBuilder API Reference

`Registry.richQuery(Class<T>)` returns a `RichQueryBuilder<T>`.

Selector methods:

- `where(String field)`
- `and(String field)`
- `or(String field)`

Field operators:

- `is(Object value)` maps to `$eq`
- `isNot(Object value)` maps to `$ne`
- `greaterThan(Object value)` maps to `$gt`
- `greaterThanOrEqualTo(Object value)` maps to `$gte`
- `lessThan(Object value)` maps to `$lt`
- `lessThanOrEqualTo(Object value)` maps to `$lte`
- `in(Object... values)` maps to `$in`
- `notIn(Object... values)` maps to `$nin`
- `exists(boolean exists)` maps to `$exists`

Additional clauses:

- `sortBy(String field, SortOrder order)`
- `limit(int n)`
- `skip(int n)`
- `acknowledgeStaleReads()`
- `execute()`

All referenced fields are validated against the entity class before execution. Unknown fields cause
an `IllegalArgumentException`.

## RangeQueryBuilder API Reference

`Registry.rangeQuery(Class<T>)` returns a `RangeQueryBuilder<T>`.

Range-query methods:

- `whereKey(String primaryKeyField)`
- `execute()`

`whereKey(...).is(...)` accepts primary-key parts in the exact left-prefix order declared by
`@PrimaryKey`.

Rules:

- The first supplied key must match the first `@PrimaryKey` entry
- Each additional key must match the next declared `@PrimaryKey` entry
- Skipping an earlier primary-key component is invalid
- Using a field outside `@PrimaryKey` is invalid
- Attribute mappers declared in `@AttributeInfo(mapper = ...)` are applied before the partial
  composite key is sent to Fabric

## Usage Examples

```java
// Rich query example
List<Asset> results = registry.richQuery(Asset.class)
    .where("color").is("blue")
    .and("size").greaterThan(10)
    .and("owner").in("Alice", "Bob")
    .sortBy("value", SortOrder.DESC)
    .limit(50)
    .execute();

// Range query example
List<Asset> results = registry.rangeQuery(Asset.class)
    .whereKey("owner").is("Alice")
    .execute();
```

## Known Limitations

- CouchDB rich queries require suitable JSON indexes for production performance and, in some cases,
  for query planning to succeed.
- Range queries are limited to left-prefix scans over the declared `@PrimaryKey` hierarchy.
- Rich queries do not see uncommitted writes still buffered by `WriteBackCachedStubMiddleware`.
- Range queries are designed around Fabric composite keys, so their semantics differ from
  LevelDB-native ad hoc querying.
