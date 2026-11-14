---
name: event-driven
description: CQRS for Spring Boot 4.x - command and query handlers, read models updated from domain events, transactional outbox with Apache Kafka or in-process events with Spring Modulith, as separate command and query services or a single service. Use when the architecture block in the project's CLAUDE.md names the cqrs style, or when the user asks for CQRS, an outbox, projections or read models. Event Sourcing and Axon Framework are not yet supported.
metadata:
  version: "1.0.0"
  domain: backend
  triggers: CQRS, command handler, query handler, read model, projection, domain events, transactional outbox, Apache Kafka, Spring Modulith, Event Sourcing, Axon
  role: specialist
  scope: implementation
  output-format: code
---

# Event-Driven Skill

CQRS for Spring Boot 4.x: the write side and the read side are separate models, and the read side is updated from domain events.

## When to Use

Load this skill only when the architecture decision (`conventions` skill, "Architecture Decision") is not layered CRUD:

- The project's `CLAUDE.md` (`AGENTS.md` in Codex) has `style: cqrs` in its `## Architecture` block.
- The user asks for CQRS, a transactional outbox, projections or read models.

A project without the block is layered CRUD: do not apply this skill to it, and do not start the dialog here. The dialog and the block belong to `conventions`.

## What Is Supported

| Style | Status |
|-------|--------|
| Layered CRUD | Default; this skill does not apply |
| CQRS (no event sourcing) | Supported |
| CQRS + Event Sourcing (PostgreSQL event store) | Not yet supported |
| CQRS + Event Sourcing (Axon Framework) | Not yet supported |
| CQRS for an API built for a UI (`spring-boot` skill, `references/ui-contract.md`) | Not yet supported |

For a style that is not yet supported, say so and stop. Do not approximate it with the CQRS rules, and never use Kafka as an event store.

## Core Workflow

1. **Read the decision** - `style`, `scope`, `topology`, `transport`, `read-store` from the architecture block. Aggregates outside `scope` stay layered CRUD.
2. **Lay out the project** - modules or packages from `references/topology.md`.
3. **Write side** - entity, migration, command records, one handler per command, command controller (`references/cqrs.md`).
4. **Events** - one sealed interface per aggregate, records that carry the new state and the `version`.
5. **Transport** - the outbox and its relay for Kafka (`references/kafka.md`), or the Spring Modulith event publication registry for in-process events (`references/cqrs.md`).
6. **Read side** - read-model table and migration, projection, query records, one handler per query, query controller.
7. **Test** - `references/testing.md`; run `./mvnw clean verify` and confirm the integration tests ran.
8. **Tell the user** what eventual consistency means for their API: a query right after a command may return the old state.

## Reference Guide

| Topic | Reference | When to Load |
|-------|-----------|-------------|
| Topology | `references/topology.md` | Always: modules, packages, databases, Compose, deployment, CI |
| CQRS rules | `references/cqrs.md` | Always: commands, queries, handlers, events, projections, in-process events, review signals |
| Kafka | `references/kafka.md` | `transport: kafka`: outbox, relay, topics, consumers, dead-letter topic |
| Testing | `references/testing.md` | Always: handler, outbox, projection and end-to-end tests |

## Constraints

### MUST DO
- Commands and queries are records; one handler class per command or query
- The write side keeps JPA, `@Version` and 409 on a stale `version` (`jpa-patterns` skill, Optimistic Locking)
- Every event carries the aggregate id, the aggregate's `version` and its state after the change
- Projections are idempotent: an event that is not newer than the stored version is ignored
- With Kafka, events leave the write side only through the transactional outbox
- Query endpoints return `<Resource>Response` records in a `PagedModel`, with the `version` a client sends back on an update
- Flyway owns every table, including the outbox and the Spring Modulith event publication table
- All rules of the `conventions` skill that the architecture decision does not replace

### MUST NOT DO
- Publish to Kafka from a command handler, a controller or any class other than the outbox relay
- Let `query` or `projection` code depend on `command` code or on the write-side entities
- Let the query service call the command service, or depend on its module
- Return a JPA entity, a `Page` or a read model from a command endpoint
- Change or remove a field of an event that is already published
- Update a read model in the command's transaction
- Use Kafka as the source of truth: the write database is, and the topic only replays events for projections
