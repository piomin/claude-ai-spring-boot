# Testing a CQRS Project

Adds to the `testing-patterns` skill; its rules (positive and negative cases, `RestTestClient`, Testcontainers with `@ServiceConnection`, the real database) apply unchanged.

| What | Kind of test | Needs |
|------|--------------|-------|
| Command handler logic | Unit test (Mockito) | nothing |
| Command → outbox → Kafka | Integration test in the command service | PostgreSQL, Kafka |
| Kafka → projection → query | Integration test in the query service | PostgreSQL, Kafka |
| Command → read model, single service | End-to-end test | PostgreSQL (and Kafka for `transport: kafka`) |
| Package dependencies, single service | `ArchitectureTest` (`topology.md`) | nothing |

With separate services, each service is tested alone against Kafka. No test starts both. The `-events` module is the contract: the command service's tests assert what is published, and the query service's tests publish the same event records.

## Dependencies

```xml
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-webmvc-test</artifactId><scope>test</scope></dependency>
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa-test</artifactId><scope>test</scope></dependency>
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-kafka-test</artifactId><scope>test</scope></dependency>
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-resttestclient</artifactId><scope>test</scope></dependency>
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-testcontainers</artifactId><scope>test</scope></dependency>
<dependency><groupId>org.testcontainers</groupId><artifactId>testcontainers-junit-jupiter</artifactId><scope>test</scope></dependency>
<dependency><groupId>org.testcontainers</groupId><artifactId>testcontainers-postgresql</artifactId><scope>test</scope></dependency>
<dependency><groupId>org.testcontainers</groupId><artifactId>testcontainers-kafka</artifactId><scope>test</scope></dependency>
```

Awaitility comes with the Spring Boot test starters.

## Containers

Declare the containers as beans. They then stop after the beans that use them; with `static @Container` fields the broker is gone while the relay and the listeners are still running, and the build waits for their timeouts.

```java
/** Containers as beans: they stop after the beans that use them, so the context shuts down cleanly. */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {              // org.testcontainers.postgresql.PostgreSQLContainer
        return new PostgreSQLContainer("postgres:18");
    }

    @Bean
    @ServiceConnection
    KafkaContainer kafka() {                      // org.testcontainers.kafka.KafkaContainer
        return new KafkaContainer("apache/kafka:4.1.0");
    }
}
```

Use `org.testcontainers.kafka.KafkaContainer` with an `apache/kafka` image. Testcontainers 2 container classes have no type parameter.

## Command Handler (unit test)

```java
@ExtendWith(MockitoExtension.class)
class UpdateOrderHandlerTest {

    @Mock
    OrderRepository repository;

    @Mock
    Outbox outbox;

    @InjectMocks
    UpdateOrderHandler handler;

    @Test
    void handle_staleVersion_throwsAndWritesNoEvent() {
        Order order = new Order(7L, new BigDecimal("10.00"));           // version is null until persisted
        when(repository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> handler.handle(new UpdateOrderCommand(1L, "PAID", new BigDecimal("10.00"), 5L)))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        verify(repository, never()).saveAndFlush(any());
        verify(outbox, never()).append(any(), any(), any());
    }
}
```

Cover for every handler: the event that is written (type and fields), and that a rejected command (not found, stale version, broken rule) writes none.

## Command Service: Outbox and Relay (integration test)

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfig.class)
class OrderCommandIntegrationTest {

    @Autowired
    RestTestClient restTestClient;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    KafkaContainer kafka;

    @Test
    void createOrder_validCommand_publishesOrderCreated() {
        CommandResult created = create();
        assertThat(created.version()).isZero();

        ConsumerRecord<String, String> record = eventsOf(created.id()).getFirst();
        assertThat(header(record)).isEqualTo("OrderCreated");
        assertThat(record.value()).contains("\"orderId\":" + created.id(), "\"version\":0");
        await().untilAsserted(() -> assertThat(unpublished(created.id())).isZero());
    }

    @Test
    void updateOrder_staleVersion_returns409AndPublishesNothing() {
        CommandResult created = create();

        restTestClient.put().uri("/api/v1/orders/{id}", created.id())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new UpdateOrderRequest("PAID", new BigDecimal("10.00"), 99L))
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.status").isEqualTo(409);

        assertThat(jdbc.sql("SELECT count(*) FROM outbox_event WHERE aggregate_id = ?")
                .param(String.valueOf(created.id())).query(Long.class).single()).isEqualTo(1);
    }

    private CommandResult create() {
        return restTestClient.post().uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateOrderCommand(7L, new BigDecimal("10.00")))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(CommandResult.class)
                .returnResult().getResponseBody();
    }

    private long unpublished(Long orderId) {
        return jdbc.sql("SELECT count(*) FROM outbox_event WHERE aggregate_id = ? AND published_at IS NULL")
                .param(String.valueOf(orderId)).query(Long.class).single();
    }

    private static String header(ConsumerRecord<String, String> record) {
        return new String(record.headers().lastHeader(OutboxRelay.EVENT_TYPE_HEADER).value(), StandardCharsets.UTF_8);
    }

    /** Reads the topic from the beginning and returns the records keyed by the order id. */
    private List<ConsumerRecord<String, String>> eventsOf(Long orderId) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + System.nanoTime(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        List<ConsumerRecord<String, String>> found = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(OrderEvent.TOPIC));
            await().atMost(Duration.ofSeconds(30)).until(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(record -> {
                    if (String.valueOf(orderId).equals(record.key())) {
                        found.add(record);
                    }
                });
                return !found.isEmpty();
            });
        }
        return found;
    }
}
```

Assert on the record's key (the aggregate id), the `event-type` header and the payload, and that the outbox row gets its `published_at`. Negative cases: a stale version and an invalid command leave the outbox unchanged. Keep the Flyway assertion from `testing-patterns` (`flyway.info().pending()` is empty).

## Query Service: Projection (integration test)

The test publishes the events itself, with the `KafkaTemplate` the service already has for its dead-letter topic.

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfig.class)
class OrderProjectionIntegrationTest {

    @Autowired
    RestTestClient restTestClient;

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    JsonMapper jsonMapper;

    @Autowired
    OrderViewRepository repository;

    @Autowired
    KafkaContainer kafka;

    @Test
    void duplicateAndOlderEvents_areIgnored() {
        OrderUpdated paid = new OrderUpdated(2L, 2, 7L, "PAID", new BigDecimal("20.00"), Instant.now());
        publish(new OrderCreated(2L, 0, 7L, "NEW", new BigDecimal("20.00"), Instant.now()));
        publish(paid);
        publish(paid);                                                                             // redelivery
        publish(new OrderUpdated(2L, 1, 7L, "PENDING", new BigDecimal("20.00"), Instant.now()));   // older
        publish(new OrderCreated(3L, 0, 7L, "NEW", BigDecimal.ONE, Instant.now()));                // marker: all above consumed

        await().until(() -> repository.existsById(3L));
        restTestClient.get().uri("/api/v1/orders/2").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("PAID").jsonPath("$.version").isEqualTo(2);
    }

    @Test
    void brokenPayload_goesToDeadLetterTopicAndLaterEventsAreProcessed() {
        send("6", "OrderCreated", "{not json");
        publish(new OrderCreated(6L, 0, 7L, "NEW", BigDecimal.ONE, Instant.now()));

        await().atMost(Duration.ofSeconds(30)).until(() -> repository.existsById(6L));

        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-dlt",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(OrderEvent.TOPIC + "-dlt"));
            await().atMost(Duration.ofSeconds(30)).until(() -> consumer.poll(Duration.ofMillis(500)).count() == 1);
        }
    }

    private void publish(OrderEvent event) {
        send(String.valueOf(event.orderId()), event.getClass().getSimpleName(), jsonMapper.writeValueAsString(event));
    }

    private void send(String key, String type, String payload) {
        ProducerRecord<String, String> record = new ProducerRecord<>(OrderEvent.TOPIC, key, payload);
        record.headers().add("event-type", type.getBytes(StandardCharsets.UTF_8));
        kafkaTemplate.send(record).join();
    }
}
```

Cover for every projection:

- the read model appears after a created event, and the list query returns it in a `PagedModel`;
- a repeated event and an older event change nothing;
- a deleted event removes the record (404 afterwards);
- a broken record ends on the dead-letter topic and the events after it are still processed.

Each test uses its own aggregate ids and filter values: the tests share one topic and one database.

## Awaiting the Read Model

The read model is updated after the command returns, so a test waits for it.

- Wait on the repository or the table, then assert over HTTP once. Retrying a `RestTestClient` assertion inside `await()` logs an error for every failed attempt.
- To prove that something did **not** change, publish a marker event afterwards and wait for the marker. A fixed sleep is slow and still unreliable.
- Never use `Thread.sleep`.

## Single Service: End to End

Send a command with `RestTestClient`, wait for the read model, then query it.

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OrderEndToEndTest {

    @Autowired
    RestTestClient restTestClient;

    @Autowired
    JdbcClient jdbc;

    @Test
    void createAndUpdate_readModelFollows() {
        CommandResult created = restTestClient.post().uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateOrderCommand(7L, new BigDecimal("10.00")))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(CommandResult.class).returnResult().getResponseBody();
        awaitViewVersion(created.id(), 0);
        restTestClient.get().uri("/api/v1/orders/{id}", created.id()).exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("NEW");

        restTestClient.put().uri("/api/v1/orders/{id}", created.id())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new UpdateOrderRequest("PAID", new BigDecimal("10.00"), created.version()))
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.version").isEqualTo(1);
        awaitViewVersion(created.id(), 1);
        restTestClient.get().uri("/api/v1/orders/{id}", created.id()).exchange()
                .expectBody().jsonPath("$.status").isEqualTo("PAID");
    }

    /** Waits on the read-model table: quiet, unlike retrying an HTTP assertion. */
    private void awaitViewVersion(Long orderId, long version) {
        await().until(() -> jdbc.sql("SELECT version FROM query.order_view WHERE id = ?")
                .param(orderId).query(Long.class).optional().filter(v -> v == version).isPresent());
    }
}
```

- Also cover: a delete makes the query return 404, and a stale version returns 409 and leaves the read model unchanged.
- With Spring Modulith, assert that no publication is left unfinished: `SELECT count(*) FROM command.event_publication WHERE completion_date IS NULL` reaches zero.
- `@DirtiesContext(AFTER_CLASS)` closes the application before the JVM stops. Spring Modulith reads its publication table on shutdown; when the containers are stopped by a JVM shutdown hook (Podman with `TESTCONTAINERS_RYUK_DISABLED=true`), the database can be gone first and the build then waits 30 seconds per module.

## Running

`./mvnw clean verify` at the root, as in `testing-patterns` (including the Podman settings). Check that the integration tests ran and were not skipped: every module that has them needs a container engine.
