# Architecture

The middleware chain processes each request in order, adding cross-cutting behavior before invoking the business handler.

```mermaid
flowchart LR
    client[Client Request] --> auth[Auth Middleware]
    auth --> validation[Validation Middleware]
    validation --> ratelimit[Rate-Limit Middleware]
    ratelimit --> handler[Handler]
    handler --> response[Response]
```

The entity lifecycle routes CRUD operations through Hypernate components before data reaches persistent storage.

```mermaid
flowchart LR
    client[Client] -->|create| registry[Registry]
    registry -->|create| middleware[Middleware]
    middleware -->|create| shim[Shim]
    shim -->|create| storage[Storage]

    client -->|read| registry
    registry -->|read| middleware
    middleware -->|read| shim
    shim -->|read| storage

    client -->|update| registry
    registry -->|update| middleware
    middleware -->|update| shim
    shim -->|update| storage

    client -->|delete| registry
    registry -->|delete| middleware
    middleware -->|delete| shim
    shim -->|delete| storage
```
