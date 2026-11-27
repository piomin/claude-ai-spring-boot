# CQRS + Event Sourcing (PostgreSQL Event Store)

For `style: cqrs-event-sourcing` with `event-store: postgresql`. The write side stores no current state. Every change is an event appended to a stream, and an aggregate's state is rebuilt from its events each time a command arrives. The read side is the same as in `cqrs.md` (read-model tables, query records, query handlers, `<Resource>Response`), fed by projections that track their position.

Packages and modules are in `topology.md`. Examples use an `Order` aggregate with the event store tables in the command service's own database; a single service puts them in the `command` schema (`command.event_store`).

What changes compared with CQRS without event sourcing:

| | CQRS (`cqrs.md`) | Event Sourcing |
|---|---|---|
| Write model | JPA entity with the current state | Events in `event_store`; the aggregate is a plain class |
| Aggregate id | Identity column (`Long`) | `UUID`, created by the application before the first event |
| Lost-update check | `@Version`, 409 | Stream version on append, 409 |
| Events | State after the change | What happened (`OrderPaid`), nothing more |
| Outbox | Separate `outbox_event` table | The event store itself |
| Projection is idempotent by | The event's version | Its checkpoint in the global sequence |
| Command endpoints | 201, 200, 204 | 201 for a new aggregate, 202 otherwise |

## Event Store

One append-only table, owned by Flyway. A stream is the events of one aggregate.

```sql
-- V1__create_event_store.sql
CREATE TABLE event_store (
    global_seq  BIGINT GENERATED ALWAYS AS IDENTITY,
    stream_id   UUID         NOT NULL,
    stream_type VARCHAR(100) NOT NULL,
    version     BIGINT       NOT NULL,
    event_type  VARCHAR(100) NOT NULL,
    payload     JSONB        NOT NULL,
    metadata    JSONB        NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_event_store PRIMARY KEY (global_seq),
    CONSTRAINT uk_event_store_stream_version UNIQUE (stream_id, version),
    CONSTRAINT ck_event_store_version CHECK (version > 0)
);

-- Append-only: an event is never changed or removed
CREATE FUNCTION reject_event_store_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'event_store is append-only';
END $$;

CREATE TRIGGER tr_event_store_append_only
    BEFORE UPDATE OR DELETE ON event_store
    FOR EACH ROW EXECUTE FUNCTION reject_event_store_change();
```

| Column | Meaning |
|--------|---------|
| `global_seq` | Position of the event among all events; projections and the relay read in this order |
| `stream_id` | The aggregate id |
| `stream_type` | The aggregate type (`Order`) |
| `version` | Position in the stream, from 1; `UNIQUE (stream_id, version)` rejects a concurrent append |
| `event_type` | The event record's simple name |
| `payload` | The event as JSON |
| `metadata` | Who and what caused it: the command name, the user, a trace or request id. Never business data |
| `occurred_at` | When it was stored |

```java
/** An event as read from the event store: the event plus its place in the stream and in the store. */
public record StoredEvent<E>(long globalSeq, UUID streamId, long version, E event, Instant occurredAt) {
}
```

```java
/** Append-only event store on PostgreSQL. One stream per aggregate; versions start at 1. */
@Component
public class EventStore {

    /** Serializes appends, so the global sequence follows the commit order and a reader never skips an event. */
    private static final long APPEND_LOCK = 7_310_001L;

    private final JdbcClient jdbc;
    private final EventTypes eventTypes;
    private final JsonMapper jsonMapper;   // tools.jackson.databind.json.JsonMapper (Jackson 3)

    public EventStore(JdbcClient jdbc, EventTypes eventTypes, JsonMapper jsonMapper) {
        this.jdbc = jdbc;
        this.eventTypes = eventTypes;
        this.jsonMapper = jsonMapper;
    }

    /** All events of one stream, oldest first; empty when the stream does not exist. */
    public <E> List<StoredEvent<E>> load(UUID streamId, Class<E> eventType) {
        return jdbc.sql("""
                        SELECT global_seq, stream_id, version, event_type, payload, occurred_at
                        FROM event_store WHERE stream_id = :streamId ORDER BY version
                        """)
                .param("streamId", streamId)
                .query((rs, row) -> stored(rs, eventType))
                .list();
    }

    /**
     * Appends events after {@code expectedVersion} (0 for a new stream) and returns the stream's new version.
     *
     * @throws OptimisticLockingFailureException when the stream has moved on since it was loaded
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public long append(String streamType, UUID streamId, long expectedVersion, List<?> events, Map<String, String> metadata) {
        jdbc.sql("SELECT pg_advisory_xact_lock(:key)").param("key", APPEND_LOCK).query((rs, row) -> 0).single();
        long version = expectedVersion;
        try {
            for (Object event : events) {
                jdbc.sql("""
                                INSERT INTO event_store (stream_id, stream_type, version, event_type, payload, metadata, occurred_at)
                                VALUES (:streamId, :streamType, :version, :eventType, CAST(:payload AS jsonb), CAST(:metadata AS jsonb), now())
                                """)
                        .param("streamId", streamId)
                        .param("streamType", streamType)
                        .param("version", ++version)
                        .param("eventType", eventTypes.name(event))
                        .param("payload", eventTypes.serialize(event))
                        .param("metadata", jsonMapper.writeValueAsString(metadata))
                        .update();
            }
        } catch (DuplicateKeyException e) {
            throw new OptimisticLockingFailureException("Stream " + streamId + " was changed by someone else", e);
        }
        return version;
    }

    /** Events of all streams after a position in the global sequence, in order. For projections. */
    public List<StoredEvent<Object>> readAfter(long position, int limit) {
        return jdbc.sql("""
                        SELECT global_seq, stream_id, version, event_type, payload, occurred_at
                        FROM event_store WHERE global_seq > :position ORDER BY global_seq LIMIT :limit
                        """)
                .param("position", position)
                .param("limit", limit)
                .query((rs, row) -> stored(rs, Object.class))
                .list();
    }

    private <E> StoredEvent<E> stored(ResultSet rs, Class<E> eventType) throws SQLException {
        Object event = eventTypes.deserialize(rs.getString("event_type"), rs.getString("payload"));
        return new StoredEvent<>(rs.getLong("global_seq"), rs.getObject("stream_id", UUID.class), rs.getLong("version"),
                eventType.cast(event), rs.getTimestamp("occurred_at").toInstant());
    }
}
```

- **The append lock is required.** An identity value is taken when the row is inserted, not when its transaction commits. Without the lock, an event with a lower `global_seq` can become visible after a reader has passed it, and a projection loses it for good. The transaction-scoped advisory lock is held only from the append to the commit. If a measured write load makes it a bottleneck, replace it with a reader that waits for open transactions (compare a stored `pg_current_xact_id()` with `pg_snapshot_xmin`); never just remove it.
- **Append is the last step of a command handler**, so the lock is held briefly and nothing else runs under it.
- `Propagation.MANDATORY`: appending outside the command's transaction fails.
- The command service needs no JPA: `spring-boot-starter-jdbc`, Flyway and `JdbcClient`. Only the read side maps entities.

## Events

```java
/** Events of the Order aggregate: facts in the past tense, never changed once stored. */
public sealed interface OrderEvent permits OrderPlaced, OrderPaid, OrderCancelled {

    String TOPIC = "order-events";   // transport: kafka only
}

public record OrderPlaced(Long customerId, BigDecimal total) implements OrderEvent {
}

public record OrderPaid() implements OrderEvent {
}

public record OrderCancelled(String reason) implements OrderEvent {
}
```

- Immutable records under one sealed interface per aggregate, named in the past tense after what happened in the business (`OrderPaid`), not after the change to the data (`OrderStatusUpdated`).
- An event holds only what the fact adds. The aggregate id, the version and the time are on `StoredEvent`, not in the payload.
- Values only: ids, strings, numbers, dates, enum names, nested records. Never an entity, never a secret.
- Record names are stored as `event_type`, so they are unique across all aggregates and are never renamed.

```java
/** Maps the stored event type name to its record class, upcasting old types first. */
public class EventTypes {

    private final Map<String, Class<?>> types = new HashMap<>();
    private final Map<String, Upcaster> upcasters = new HashMap<>();
    private final JsonMapper jsonMapper;

    /** @param sealedEventTypes one sealed interface per aggregate; its permitted records are the event types */
    public EventTypes(JsonMapper jsonMapper, List<Upcaster> upcasters, Class<?>... sealedEventTypes) {
        this.jsonMapper = jsonMapper;
        for (Class<?> sealed : sealedEventTypes) {
            for (Class<?> type : sealed.getPermittedSubclasses()) {
                if (types.put(type.getSimpleName(), type) != null) {
                    throw new IllegalStateException("Duplicate event type name " + type.getSimpleName());
                }
            }
        }
        upcasters.forEach(upcaster -> this.upcasters.put(upcaster.fromType(), upcaster));
    }

    public boolean knows(String type) {
        return types.containsKey(type) || upcasters.containsKey(type);
    }

    public String name(Object event) {
        return event.getClass().getSimpleName();
    }

    public String serialize(Object event) {
        return jsonMapper.writeValueAsString(event);
    }

    public Object deserialize(String type, String payload) {
        if (!upcasters.containsKey(type)) {
            return jsonMapper.readValue(payload, eventClass(type));
        }
        // BigDecimal for decimals: a JSON tree would otherwise hold them as double and lose digits
        JsonNode node = jsonMapper.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readTree(payload);
        while (upcasters.containsKey(type)) {
            Upcaster upcaster = upcasters.get(type);
            node = upcaster.upcast((ObjectNode) node);
            type = upcaster.toType();
        }
        return jsonMapper.treeToValue(node, eventClass(type));
    }

    private Class<?> eventClass(String type) {
        Class<?> eventClass = types.get(type);
        if (eventClass == null) {
            throw new IllegalStateException("Unknown event type " + type);
        }
        return eventClass;
    }
}

@Configuration
public class EventStoreConfig {

    /** Lists the sealed event interface of every event-sourced aggregate. */
    @Bean
    EventTypes eventTypes(JsonMapper jsonMapper, List<Upcaster> upcasters) {
        return new EventTypes(jsonMapper, upcasters, OrderEvent.class);
    }
}
```

### Event Versioning and Upcasting

A stored event is never changed, so the code must read every shape an event ever had.

| Change | How |
|--------|-----|
| New optional field | Add it to the record; old events read it as `null`. Give it a meaning for `null` |
| New required field, renamed field, changed type, split or merged event | New record with a new name (`OrderPlacedV2`), plus an upcaster from the old type. Delete the old record |
| Event no longer produced | Keep the record and its `apply` case; old streams still contain it |

```java
/** Turns the stored JSON of an old event type into the JSON of its successor, when the event is read. */
public interface Upcaster {

    String fromType();

    String toType();

    ObjectNode upcast(ObjectNode payload);   // tools.jackson.databind.node.ObjectNode
}

/** OrderPlaced had no currency before 1.3.0; those orders were all in EUR. */
@Component
public class OrderPlacedToV2 implements Upcaster {

    public String fromType() { return "OrderPlaced"; }

    public String toType() { return "OrderPlacedV2"; }

    public ObjectNode upcast(ObjectNode payload) {
        return payload.put("currency", "EUR");
    }
}
```

- Upcasters run when an event is read, in a chain (`V1` → `V2` → `V3`). The stored row keeps its old type and payload.
- The aggregate and the projections only ever see the newest type.
- Every upcaster has a test with a real old payload.
- With separate services the query service reads old events from the topic, so it has the same upcasters. Add an upcaster to both services in the same change.
- Never write a migration that updates `payload` or `event_type`; the trigger rejects it.

## Aggregate

A plain class in `<aggregate>.domain`: no JPA mapping, no Spring type, no repository. It has two kinds of methods and keeps them apart.

```java
/** Event-sourced aggregate: no JPA mapping. State comes from past events; a command returns new events. */
public final class Order {

    public enum Status { PLACED, PAID, CANCELLED }

    private Status status;

    private Order() {
    }

    /** Rebuilds the state from the stream's events. */
    public static Order replay(List<OrderEvent> history) {
        Order order = new Order();
        history.forEach(order::apply);
        return order;
    }

    // Commands: check the rules against the current state and return the events; never change state here

    public static List<OrderEvent> place(Long customerId, BigDecimal total) {
        if (total.signum() < 0) {
            throw new DomainRuleException("Total must not be negative");
        }
        return List.of(new OrderPlaced(customerId, total));
    }

    public List<OrderEvent> pay() {
        if (status != Status.PLACED) {
            throw new DomainRuleException("Only a placed order can be paid; the order is " + status);
        }
        return List.of(new OrderPaid());
    }

    public List<OrderEvent> cancel(String reason) {
        if (status == Status.CANCELLED) {
            throw new DomainRuleException("The order is already cancelled");
        }
        return List.of(new OrderCancelled(reason));
    }

    // State changes: only here, and never rejecting - the event already happened

    private void apply(OrderEvent event) {
        switch (event) {
            case OrderPlaced e -> status = Status.PLACED;
            case OrderPaid e -> status = Status.PAID;
            case OrderCancelled e -> status = Status.CANCELLED;
        }
    }
}
```

- **Command methods decide.** They check the rules and return the new events. They do not change fields.
- **`apply` changes state** and nothing else: no validation, no exception, no clock, no id generation, no call to another class. Replaying the same events always gives the same state.
- The aggregate keeps only the state its rules need (here the status), not everything the read model shows.
- The `switch` has no `default`, so a new event type does not compile until the aggregate handles it.
- A broken rule throws `DomainRuleException` (a plain `RuntimeException` in `domain`), which the `@RestControllerAdvice` maps to 409 Problem Details with the message as `detail`.
- No delete: an aggregate ends with an event (`OrderCancelled`, `CustomerClosed`). Its stream stays.

## Command Handlers

Command records and the one-handler-per-command rule are as in `cqrs.md`. A handler loads the stream, checks the version the client saw, lets the aggregate decide and appends the result.

```java
/** Loads an order from its stream and checks the version the client saw. */
@Component
public class OrderStream {

    private final EventStore eventStore;

    public OrderStream(EventStore eventStore) {
        this.eventStore = eventStore;
    }

    public Order load(UUID orderId, long expectedVersion) {
        List<StoredEvent<OrderEvent>> history = eventStore.load(orderId, OrderEvent.class);
        if (history.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order " + orderId + " not found");
        }
        if (history.getLast().version() != expectedVersion) {
            throw new OptimisticLockingFailureException("Order " + orderId + " was changed by someone else");
        }
        return Order.replay(history.stream().map(StoredEvent::event).toList());
    }
}

@Service
public class PlaceOrderHandler {

    private final EventStore eventStore;

    public PlaceOrderHandler(EventStore eventStore) {
        this.eventStore = eventStore;
    }

    @Transactional
    public CommandResult handle(PlaceOrderCommand command) {
        UUID orderId = UUID.randomUUID();
        long version = eventStore.append("Order", orderId, 0, Order.place(command.customerId(), command.total()),
                Map.of("command", "PlaceOrderCommand"));
        return new CommandResult(orderId, version);
    }
}

@Service
public class PayOrderHandler {

    private final OrderStream orders;
    private final EventStore eventStore;

    public PayOrderHandler(OrderStream orders, EventStore eventStore) {
        this.orders = orders;
        this.eventStore = eventStore;
    }

    @Transactional
    public CommandResult handle(PayOrderCommand command) {
        Order order = orders.load(command.orderId(), command.version());
        long version = eventStore.append("Order", command.orderId(), command.version(), order.pay(),
                Map.of("command", "PayOrderCommand"));
        return new CommandResult(command.orderId(), version);
    }
}
```

```java
@ExceptionHandler
ProblemDetail handleVersionConflict(OptimisticLockingFailureException ex) {   // org.springframework.dao
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Record was changed by someone else; reload and retry");
}

@ExceptionHandler
ProblemDetail handleDomainRule(DomainRuleException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
}
```

- **A version conflict is 409** in two places, both as `OptimisticLockingFailureException`: the client's `version` is not the stream's current version, or a concurrent command appended first and the unique constraint rejected this one. This replaces `@Version`.
- Every command except the one that creates the aggregate carries the `version` from the last `CommandResult` or query response.
- `metadata` names at least the command. Add the user name and a trace or request id when the application has them.
- A handler appends to one stream. A process that spans aggregates is a saga (`design-patterns` skill), not one transaction over two streams.

### Command Endpoints

```java
public record CommandResult(UUID id, long version) {
}

@PostMapping
@ResponseStatus(HttpStatus.CREATED)
public CommandResult place(@Valid @RequestBody PlaceOrderCommand command) {
    return placeOrder.handle(command);
}

@PostMapping("/{id}/pay")
@ResponseStatus(HttpStatus.ACCEPTED)
public CommandResult pay(@PathVariable UUID id, @Valid @RequestBody PayOrderRequest request) {
    return payOrder.handle(new PayOrderCommand(id, request.version()));
}
```

| Command | Success |
|---------|---------|
| Creates an aggregate | 201 with `CommandResult` (the new id and version 1) |
| Any other command | 202 with `CommandResult`: the event is stored, the read model follows |

- Endpoints are named after the command (`POST /orders/{id}/pay`), not after a field update. A `PUT` that replaces the record does not fit: there is no record to replace, only things that happen.
- `{id}` is a `UUID`.
- The README and the OpenAPI description state that queries are eventually consistent: after a 201 or 202 a `GET` may still return the old state or 404.

## Projections

A projection applies events in the order of the global sequence and stores its position in the transaction that updates the read model. An event is then applied exactly once, even after a crash or a redelivery, and the projection needs no version check.

Projections apply what happened, so they depend on order: `OrderPaid` changes a row that `OrderPlaced` created.

### Single Service: Polling the Event Store

For a single service. The event store is in the same database, so the projection reads it directly. Neither Kafka nor Spring Modulith is used, and the transport question of the dialog is not asked (`transport: in-process`).

```sql
-- One row per projection: the global sequence of the last event it applied
CREATE TABLE query.projection_checkpoint (
    name     VARCHAR(100) NOT NULL,
    position BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_projection_checkpoint PRIMARY KEY (name)
);

INSERT INTO query.projection_checkpoint (name) VALUES ('order-view');
```

```java
/** Feeds a named projection with the events after its checkpoint and moves the checkpoint, in one transaction. */
@Component
public class Subscriptions {

    private final JdbcClient jdbc;
    private final EventStore eventStore;

    public Subscriptions(JdbcClient jdbc, EventStore eventStore) {
        this.jdbc = jdbc;
        this.eventStore = eventStore;
    }

    @Transactional
    public void poll(String name, Consumer<StoredEvent<Object>> handler) {
        // SKIP LOCKED: when another replica is running this projection, do nothing
        Optional<Long> position = jdbc.sql("SELECT position FROM query.projection_checkpoint WHERE name = :name FOR UPDATE SKIP LOCKED")
                .param("name", name).query(Long.class).optional();
        if (position.isEmpty()) {
            return;
        }
        List<StoredEvent<Object>> events = eventStore.readAfter(position.get(), 100);
        if (events.isEmpty()) {
            return;
        }
        events.forEach(handler);
        jdbc.sql("UPDATE query.projection_checkpoint SET position = :position WHERE name = :name")
                .param("position", events.getLast().globalSeq()).param("name", name).update();
    }

    /** Moves a projection back to the start; call it in the transaction that empties its tables. */
    @Transactional
    public void reset(String name) {
        jdbc.sql("UPDATE query.projection_checkpoint SET position = 0 WHERE name = :name").param("name", name).update();
    }
}

/**
 * Builds the order read model from the event store. The checkpoint moves in the transaction that
 * updates the read model, so every event is applied exactly once and in order.
 */
@Component
public class OrderProjection {

    public static final String NAME = "order-view";

    private final Subscriptions subscriptions;
    private final OrderViewRepository repository;

    public OrderProjection(Subscriptions subscriptions, OrderViewRepository repository) {
        this.subscriptions = subscriptions;
        this.repository = repository;
    }

    @Scheduled(fixedDelayString = "${projection.delay:200ms}")
    public void project() {
        subscriptions.poll(NAME, this::on);
    }

    /** Empties the read model and replays every event. */
    @Transactional
    public void rebuild() {
        repository.deleteAllInBatch();
        subscriptions.reset(NAME);
    }

    private void on(StoredEvent<Object> stored) {
        if (!(stored.event() instanceof OrderEvent event)) {
            return;   // an event of another aggregate
        }
        OrderView view = switch (event) {
            case OrderPlaced e -> {
                OrderView created = new OrderView(stored.streamId(), e.customerId(), e.total());
                created.changed("PLACED", stored.version(), stored.occurredAt());
                yield created;
            }
            case OrderPaid e -> change(stored, "PAID");
            case OrderCancelled e -> change(stored, "CANCELLED");
        };
        repository.save(view);
    }

    private OrderView change(StoredEvent<Object> stored, String status) {
        OrderView view = repository.findById(stored.streamId()).orElseThrow();
        view.changed(status, stored.version(), stored.occurredAt());
        return view;
    }
}
```

- `@EnableScheduling` on the application class. Each projection has its own checkpoint row, added by the migration that creates its tables.
- `SKIP LOCKED` on the checkpoint row lets only one replica run a projection at a time.
- The read model stores the stream `version` of the last event applied, and `<Resource>Response` returns it for the client's next command.
- **Rebuild:** `rebuild()` empties the tables and resets the checkpoint in one transaction; the next polls replay the store. Use it for a new read model, a changed one, or a fixed projection bug. Expose it only to operators (an Actuator endpoint behind a role, or a one-off job), never in the public API.
- A projection that throws stops at that event and retries it on every poll. Log it as an error and alert on a checkpoint that stops moving; do not skip events to get past it.

### Separate Services: From the Topic

The query service cannot read the event store. The command service relays the store to Kafka, and the query service's listener records the last `global-seq` it applied for each partition. Both are in `kafka.md` ("Event Sourcing").

## Snapshots

Do not generate snapshots. Add them only when a measured load problem exists: a command on a long stream is slow because of the replay, and the stream cannot be made shorter by modelling (for example a stream per order instead of per customer).

When they are needed: a `snapshot` table (`stream_id`, `version`, state as JSON) written every N events, and a load that starts from the latest snapshot and replays only the events after its version. A snapshot is a cache. It can be deleted at any time, and a change to the aggregate's state invalidates it.

## Personal Data

Events are never changed or deleted, so personal data in a payload cannot be erased later. No personal data goes into an event payload or its metadata without an erasure strategy documented in the README. Pick one before the first event is written:

| Strategy | How |
|----------|-----|
| Keep it out (default) | Events carry an id (`customerId`); the personal data lives in an ordinary table that can be updated and deleted |
| Crypto-shredding | Personal fields are encrypted with a key per person, kept outside the event store; deleting the key makes them unreadable |

The same applies to `metadata`: a user id, not a name or an e-mail address. Read models and logs that copy personal data are covered by the same erasure procedure.

## Review Signals

For the `code-reviewer` agent, when the architecture block says `style: cqrs-event-sourcing`. They add to the signals in `cqrs.md`.

| Signal in the diff | Severity | Rule |
|--------------------|----------|------|
| `@Entity`, `@Table` or a Spring Data repository on an aggregate in `domain`; an aggregate saved as current state | Critical | Aggregate |
| `UPDATE` or `DELETE` on `event_store`, in code or in a migration; the append-only trigger dropped | Critical | Event Store |
| A field removed, renamed or retyped in an existing event record, or a record renamed, without an upcaster | Critical | Event Versioning and Upcasting |
| Append without the advisory lock, or outside the command's transaction | Critical | Event Store |
| Personal data in an event or in metadata, with no erasure strategy in the README | Critical | Personal Data |
| `apply` that validates, throws, reads the clock, generates ids or calls another class | Important | Aggregate |
| Command method that changes the aggregate's fields | Important | Aggregate |
| Projection that updates its read model and its checkpoint in different transactions, or has no checkpoint | Important | Projections |
| Command without `version`, or a handler that does not pass it to `append` | Important | Command Handlers |
| One handler appending to two streams | Important | Command Handlers |
| Upcaster without a test, or (separate services) present in only one service | Important | Event Versioning and Upcasting |
| Event named after a data change (`OrderUpdated`, `StatusChanged`) | Code smell | Events |
| Snapshot code with no measurement that motivates it | Code smell | Snapshots |
| Command endpoint that returns the read model, or 200 where 202 is documented | Important | Command Endpoints |

## Quick Reference

| Need | Use |
|------|-----|
| Store events | `EventStore.append(streamType, streamId, expectedVersion, events, metadata)` |
| Load an aggregate | `EventStore.load(streamId, OrderEvent.class)`, then `Order.replay(...)` |
| Lost-update check | `expectedVersion` on append, `OptimisticLockingFailureException` → 409 |
| Read model | Projection with a checkpoint, `Subscriptions.poll` or the Kafka listener |
| Rebuild a read model | Empty its tables and reset its checkpoint in one transaction |
| Change an event | New record plus an `Upcaster`; never touch stored rows |
| Test an aggregate | Given events, when command, then events (`testing.md`) |
