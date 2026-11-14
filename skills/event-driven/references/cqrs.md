# CQRS (No Event Sourcing)

The write side stores the current state with JPA, as in a layered CRUD project. Every change also produces a domain event, and the read side builds its own tables from those events. Packages and modules are in `topology.md`.

Examples use an `Order` aggregate. Replace it with your aggregates.

## Write Side

The entity, its repository and its migration follow `conventions` and `jpa-patterns` unchanged: identity id, `@Version`, Flyway.

### Commands

A command is a record named `<Verb><Resource>Command`. It is the validated request body when the body holds everything. When the id comes from the path, the body is a `<Resource>UpdateRequest` and the controller builds the command.

```java
public record CreateOrderCommand(@NotNull Long customerId, @NotNull @DecimalMin("0.0") BigDecimal total) {
}

public record UpdateOrderRequest(@NotBlank String status, @NotNull @DecimalMin("0.0") BigDecimal total,
                                 @NotNull Long version) {
}

public record UpdateOrderCommand(Long orderId, String status, BigDecimal total, Long version) {
}

public record DeleteOrderCommand(Long orderId) {
}

/** What a command endpoint returns: the aggregate id and its new version. */
public record CommandResult(Long id, Long version) {
}
```

### Command Handlers

One class per command, named `<Command>Handler` without the `Command` suffix (`UpdateOrderHandler`), with one `@Transactional` method `handle`. Handlers replace the service layer; there is no command bus.

```java
@Service
public class UpdateOrderHandler {

    private final OrderRepository repository;
    private final Outbox outbox;

    public UpdateOrderHandler(OrderRepository repository, Outbox outbox) {
        this.repository = repository;
        this.outbox = outbox;
    }

    @Transactional
    public CommandResult handle(UpdateOrderCommand command) {
        Order order = repository.findById(command.orderId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order " + command.orderId() + " not found"));
        if (!command.version().equals(order.getVersion())) {
            throw new ObjectOptimisticLockingFailureException(Order.class, command.orderId());   // 409
        }
        order.change(command.status(), command.total());
        order = repository.saveAndFlush(order);          // flushed: the event needs the new version
        outbox.append(OrderEvent.TOPIC, order.getId(), new OrderUpdated(order.getId(), order.getVersion(),
                order.getCustomerId(), order.getStatus(), order.getTotal(), Instant.now()));
        return new CommandResult(order.getId(), order.getVersion());
    }
}
```

- The state change and the event are written in one transaction. With Kafka the event goes into the outbox table (`kafka.md`); with in-process events it is published with `ApplicationEventPublisher` (below). A handler never sends to Kafka.
- `saveAndFlush` before the event is created, so the event carries the version the database now holds.
- A stale `version` is 409 Problem Details without `errors`, exactly as in layered CRUD. No event is written.
- A delete publishes `<Resource>Deleted` with the last version plus one: `repository.delete(order)`, `repository.flush()`, then the event.
- A handler does not call another handler. Shared logic lives in the entity or in a domain class.

### Command Controller

```java
@RestController
@RequestMapping("/api/v1/orders")
public class OrderCommandController {

    private final CreateOrderHandler createOrder;
    private final UpdateOrderHandler updateOrder;
    private final DeleteOrderHandler deleteOrder;

    public OrderCommandController(CreateOrderHandler createOrder, UpdateOrderHandler updateOrder,
                                  DeleteOrderHandler deleteOrder) {
        this.createOrder = createOrder;
        this.updateOrder = updateOrder;
        this.deleteOrder = deleteOrder;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CommandResult create(@Valid @RequestBody CreateOrderCommand command) {
        return createOrder.handle(command);
    }

    @PutMapping("/{id}")
    public CommandResult update(@PathVariable Long id, @Valid @RequestBody UpdateOrderRequest request) {
        return updateOrder.handle(new UpdateOrderCommand(id, request.status(), request.total(), request.version()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        deleteOrder.handle(new DeleteOrderCommand(id));
    }
}
```

| Command | Success |
|---------|---------|
| Create | 201 with `CommandResult` |
| Update | 200 with `CommandResult` |
| Delete | 204 |

A command endpoint never returns the record: the read model may not have it yet. Document in the README and in the OpenAPI description that queries are eventually consistent, and that a client sends the `version` from `CommandResult` or from its last read.

## Events

One sealed interface per aggregate, with one record per kind of change. Names are in the past tense.

```java
/** Events of the Order aggregate. Each carries the aggregate's state after the change and its version. */
public sealed interface OrderEvent permits OrderCreated, OrderUpdated, OrderDeleted {

    String TOPIC = "order-events";   // transport: kafka only

    Long orderId();

    long version();
}

public record OrderCreated(Long orderId, long version, Long customerId, String status, BigDecimal total,
                           Instant occurredAt) implements OrderEvent {
}

public record OrderUpdated(Long orderId, long version, Long customerId, String status, BigDecimal total,
                           Instant occurredAt) implements OrderEvent {
}

public record OrderDeleted(Long orderId, long version, Instant occurredAt) implements OrderEvent {
}
```

- Every event has the aggregate id, the aggregate's `version` after the change and `occurredAt`.
- An event carries the **state after the change**, not the difference. A projection can then apply any event on its own, and a repeated or late event is recognised by its version.
- Events hold values only: ids, strings, numbers, dates, enums as strings, nested records. Never an entity.
- A published event is a contract. Add a field (old events then read it as `null`); never rename, remove or change the meaning of one. A change that cannot be an added field is a new event type.
- No secrets in events. Personal data goes into an event only when the read model needs it.

## Read Side

The read model is a table shaped for the queries, with the aggregate id as its primary key and the event's version as a plain column. It has no identity column and no `@Version`.

```java
/** Read model of an order. The id and the version come from the events, never from this database. */
@Entity
@Table(name = "order_view")
public class OrderView {

    @Id
    private Long id;

    @Column(nullable = false)
    private Long customerId;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(nullable = false)
    private BigDecimal total;

    @Column(nullable = false)
    private long version;

    @Column(nullable = false)
    private Instant updatedAt;

    protected OrderView() {
    }

    public OrderView(Long id) {
        this.id = id;
    }

    public void apply(Long customerId, String status, BigDecimal total, long version, Instant occurredAt) {
        this.customerId = customerId;
        this.status = status;
        this.total = total;
        this.version = version;
        this.updatedAt = occurredAt;
    }

    // getters
}
```

```sql
-- V1__create_order_view.sql
CREATE TABLE order_view (
    id          BIGINT         NOT NULL,
    customer_id BIGINT         NOT NULL,
    status      VARCHAR(16)    NOT NULL,
    total       NUMERIC(38, 2) NOT NULL,
    version     BIGINT         NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_order_view PRIMARY KEY (id)
);

CREATE INDEX ix_order_view_customer_id ON order_view (customer_id);
```

A read model may copy data from other aggregates (a customer name next to the order) so that a query needs no join. It then listens to that aggregate's events too.

### Projection

```java
/** Updates the order read model. Idempotent: an event that is not newer than the stored version is ignored. */
@Component
public class OrderProjection {

    private final OrderViewRepository repository;

    public OrderProjection(OrderViewRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void apply(OrderEvent event) {
        switch (event) {
            case OrderCreated e -> upsert(e, e.customerId(), e.status(), e.total(), e.occurredAt());
            case OrderUpdated e -> upsert(e, e.customerId(), e.status(), e.total(), e.occurredAt());
            case OrderDeleted e -> repository.deleteById(e.orderId());
        }
    }

    private void upsert(OrderEvent event, Long customerId, String status, BigDecimal total, Instant occurredAt) {
        OrderView view = repository.findById(event.orderId()).orElse(null);
        if (view != null && view.getVersion() >= event.version()) {
            return;   // duplicate or older event
        }
        if (view == null) {
            view = new OrderView(event.orderId());
        }
        view.apply(customerId, status, total, event.version(), occurredAt);
        repository.save(view);
    }
}
```

- The `switch` over the sealed interface has no `default`: a new event type does not compile until the projection handles it.
- The version check makes the projection idempotent. Events arrive at least once, so every projection needs it.
- A projection only writes read-model tables. It calls no command handler and no other service.
- With Kafka, the listener that feeds `apply` is in `kafka.md`.

### Queries and Query Handlers

A query is a record. One class per query, named `<Query>Handler` without the `Query` suffix, with one `@Transactional(readOnly = true)` method `handle`. It returns `<Resource>Response` records, never the read-model entity.

```java
public record GetOrderQuery(Long orderId) {
}

public record FindOrdersQuery(Long customerId, Pageable pageable) {
}

public record OrderResponse(Long id, Long customerId, String status, BigDecimal total, long version, Instant updatedAt) {

    public static OrderResponse from(OrderView view) {
        return new OrderResponse(view.getId(), view.getCustomerId(), view.getStatus(), view.getTotal(),
                view.getVersion(), view.getUpdatedAt());
    }
}

@Service
public class FindOrdersHandler {

    private final OrderViewRepository repository;

    public FindOrdersHandler(OrderViewRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> handle(FindOrdersQuery query) {
        Page<OrderView> page = query.customerId() == null
                ? repository.findAll(query.pageable())
                : repository.findByCustomerId(query.customerId(), query.pageable());
        return page.map(OrderResponse::from);
    }
}

@RestController
@RequestMapping("/api/v1/orders")
public class OrderQueryController {

    private final GetOrderHandler getOrder;
    private final FindOrdersHandler findOrders;

    public OrderQueryController(GetOrderHandler getOrder, FindOrdersHandler findOrders) {
        this.getOrder = getOrder;
        this.findOrders = findOrders;
    }

    @GetMapping
    public PagedModel<OrderResponse> list(@RequestParam(required = false) Long customerId, Pageable pageable) {
        return new PagedModel<>(findOrders.handle(new FindOrdersQuery(customerId, pageable)));
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable Long id) {
        return getOrder.handle(new GetOrderQuery(id));
    }
}
```

- The controller wraps the page in `PagedModel`; `Page` never leaves the controller (`conventions`).
- `<Resource>Response` includes `version`, so a client can send it with its next update command.
- A record that is not in the read model yet is a 404, like any missing record.

## In-Process Events (Spring Modulith)

For a single service with `transport: in-process`. Spring Modulith stores every event publication in a table in the command's transaction and runs the listener after the commit, in its own transaction. A listener that fails or is cut off by a restart is run again, so no outbox and no Kafka are needed.

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.modulith</groupId>
            <artifactId>spring-modulith-bom</artifactId>
            <version>2.1.2</version> <!-- 2.x for Spring Boot 4; use the latest release -->
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependency>
    <groupId>org.springframework.modulith</groupId>
    <artifactId>spring-modulith-starter-jdbc</artifactId>
</dependency>
```

```yaml
spring:
  modulith:
    events:
      jdbc:
        schema: command
        schema-initialization:
          enabled: false            # the event_publication table is a Flyway migration
      republish-outstanding-events-on-restart: true
```

```sql
-- V2__create_event_publication.sql: Spring Modulith event publication registry (schema of Spring Modulith 2.x)
CREATE TABLE command.event_publication (
    id                     UUID NOT NULL,
    listener_id            TEXT NOT NULL,
    event_type             TEXT NOT NULL,
    serialized_event       TEXT NOT NULL,
    publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date        TIMESTAMP WITH TIME ZONE,
    status                 TEXT,
    completion_attempts    INT,
    last_resubmission_date TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_event_publication PRIMARY KEY (id)
);

CREATE INDEX ix_event_publication_serialized_event ON command.event_publication USING hash (serialized_event);
CREATE INDEX ix_event_publication_completion_date ON command.event_publication (completion_date);
```

Take the columns from the appendix of the Spring Modulith reference (event publication registry schemas) for the version in use; they changed between 1.x and 2.x.

The handler publishes the event instead of writing to the outbox:

```java
@Transactional
public CommandResult handle(CreateOrderCommand command) {
    Order order = repository.saveAndFlush(new Order(command.customerId(), command.total()));
    events.publishEvent(new OrderCreated(order.getId(), order.getVersion(),
            order.getCustomerId(), order.getStatus(), order.getTotal(), Instant.now()));   // ApplicationEventPublisher
    return new CommandResult(order.getId(), order.getVersion());
}
```

The projection listens with `@ApplicationModuleListener` (asynchronous, after commit, in a new transaction):

```java
/**
 * Updates the order read model after the command's transaction commits, in its own transaction.
 * Listeners run asynchronously and in no fixed order, so every change is guarded by the event's version.
 */
@Component
public class OrderProjection {

    private final OrderViewRepository repository;

    public OrderProjection(OrderViewRepository repository) {
        this.repository = repository;
    }

    @ApplicationModuleListener
    public void on(OrderEvent event) {
        OrderView view = repository.findForUpdate(event.orderId()).orElseGet(() -> new OrderView(event.orderId()));
        if (view.getUpdatedAt() != null && view.getVersion() >= event.version()) {
            return;   // duplicate or older event
        }
        switch (event) {
            case OrderCreated e -> view.apply(e.customerId(), e.status(), e.total(), e.version(), e.occurredAt());
            case OrderUpdated e -> view.apply(e.customerId(), e.status(), e.total(), e.version(), e.occurredAt());
            case OrderDeleted e -> view.markDeleted(e.version(), e.occurredAt());
        }
        repository.save(view);
    }
}
```

- **Lock the row.** Two events of one aggregate can be handled at the same time. The projection loads the row with a lock, so the version check and the write of one event finish before the next event is read:

  ```java
  /** For the projection: locks the row, so two events of one order are applied one after the other. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT v FROM OrderView v WHERE v.id = :id")
  Optional<OrderView> findForUpdate(@Param("id") Long id);
  ```

  With Kafka this is not needed: one partition delivers the events of an aggregate one at a time.
- **Deletes keep the row.** In-process listeners have no order, so a late `OrderUpdated` could bring back a row that `OrderDeleted` removed. The read model gets a `deleted BOOLEAN NOT NULL DEFAULT FALSE` column, `markDeleted` sets it together with the version, and every query filters on it (`findByIdAndDeletedFalse`, `findByDeletedFalse(Pageable)`). With Kafka the events of one aggregate arrive in order, and the projection deletes the row.
- Never use a plain `@EventListener` or `@TransactionalEventListener` for a projection: the first updates the read model inside the command's transaction, the second loses the event when the application stops between commit and listener.
- `republish-outstanding-events-on-restart: true` runs unfinished publications again on startup. With several replicas, leave it off and resubmit with `IncompleteEventPublications` from a scheduled job instead.
- Completed publications stay in the table. Remove them on a schedule (`CompletedEventPublications`), or set `spring.modulith.events.completion-mode: delete`.
- To move to Kafka later, replace the publication with the outbox and the listener method with a `@KafkaListener`. Commands, events, queries and read models stay as they are.

## Review Signals

For the `code-reviewer` agent, when the architecture block says `style: cqrs`. Cite the rule by its section in this skill.

| Signal in the diff | Severity | Rule |
|--------------------|----------|------|
| `KafkaTemplate` used in a handler, controller or any class other than the outbox relay, or a `send` inside a business `@Transactional` method | Critical | `kafka.md`, Transactional Outbox |
| Event written to the outbox outside the command's transaction, or before the entity is flushed | Critical | Command Handlers |
| Projection without a version check | Important | Projection |
| `@EventListener` or `@TransactionalEventListener` updating a read model | Important | In-Process Events |
| `query` or `projection` class importing from `command`, or a `domain` entity; the query service's `pom.xml` depending on the command service | Important | `topology.md` |
| A field renamed or removed in an existing event record, or its type changed | Important | Events |
| Query endpoint returning a JPA entity or `Page`; command endpoint returning `<Resource>Response` | Important | Queries and Query Handlers, Command Controller |
| `GET` mapping in the command service, or a write mapping in the query service | Important | `topology.md` |
| Update command without `version`, or a handler that does not compare it | Important | Command Handlers |
| Listener without an error handler and dead-letter topic | Important | `kafka.md`, Consumers |
| Event carrying an entity, a secret or personal data the read model does not use | Important | Events |
| Handler calling another handler | Code smell | Command Handlers |

Do not flag the missing service layer, or a controller that calls a handler directly: the architecture block is the documented decision.
