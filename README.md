# Quarkus Network Faults

[![Version](https://img.shields.io/maven-central/v/io.quarkiverse.networkfaults/quarkus-network-faults?logo=apache-maven&style=flat-square)](https://central.sonatype.com/artifact/io.quarkiverse.networkfaults/quarkus-network-faults)

Quarkus extension that lets a `@QuarkusTest` verify what a request does when a
dependency is down, unreachable, slow, hung, or dies mid-call. No Docker, no external tooling:
an in-process Vert.x proxy is placed in front of every outbound connection the configuration
describes, and tests apply faults to it. Resilience is tested where it lives, in each service's
own test suite, instead of in an end-to-end environment where failures are hard to produce.

## Why this extension?

Modern software increasingly depends on external services accessed over the network. While Quarkus abstractions make it remarkably easy to build such distributed systems, a critical question remains: how can developers ensure their applications handle more than just the happy path?

In production environments, external services can be unreliable—they may be slow, unresponsive, or fail entirely. Understanding how your application behaves under these conditions is essential for building resilient systems. This extension was created to make it trivial for developers to test and validate their Quarkus applications against real-world network failures, without the complexity of external tooling or infrastructure.

## What a Test Looks Like

```java
@QuarkusTest
@TestProfile(NetworkFaultsProfile.class)
class OrderResourceTest {

    @Inject
    NetworkFaults faults;

    @Test
    void inventoryServiceHangs() {
        try (var ignored = faults.target(InventoryClient.class).hangs()) {
            when().post("/orders").then().statusCode(503);   // the @Timeout fallback
        }
        when().post("/orders").then().statusCode(201);       // recovered, breaker reset
    }
}
```

For this test, add `quarkus-network-faults-junit` as a test dependency. It provides
`NetworkFaultsProfile` and brings in `quarkus-network-faults` transitively, so a single
dependency is enough. The extension automatically includes fault injection support for the
supported integrations already present in your application; no integration-specific Network Faults
dependencies are needed.

Read the [documentation](https://docs.quarkiverse.io/quarkus-network-faults/dev/index.html): a
quick start, guides for a failing REST dependency, a failing database, SmallRye Fault Tolerance,
streamed responses, Quarkus LangChain4j models and MCP servers, and the reference for the faults, the targets
and the timeout defaults these tests tend to surface. The sources are in `docs/modules/ROOT/pages`.

## Requirements

- Quarkus `4.0.0.Beta1` or later
- Java 21 or later

## What it covers

Targets are discovered from the application's configuration, Dev Services included, and a
test addresses one by the bean the application injects:

- JDBC datasources, `target(DataSource.class)`, and reactive ones, `target(Pool.class)`;
  named datasources by their qualifier.
- REST Clients by interface, whether the URL comes from configuration or from
  `@RegisterRestClient(baseUri)`.
- Redis clients, `target(RedisDataSource.class)` or the Vert.x clients, standalone mode.
- MongoDB clients, `target(MongoClient.class)` or the reactive client, standalone servers.
- SmallRye GraphQL clients, typesafe ones by interface, dynamic ones by name.
- gRPC clients by stub or service type plus `GrpcClient.Literal.of(name)`.
- Kafka, by emitter or client bean; the bootstrap connection only, see below.
- Quarkus LangChain4j models, `target(ChatModel.class, ModelName.Literal.of(name))`, and the
  AI services that use them; exercised against OpenAI, Ollama and Anthropic. The chat memory
  store and the embedding stores, `target(ChatMemoryStore.class)` and
  `target(EmbeddingStore.class)`, resolved to the Redis, MongoDB or datasource client behind
  them, Chroma by its own URL. MCP servers, `target(McpClient.class, McpClientName.Literal.of(name))`,
  over the streamable HTTP transport.
- Hibernate ORM and Hibernate Reactive persistence units, `target(EntityManager.class)` or
  `target(Mutiny.SessionFactory.class)`, and Quarkus Data or Jakarta Data repositories, all
  resolved to the datasource behind them.

What a test can do with a target:

- Faults: `unavailable`, `connectTimeout` (Linux only), `hangs`, `slow`, `diesMidRequest`,
  `closes`, `dropConnections`, plus latency with jitter, bandwidth and slicing under
  `advanced()`, and `http().respondsWith(status)`. `diesMidRequest` (a reset), `closes` (a clean close) and `hangs` take a moment: on the client's next write, or in the response after n
  bytes, after the headers, after n chunks or after n Server-Sent Events, for tests of a stream
  cut, ended early or stalled half-way.
- `http().respondsWith(status)` answers every request with a synthetic HTTP response instead of
  relaying it, so a REST Client sees the remote API fail with a chosen status; a body, content
  type and headers can be added, or a body of any length streamed with `bodyOfLength(n)` for the
  unbounded result set. Plaintext HTTP/1.x only.
- Some of the requests, by count: `http().forFirst(n)`, `every(n)` and `after(n)` select requests
  in the order they arrive, and any fault follows: a transient failure a `@Retry` recovers from,
  one call in n failing, one call in ten slow (tail latency). Deterministic: the test sends the
  calls, so it knows which ones fail.
- A fault belongs to the target it was applied to; other beans of the same type, another
  datasource or another REST Client, keep working.
- A fault can act on some requests only: `target(InventoryClient::reserve)` names the requests
  one REST Client method sends, and `http().requests("POST", "/embeddings")` those of any
  plaintext HTTP target. The other requests, even on the same connection, are relayed untouched;
  faults on different requests of one target are active together, and each such endpoint counts
  its own requests with `traffic()`.
- Consequences are undone when a fault ends and after every test: Agroal pools flushed,
  SmallRye Fault Tolerance circuit breakers reset.
- A guard fails a test whose fault no connection went through, since such a test cannot have
  tested the fault.
- Observation: connections live and seen, bytes each way, HTTP/1.x requests counted and
  listed with their headers, a body fingerprint and response time, so a test can check that every
  retry carried the same idempotency key and body, and a bounded event log per target.
  `awaitConnections(n, timeout)` waits for a pool to fill before the request that should not get
  a connection; `Attempts` and `Stopwatch` in the JUnit module assert the shape of a retry policy
  and the timing of a call.
- In dev mode, the "Network faults" Dev UI card lists every target and applies and ends
  faults, drops connections and shows the event log.

## Known limitations

- Kafka: only the bootstrap connection is proxied; the client reconnects to the broker's
  advertised address. Fixing this needs the Kafka Dev Services launchers to advertise a
  reserved proxy port. `integration-tests/kafka` pins the limitation.
  Redis in cluster or sentinel mode and MongoDB replica sets bypass the proxy the same way;
  standalone servers are fine.
- `connectTimeout()` is Linux only.
- TLS passes through but certificates must accept `127.0.0.1`; request counting needs
  plaintext HTTP/1.x.
- No `@QuarkusIntegrationTest` support

## Where this could go next

Measured against the failure modes in _Release It!_ and _Building Resilient Distributed Systems_,
the extension covers the connection-level and HTTP-level faults a single dependency can produce.
What it does not cover yet, in the order we would tackle it; each is a self-contained piece of
work, and contributions are welcome:

- **TLS and HTTP/2.** Over `https://` the proxy relays bytes it cannot read, so request counting,
  `respondsWith`, the HTTP moments and faults on one method are unavailable, and so are gRPC
  status errors such as `UNAVAILABLE`. The plan is to terminate TLS in the proxy with a
  certificate from a named configuration in the Quarkus TLS registry, reconnect to the upstream
  with the client's own TLS configuration, and point the client at the proxy's configuration, so
  the application's TLS setup is not changed, only re-pointed. HTTP/2 then needs a frame-aware
  framer where `HttpFramer` follows HTTP/1.x today.
- **DNS failures.** The rewrite points the client at `127.0.0.1`, so a host that does not resolve,
  or resolves slowly, cannot be produced. If the rewrite pointed at a synthetic hostname instead,
  `inventory.faults.test:41234`, resolution becomes a handle: a resolver shipped by the extension
  answers it with the loopback address, and a fault makes that resolution fail or stall. Java 18+
  has `InetAddressResolverProvider` for the clients that resolve through the JDK (JDBC, Kafka,
  MongoDB); the Vert.x-based clients (REST Client, Redis, gRPC) use Vert.x's own resolver, and
  how to point it at the same answers is the open question. Caches matter too: the JDK caches
  lookups and pools never re-resolve.
- **Messaging beyond the bootstrap connection.** Kafka clients learn the brokers' addresses from
  the cluster and connect to them directly, so only the first connection is proxied. Faulting a
  broker needs either the advertised addresses rewritten on the way through, which means reading
  the Kafka protocol, or a proxy per broker whose address the client is told through
  `advertised.listeners` of a Dev Services broker. Redis cluster and sentinel and MongoDB replica
  sets have the same shape.
- **A timeout and limits audit.** Each module knows the configuration key of its clients, so it
  could read the connect, read and acquisition timeouts and the pool sizes in effect, flag the
  ones left at a library default in the Dev UI and in the guard's message when a `hangs` test runs
  for exactly that default, and let a test read a pool's size instead of hard-coding it. Getting
  every library's defaults right across nine extensions is the work.
- **Load-driven patterns.** Bulkheads, load shedding and the thundering herd need many requests
  at once. The extension supplies the slow or recovering dependency and `traffic()` shows the
  retry storm; a guide that drives the load and reads the timestamps back would show the pattern,
  and a helper that asserts on request timing may follow from it.
- **Faults on the request side.** Everything today acts on the response or the connection. A
  fault that alters the request before it reaches the upstream, an extra header, a truncated body,
  would test how the dependency copes rather than the application, which may be a different tool.
- **Other protocols.** Database wire protocols are relayed opaquely, so a datasource takes the
  connection faults only. A framer for the PostgreSQL protocol would allow "the query was sent,
  the result never came" to be told from "the connection never opened", the way the HTTP moments
  do for a REST call.

Faults that fire at random or on a clock were considered and left out on purpose: a test that
fails one run in twenty is worse than no test. `forFirst`, `every` and `after` give the same
scenarios a deterministic shape.

## Build

`./mvnw install`. Tests need Docker for Dev Services.
