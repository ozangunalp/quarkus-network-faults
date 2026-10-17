# Dev Mode Experience Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give AI agents what they need to write resilience tests with this extension (a guide, an inventory, Dev MCP tools and a playbook) and grow the Dev UI card into a console with requests, history and a report.

**Architecture:** `FaultSpec` in `core/runtime-dev` parses the textual fault the card and the tools send, now with `responds-with`, a request scope and a body. A listener added to `FaultTracker` feeds a bounded history in `core/runtime-dev`, which the JSON-RPC service, the card and the Markdown report all read. The guide, the playbook and the build-time inventory are served as Dev MCP resources from `core/deployment`, the Markdown kept under the docs so the page and the resource are one file.

**Tech Stack:** Quarkus 4.0.0.Beta1 (Dev UI, Dev JSON-RPC, Dev MCP), Jandex, Vert.x proxy (existing), Lit + Vaadin web components for the card, JUnit 5, `QuarkusDevModeTest` + `DevUIJsonRPCTest` / `DevUIBuildTimeDataTest` for dev-mode tests, Antora docs.

**Spec:** `docs/superpowers/specs/2026-10-16-dev-experience-design.md`

## Global Constraints

- Quarkus `4.0.0.Beta1`, Java 21 or later. `io.quarkus.runtime.annotations.JsonRpcDescription` (targets METHOD and PARAMETER) and `io.quarkus.runtime.annotations.DevMCPEnableByDefault` are in quarkus-core.
- Dev MCP resource names carry no underscore; the server prefixes them `quarkus-network-faults_`.
- One source for a document that is both a docs page and a jar resource: it lives under `docs/modules/ROOT/examples/` and `core/deployment/pom.xml` copies it into the jar under `network-faults/`.
- Project conventions: constructor injection into private final fields, no lambdas, method references or streams unless clearly clearer, Javadoc is a multi-line block (never `/** one line */`), short remarks are `//`, no unexplained numeric constants, RestAssured examples written `when()` then one chained call per line as in `docs/modules/ROOT/pages/index.adoc`.
- No test code generation anywhere: no tool, cell or report renders Java.
- No random or timed faults, no persisted Dev UI state, no auto-off, no metrics or tracing modules.
- Tools that apply or end one fault or drop connections are opt-in Dev MCP tools; read-only tools and `resetAll` are enabled by default.
- The build formats sources (`mvn install` runs the formatter; CI runs with `-Dno-format`). Run `./mvnw -q install -pl <module> -am -DskipTests` after finishing a module to let it format.
- Don't commit anything as commits will only be done manually.

## Review Focus

- An `apply` with `responds-with` or an HTTP moment on a target whose upstream is `https://`: the agent must learn from `getTargets` (`plaintextHttp: false`) not to try, and `getRequests` on it must say why it is empty rather than return nothing. (Task 3 tests both.)
- A `requests` scope string without a space, or with a lower-case method: `apply` must reject it with a message naming the expected form, never apply the fault to the whole target. (Task 2 tests it.)
- A fault ended by `resetAll` from application code rather than by the card: the history entry must still close with the guard's verdict. (Task 4 tests it through `resetAll`.)
- A guarded method declared on a class whose annotation is class-level, and a private or static method on the same class: the first is listed with the class-level values, the others are not listed. (Task 5 tests it.)
- The playbook or the guide missing from the jar: the build must fail naming the file, never serve an empty resource. (Task 6 covers it by reading the resource the way Goblin does, failing with the path.)

---

### Task 1: Response status on `HttpRequestRecord`

**Files:**
- Modify: `core/runtime-api/src/main/java/io/quarkiverse/networkfaults/HttpRequestRecord.java`
- Modify: `core/runtime/src/main/java/io/quarkiverse/networkfaults/runtime/internal/proxy/TargetStats.java:106-111` (`responseSeen`)
- Modify: `core/runtime/src/main/java/io/quarkiverse/networkfaults/runtime/internal/proxy/HttpConversation.java:123` (the `responseSeen` call)
- Modify: `core/runtime/src/main/java/io/quarkiverse/networkfaults/runtime/internal/proxy/HttpResponseFramer.java` (expose the parsed `status`)
- Modify: `junit/src/test/java/io/quarkiverse/networkfaults/junit/AttemptsTest.java:21` (constructor gains an argument)
- Modify: `docs/modules/ROOT/pages/traffic.adoc` (one line on `responseStatus()` where the record's fields are described)
- Test: `core/runtime/src/test/java/io/quarkiverse/networkfaults/runtime/internal/proxy/ProxyCoreTest.java`

**Interfaces:**
- Produces: `HttpRequestRecord(Instant time, long connectionId, String method, String target, Map<String, List<String>> headers, String bodyFingerprint, Duration responseTime, Integer responseStatus)`; `responseStatus` is null exactly when `responseTime` is null. `TargetStats.responseSeen(HttpExchange request, int status)`.

- [ ] **Step 1: Write the failing test**

In `ProxyCoreTest`, next to the existing test that asserts `responseTime` on `httpRequests()`, using the same `TestUpstream` fixture, with the upstream answering `204` to one request:

```java
@Test
void requestRecordCarriesTheResponseStatus() {
    HttpRequestRecord record = target.httpRequests(RequestScope.ALL).getFirst();
    assertEquals(204, record.responseStatus());
    assertNotNull(record.responseTime());
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw -q test -pl core/runtime -Dtest=ProxyCoreTest#requestRecordCarriesTheResponseStatus`
Expected: compilation failure, `responseStatus()` undefined.

- [ ] **Step 3: Add the component and thread the status through**

Add `Integer responseStatus` as the last component of `HttpRequestRecord`, with a `@param` line: the status of the response, or null while there is none. `HttpResponseFramer` gets a package-private `int status()` accessor. `HttpConversation` passes the framer's status into `responseSeen(answered, status)`. `TargetStats.RequestEntry` stores it next to `responseTime`. Fix `AttemptsTest`'s record construction with `200`.

- [ ] **Step 4: Run the core and junit tests**

Run: `./mvnw -q test -pl core/runtime,junit`
Expected: all pass.

---

### Task 2: `FaultSpec` parses `responds-with`, a request scope and a body

**Files:**
- Modify: `core/runtime-dev/pom.xml` (add `junit-jupiter` as a test dependency the way `core/runtime/pom.xml` does)
- Modify: `core/runtime-dev/src/main/java/io/quarkiverse/networkfaults/dev/FaultSpec.java`
- Create: `core/runtime-dev/src/main/java/io/quarkiverse/networkfaults/dev/FaultRequest.java`
- Test: `core/runtime-dev/src/test/java/io/quarkiverse/networkfaults/dev/FaultSpecTest.java`

**Interfaces:**
- Produces: `record FaultRequest(String spec, String requests, String body, String contentType)`: `requests` is `"METHOD /path"` or null; `body` and `contentType` are only meaningful with `responds-with` and null otherwise.
- Produces: `FaultSpec.parse(FaultRequest request)` returning `FaultSpec.Parsed`, a record `(String kind, String scopeMethod, String scopePath, Moment moment, Duration duration, Integer status, String body, String contentType)` where fields that do not apply are null; it throws `IllegalArgumentException` for every bad input. `FaultSpec.apply(Parsed parsed, Target target)` returning `ActiveFault`. `FaultSpec.examples()` returning `List<FaultSpec.Example>` with `record Example(String spec, String meaning)`.
- The old `EXAMPLES` constant and `apply(String, Target)` go away; `NetworkFaultsJsonRPCService` is updated in Task 3, so for this task make it compile by calling `apply(parse(new FaultRequest(fault, null, null, null)), target)` and mapping examples to their specs.

- [ ] **Step 1: Write the failing `FaultSpecTest`**

```java
@Test
void parsesEveryForm() {
    assertEquals("hangs", parse("hangs").kind());
    assertEquals(Moment.Http.afterSseEvents(3), parse("closes:sse:3").moment());
    assertEquals(Moment.afterBytes(512), parse("dies-mid-request:512").moment());
    assertEquals(Moment.onNextWrite(), parse("hangs:next-write").moment());
    assertEquals(Moment.Http.afterHeaders(), parse("hangs:headers").moment());
    assertEquals(Moment.Http.afterChunks(2), parse("dies-mid-request:chunks:2").moment());
    assertEquals(Duration.ofMillis(500), parse("slow:500ms").duration());
    assertEquals(503, parse("responds-with:503").status());
}

@Test
void parsesAScopeAndAResponse() {
    FaultSpec.Parsed scoped = FaultSpec.parse(new FaultRequest("hangs", "POST /inventory/reserve", null, null));
    assertEquals("POST", scoped.scopeMethod());
    assertEquals("/inventory/reserve", scoped.scopePath());
    FaultSpec.Parsed response = FaultSpec.parse(new FaultRequest("responds-with:429", null, "{}", "application/json"));
    assertEquals(429, response.status());
    assertEquals("{}", response.body());
    assertEquals("application/json", response.contentType());
}

@Test
void rejectsBadInput() {
    assertMessage("expected \"METHOD /path\"", new FaultRequest("hangs", "POST", null, null));
    assertMessage("expected \"METHOD /path\"", new FaultRequest("hangs", "post /x", null, null));
    assertMessage("takes no request scope", new FaultRequest("unavailable", "POST /x", null, null));
    assertMessage("takes no request scope", new FaultRequest("connect-timeout", "POST /x", null, null));
    assertMessage("responds-with needs a status", new FaultRequest("responds-with", null, null, null));
    assertMessage("responds-with needs a status", new FaultRequest("responds-with:abc", null, null, null));
    assertMessage("only responds-with takes a body", new FaultRequest("hangs", null, "x", null));
    assertMessage("unknown fault", new FaultRequest("explodes", null, null, null));
}

@Test
void examplesCarryAMeaningAndIncludeRespondsWith() {
    boolean found = false;
    for (FaultSpec.Example e : FaultSpec.examples()) {
        assertFalse(e.meaning().isBlank());
        found |= e.spec().equals("responds-with:503");
    }
    assertTrue(found);
}
```

`parse(spec)` builds `new FaultRequest(spec, null, null, null)`; `assertMessage(fragment, request)` asserts an `IllegalArgumentException` whose message contains the fragment.

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw -q test -pl core/runtime-dev -Dtest=FaultSpecTest`
Expected: compilation failure.

- [ ] **Step 3: Implement `FaultRequest`, `parse`, `apply(Parsed, Target)` and `examples()`**

Rules the tests do not already fix:
- A scope string is split on its first space into method and path; the method must be upper-case letters only; the message says `expected "METHOD /path", for example "POST /inventory/reserve"`.
- `apply` with a scope first calls `target.http().requests(method, path)` and applies the fault to that endpoint.
- `responds-with` applies `http().respondsWith(status)`, then `contentType` and `body` when given.
- `examples()` is the old list with a meaning each, plus `responds-with:503` ("every request is answered with that status instead of reaching the upstream").

- [ ] **Step 4: Run the tests**

Run: `./mvnw -q test -pl core/runtime-dev`
Expected: PASS.

---

### Task 3: The JSON-RPC service as Dev MCP tools

**Files:**
- Modify: `core/runtime-dev/src/main/java/io/quarkiverse/networkfaults/dev/NetworkFaultsJsonRPCService.java`
- Modify: `core/runtime/src/main/java/io/quarkiverse/networkfaults/runtime/internal/FaultTracker.java` and `NetworkFaultsImpl.java` (`endCovering`, `activeCount`)
- Modify: `core/runtime/src/main/java/io/quarkiverse/networkfaults/runtime/internal/TargetSnapshot.java` and `ProxyTarget.snapshot()` (`boolean plaintextHttp` after `sourceProperty`, from `upstream().http()`)
- Modify: `extension-tests/src/test/java/io/quarkiverse/networkfaults/test/NetworkFaultsDevUITest.java`
- Create: `extension-tests/src/test/java/io/quarkiverse/networkfaults/test/StubSecureClient.java` (`@RegisterRestClient(configKey = "secure")`, `@Path("/api")`, one `@GET` method) and read `quarkus.rest-client.secure.url` in `StubClientUser` as the stub's URL is read

**Interfaces:**
- Consumes: Task 1's `responseStatus`, Task 2's `FaultSpec.parse`, `FaultSpec.apply`, `FaultRequest`, `FaultSpec.examples`.
- Produces: `FaultTracker.endCovering(TargetName selector)` ending, through `Group.end()`, every tracked group with a member whose `target().targetName()` the selector covers; `FaultTracker.activeCount()`; `NetworkFaultsImpl.endFaultsOf(String name)` and `activeFaultCount()` delegating to them.
- Produces, JSON-RPC methods (every method and parameter carries `@JsonRpcDescription`; the ones marked default carry `@DevMCPEnableByDefault`):
  - `getStatus()` default: `enabled`, `hint`, `faultsActive` (int), `targets`. The hint when enabled names the resources `quarkus-network-faults_testWritingGuide`, `quarkus-network-faults_agentPlaybook` and `quarkus-network-faults_inventory`, says the guide is what to read before writing a test, and says which tools are opt-in and where to enable them (Dev UI, Settings, Dev MCP tab, Tools).
  - `getTargets()` default: each entry adds `kind` (the name's family) and `plaintextHttp`.
  - `getEvents(target)` default: unchanged.
  - `getRequests(target)` default: `{requests: [...], reason: ""}`; each request has `time`, `connectionId`, `method`, `target`, `headers`, `bodyFingerprint`, `responseTimeMillis` (nullable), `responseStatus` (nullable); for a target that is not plaintext HTTP, `requests` is empty and `reason` says the target is not reached over plaintext HTTP/1.x and names its source property.
  - `getFaults()` default: `[{spec, meaning}]`.
  - `apply(target, fault, Optional<String> requests, Optional<String> body, Optional<String> contentType)` opt-in: parses first (so a bad request changes nothing); without a scope it ends the target's faults through `endFaultsOf` first; with a scope it ends nothing, and a conflict surfaces as the API's own message. Returns the confirmation string as today.
  - `reset(target)` opt-in: `endFaultsOf(target)`, then `byName(target).reset()` for faults applied outside the tracker.
  - `resetAll()` default, `dropConnections(target)` opt-in.
- `getHistory` and `getReport` come in Task 4.

- [ ] **Step 1: Extend the dev-mode test (failing)**

In `NetworkFaultsDevUITest`, add `quarkus.rest-client.secure.url=https://stub.example:8443/api` to the properties and `StubSecureClient` to the archive, then:

```java
@Test
public void targetsSayWhetherHttpFaultsApply() throws Exception {
    JsonNode targets = executeJsonRPCMethod("getTargets");
    JsonNode stub = byName(targets, "rest-client:stub");
    assertEquals("rest-client", stub.get("kind").asText());
    assertTrue(stub.get("plaintextHttp").asBoolean());
    JsonNode secure = byName(targets, "rest-client:secure");
    assertFalse(secure.get("plaintextHttp").asBoolean());
    JsonNode requests = executeJsonRPCMethod("getRequests", Map.of("target", "rest-client:secure"));
    assertEquals(0, requests.get("requests").size());
    assertTrue(requests.get("reason").asText().contains("quarkus.rest-client.secure.url"));
    assertEquals(0, executeJsonRPCMethod("getRequests", Map.of("target", "rest-client:stub")).get("requests").size());
}

@Test
public void applyTakesAScopeAndStatusCountsActiveFaults() throws Exception {
    executeJsonRPCMethod("apply", Map.of("target", "rest-client:stub", "fault", "slow:2s", "requests", "GET /api"));
    JsonNode status = executeJsonRPCMethod("getStatus");
    assertEquals(1, status.get("faultsActive").asInt());
    assertTrue(status.get("hint").asText().contains("testWritingGuide"));
    executeJsonRPCMethod("reset", Map.of("target", "rest-client:stub"));
    assertEquals(0, executeJsonRPCMethod("getStatus").get("faultsActive").asInt());
    JsonNode faults = executeJsonRPCMethod("getFaults");
    assertTrue(faults.get(0).has("meaning"), faults.toString());
}
```

`byName` is a small helper looping over the array; the existing test's assertion on `targets.size()` becomes `2`.

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw -q install -DskipTests -pl core/runtime,core/runtime-dev,core/deployment -am && ./mvnw -q verify -pl extension-tests -Dtest=NetworkFaultsDevUITest`
Expected: FAIL on `kind` missing.

- [ ] **Step 3: Implement the tracker and impl additions, then the service**

`endCovering` iterates a copy of `active`, as `endAll` does. Every description is written for an agent: what the call does, what each argument accepts (for `fault`, the spec grammar with one example per form; for `requests`, `"METHOD /path"`), what comes back, what to call next. Errors propagate as the exceptions the test API throws.

- [ ] **Step 4: Run the dev-mode tests**

Run: `./mvnw -q verify -pl extension-tests`
Expected: PASS.

- [ ] **Step 5: Check the MCP surface by hand**

Run `./mvnw quarkus:dev -pl integration-tests/rest-client-and-datasource` with `quarkus.dev-mcp.enabled=true` and `%dev.quarkus.network-faults.enabled=true`, open the Dev UI, Settings, Dev MCP, Tools: the eight default tools are enabled, `apply`, `reset` and `dropConnections` are listed disabled. Note the result in the task's hand-back.

---

### Task 4: Fault history and the Markdown report

**Files:**
- Create: `core/runtime/src/main/java/io/quarkiverse/networkfaults/runtime/internal/FaultListener.java`
- Create: `core/runtime/src/main/java/io/quarkiverse/networkfaults/runtime/internal/FaultRecord.java`
- Create: `core/runtime/src/main/java/io/quarkiverse/networkfaults/runtime/internal/FaultEnd.java`
- Modify: `core/runtime/src/main/java/io/quarkiverse/networkfaults/runtime/internal/FaultTracker.java`, `NetworkFaultsImpl.java`
- Create: `core/runtime-dev/src/main/java/io/quarkiverse/networkfaults/dev/FaultHistory.java`
- Create: `core/runtime-dev/src/main/java/io/quarkiverse/networkfaults/dev/MarkdownReport.java`
- Modify: `core/runtime-dev/src/main/java/io/quarkiverse/networkfaults/dev/NetworkFaultsJsonRPCService.java`
- Modify: `core/deployment/src/main/java/io/quarkiverse/networkfaults/deployment/NetworkFaultsDevUIProcessor.java` (produce `AdditionalBeanBuildItem.unremovableOf(FaultHistory.class)`: `core/runtime-dev` has no `beans.xml`)
- Test: `core/runtime/src/test/java/io/quarkiverse/networkfaults/runtime/internal/NetworkFaultsImplTest.java`
- Test: `core/runtime-dev/src/test/java/io/quarkiverse/networkfaults/dev/MarkdownReportTest.java`
- Test: `extension-tests/.../NetworkFaultsDevUITest.java`

**Interfaces:**
- Produces: `record FaultRecord(ActiveFault fault, Instant started, List<String> targets, String description)`; `record FaultEnd(Instant at, boolean observable, boolean sawTraffic, long connectionsSeen, long httpRequestsSeen)`; `interface FaultListener { void started(FaultRecord record); void ended(FaultRecord record, FaultEnd end); }`.
- Produces: `FaultTracker.addListener(FaultListener)`, called from `NetworkFaultsImpl.addFaultListener(FaultListener)`. `started` fires in `track`, `ended` in `settle` after the guard verdict, with `connectionsSeen` and `httpRequestsSeen` the sums over the members of the difference between the counters at `track` and now (`ProxyTarget.connectionsSeen()`, `httpRequestsSeen(RequestScope.ALL)`).
- Produces: `FaultHistory` (`@ApplicationScoped`, constructor takes `NetworkFaults` and registers itself as listener; a `void onStart(@Observes StartupEvent event)` method forces its creation at startup so faults applied from application code before the first JSON-RPC call are seen); `record Entry(long id, Instant started, Instant ended, List<String> targets, String description, String spec, String requests, String source, Boolean sawTraffic, long connectionsSeen, long httpRequestsSeen)`; `List<Entry> entries()` newest first; `void tag(ActiveFault fault, FaultRequest request)` called by `apply` so the entry carries the spec and scope with `source = "card"`; untagged entries have `source = "api"`. Capacity `HISTORY_CAPACITY = 500`, the same as the event log so both roll at the same pace (say so in a comment).
- Produces: `MarkdownReport.render(List<TargetSnapshot> targets, List<FaultHistory.Entry> history)` returning a String; JSON-RPC `getHistory()` default returning the entries as maps with the record's field names, and `getReport()` default returning `{markdown, generatedAt}`.

- [ ] **Step 1: Write the failing listener test in `NetworkFaultsImplTest`**

Using the class's existing fixture that builds a `NetworkFaultsImpl` over a `TestTargets` proxy:

```java
@Test
void listenerSeesAFaultStartAndEndWithTheGuardVerdict() {
    List<String> seen = new ArrayList<>();
    faults.addFaultListener(new FaultListener() {
        @Override
        public void started(FaultRecord record) {
            seen.add("started " + record.targets() + " " + record.description());
        }
        @Override
        public void ended(FaultRecord record, FaultEnd end) {
            seen.add("ended sawTraffic=" + end.sawTraffic() + " connections=" + end.connectionsSeen());
        }
    });
    faults.target(/* the fixture's bean type */).hangs();
    faults.resetAll();
    assertEquals(2, seen.size(), seen.toString());
    assertTrue(seen.get(0).startsWith("started ["), seen.toString());
    assertEquals("ended sawTraffic=false connections=0", seen.get(1));
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw -q test -pl core/runtime -Dtest=NetworkFaultsImplTest`
Expected: compilation failure.

- [ ] **Step 3: Implement the listener in core**

Listeners are a `CopyOnWriteArrayList`; a listener throwing is logged at WARN and does not stop the others. The `Group` keeps `started` and the per-member starting counters.

- [ ] **Step 4: Run the core tests**

Run: `./mvnw -q test -pl core/runtime`
Expected: PASS.

- [ ] **Step 5: Write the failing `MarkdownReportTest`**

```java
@Test
void reportListsTargetsAndHistory() {
    String md = MarkdownReport.render(List.of(snapshot("rest-client:inventory", "quarkus.rest-client.inventory.url")),
            List.of(entry("rest-client:inventory", "hangs", "card", true)));
    assertTrue(md.startsWith("# Network faults report"));
    assertTrue(md.contains("| rest-client:inventory | quarkus.rest-client.inventory.url |"));
    assertTrue(md.contains("hangs"));
    assertTrue(md.contains("saw traffic"));
}

@Test
void emptyHistorySaysSo() {
    assertTrue(MarkdownReport.render(List.of(), List.of()).contains("No fault has been applied"));
}
```

`snapshot` and `entry` are test helpers building the records with the other fields zero or null.

- [ ] **Step 6: Run it to verify it fails, implement `FaultHistory` and `MarkdownReport`, run again**

Run: `./mvnw -q test -pl core/runtime-dev -Dtest=MarkdownReportTest`
Expected: FAIL, then PASS after implementing. The report has three sections: Targets (a table of name, property, upstream, active fault), Faults applied (one block per entry, newest first: when, targets, description, source, and "saw traffic" or "no connection went through the proxy while it was active"), Events (connections seen, requests seen, resets and upstream failures per target).

- [ ] **Step 7: Extend the dev-mode test (failing), then wire the service**

```java
@Test
public void historyFollowsFaultsFromTheCardAndFromTheApi() throws Exception {
    executeJsonRPCMethod("apply", Map.of("target", "rest-client:stub", "fault", "hangs", "requests", "GET /api"));
    executeJsonRPCMethod("apply", Map.of("target", "rest-client:stub", "fault", "slow:1s", "requests", "POST /api"));
    executeJsonRPCMethod("resetAll");
    JsonNode history = executeJsonRPCMethod("getHistory");
    assertTrue(history.size() >= 2, history.toString());
    JsonNode newest = history.get(0);
    assertEquals("card", newest.get("source").asText());
    assertEquals("slow:1s", newest.get("spec").asText());
    assertEquals("POST /api", newest.get("requests").asText());
    assertFalse(newest.get("ended").isNull(), newest.toString());
    assertFalse(newest.get("sawTraffic").asBoolean());
    JsonNode report = executeJsonRPCMethod("getReport");
    assertTrue(report.get("markdown").asText().contains("rest-client:stub"));
}
```

The second `apply` does not end the first because both carry a scope (Task 3's `apply` rule).

Run: `./mvnw -q install -DskipTests -pl core/runtime,core/runtime-dev,core/deployment -am && ./mvnw -q verify -pl extension-tests -Dtest=NetworkFaultsDevUITest`
Expected: FAIL on `getHistory` unknown, then PASS once wired.

---

### Task 5: The inventory of guarded methods as a Dev MCP resource

**Files:**
- Create: `core/deployment/src/main/java/io/quarkiverse/networkfaults/deployment/ResilienceInventory.java`
- Modify: `core/deployment/src/main/java/io/quarkiverse/networkfaults/deployment/NetworkFaultsDevUIProcessor.java`
- Modify: `extension-tests/pom.xml` (test dependency `org.eclipse.microprofile.fault-tolerance:microprofile-fault-tolerance-api`, managed by the Quarkus BOM)
- Create: `extension-tests/src/test/java/io/quarkiverse/networkfaults/test/GuardedService.java`
- Create: `extension-tests/src/test/java/io/quarkiverse/networkfaults/test/NetworkFaultsInventoryTest.java`
- Create: `extension-tests/src/test/java/io/quarkiverse/networkfaults/test/NetworkFaultsEmptyInventoryTest.java`

**Interfaces:**
- Produces: `ResilienceInventory.of(IndexView applicationIndex)` returning `Map<String, Object>` with `guardedMethods` (a `List<Map<String, Object>>`, each `class`, `method`, `annotations` (map of simple annotation name to parameter map), `injects` (sorted list of fully qualified type names)) and `notes` (list of strings).
- The card calls `addBuildTimeData("inventory", ResilienceInventory.of(applicationArchives.getRootArchive().getIndex()), INVENTORY_DESCRIPTION, true)`. The description: where this application believes it is protected, every method guarded by a MicroProfile Fault Tolerance annotation with its effective parameters as declared in the code, and the types it injects that a test can pass to `target(...)`; targets themselves come from `getTargets`; derive the tests to write from it, one per guarded method and fault.

- [ ] **Step 1: Write the failing dev-mode tests**

`GuardedService`:

```java
@ApplicationScoped
@Retry(maxRetries = 2)
public class GuardedService {
    private final StubClient client;
    public GuardedService(@RestClient StubClient client) { this.client = client; }
    @Timeout(2000)
    @Fallback(fallbackMethod = "later")
    public String place() { return client.get(); }
    public String later() { return "later"; }
    private String helper() { return ""; }
    public static String util() { return ""; }
}
```

`NetworkFaultsInventoryTest extends DevUIBuildTimeDataTest` (constructor `super("quarkus-network-faults")`) with the same archive as `NetworkFaultsDevUITest` plus `GuardedService`:

```java
@Test
public void inventoryListsGuardedMethodsWithEffectiveValuesAndInjectedTypes() throws Exception {
    JsonNode inventory = getBuildTimeData("inventory");
    JsonNode methods = inventory.get("guardedMethods");
    JsonNode place = byMethod(methods, "place");
    assertEquals("io.quarkiverse.networkfaults.test.GuardedService", place.get("class").asText());
    assertEquals(2000, place.get("annotations").get("Timeout").get("value").asInt());
    assertEquals("MILLIS", place.get("annotations").get("Timeout").get("unit").asText());
    assertEquals("later", place.get("annotations").get("Fallback").get("fallbackMethod").asText());
    assertEquals(2, place.get("annotations").get("Retry").get("maxRetries").asInt());
    assertEquals(0, place.get("annotations").get("Retry").get("delay").asInt());
    assertEquals("io.quarkiverse.networkfaults.test.StubClient", place.get("injects").get(0).asText());
    JsonNode later = byMethod(methods, "later");
    assertEquals(2, later.get("annotations").get("Retry").get("maxRetries").asInt()); // class-level
    assertNull(later.get("annotations").get("Timeout"));
    assertNull(byMethod(methods, "helper"));
    assertNull(byMethod(methods, "util"));
    assertTrue(inventory.get("notes").get(0).asText().contains("MicroProfile Config"));
}
```

`NetworkFaultsEmptyInventoryTest` with the archive of `NetworkFaultsDevUIWithoutTargetsTest`:

```java
@Test
public void noGuardedMethodIsAnEmptyListWithANote() throws Exception {
    JsonNode inventory = getBuildTimeData("inventory");
    assertEquals(0, inventory.get("guardedMethods").size());
    assertTrue(inventory.get("notes").get(0).asText().contains("No method"));
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./mvnw -q verify -pl extension-tests -Dtest='NetworkFaults*InventoryTest'`
Expected: FAIL, no build-time data named `inventory`.

- [ ] **Step 3: Implement `ResilienceInventory`**

Jandex over the root archive only. Annotations and their parameters with the specification defaults (declared values merged over them, durations reported with their unit, never converted):

| Annotation | Parameters and defaults |
|---|---|
| `org.eclipse.microprofile.faulttolerance.Timeout` | `value` 1000, `unit` MILLIS |
| `...Retry` | `maxRetries` 3, `delay` 0, `delayUnit` MILLIS, `maxDuration` 180000, `durationUnit` MILLIS, `jitter` 200, `jitterDelayUnit` MILLIS, `retryOn` [java.lang.Exception], `abortOn` [] |
| `...CircuitBreaker` | `requestVolumeThreshold` 20, `failureRatio` 0.5, `delay` 5000, `delayUnit` MILLIS, `successThreshold` 1, `failOn` [java.lang.Throwable], `skipOn` [] |
| `...Fallback` | `fallbackMethod` "", `value` "", `applyOn` [java.lang.Throwable], `skipOn` [] |
| `...Bulkhead` | `value` 10, `waitingTaskQueue` 10 |
| `io.smallrye.faulttolerance.api.RateLimit` | `value` 100, `window` 1, `windowUnit` SECONDS, `minSpacing` 0, `minSpacingUnit` SECONDS |

A business method is neither static, private, synthetic nor a constructor. A class-level annotation applies to every business method; a method-level one of the same type wins. `injects` is the set of types of the class's fields and of its constructors' parameters, as fully qualified names, sorted, keeping only interfaces annotated `@RegisterRestClient` or `@GraphQLClientApi`, types annotated `@RegisterAiService`, and types whose simple name is one of `DataSource`, `Pool`, `RedisDataSource`, `ReactiveRedisDataSource`, `MongoClient`, `ReactiveMongoClient`, `EntityManager`, `SessionFactory`, `ChatModel`, `StreamingChatModel`, `EmbeddingModel`, `ChatMemoryStore`, `EmbeddingStore`, `McpClient`, `KafkaClientService`, `Emitter`, `MutinyEmitter` (one constant with a comment: the bean types the Targets reference lists). Notes: the config-override and inheritance limits; with no guarded method, `"No method of this application is guarded by a MicroProfile Fault Tolerance annotation."`.

- [ ] **Step 4: Run the tests**

Run: `./mvnw -q install -DskipTests -pl core/deployment && ./mvnw -q verify -pl extension-tests`
Expected: PASS.

---

### Task 6: The guide, the playbook, the template and the agents page

**Files:**
- Create: `docs/modules/ROOT/examples/test-writing-guide.md`
- Create: `docs/modules/ROOT/examples/agent-playbook.md`
- Create: `docs/modules/ROOT/examples/RESILIENCE.md`
- Create: `docs/modules/ROOT/pages/agents.adoc`
- Modify: `docs/modules/ROOT/nav.adoc` (Reference: `* xref:agents.adoc[AI Agents]` after Dev UI)
- Modify: `docs/modules/ROOT/pages/dev-ui.adoc` (a "Driving the Card from an Agent" section pointing at `agents.adoc`)
- Modify: `core/deployment/pom.xml` (resources: `../docs/modules/ROOT/examples`, includes the two `.md` guides, `targetPath` `network-faults`, with the comment Goblin's pom carries on why the file lives under docs)
- Modify: `core/deployment/src/main/java/io/quarkiverse/networkfaults/deployment/NetworkFaultsDevUIProcessor.java`
- Test: `extension-tests/src/test/java/io/quarkiverse/networkfaults/test/NetworkFaultsInventoryTest.java` (one more test)

**Interfaces:**
- Produces: build-time data `testWritingGuide` and `agentPlaybook`, each the file's text, `true`.
- Guide description: "Read this before writing or changing a test that uses Quarkus Network Faults: the test shape, how to address each kind of target, which fault for which scenario, counting calls, the guard, timeouts, and the mistakes to avoid."
- Playbook description: "Read this before any network fault experiment: safety rules, which fault proves which Fault Tolerance annotation, how to tell the fault was exercised, the hypothesis / fault / observation / conclusion loop that ends in a test, and the shape of the report."

- [ ] **Step 1: Write the failing test**

```java
@Test
public void guideAndPlaybookAreServed() throws Exception {
    assertTrue(getBuildTimeData("testWritingGuide").asText().startsWith("# Writing tests with Quarkus Network Faults"));
    assertTrue(getBuildTimeData("agentPlaybook").asText().startsWith("# Network faults agent playbook"));
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw -q verify -pl extension-tests -Dtest=NetworkFaultsInventoryTest`
Expected: FAIL.

- [ ] **Step 3: Write the three Markdown files**

`test-writing-guide.md`, title `# Writing tests with Quarkus Network Faults`, the eight sections of the design in order: the dependency and the test shape (the `index.adoc` example verbatim); addressing targets (the `targets.adoc` table compressed to one line per kind, how to go from a `getTargets` entry and its property to the bean, plus method references and `http().requests`); which fault for which scenario (the `guide-fault-tolerance.adoc` table plus a "what the client sees" column from `faults.adoc`, in the order `unavailable`, `hangs`, `slow`, `respondsWith`, `diesMidRequest`, `closes`); some of the calls (`forFirst`, `every`, `after` with the retry example from `guide-rest-client.adoc`); observing (`traffic()`, `HttpRequestRecord` fields including `responseStatus`, `awaitConnections`, `Attempts`, `Stopwatch`); the guard (its message and the three usual causes from `guard.adoc`); timeouts (REST Client read timeout 30000 ms, Agroal acquisition timeout 5 s, Vert.x connect timeout 60000 ms, from `timeouts.adoc`, and the advice to set the application timeout in the test profile); mistakes (the seven in the design, each with its fix).

`agent-playbook.md`, title `# Network faults agent playbook`, sections in this order: what the extension is and what an experiment is for (two sentences each); the tools (the JSON-RPC names with the `quarkus-network-faults_` prefix, which are opt-in); safety rules (one whole-target fault per target at a time and the API enforces it; end every fault when done, `resetAll` is the way; do not work around an opt-in tool, ask the developer to enable it at Dev UI, Settings, Dev MCP tab, Tools; read `getEvents` before it rolls, 500 events per target; a fault no request exercises proves nothing); which fault proves which annotation (the guide's table by spec name); where to start (the `inventory` resource and `getTargets`, joined on the injected type and the target's property); the loop (hypothesis; `apply` one fault to one target; call the endpoint yourself and record status, body and elapsed time; `getEvents` and `getRequests` to confirm the fault was exercised; conclude held / did not hold / inconclusive; `reset` or `resetAll`; then write the test with the guide: same target, same fault, the status observed inside the block and after it); how to tell the fault was not exercised (zero connections seen, or `upstreamConnectFailures` rising, with the guard's two explanations); the report (`getReport`, and the write-up for the developer: each hypothesis, observation, verdict and the tests written); `RESILIENCE.md` in the project root for the promises the extension cannot know, ask the developer if it is missing.

`RESILIENCE.md`: Goblin's template in this extension's vocabulary: what healthy means, dependencies and what protects them (columns: dependency as `target(...)`, called from, protection, expected behaviour when it fails), promises to verify, out of bounds.

- [ ] **Step 4: Write `agents.adoc` and wire the resources**

`agents.adoc` (Quarkus LangChain4j docs style, the usual `include::./includes/attributes.adoc[]` and `customization.adoc` lines): "Writing Tests with an Agent" (why the guide exists, how to hand it to an agent: the docs URL, or the Dev MCP resource) including `{examples-dir}test-writing-guide.md` in a `[source,markdown]` block; "Driving the Application from an Agent": enabling Dev MCP (`quarkus.dev-mcp.enabled=true`, `/q/dev-mcp`), the tools with the default and opt-in split as a table, the three resources, a typical session that ends with a test; "The Playbook" including the playbook; "Your Application's Promises" including `RESILIENCE.md`. Add the nav entry and the `dev-ui.adoc` section. In the processor, read both files from the classloader (`network-faults/test-writing-guide.md`, `network-faults/agent-playbook.md`), throwing `IllegalStateException` naming the path when missing, as Goblin's `readPlaybook` does.

- [ ] **Step 5: Run the test and the docs build**

Run: `./mvnw -q install -DskipTests -pl core/deployment && ./mvnw -q verify -pl extension-tests -Dtest=NetworkFaultsInventoryTest && ./mvnw -q install -pl docs`
Expected: PASS, docs build without a missing-include warning.

---

### Task 7: The card

**Files:**
- Modify: `core/deployment/src/main/resources/dev-ui/qwc-network-faults.js`
- Create: `core/deployment/src/main/resources/dev-ui/qwc-network-faults-history.js` (if History becomes a second page; then `NetworkFaultsDevUIProcessor` adds the page with icon `font-awesome-solid:scroll`)
- Modify: `docs/modules/ROOT/pages/dev-ui.adoc`

**Interfaces:**
- Consumes: Task 3's and Task 4's JSON-RPC methods and shapes.

- [ ] **Step 1: Every fault from the API on a whole target**

The fault select is fed by `getFaults` (`spec` as value, `meaning` as tooltip). Next to it a `vaadin-text-field` placeholder `POST /path` for the scope, sent as `requests` when not blank. When the selected spec starts with `responds-with`, two more fields appear: body and content type. A target with `plaintextHttp: false` disables `responds-with` and the moment forms, with the reason as the title attribute.

- [ ] **Step 2: A Requests view**

A *Requests* button next to *Events* opens a `vaadin-details` with a grid of `getRequests`: time, method, target, status, response time; a row expands to its headers and body fingerprint. The button is disabled with the `reason` as title when `plaintextHttp` is false.

- [ ] **Step 3: History and Report**

A History section (or page) over `getHistory`, refreshed every 2 seconds like the rest: a filter on target and on fault kind (the spec before its first colon), a summary line "N faults applied, M saw no traffic", and a grid newest first with when, target(s), fault, scope, source, traffic (connections and requests seen), ended. An *Export report* button calls `getReport` and offers copy and download (`network-faults-report.md`).

- [ ] **Step 4: Check by hand and record it**

Run `./mvnw quarkus:dev -pl integration-tests/rest-client-and-datasource` with `%dev.quarkus.network-faults.enabled=true`, drive `POST /orders` with curl while applying `hangs`, `responds-with:503` with a body, and a scoped `slow:2s`; confirm requests, history and the report. Take a screenshot `docs/modules/ROOT/assets/images/dev-ui-card.png` and a short GIF `docs/modules/ROOT/assets/images/dev-ui-demo.gif` of apply, request, history, report.

- [ ] **Step 5: Update `dev-ui.adoc`**

The spec table gains `responds-with:<status>` and the scope field; new sections Requests, History, Report; the screenshot after the opening paragraph.

- [ ] **Step 6: Run the dev-mode tests**

Run: `./mvnw -q install -DskipTests -pl core/deployment && ./mvnw -q verify -pl extension-tests`
Expected: PASS.

---

### Task 8: README

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Write the paragraphs**

Under "What it covers", replace the Dev UI bullet with one on the card (every fault, requests, history, report) and add one on agents: a test-writing guide an agent reads from the docs or from the running app, Dev MCP tools to experiment against the dev-mode app, a playbook whose loop ends in a test, and an inventory of the guarded methods, with the link to `agents.adoc` on the docs site. Under "Why this extension?", add one sentence placing this extension next to Goblin: Goblin injects faults from Java code inside the application, this extension makes the real client library fail on the wire, and the two answer different questions. Embed the GIF from Task 7 after "What a Test Looks Like".
