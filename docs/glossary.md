# Glossary

middleware
:   A reusable processing component that intercepts requests or responses to apply cross-cutting behavior such as logging, caching, authentication, or validation.

registry
:   A typed access layer that provides CRUD-style operations over entities and coordinates key construction and storage access.

composite key
:   A key made from multiple ordered parts, used together to uniquely identify an entity and support partial or range-style queries.

shim
:   The adapter layer between Hypernate abstractions and the underlying ledger API, translating framework operations into low-level storage calls.

entity
:   A domain object that is persisted on the ledger and managed through Hypernate annotations, key metadata, and registry operations.

handler
:   The business-logic function or transaction method that processes a request after middleware responsibilities are applied.

pipeline
:   The ordered sequence of middleware and handlers that a request traverses from entry to final response.
