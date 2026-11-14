---
name: conventions
description: Spring Boot project conventions and mandatory rules. Auto-loads when working with Spring Boot, Java, or Maven projects, and before a new Spring Boot project or REST API is generated - it asks for the architecture style (layered CRUD, CQRS, Event Sourcing) first. Enforces No-Lombok, constructor injection, RestClient, Flyway for relational databases, Problem Details errors, and other standards; adds OpenAPI contract rules when the API is built for a UI.
metadata:
  version: "1.2.0"
  domain: backend
  triggers: Spring Boot, Java, Maven, Spring Framework, REST API, new project, architecture, CQRS, Event Sourcing, JPA, Hibernate, microservices, Flyway, database migration, PostgreSQL, MySQL, OpenAPI, springdoc, Problem Details, Actuator
  role: guide
  scope: conventions
  output-format: code
---

# Spring Boot Project Conventions

These conventions apply to ALL code generated or reviewed in the project. Follow them strictly.

## Architecture Decision

Decide the architecture style before generating any code. The decision is recorded once in the project's `CLAUDE.md` (`AGENTS.md` in Codex) and followed in every later session.

| Situation | Action |
|-----------|--------|
| New project: no build file (`pom.xml`, `build.gradle*`) and no architecture block | **Ask**, even when the prompt does not mention architecture |
| The prompt already states a choice | Skip that question, confirm the choice in one line, ask the remaining ones |
| The architecture block exists | Never ask again; follow it |
| Existing project without the block | Layered CRUD; ask only when the user requests CQRS or Event Sourcing |

The architecture block is a `## Architecture` section that has a `style:` line. A `## Architecture` heading without `style:` is a description, not a decision.

**Who asks.** The dialog runs in the main conversation, one question at a time, with the default listed first. A subagent that is started without a decision does not ask and does not generate code: it stops and returns the question to the main conversation.

| # | Question | Options (default first) | Asked when |
|---|----------|-------------------------|------------|
| 1 | Architecture style | Layered CRUD / CQRS / CQRS + Event Sourcing | always |
| 2 | Scope | Which aggregates or bounded contexts | style is not CRUD |
| 3 | Topology | Separate command and query services / Single service | style is not CRUD |
| 4 | Event transport | Apache Kafka / In-process events (Spring Modulith) | single service only; separate services always use Kafka |
| 5 | Read model store | PostgreSQL / another store named by the user | style is not CRUD |
| 6 | Event store | PostgreSQL append-only table / Axon Framework | Event Sourcing (not yet supported) |

With question 1, state the cost in two lines:

> CQRS: a query right after a command may still return the old state (eventual consistency), and there is more code: commands, queries, handlers, events and projections.
> Event Sourcing adds event versioning: a published event is never changed, so every change to it needs a new version.

If the user picks layered CRUD, ask nothing else.

**Not yet supported.** Reply "not yet supported in this version of the plugin" and ask again between the supported options. Never fall back silently:

- **CQRS + Event Sourcing** and **Axon Framework**, whether picked in the dialog, stated in the prompt, or found as `style: cqrs-event-sourcing` in an existing block.
- **CQRS for an API built for a UI** (see "REST API Built for a UI"): the UI contract expects one API that returns the full record on every write. Offer layered CRUD only.

**Record the decision** before generating code. Create the file if the project has none, and keep the rest of an existing file:

    ## Architecture
    style: cqrs
    scope: Order, Payment
    topology: separate-services
    transport: kafka
    read-store: postgresql

| Key | Values |
|-----|--------|
| `style` | `layered-crud`, `cqrs` (`cqrs-event-sourcing` is reserved) |
| `scope` | aggregate or bounded context names |
| `topology` | `separate-services`, `single-service` |
| `transport` | `kafka`, `in-process` |
| `read-store` | `postgresql`, or the store the user named |

Layered CRUD is recorded as the single line `style: layered-crud`, so a subagent or a later session does not ask again. Record a decision only after the dialog ran: an existing project without the block is layered CRUD as it is, and gets no block added.

**What each style replaces.** Every rule in this skill applies to every style, except:

| Rule | Layered CRUD | CQRS |
|------|--------------|------|
| Layering and packages | Controller → Service → Repository (`spring-boot` skill, Architecture Patterns) | Controller → handler → repository, in `command`, `query`, `domain`, `projection` packages |
| `@Transactional(readOnly = true)` at service class level | applies | `@Transactional` on command handlers, `@Transactional(readOnly = true)` on query handlers |
| Request records | `<Resource>CreateRequest`, `<Resource>UpdateRequest` | command records (`Create<Resource>Command`, ...) |
| What a write returns | the record (`<Resource>Response`) | `CommandResult` with the id and the new `version`; only queries return `<Resource>Response` |
| Identity columns for ids | every table | write side only; a read-model table takes its id from the events |

When the style is `cqrs`, load the `event-driven` skill and follow it. It holds the topology, the CQRS rules, Kafka and testing.

## Mandatory Rules

- **No Lombok** — write explicit constructors, getters, setters; use `LoggerFactory.getLogger()` for loggers
- **Constructor injection** — no `@Autowired` on fields
- **RestClient** over RestTemplate/WebClient for blocking HTTP calls
- **RestTestClient** with `@AutoConfigureRestTestClient` for integration tests
- **Testcontainers** with `@ServiceConnection` (not `@DynamicPropertySource`)
- **Jakarta Persistence** (not `javax.persistence`)
- **Java records** for DTOs: `<Resource>Response`, `<Resource>CreateRequest`, `<Resource>UpdateRequest`; controllers never expose entities
- **Maven** for dependency management
- **Group ID**: `pl.piomin.services`
- **Artifact name** = parent directory name
- **Semantic versioning** from `1.0.0` — PATCH by default, MINOR for a backward-compatible feature, MAJOR for a breaking API change; every OpenAPI contract change bumps the version
- **Docker Compose** for local dependencies
- **CI pipeline** — generate CI/CD config; detect existing CI setup (`.github/workflows/`, `.circleci/`, `.gitlab-ci.yml`) and match it, or default to a sensible choice
- **Tests** — always write both positive and negative test cases
- Update `README.md` on each new version

## Code Quality Standards

- Use latest Spring Boot 4.x and Java 21+
- `@Transactional(readOnly = true)` at service class level, `@Transactional` on mutating methods
- `@Valid @RequestBody` on all POST/PUT endpoints
- `@RestControllerAdvice` for global exception handling
- Return `ResponseEntity` with proper status codes (201 Created, 204 No Content)
- `@ConfigurationProperties` for type-safe config (no raw `@Value` for groups)
- `@HttpExchange` interfaces for declarative HTTP clients
- Virtual threads enabled by default (`spring.threads.virtual.enabled: true`)
- Problem Details enabled (`spring.mvc.problemdetails.enabled: true`); every error, including the catch-all 500, is RFC 9457 Problem Details, and the catch-all never returns the exception message
- List endpoints return `PagedModel`; never `Page`/`PageImpl`
- Optimistic locking with `@Version`; a lost update returns 409
- Graceful shutdown configured (`server.shutdown: graceful`)
- Actuator probes: permit `/actuator/health/**` (not only `/actuator/health`), so the `liveness`/`readiness` probes work behind security

## REST API Built for a UI (OpenAPI Contract)

Decide first whether these rules apply. They are **required only when the API is built for a UI** (or another client) that is generated from its OpenAPI contract; they cost extra code and tests that a plain API does not need.

| Situation | Action |
|-----------|--------|
| The user says the API backs a web UI or generated clients; the project's `CLAUDE.md` names a UI (e.g. a `crud-ui-blueprint` UI); the repo has `contract/openapi.json`, `docs/API-CONTRACT.md` or `docs/DEPLOY-CONVENTION.md` | **Load the `spring-boot` skill, `references/ui-contract.md`, and follow it** |
| Service-to-service API, internal API, or no client is generated from the contract | **Optional** - the general rules above are enough; use a rule from `ui-contract.md` only when the user asks for it |
| Unsure | Ask once whether a UI or generated client will consume the API |

`references/ui-contract.md` is the single source of these rules: `/api` base path without CORS, operationIds, the springdoc 3 contract exported to `contract/openapi.json` and checked in CI, the fixed `FieldError` codes, `PUT` with a required `version`, filters, `Reference {id, label}` relations, Keycloak realm layout, public `health`/`info`, the README "Connect a UI" section, and the deployment shared with the UI repo (`deploy/` overlays, no host or secret in git). Where it differs from the defaults above (e.g. `/api` instead of `/api/v1`), it wins; the project's own copies of `docs/API-CONTRACT.md` and `docs/DEPLOY-CONVENTION.md` win over both.

## Relational Database Schema (Flyway)

Decide first whether Flyway applies:

| Situation | Action |
|-----------|--------|
| JDBC/JPA with a Flyway-supported relational database (PostgreSQL, MySQL, MariaDB, Oracle, SQL Server, DB2, H2, ...) | **Use Flyway** |
| Project already uses Liquibase | Keep Liquibase, do not add Flyway |
| No persistence, NoSQL only (MongoDB, Redis, Cassandra, Elasticsearch), or a database Flyway does not support | **Skip Flyway** - no dependency, no `db/migration`, no `spring.flyway.*` |

When Flyway applies:

- Flyway owns the schema (`src/main/resources/db/migration`); Hibernate only validates it (`spring.jpa.hibernate.ddl-auto: validate`). Never use `update`/`create` outside tests
- Dependencies: `spring-boot-starter-flyway` plus the database module (e.g. `flyway-database-postgresql`)
- `application.yml`: `ddl-auto: validate`, `open-in-view: false`, and the database in the readiness probe (`management.endpoint.health.group.readiness.include: readinessState,db`). Flyway defaults need no `spring.flyway.*` keys (exception: a CQRS single service with `command` and `query` schemas sets `spring.flyway.schemas`, see the `event-driven` skill)
- IDs are standard identity columns mapped with `GenerationType.IDENTITY`: `GENERATED BY DEFAULT AS IDENTITY` (PostgreSQL, Oracle, DB2, H2), `AUTO_INCREMENT` (MySQL/MariaDB), `IDENTITY` (SQL Server); not `SERIAL`/`BIGSERIAL`
- Never edit an applied migration; every change is one new file `V<n>__<description>.sql`
- Every entity change ships with its migration in the same commit
- A new `NOT NULL` column gets a `DEFAULT` or a backfill, so existing rows keep working
- Name constraints and indexes explicitly (`pk_`, `uk_`, `ck_`, `fk_`, `ix_`); index every foreign key column; a unique constraint the API reports as a 409 conflict is referenced by name from the exception handler
- Column `DEFAULT`s match entity defaults; validation rules get a matching DB `CHECK`
- Integration tests use Testcontainers with the production database and assert that all migrations were applied

Details and examples: `spring-boot` skill, `references/data.md` (Database Migrations).
