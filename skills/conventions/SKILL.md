---
name: conventions
description: Spring Boot project conventions and mandatory rules. Auto-loads when working with Spring Boot, Java, or Maven projects. Enforces No-Lombok, constructor injection, RestClient, Flyway for relational databases, Problem Details errors, and other standards; adds OpenAPI contract rules when the API is built for a UI.
metadata:
  version: "1.1.0"
  domain: backend
  triggers: Spring Boot, Java, Maven, Spring Framework, REST API, JPA, Hibernate, microservices, Flyway, database migration, PostgreSQL, MySQL, OpenAPI, springdoc, Problem Details, Actuator
  role: guide
  scope: conventions
  output-format: code
---

# Spring Boot Project Conventions

These conventions apply to ALL code generated or reviewed in the project. Follow them strictly.

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
- `application.yml`: `ddl-auto: validate`, `open-in-view: false`, and the database in the readiness probe (`management.endpoint.health.group.readiness.include: readinessState,db`). Flyway defaults need no `spring.flyway.*` keys
- IDs are standard identity columns mapped with `GenerationType.IDENTITY`: `GENERATED BY DEFAULT AS IDENTITY` (PostgreSQL, Oracle, DB2, H2), `AUTO_INCREMENT` (MySQL/MariaDB), `IDENTITY` (SQL Server); not `SERIAL`/`BIGSERIAL`
- Never edit an applied migration; every change is one new file `V<n>__<description>.sql`
- Every entity change ships with its migration in the same commit
- A new `NOT NULL` column gets a `DEFAULT` or a backfill, so existing rows keep working
- Name constraints and indexes explicitly (`pk_`, `uk_`, `ck_`, `fk_`, `ix_`); index every foreign key column; a unique constraint the API reports as a 409 conflict is referenced by name from the exception handler
- Column `DEFAULT`s match entity defaults; validation rules get a matching DB `CHECK`
- Integration tests use Testcontainers with the production database and assert that all migrations were applied

Details and examples: `spring-boot` skill, `references/data.md` (Database Migrations).
