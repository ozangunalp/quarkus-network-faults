# Dev mode experience: agents writing tests, agents driving the app, the card

Date: 2026-10-16

## Intent

The core of the extension, an in-process proxy that makes a real client library fail the way it
would in production, deterministically, inside a `@QuarkusTest`, is sound and stays as it is.
This work makes the extension more appealing to use, in three places:

1. **Agents writing tests.** The main functional benefit. An AI agent asked to make sure an
   application behaves as expected when a dependency fails should be able to write the
   `@QuarkusTest` that proves it, with this extension, without reading the reference docs end
   to end. It gets one guide written for it and, from the running application, an inventory of
   where the application believes it is protected and a list of what it reaches out to.
2. **Agents driving the app.** The Dev UI's JSON-RPC service becomes a set of Dev MCP tools, so
   an agent can run an experiment against the dev-mode application, apply a fault, call the
   endpoint, see what happened, and then write the test that pins what it saw. A playbook tells
   it how. The experiment is a means; the test is the deliverable.
3. **The card.** For the developer who wants to see things, the Dev UI card grows from a target
   table into a console as polished as Goblin's: every fault the API offers on a whole target,
   the HTTP requests a target saw, a history of faults across all targets, and a report export.

Nothing generates test code. The extension gives an agent the knowledge and the observations;
the agent writes the test.

This came out of a comparison with [quarkus-goblin](https://github.com/quarkiverse/quarkus-goblin),
whose Dev UI console, Dev MCP tools, agent playbook and resilience inventory are the prior art.
Goblin injects faults from Java code inside the application; this extension injects them on the
wire. What is borrowed is the experience around the faults, not the faults.

## Goals

- An agent in a user's repository, with or without the app running, has a single document that
  tells it how to write a test with this extension, and that document is the same text on the
  docs site and as a Dev MCP resource.
- An agent connected to `/q/dev-mcp` can list the targets, apply and end a fault, drop
  connections and read what happened, from tool descriptions alone, and knows from them to read
  the guide and the playbook first.
- A developer at the card can apply every fault the test API offers on a whole target or on some
  of its requests, see every request that went through a target, see every fault applied since
  the app started and what it did, and export a report.
- The proxy and the test API stay as they are, with two additions this work needs and the tests
  profit from too: `HttpRequestRecord` gains the response status, and the fault tracker gains an
  internal listener so dev mode can observe faults as they start and end.

## Non-goals

- Generating test code: no tool, card cell or report that renders Java. (The user's decision.)
- A receiver per target contributed by each module: it existed only to render Java. The guide
  explains how a test addresses each kind of target, and `getTargets` names the configuration
  property, which is enough for an agent to find the bean.
- Random, percentage or clock-driven faults. The README rejects them on purpose; `forFirst`,
  `every` and `after` are the deterministic form of the same scenarios.
- Persisting Dev UI state across restarts, saved scenarios, an auto-off timer. A fault in dev
  mode is visible in the card and ends with the process.
- Faults on the application's own endpoints, beans or message consumers. That is Goblin's job
  and the proxy cannot do it.
- Metrics or tracing modules. The event log and the report serve that purpose here.
- The timeout audit. It stays a README roadmap item.
- `@QuarkusIntegrationTest` support, TLS, HTTP/2: unchanged limitations.

## Anti-goals

- A Dev MCP tool that can break the running application enabled by default. Tools that apply
  faults are opt-in, as Goblin does it; read-only tools and "end all faults" are on by default.
- A guide or playbook that duplicates the reference docs. They say what the reference does not:
  the shape of a good test, which fault proves which annotation, the order of operations, what
  the guard failure means, what mistakes agents make.
- A playbook that lets an experiment be the end. Every loop ends with "write the test".

## Constraints

- Quarkus `4.0.0.Beta1`. `io.quarkus.runtime.annotations.DevMCPEnableByDefault` and
  `io.quarkus.runtime.annotations.JsonRpcDescription` exist there. The MCP server is
  `/q/dev-mcp`, enabled by the user with `quarkus.dev-mcp.enabled=true`; tool opt-in is
  remembered per machine in `~/.quarkus/dev-mcp.properties`.
- Dev MCP resources are `CardPageBuildItem.addBuildTimeData(name, value, description, true)`.
  The resource name must not contain an underscore. The description is what the agent reads to
  decide whether to open it.
- One source for every document that is both a docs page and a jar resource: the Markdown lives
  under `docs/modules/ROOT/examples/`, the deployment `pom.xml` copies it into the jar, and the
  docs include it with `include::{examples-dir}...[]`, as Goblin does.
- Project conventions apply: constructor injection, no lambdas or streams unless clearer, no
  one-line Javadoc, no unexplained constants, docs in the Quarkus LangChain4j style, hand-drawn
  SVG for any figure. The API is unreleased, so the JSON-RPC shapes may change freely.
- The dev-mode code lives in `core/runtime-dev` and `core/deployment`; `core/runtime` gets the
  listener and the response status noted under Goals, nothing else changes outside core.

## Part 1: agents writing tests

### The guide

`quarkus-network-faults_testWritingGuide`: Markdown, served as a Dev MCP resource with a
description that says when to read it, "before writing or changing a test that uses Quarkus
Network Faults", and included in the `agents.adoc` docs page. Contents, in order:

1. The dependency to add (`quarkus-network-faults-junit`, test scope) and the test shape from
   the README: `@QuarkusTest`, `@TestProfile(NetworkFaultsProfile.class)`, `@Inject
   NetworkFaults`, try-with-resources around the fault, assert the failure inside the block and
   the recovery after it.
2. How to address each kind of target, the table from `targets.adoc` compressed to one line per
   kind: by bean type and qualifier, by REST Client interface, by method reference for one
   method's requests, by `http().requests(method, path)` for any plaintext HTTP target. How to
   go from a target name and its configuration property, as `getTargets` lists them, to the
   bean: the config key of a REST Client is its interface's `configKey`, a datasource name is
   its qualifier, and so on.
3. Which fault for which scenario and which annotation: the table from
   `guide-fault-tolerance.adoc` with a "what the client sees" column from `faults.adoc`, in the
   order a human would choose them: `unavailable`, `hangs`, `slow`, `respondsWith`,
   `diesMidRequest`, `closes`.
4. Some of the calls: `forFirst`, `every`, `after`, with the retry example.
5. Observing: `traffic()`, `HttpRequestRecord` including the response status, `awaitConnections`,
   `Attempts`, `Stopwatch`, each with the one-line use case.
6. The guard: what its failure message means and the three usual causes.
7. Timeouts: the three defaults that most often decide what a test sees (REST Client read
   timeout, Agroal acquisition timeout, Vert.x connect timeout), and the advice to set the
   application timeout in the test profile rather than wait for the library default.
8. Mistakes agents make, each with the fix: the fault on a bean the request does not go
   through; an HTTP fault on a datasource; a second fault on a target that has one; asserting
   on a response time without slack; forgetting that pools keep connections, so `hangs` after a
   warm request needs `dropConnections()` first; a flaky test with a random or timed fault
   instead of `every(n)`; asserting a status the agent did not observe.

### The inventory resource

`quarkus-network-faults_inventory`: JSON built at build time, where this application believes
it is protected. Targets are not in it: they are only concrete at runtime, so the description
sends the agent to `getTargets` for them.

```json
{
  "guardedMethods": [
    {
      "class": "org.acme.OrderService",
      "method": "place",
      "annotations": {
        "Timeout": { "value": 2000, "unit": "MILLIS" },
        "Fallback": { "fallbackMethod": "placeLater" }
      },
      "injects": ["org.acme.InventoryClient", "javax.sql.DataSource"]
    }
  ],
  "notes": ["Values are the ones declared in the code: MicroProfile Config overrides and annotations inherited from a superclass are not reflected."]
}
```

- `guardedMethods` is Goblin's inventory shape: the application's own classes (root archive)
  scanned with Jandex for `@Timeout`, `@Retry`, `@CircuitBreaker`, `@Fallback`, `@Bulkhead` and
  SmallRye `@RateLimit`, class-level merged under method-level, declared values merged over the
  specification defaults so the agent reads the effective value.
- `injects` lists the types the declaring class injects through a field or a constructor
  parameter, keeping those a test can address: a REST Client or GraphQL client interface, an AI
  service, or one of the bean types the Targets reference lists. It is a hint, named as such in
  the description, not a call graph. Each one is what a test passes to `target(...)`.
- An application with no guarded method gets an empty `guardedMethods` and a note, never a
  missing resource.

The inventory is what turns "write resilience tests for this app" into a list: one guarded
method, one injected target, one fault that proves the annotation, one test.

## Part 2: agents driving the app

### Tools

The existing `NetworkFaultsJsonRPCService` methods, each annotated with `@JsonRpcDescription`
on the method and on every parameter, written for an agent: what it does, what the arguments
accept, what it returns, and what to call next.

| Tool | Default | Notes |
|---|---|---|
| `getStatus` | on | Adds `faultsActive` (count) and a `hint` naming the guide, the playbook and the inventory, so an agent that starts here is told what to read first, and saying which tools are opt-in and how the developer enables them (Dev UI, Settings, Dev MCP tab, Tools). |
| `getTargets` | on | One entry per target: name, source property, upstream, proxy port, active fault, counters, connections. Adds `kind` (the name's family) and `plaintextHttp` (whether HTTP faults and moments apply), so the agent does not try `responds-with` on a datasource. |
| `getEvents` | on | Unchanged shape; the description explains the event types. |
| `getRequests` | on | New: the HTTP requests a target saw: time, connection, method, path, headers, body fingerprint, response status and time. Empty with a `reason` for a target that is not plaintext HTTP. |
| `getHistory` | on | New, see Part 3. |
| `getFaults` | on | The accepted fault specs, each with a one-line meaning. |
| `getReport` | on | New, see Part 3. |
| `resetAll` | on | "End every fault": the kill switch, so it stays on by default. |
| `apply` | opt-in | `apply(target, fault, requests?, body?, contentType?)`: the fault spec stays the fault alone; the scope to some requests (`"POST /inventory/reserve"`) and the body and content type of a `responds-with` are separate optional parameters. Without a scope it ends the target's faults first, so switching is one call; with a scope it ends nothing. |
| `reset` | opt-in | Ends one target's faults, through the tracker so the history closes them. |
| `dropConnections` | opt-in | |

Errors come back as JSON-RPC errors carrying the test API's own messages, which already name
the target and the property.

### The playbook resource

`quarkus-network-faults_agentPlaybook`: Markdown, the same text for every application. Its
description tells the agent to read it before a first experiment. Contents:

1. What the extension is in two sentences, and what an experiment is for: to find out what the
   application does, so that a test can assert it.
2. Safety rules: one fault per whole target at a time and the API enforces it; end every fault
   when done, `resetAll` is the way; do not work around an opt-in tool, ask the developer to
   enable it; read the event log before it rolls; a fault no request exercises proves nothing.
3. Which fault proves which annotation: the same table as the guide, by spec name.
4. The loop: read the inventory and `getTargets`, form a one-sentence hypothesis, `apply` one
   fault to one target, call the endpoint yourself and record status, body and elapsed time,
   read `getEvents` and `getRequests` to confirm the fault was exercised, conclude held / did
   not hold / inconclusive, end the fault, **then write the test** with the guide: the same
   target, the same fault, the status observed inside the block and after it.
5. How to tell the fault was not exercised: zero connections seen while it was active, or
   `upstreamConnectFailures` rising, with the guard's two explanations.
6. The report: `getReport`, and what to put in a write-up for the developer: each hypothesis,
   what was observed, the verdict, and the tests written.
7. A pointer to `RESILIENCE.md` in the project root for the promises the extension cannot know.
   The template, adapted from Goblin's to targets rather than layers, ships under
   `docs/modules/ROOT/examples/`.

### Docs

A new reference page, `agents.adoc`, under Reference in the nav: writing tests with an agent
(the guide, included), enabling Dev MCP, the default and opt-in split, the three resources, a
typical session, the playbook (included), the `RESILIENCE.md` template (included). The
`dev-ui.adoc` page links to it.

## Part 3: the card

The card keeps its target table and grows four things. Layout is left to the builder within
these constraints: Vaadin components the Dev UI already ships, the 2-second refresh that exists
today, and one page unless the history clearly needs its own tab (Goblin gives it one).

1. **Every fault from the API, on a whole target.** The fault drop-down gains
   `responds-with:<status>`, and for it an optional body and content type. Faults on some
   requests: a free-text field next to the fault, `POST /inventory/reserve`, passed to `apply`
   as its `requests` parameter. Latency with jitter, bandwidth and slicing under `advanced()`
   stay API-only (design call: rare in dev mode, and the drop-down would double).
2. **Requests.** Next to *Events*, a *Requests* button that opens the HTTP request list of a
   target: time, method, path, status, response time, with the headers and body fingerprint
   expandable. Disabled, with the reason as a tooltip, for a target that is not plaintext HTTP.
3. **History.** A fault log across all targets, newest first: when, target, fault, source
   (card or API), connections and requests seen while it was active, when it ended and whether
   traffic went through it, which is the dev-mode form of the guard. Filter by target and by
   fault kind; a summary line with the count of faults applied and the count that saw no
   traffic. Capped like the event log, with the cap named and explained in code. Backed by a
   `core/runtime-dev` structure that subscribes to a listener added to the fault tracker in
   `core/runtime` (internal, not API, no change to proxy behaviour), so it sees faults applied
   from the card, from an MCP tool and from application code alike, and the verdict is the
   guard's own.
4. **Report.** *Export report* renders Markdown: the targets and their properties, the history,
   and the event counts per target; copy or download. It is `getReport` on the JSON-RPC side,
   so an agent gets the same text. Its purpose is a paste into an issue, a pull request or an
   agent conversation.

## Build order and verification

1. The response status in `HttpRequestRecord`. Verify: the proxy unit tests.
2. `FaultSpec` parsing for `responds-with`, the request scope, the body. Verify: unit tests.
3. Tools and descriptions, `getRequests`, `getTargets` fields, Dev MCP annotations. Verify: the
   dev-mode JSON-RPC tests; an MCP client against `/q/dev-mcp` lists the default tools and not
   the opt-in ones.
4. The listener, the history and the report. Verify: a core unit test for the listener, a unit
   test for the report, a dev-mode test for history and report.
5. The inventory build step. Verify: a build-time-data test with a guarded class, and the
   empty-application case.
6. The guide, the playbook, the template, the `agents.adoc` page, the pom copy. Verify: the
   resources are served; the docs build.
7. The card. Verify: by hand in `integration-tests/rest-client-and-datasource` in dev mode, a
   screenshot for the docs and a GIF for the README.
8. README: the card and the agents paragraphs, the GIF, the sentence placing the extension
   next to Goblin.

## Left to the builder

- Internal shapes of the history structure and the report renderer.
- Card layout and whether History is a second tab.
- Exact wording of every tool and parameter description, within the rule that an agent must be
  able to use the tool from the description alone.
- How `injects` decides which injected types are worth listing, within the rule above.
