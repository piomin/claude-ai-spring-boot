# Topology - Separate Services or a Single Service

The architecture block (`conventions` skill, "Architecture Decision") names the topology. Both use the same four packages per aggregate, so a single service can be split later without changing the code inside them.

| Package | Holds | May depend on |
|---------|-------|---------------|
| `<aggregate>.domain` | The write-side JPA entity and its repository | `domain.event` |
| `<aggregate>.domain.event` | Event records under one sealed interface; no Spring, JPA or Jackson types | nothing |
| `<aggregate>.command` | Command records, command handlers, the command controller | `domain`, `domain.event` |
| `<aggregate>.projection` | Listeners that update the read model from events | `domain.event`, `query` |
| `<aggregate>.query` | Read-model entity and repository, query records, query handlers, `<Resource>Response`, the query controller | nothing of the above |

`query` and `projection` never use `command` or the `domain` entities. `command` and `domain` never use `query` or `projection`. Shared infrastructure (`outbox`, `config`, `exception`) sits next to the aggregate packages.

Aggregates outside the `scope` of the architecture block stay layered CRUD. With separate services they do not belong in the command or the query service: keep them in their own service, or add them to the scope.

## Separate Command and Query Services (default)

A Maven multi-module build. `<name>` is the parent directory name, as for any artifact (`conventions`).

```
<name>/
├── pom.xml                      # packaging pom, parent spring-boot-starter-parent, lists the modules
├── <name>-events/               # <aggregate>.domain.event - records only, no dependencies
├── <name>-command-service/
│   └── src/main/
│       ├── java/pl/piomin/services/
│       │   ├── <aggregate>/command/
│       │   ├── <aggregate>/domain/
│       │   ├── outbox/          # Outbox, OutboxRelay, topic configuration
│       │   └── exception/
│       └── resources/db/migration/   # write tables and the outbox table
├── <name>-query-service/
│   └── src/main/
│       ├── java/pl/piomin/services/
│       │   ├── <aggregate>/projection/
│       │   ├── <aggregate>/query/
│       │   ├── config/          # Kafka error handler, dead-letter topic
│       │   └── exception/
│       └── resources/db/migration/   # read-model tables
├── docker-compose.yml           # both services' dependencies: two databases and Kafka
├── deploy/                      # one kustomize tree for both services
└── skaffold.yaml
```

```xml
<!-- <name>/pom.xml -->
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.1.2</version> <!-- use the latest Spring Boot 4.x release -->
    <relativePath/>
</parent>
<groupId>pl.piomin.services</groupId>
<artifactId>orders</artifactId>
<version>1.0.0</version>
<packaging>pom</packaging>
<modules>
    <module>orders-events</module>
    <module>orders-command-service</module>
    <module>orders-query-service</module>
</modules>
```

```xml
<!-- both services -->
<dependency>
    <groupId>pl.piomin.services</groupId>
    <artifactId>orders-events</artifactId>
    <version>${project.version}</version>
</dependency>
```

Rules:

- **`-events` module:** event records and their sealed interface, nothing else. Its `pom.xml` has no `<dependencies>`. It is the contract between the two services.
- **Command service:** owns the write database, the outbox and the Kafka producer. It exposes only command endpoints (`POST`, `PUT`, `DELETE`).
- **Query service:** owns its own read database and the Kafka consumers. It exposes only query endpoints (`GET`). It never calls the command service and has no dependency on its module.
- **Same paths, two base URLs.** Both services use the same resource path (`/api/v1/orders`): writes go to the command service, reads to the query service. No gateway is generated; the README lists both base URLs. Locally the command service listens on 8080 and the query service on 8081 (`server.port: ${SERVER_PORT:8081}`).
- **Flyway per service.** Each service has its own `db/migration` and its own schema history. A change to an event ships with the migrations of both services when both need one.
- **Shared code is not shared.** Each service has its own `ApiExceptionHandler` and configuration. Only events are shared, so the `-events` module stays free of Spring.
- **Version.** The modules share the parent's version. A change to an event is a contract change and bumps it.

### Docker Compose

One `docker-compose.yml` at the root starts what both services need: one PostgreSQL per service and Kafka. Healthchecks follow the `spring-boot` skill, `references/cloud.md`; the Kafka service is in `references/kafka.md`.

```yaml
services:
  command-db:
    image: postgres:18
    environment:
      POSTGRES_DB: orders_command
      POSTGRES_USER: orders
      POSTGRES_PASSWORD: orders
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD", "pg_isready", "-h", "127.0.0.1", "-U", "orders", "-d", "orders_command"]
      interval: 5s
      start_period: 10s
    volumes:
      - command-db:/var/lib/postgresql
  query-db:
    image: postgres:18
    environment:
      POSTGRES_DB: orders_query
      POSTGRES_USER: orders
      POSTGRES_PASSWORD: orders
    ports:
      - "5433:5432"             # the query service's default datasource URL uses 5433
    healthcheck:
      test: ["CMD", "pg_isready", "-h", "127.0.0.1", "-U", "orders", "-d", "orders_query"]
      interval: 5s
      start_period: 10s
    volumes:
      - query-db:/var/lib/postgresql
  # kafka: see references/kafka.md
volumes:
  command-db:
  query-db:
```

When the Compose file also runs the two services, each `depends_on` its database and Kafka with `condition: service_healthy`, and reaches Kafka on the internal listener (`kafka:29092`).

### Images, Deployment and CI

- **Images:** one image per service, built with Paketo Buildpacks as in every other project: `./mvnw install -DskipTests`, then `./mvnw -pl <name>-command-service,<name>-query-service spring-boot:build-image`. Add a Dockerfile only when the project already builds with one. The `spring-boot-maven-plugin` is declared in the two service modules, not in the parent or in `-events`.
- **Kubernetes:** one `deploy/` tree and one `skaffold.yaml` for the project, laid out as in the `spring-boot` skill, `references/cloud.md`. `deploy/base` holds a Deployment, a Service and a ConfigMap for each service, plus the two databases and Kafka. `skaffold.yaml` lists two artifacts.
- **Kafka in the cluster** gets a PVC, `strategy: Recreate` and startup, readiness and liveness probes, like PostgreSQL. It advertises its Service name (`kafka:9092`). The services read `SPRING_KAFKA_BOOTSTRAP_SERVERS` from their ConfigMaps.
- **Replicas:** both services can run several replicas. The outbox relay and the consumer group are safe for that (`references/kafka.md`).
- **CI:** `./mvnw verify` at the root builds and tests all modules. Test reports are under `*/target/surefire-reports`, and the Maven cache key covers every `pom.xml`.

## Single Service

One Maven module with the same packages:

```
src/main/java/pl/piomin/services/
├── <aggregate>/
│   ├── command/
│   ├── domain/
│   │   └── event/
│   ├── projection/
│   └── query/
├── outbox/          # transport: kafka only
├── config/
└── exception/
```

Rules:

- **One controller per side:** `<Resource>CommandController` in `command`, `<Resource>QueryController` in `query`, on the same resource path.
- **Schemas.** The read and the write model may share one PostgreSQL database, in the schemas `command` and `query`:

  ```yaml
  spring:
    flyway:
      schemas: command,query   # created by Flyway; the first one holds the schema history
  ```

  Migrations and entities name the schema (`CREATE TABLE command.orders`, `@Table(name = "orders", schema = "command")`). No foreign key and no join crosses the two schemas.
- **Transport** is the user's choice: Kafka with the outbox (`references/kafka.md`), or in-process events with Spring Modulith (`references/cqrs.md`). Either way the read model is updated after the command's transaction, never inside it.
- **Extraction rule.** The package split must allow a later split into two services: `domain.event` becomes the `-events` module, `command` and `domain` the command service, `projection` and `query` the query service. A test enforces the dependency rules:

  ```java
  /** Keeps the package split extractable into separate command and query services. */
  @AnalyzeClasses(packages = "pl.piomin.services", importOptions = ImportOption.DoNotIncludeTests.class)
  class ArchitectureTest {

      @ArchTest
      static final ArchRule readSideDoesNotUseWriteSide = noClasses()
              .that().resideInAnyPackage("..query..", "..projection..")
              .should().dependOnClassesThat().resideInAnyPackage("..command..")
              .orShould().dependOnClassesThat().resideInAPackage("pl.piomin.services..domain");

      @ArchTest
      static final ArchRule writeSideDoesNotUseReadSide = noClasses()
              .that().resideInAnyPackage("..command..", "..domain..")
              .should().dependOnClassesThat().resideInAnyPackage("..query..", "..projection..");
  }
  ```

  ```xml
  <dependency>
      <groupId>com.tngtech.archunit</groupId>
      <artifactId>archunit-junit5</artifactId>
      <version>1.5.1</version> <!-- not managed by Spring Boot; use the latest release -->
      <scope>test</scope>
  </dependency>
  ```

  Write the base package in full (`pl.piomin.services..domain`): `..domain` alone also matches `org.springframework.data.domain`.

## Quick Reference

| Question | Separate services | Single service |
|----------|-------------------|----------------|
| Build | Multi-module: `-events`, `-command-service`, `-query-service` | One module |
| Database | One per service | One, schemas `command` and `query` |
| Transport | Kafka with the outbox | Kafka with the outbox, or Spring Modulith |
| Endpoints | Writes on the command service, `GET` on the query service | Both in one application, two controllers |
| Enforced by | Module dependencies (the query service has none on the command service) | `ArchitectureTest` |
