---
name: code-quality
description: Review checklist for Java / Spring Boot code - correctness, security, performance, Spring signals, REST semantics, and code smells; project rules are referenced from conventions, not repeated. Use when user says "review code", "check this PR", "refactor", "check API", or before merging changes.
---

# Code Quality Review Skill

Knowledge for reviewing Java / Spring Boot changes: **what to flag** and how to recognize it. The review process, scope, and report format belong to the `code-reviewer` agent; project rules belong to the `conventions` skill.

**No duplication:** this skill holds only what no other skill states (generic Java quality, smells, REST semantics, detection signals). Project rules live in `conventions`, `spring-boot`, and `jpa-patterns`; the references here point to them and never restate them, so a rule change is made in one place.

## When to Use
- "review this code" / "code review" / "check this PR"
- "refactor" / "clean this code" / "improve readability"
- "review API" / "check endpoints" / "REST review"
- Before merging a PR or releasing API changes

## What to Load

Read only the references that match the changed files.

| If the change contains | Read |
|------------------------|------|
| Any Java code | `references/correctness.md`, `references/spring-conventions.md` (signals; rules in `conventions`) |
| Controllers, DTOs, OpenAPI, error handling | `references/api-contract.md` |
| Input handling, auth, config, secrets, actuator | `references/security.md` |
| Repositories, queries, entity loops | `references/performance.md` |
| Entities, `*.sql` migrations, `spring.jpa.*`, Flyway | `conventions` → Relational Database Schema (Flyway); `spring-boot` → `references/data.md` |
| Any non-trivial logic | `references/smells.md` |

Related skills, loaded only when the diff needs them:

| Topic | Skill |
|-------|-------|
| Project rules (No Lombok, RestClient, Flyway, Problem Details) | `conventions` |
| Lazy loading, fetching, optimistic locking | `jpa-patterns` |
| Test quality, `RestTestClient`, Testcontainers | `testing-patterns` |
| Log levels, MDC, structured logging | `logging-patterns` |
| UI contract details | `spring-boot` → `references/ui-contract.md` |
| Records, sealed types, pattern matching | `java-modern` |

## Severity

- **Critical** - security, data loss, crash risk → must fix before merge
- **Important** - correctness, performance, maintainability → should fix
- **Code Smell** - style, complexity, minor issues → nice to have (always phrased as a heuristic, see `references/smells.md`)
- **Good** - positive feedback worth reinforcing

## Non-Goals

- Formatting, import order, naming style that a formatter or linter enforces
- Personal taste or rewrites that do not fix a concrete problem
- Rules the repo contradicts on purpose: when `CLAUDE.md`, `conventions`, or an existing documented decision overrides a rule here, the repo wins

## Quick Reference

| Category | Red flags |
|----------|-----------|
| Null safety | chained calls, `Optional.get()`, returning `null` |
| Exceptions | empty catch, broad catch, lost stack trace |
| Resources | manual `close()` |
| Transactions | multi-step writes without `@Transactional`, self-invocation |
| API | wrong verb or status |
| Performance | N+1, `findAll()` without paging, `LIKE` from user input |
