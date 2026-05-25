# Claude Code Template for Spring Boot Application

This template provides a structured starting point for Spring Boot applications, optimized for Claude AI's code completion capabilities. It includes essential configurations and best practices to streamline development and enhance productivity.

It targets **Spring Boot 4.x** (built on Spring Framework 7) while remaining compatible with Spring Boot 3.x, and covers both **Spring Data JPA** and **Spring Data MongoDB**.

The idea behind this template is that you can just clone this repository and use it to generate the app you want with Claude Code.

```shell
.
├── .claude
│   ├── agents
│   │   ├── code-reviewer.md
│   │   ├── devops-engineer.md
│   │   ├── docker-expert.md
│   │   ├── kubernetes-specialist.md
│   │   ├── security-engineer.md
│   │   └── spring-boot-engineer.md
│   ├── settings.local.json
│   └── skills
│       ├── README.md
│       ├── code-quality
│       │   └── SKILL.md
│       ├── design-patterns
│       │   └── SKILL.md
│       ├── jpa-patterns
│       │   └── SKILL.md
│       ├── logging-patterns
│       │   └── SKILL.md
│       ├── mongodb-patterns
│       │   └── SKILL.md
│       └── spring-boot
│           ├── SKILL.md
│           └── references
│               ├── cloud.md
│               ├── data.md
│               ├── mongodb.md
│               ├── security.md
│               ├── testing.md
│               └── web.md
├── .claude-plugin
│   └── plugin.json
├── CLAUDE.md
├── LICENSE
├── README.md
└── pom.xml
```

## Skills

| Skill | Description |
|-------|-------------|
| `spring-boot` | Spring Boot 4.x (3.x compatible): REST APIs, JPA, MongoDB, Security, Testing, Cloud. Loads topic references on demand. |
| `jpa-patterns` | Spring Data JPA/Hibernate pitfalls — N+1, lazy loading, transactions, query tuning. |
| `mongodb-patterns` | Spring Data MongoDB pitfalls — embed vs reference, indexing, aggregation cost, pagination. |
| `code-quality` | Clean-code review: API contracts, null safety, exception handling, performance. |
| `design-patterns` | GoF design patterns with Java examples. |
| `logging-patterns` | SLF4J, structured (JSON) logging, MDC request tracing. |

### Spring Boot 4.x coverage

The `spring-boot` skill defaults to Spring Boot 4.x and calls out where 3.x differs, including:
native API versioning, declarative `@HttpExchange` HTTP clients, built-in resilience
(`@Retryable` / `@ConcurrencyLimit`), JSpecify null safety, Jackson 3, and the `@MockitoBean`
test annotations that replaced `@MockBean`.

You can find the detailed explanation and description of that that template in my post [Claude Code Template for Spring Boot](https://piotrminkowski.com/2026/03/24/claude-code-template-for-spring-boot/).
