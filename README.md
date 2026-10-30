# Spring Boot Plugin for Claude Code (v1.2)

A Claude Code plugin with production-ready skills and agents for building Spring Boot 4.x / Java 21+ applications. Install the plugin and start generating your app — the skills and agents guide Claude to follow best practices for REST APIs with Problem Details errors, JPA, Flyway database migrations, security, testing, cloud-native deployment, and Kubernetes.

## Installation

Install from Anthropic marketplace

```bash
claude plugin install spring-boot-template@piomin
```

Or install directly from repository

``` 
claude plugin marketplace add piomin/claude-ai-spring-boot
claude plugin install spring-boot-template@piomin
```

Then run Claude Code and type:
```shell
/plugin
```

To test locally during development:

```bash
claude --plugin-dir /path/to/claude-ai-spring-boot
```

## What's Included

### Agents (`agents/`)

Specialized agents that Claude Code can invoke for specific tasks:

| Agent | Purpose |
|-------|---------|
| `spring-boot-engineer` | Build Spring Boot 4.x apps with layered architecture and Problem Details errors; adds the OpenAPI contract rules when the API is built for a UI |
| `code-reviewer` | Read-only review of a diff or branch: fixes the scope, loads the `conventions` and `code-quality` skills, reports cited findings by severity |
| `kubernetes-specialist` | K8s deployments, probes, ConfigMap/Secret, Prometheus, Skaffold |
| `devops-engineer` | CI/CD pipelines, Paketo Buildpacks, Skaffold |
| `docker-expert` | Multi-stage Dockerfiles, Paketo Buildpacks, image optimization |
| `security-engineer` | Spring Security, OWASP, input validation, SSL Bundles |

### Skills (`skills/`)

Reusable knowledge that Claude loads contextually:

| Skill | What it teaches |
|-------|-----------------|
| `conventions` | Mandatory project rules: No Lombok, constructor injection, RestClient, testing standards, conditional Flyway for relational databases, Problem Details, conditional OpenAPI contract rules for APIs built for a UI |
| `spring-boot` | Core Spring Boot development with 6 reference guides (web incl. Problem Details and paging, OpenAPI with springdoc 3, data incl. Flyway migrations, security, cloud, testing) |
| `code-quality` | Review checklist loaded on demand from 6 references (correctness, security, performance, Spring signals, REST semantics, code smells); points to `conventions` and other skills for project rules instead of repeating them |
| `design-patterns` | Classic patterns (Factory, Builder, Strategy) + enterprise (Saga, Outbox, CQRS) |
| `testing-patterns` | JUnit 5, Mockito, MockMvc, RestTestClient, Testcontainers with @ServiceConnection, relation and status-endpoint integration tests, Podman |
| `java-modern` | Java 21+ features: records, sealed classes, pattern matching, virtual threads |
| `jpa-patterns` | JPA/Hibernate pitfalls (N+1, lazy loading), Hibernate 6/7, optimistic locking across HTTP requests, relation writes with OSIV off, schema drift |
| `logging-patterns` | Structured JSON logging, SLF4J, MDC, AI-friendly formats |

### Key Conventions

The plugin enforces these rules via the `conventions` skill:

- **No Lombok** — explicit constructors, getters/setters, `LoggerFactory.getLogger()`
- **Constructor injection** — no `@Autowired` on fields
- **RestClient** over RestTemplate/WebClient for blocking HTTP calls
- **RestTestClient** with `@AutoConfigureRestTestClient` for integration tests
- **Testcontainers** with `@ServiceConnection` (not `@DynamicPropertySource`)
- **Jakarta Persistence** (not `javax.persistence`)
- **Problem Details** (RFC 9457) for every error; **paged lists** as `PagedModel`; **optimistic locking** with `@Version` and 409
- **OpenAPI contract rules**, only when the API is built for a UI (see below)
- **Flyway** owns the schema (`ddl-auto: validate`) when the API persists to a Flyway-supported relational database; skipped for APIs without persistence, NoSQL-only APIs, or projects already using Liquibase
- **Maven** with `pl.piomin.services` group ID
- **CI pipeline** — auto-detected from existing project config
- **Docker Compose** for local dependencies

### Cloud-Native Coverage

The `cloud.md` reference covers:

- Spring Cloud (Config Server, Eureka, Gateway, Resilience4j, LoadBalancer)
- Distributed tracing with OpenTelemetry (OTLP) and Micrometer Observation API
- Virtual Threads, HTTP Interface Clients (`@HttpExchange`), GraalVM Native Image
- Kubernetes: ConfigMap/Secret with `envFrom`, Prometheus annotations, startup/liveness/readiness probes, `preStop` hook, graceful shutdown
- TLS: cert-manager (Ingress TLS + pod-level mTLS), SSL Bundles with hot reload
- Config Tree for reading mounted secrets from files
- Paketo Buildpacks via `spring-boot:build-image`
- Skaffold for local K8s development loop

## Usage

After installing, describe what you want to build — Claude will use the skills and agents automatically:

```bash
cd my-project
claude
> "Build a REST API for managing orders with PostgreSQL"
```

For a relational database like PostgreSQL, the generated app includes Flyway migrations in `src/main/resources/db/migration`, `ddl-auto: validate`, and Testcontainers tests that verify all migrations were applied. APIs without persistence or with NoSQL stores get no Flyway configuration.

### APIs built for a UI

Some rules are **required only when the API is built for a UI** (or another client generated from its OpenAPI contract), for example a UI made from [crud-ui-blueprint](https://github.com/mmartofel/crud-ui-blueprint). Say so in your prompt ("the API backs a web UI generated from its OpenAPI contract") or in the project's `CLAUDE.md`. The `conventions` skill then loads `skills/spring-boot/references/ui-contract.md`, the single source of these rules:

- `/api` base path, same origin (no CORS), operationIds the UI's generated client relies on
- an OpenAPI contract generated by springdoc 3, exported to `contract/openapi.json` by a test and checked in CI, with `info.version` from the build
- Problem Details field errors with a fixed set of codes the UI translates, `PUT` with a required `version`
- typed list filters, relations as `{id, label}` summaries, 409 `in-use` instead of cascading deletes
- the Keycloak realm layout the UI logs in with, public `/actuator/health` and `/actuator/info`, and a README "Connect a UI" section

Without a UI, none of this is required: the generated API keeps Problem Details, paging and optimistic locking, and skips the contract export.

You can also invoke skills and agents directly:

```bash
# Invoke a skill
> /spring-boot-template:jpa-patterns

# Use an agent
> @agent-spring-boot-template:code-reviewer
```

## Directory Structure

```
.
├── .claude-plugin/
│   └── plugin.json
├── agents/
│   ├── code-reviewer.md
│   ├── devops-engineer.md
│   ├── docker-expert.md
│   ├── kubernetes-specialist.md
│   ├── security-engineer.md
│   └── spring-boot-engineer.md
├── skills/
│   ├── conventions/SKILL.md
│   ├── code-quality/
│   │   ├── SKILL.md
│   │   └── references/
│   │       ├── api-contract.md
│   │       ├── correctness.md
│   │       ├── performance.md
│   │       ├── security.md
│   │       ├── smells.md
│   │       └── spring-conventions.md
│   ├── design-patterns/SKILL.md
│   ├── java-modern/SKILL.md
│   ├── jpa-patterns/SKILL.md
│   ├── logging-patterns/SKILL.md
│   ├── spring-boot/
│   │   ├── SKILL.md
│   │   └── references/
│   │       ├── cloud.md
│   │       ├── data.md
│   │       ├── ui-contract.md
│   │       ├── security.md
│   │       ├── testing.md
│   │       └── web.md
│   └── testing-patterns/SKILL.md
├── README.md
└── LICENSE
```

## Blog Post

You can find a detailed explanation of this plugin in the post: [Claude Code Template for Spring Boot](https://piotrminkowski.com/2026/03/24/claude-code-template-for-spring-boot/).

## License

Apache License 2.0
