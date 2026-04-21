# Hypernate - an entity framework for Hyperledger Fabric

If Fabric allows you to keep your familiar programming language, then Hypernate will allow you to _keep your familiar programming style._

No more low-level boilerplate code for key-value storage operations and other housekeeping tasks!
Take advantage of Hypernate’s _high abstraction level,_ _aspect-oriented_ approaches and _extensibility_ to keep your critical business logic as clean as possible!

Enhance your chaincode with features, like:
* Object-oriented CRUD (create, read, update, delete) operations with explicit semantics
* Declarative and flexible configuration of your entity keys
* An extensible chain of middleware processors handling non-business stuff (caching, logging, tracing, etc.)

Get access to all these features using a simple incantation:
```java
public class MyBusinessContract implements HypernateContract
```

And more features are on the way, so spoiler ahead:
* Partial and range queries based on non-key attributes
* Range query support for composite keys
* Overall friendlier query support
* OpenTelemetry integration
* Support for data schemas
* …



## User Guide

For complete examples, please refer to the [hypernate-samples](https://github.com/LF-Decentralized-Trust-labs/hypernate-samples) repository.
The following sections introduce the individual features only in an isolated manner.

The gist of using Hypernate features is the following:
1. **Include Hypernate** library in your project as a dependency.
   As of now, you either have to build the jar yourself, or use the pre-built one from the [hypernate-samples](https://github.com/LF-Decentralized-Trust-labs/hypernate-samples) repository.
2. **Use Hypernate annotations** on your entities (DTOs or POJOs), so the library can process them.
3. **Use `HypernateContract`** as base class for your business `Contract` implementation to easily gain access to everything Hypernate provides, including its _registry_ and _middleware_ features.
4. **Use the `Registry`** class to handle the annotated entities in a friendly way.
   The easiest way is to use `HypernateContext` as your transaction `Context` implementation; it takes care of the boring stuff for you.


### Declarative entity keys

#### Declaring your primary key

Why would you mix Fabric-related storage information with your business data? Keep them close - but separated - using the `PrimaryKey` attribute!

The following code snippet:
* Uses Hypernate’s `PrimaryKey` annotation to declare a composite key for the entity using an _ordered list_ of `AttributeInfo` parts.
* (Optional) Uses `lombok`’s `FieldNameConstants` annotation, so you can reference field names in a type-safe way!

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

#### Polishing your primary key with mappers

In the end, Fabric expects a string value as an entity key and the previous example used a string valued attribute as key (part), so all is good (hopefully).
But what if our ID-like attributes are not strings?
For example, you would like to use a monotonic counter as entity ID (or just part of it).
You might ask: “Why doesn’t Hypernate just `toString` it?”

Let’s see what happens when we `toString` a few ID-like numbers:
* Original number sequence: `9`, `10`, `11`
* `toString` results: `"9"`, `"10"`, `"11"`
* Lexicographically ordered keys in Fabric: `"10"`, `"11"`, `"9"`

The issue is evident: the keys do not retain their business semantic (their order) and we lose nice Fabric features like range or partial queries.
Well, they are still there, but might produce **semantically incorrect** results if the business logic depends on the enumeration order of keys!

How would you solve this problem? By a smarter `toString` implementation, of course!
The new implementation should produce the following, order-friendly strings (or something like that):
* `"009"`, `"010"`, `"011"`, …

Generalizing this idea, Hypernate gives you the opportunity to declare attribute value **mappers** to manipulate an attribute value before it is used in key construction.

The following code snippet:
* Uses Hypernate’s `PrimaryKey` annotation to declare a composite key for the entity using an _ordered list_ of `AttributeInfo` parts.
* Declares the class `IntegerZeroPadder` as the mapper for the attribute value to retain the correct ordering of resulting key part strings.
* (Optional) Uses `lombok`’s `FieldNameConstants` annotation, so you can reference field names in a type-safe way! 

```java
@FieldNameConstants
@PrimaryKey(@AttributeInfo(name = Asset.Fields.assetID, mapper = IntegerZeroPadder.class))
public record Asset(
    int assetID,
    String color,
    int size,
    int appraisedValue,
    String owner) {}
```

Currently, the following mapper classes are available in the `hu.bme.mit.ftsrg.hypernate.mappers` package (but feel free to implement and use your own):
* `IntegerZeroPadder`: Pads numbers with `"0"`s to the length of `"2147483647"`, the maximum integer value.
* `IntegerFlipperAndZeroPadder`: flips the range of positive integers before padding them to their max length.
  Useful for constructing descending string key orders from originally ascending integer keys (in case you want to enumerate them in reverse order).
* `LongZeroPadder`: Pads numbers with `"0"`s to the length of `"9223372036854775807"`, the maximum long number value.
* `LongFlipperAndZeroPadder`: flips the range of positive long numbers before padding them to their max length.
  Useful for constructing descending string key orders from originally ascending long number keys (in case you want to enumerate them in reverse order).
* `ObjectToString`: simply call `toString` on the attribute value (the default behavior)

#### Using multiple attributes as key parts

Fabric composite keys can be defined using multiple attribute values, as often necessitated by the business logic handling complex entities.
Naturally, Hypernate also supports such declarations!

The following code snippet:
* Uses Hypernate’s `PrimaryKey` annotation to declare a composite key for the entity using an _ordered list_ of `AttributeInfo` parts:
  * Uses Hypernate’s `AttributeInfo` annotation to declare the first composite key part as the `owner` attribute value, because we would like to run partial queries based on this attribute value of each asset.
  * Uses Hypernate’s `AttributeInfo` annotation to declare the second composite key part as the `assetID` attribute value.
    * Declares the class `IntegerZeroPadder` as the mapper for the attribute value to retain the correct ordering of resulting key part strings.
* (Optional) Uses `lombok`’s `FieldNameConstants` annotation, so you can reference field names in a type-safe way! 

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

> [!CAUTION] 
> This key space design means that you must know **both** the `owner` and `assetID` values to access (for example, read or delete) an asset on the ledger.
> This is not necessarily optimal, we only did this to support partial queries for the asset.
> The upcoming **query index definition** capability of Hypernate will solve this problem by automatically managing “query-enabling” key spaces separately from primary key definitions.
> _So stay tuned for exciting new features!_


### CRUD operations

Use an object-oriented `Registry` through the enhanced `HypernateContext` and access your entities easily!
The CRUD operations have their semantics encoded in their names for cases when you really _must_ perform an operation, or when you just want to _try_ to perform something, and gracefully (and intuitively!) handle when it cannot be done.

The following code snippet shows:
* How to use the `Registry` via `HypernateContext` to access entities in a strongly-typed way.
  No byte arrays, `toString` calls, or JSON parsing!
* How to allow operations to fail gracefully, i.e., just _trying_ to execute them (in the case below it is not a fatal business error if the asset does not exist).
  No more remembering what the Fabric chaincode SDK returns for non-existing assets (is it `null` or an empty array??)!

```java
@Transaction(intent = EVALUATE)
public boolean AssetExists(final HypernateContext ctx, final String assetID) {
    return ctx.getRegistry().tryRead(Asset.class, assetID) != null;
}
```

On the other hand, the following code snippet shows how to express that the operation _must_ be performed on this entity successfully.
By using the _must_ semantics, an exception will be thrown if the asset does not exist.
No more cluttering `if-else` or `try-catch` blocks for every ledger access operation!

```java
Asset toDelete = reg.mustRead(Asset.class, assetID);
ctx.getRegistry().mustDelete(toDelete);
```


### Middleware

There are some application tasks that are not closely related to the business logic, but must be performed nevertheless, and these are typically repeated from application to application.
The systems engineering world extracted these repeating tasks and packaged them into self-contained _middleware._
Middleware processors are fully functional services that are usually application-independent, thus reusable across applications. 

Hypernate also identified some repeating, application-independent tasks around the Fabric `ChaincodeStub` that might be handy across different projects.
What’s more, you can chain more middleware processors together, similarly to web server middleware!
Cherry-pick your middleware components to easily shape the feature set of your chaincode.

Currently, the following middleware processors are available (with more on the way!):
* `LoggingStubMiddleware`: wraps popular ledger access operations with logging, so you always know what’s happening between your business logic and ledger.
* `WriteBackCachedStubMiddleware`: implements caching of raw ledger entries to lower the traffic between the chaincode and the peer, and also to support the _read-your-own-write_ data access semantic. 

The following code snippet shows:
* How to use the `MiddlewareInfo` annotation to construct an _ordered list_ (i.e., a chain) of middleware processors. 
* How to use the `HypernateContract` base class to automatically take care of processing the annotations and building the corresponding chain.

```java
@MiddlewareInfo({
  LoggingStubMiddleware.class,
  WriteBackCachedStubMiddleware.class
})
public class MyBusinessContract implements HypernateContract
```

The above declaration will result in two ChaincodeStub-like components intercepting every call you make to the Fabric stub, _first_ adding some logging functionality, _then_ checking the cache for the entries you want to access.
So it is possible that the original stub won’t even get the call, it is served from the local cache.

> [!IMPORTANT] 
> Hypernate context and middleware instances are specific to your individual TX executions/endorsements!
> Hypernate does not introduce dependencies between TXs, following the traditional (and important!) Fabric chaincode development practice.



## Developer Guide

The preferred way of contribution is:

1. Fork the repository;
2. Create a branch with a meaningful name;
3. Make your changes using [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/#summary);
4. Push the branch to your fork;
5. Create a pull request.



## Referencing this work

Please use the following information when you use or reference this project (or the related [research paper](https://doi.org/10.21203/rs.3.rs-4606405/v1)) in your own work:

Text form:

Damaris Jepkurui Kangogo, Bertalan Zoltán Péter, Attila Klenik, Imre Kocsis. _Practical runtime verification of cross-organizational smart contracts_, 11 July 2024, PREPRINT (Version 1) available at Research Square [https://doi.org/10.21203/rs.3.rs-4606405/v1]

BibTeX:
```
@unpublished{kangogo2024practical,
  title={Practical runtime verification of cross-organizational smart contracts},
  author={Kangogo, Damaris Jepkurui and Péter, Bertalan Zoltán and Klenik, Attila and Kocsis, Imre},
  year={2024},
  note={Preprint at \url{https://www.researchsquare.com/article/rs-4606405/latest}},
  doi={10.21203/rs.3.rs-4606405/v1}
}
```



## License

Hypernate uses the _Apache License Version 2.0_.
For more information see [NOTICES](NOTICES.md), [MAINTAINERS](MAINTAINERS.md), and [LICENSE](LICENSE).
