# CQRS + Event Sourcing with Axon Framework

For `style: cqrs-event-sourcing` with `event-store: axon`. Axon Framework replaces the hand-written parts of `event-sourcing.md`: the event store, command dispatch and projection tracking. The rules about events, versioning, personal data and API behaviour stay.

This file is a procedure, not a set of templates. Axon's API changed deeply between major versions and keeps moving, so the code comes from the reference documentation of the version you select, never from memory and never from another version's examples.

## 1. Select the Version First

Before generating any code:

1. Read the project's Spring Boot version (`pom.xml`), or the latest Spring Boot 4.x for a new project.
2. Look up the current Axon Framework releases on Maven Central and the compatibility table on the "Spring Boot Integration" page of the Axon Framework reference (`docs.axoniq.io/axon-framework-reference`).
3. Pick the latest stable release that the table lists for that Spring Boot version. No milestone or release candidate unless the user asks.
4. **Tell the user which version you selected and why,** and name the alternative when there is one. Record it in the architecture block (`axon-version`).
5. If no release supports the project's Spring Boot version, say so and stop. Offer the PostgreSQL event store (`event-sourcing.md`) instead; do not downgrade Spring Boot on your own.

Starting point, to be verified each time (state of November 2026):

| Line | Maven coordinates of the Spring Boot starter | Notes |
|------|-----------------------------------------------|-------|
| Axon Framework 5.x | `org.axonframework.extensions.spring:axon-spring-boot-starter`, versions managed by the BOM `io.axoniq.framework:axoniq-framework-bom` | Event-sourced entities, new testing fixture; the reference lists Spring Boot 4 support for recent 5.x releases |
| Axon Framework 4.13+ | `org.axonframework:axon-spring-boot-starter` | The 4.x annotation model (`@Aggregate`); 4.13 added Spring Boot 4 and Jackson 3 support as a migration release |

Prefer the 5.x line for a new project. Use 4.13+ only for a project that already uses Axon 4.

## 2. Ask Where Events Are Stored

Ask one more question, then record the answer (`axon-event-store`):

| Option | Meaning |
|--------|---------|
| Axon Server (default) | A separate server that stores and routes events; it runs in Docker Compose locally and in the cluster next to the application |
| PostgreSQL | Events in the application's own PostgreSQL database, through the storage engine Axon provides for it |

Look up, for the selected version, which storage engines exist, their Maven artifacts, how Axon Server is switched off when it is not used, and which PostgreSQL version the engine needs. Tell the user about any limit you find (for example a feature that only Axon Server supports).

Check the licence of each engine before you offer it. Axoniq ships open-source engines and commercial add-ons side by side, with similar names. For PostgreSQL on Axon 5.4, the open-source choice was `AggregateBasedJpaEventStorageEngine` (JPA, Apache 2), and `axoniq-postgresql` was a commercial add-on. Confirm this for the selected version. Use an open-source engine unless the user asks for the commercial one. Record the engine and its licence in the block (`axon-storage-engine`).

    ## Architecture
    style: cqrs-event-sourcing
    scope: Order
    topology: single-service
    event-store: axon
    axon-version: 5.4.1
    axon-event-store: postgresql
    axon-storage-engine: AggregateBasedJpaEventStorageEngine (open source, Apache 2)
    read-store: postgresql

## 3. What Axon Replaces

| Hand-written in `event-sourcing.md` | With Axon |
|-------------------------------------|-----------|
| `event_store` table, `EventStore` class, the append lock | Axon's event store and storage engine |
| Loading a stream and replaying it in the handler | Axon loads the event-sourced aggregate (entity) for the command |
| Handlers called directly by the controller | Commands are dispatched through Axon's command gateway to command handlers |
| Stream version check on append | Axon's own consistency check; a conflict is still mapped to 409 Problem Details |
| `Subscriptions`, `projection_checkpoint`, the relay | Axon event processors with a token store |
| `Upcaster` and `EventTypes` | Axon's event versioning mechanism for the selected version |
| Aggregate unit tests on `replay` | Axon's test fixture: given events, when command, then events |

Do not generate both: a project with Axon has no `eventstore` package, no outbox and no relay of its own.

Take from the reference of the selected version, and from nowhere else: the annotations for an event-sourced aggregate and its command and event-sourcing handlers, how events are appended, how a controller dispatches a command and gets the result, how event handlers are grouped into processors, how a processor is reset and replayed, and the fixture API.

## 4. What Still Applies

- **Events** are immutable records under a sealed interface per aggregate, named in the past tense, with values only (`event-sourcing.md`, Events).
- **Published events are never changed.** A change is a new version of the event, handled by Axon's versioning mechanism, with a test that reads an old payload.
- **Aggregates have no JPA mapping** and keep only the state their rules need. Deciding and applying stay separate: command handlers decide and publish events, event-sourcing handlers only change state.
- **Personal data:** none in an event payload or in metadata without an erasure strategy documented in the README (`event-sourcing.md`, Personal Data).
- **Snapshots** only when a measured load problem exists.
- **API behaviour:** 201 for the command that creates an aggregate, 202 for the others, with the aggregate id; 409 Problem Details for a conflict or a broken rule; the README and the OpenAPI description say that queries are eventually consistent.
- **Read side:** read-model tables, query records, one handler per query, `<Resource>Response` in a `PagedModel` (`cqrs.md`). Query handlers stay plain Spring beans called by the controller unless the user asks for Axon's query bus.
- **Projections** are idempotent, track their position (Axon's token store) and can be rebuilt (reset the processor).
- **Packages and topology** as in `topology.md`. With separate services, events travel through Axon Server when it is the event store; with a PostgreSQL store, use Kafka as in `kafka.md` or ask the user.
- **Flyway owns the schema.** Read models always. For Axon's own tables (events, tokens, and so on), switch off automatic creation where the selected version allows it and create them in a migration from the DDL of that version. If the selected engine can only create its tables itself, tell the user and write it in the README.
- **No telemetry by default.** Axon's update checker sends JVM and OS details to Axoniq. Switch it off in the application, in the tests and in CI (`axoniq.update-checker.disabled=true` for 5.x; look up the property for other versions). The README says how to switch it back on.
- **Everything in `conventions`** that the architecture decision does not replace: no Lombok, constructor injection, Problem Details, Testcontainers with `@ServiceConnection`, positive and negative tests.

### Versions in Command Results

A command endpoint returns `CommandResult(id, version)`, and every command after the create carries the version the client read (`event-sourcing.md`, Command Endpoints). Check how the selected version exposes the stream's sequence number; on Axon 5.4 with the aggregate-based storage engine this worked:

- **The entity counts its own version.** A `version` field starting at -1, incremented in every `@EventSourcingHandler`. It is the sequence number of the last event applied: 0 after the creating event, the same number the event store stores and the relay sends to the query side.
- **The handler checks the client's version first** (`entity.requireVersion(command.version())`, 409 without `errors` on a mismatch), before any rule, so a client with a stale view reloads before it sees a rule error.
- **Compute the new version before the append.** Axon 5 applies an appended event to the injected entity right away, so `entity.version() + 1` read *after* the append counts the event twice. Use `command.version() + 1` (or `entity.version() + n` read before appending `n` events to that entity). A create returns 0.
- **The handler returns it, the controller reads it:** a `long` return value of the `@CommandHandler`, `commandGateway.sendAndWait(command, Long.class)`.
- **Test it end to end:** an integration test asserts that the version in the `CommandResult` equals the `sequence-number` the relay put on the event.

## 5. Build Order

1. Version and event store selected, told to the user and recorded.
2. Dependencies from the BOM; Docker Compose with Axon Server (and its healthcheck) or PostgreSQL.
3. One aggregate end to end: events, aggregate, commands, controller, fixture tests.
4. Projection, read model, queries.
5. `./mvnw clean verify` with the integration tests running. Do not report the project as working before this passes: with Axon, a mistake in the version or in an annotation shows up only at startup.
6. README: the Axon version, where events are stored, eventual consistency, the erasure strategy.

## Review Signals

In addition to `event-sourcing.md`:

| Signal in the diff | Severity |
|--------------------|----------|
| A hand-written event store, outbox or checkpoint table next to Axon | Important |
| Axon APIs of another major version than `axon-version` (they do not compile, or silently do nothing) | Critical |
| Event-sourcing handler that validates, throws or has side effects | Important |
| State changed in a command handler instead of through an event | Important |
| Axon version changed without updating the architecture block and the README | Important |
| `CommandResult.version` computed from the entity after the append (counts the new event twice) | Important |
