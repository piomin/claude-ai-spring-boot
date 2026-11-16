---
name: event-driven
description: CQRS and Event Sourcing for Spring Boot 4.x - command and query handlers, read models updated from domain events, transactional outbox with Apache Kafka or in-process events with Spring Modulith, an append-only PostgreSQL event store with aggregates rebuilt from events, upcasting and rebuildable projections, or Axon Framework, as separate command and query services or a single service. Use when the architecture block in the project's CLAUDE.md or AGENTS.md names the cqrs or cqrs-event-sourcing style, or when the user asks for CQRS, Event Sourcing, an event store, an outbox, projections or read models.
metadata:
  version: "1.1.0"
  domain: backend
  triggers: CQRS, Event Sourcing, event store, aggregate, command handler, query handler, read model, projection, domain events, upcasting, transactional outbox, Apache Kafka, Spring Modulith, Axon Framework
  role: specialist
  scope: implementation
  output-format: code
---

# Event-Driven Skill

CQRS for Spring Boot 4.x: the write side and the read side are separate models, and the read side is updated from domain events. With Event Sourcing the write side stores those events instead of the current state.

## When to Use

Load this skill only when the architecture decision (`conventions` skill, "Architecture Decision") is not layered CRUD:

- The project's `CLAUDE.md` (`AGENTS.md` in Codex and OpenCode) has `style: cqrs` or `style: cqrs-event-sourcing` in its `## Architecture` block.
- The user asks for CQRS, Event Sourcing, an event store, a transactional outbox, projections or read models.

A project without the block is layered CRUD: do not apply this skill to it, and do not start the dialog here. The dialog and the block belong to `conventions`.

## What Is Supported

| Style | Status |
|-------|--------|
| Layered CRUD | Default; this skill does not apply |
| CQRS (no event sourcing) | Supported: `references/cqrs.md` |
| CQRS + Event Sourcing (PostgreSQL event store) | Supported: `references/event-sourcing.md` |
| CQRS + Event Sourcing (Axon Framework) | Supported as a procedure: `references/axon.md`. The code comes from the Axon reference of the version selected for the project |
| CQRS or Event Sourcing for an API built for a UI | Supported: also follow `spring-boot` skill, `references/ui-contract.md` section 12 (command resources). It wins over this skill where they differ (`/api`, actions instead of `PUT`/`DELETE`, `invalid-state`, gateway) |

Do not approximate one style with another, and never use Kafka as an event store.

## Core Workflow

1. **Read the decision** - `style`, `scope`, `topology`, `transport`, `read-store` and, for Event Sourcing, `event-store` from the architecture block. Aggregates outside `scope` stay layered CRUD.
2. **Lay out the project** - modules or packages from `references/topology.md`.
3. **Write side** - entity, migration, command records, one handler per command, command controller (`references/cqrs.md`).
4. **Events** - one sealed interface per aggregate, records that carry the new state and the `version`.
5. **Transport** - the outbox and its relay for Kafka (`references/kafka.md`), or the Spring Modulith event publication registry for in-process events (`references/cqrs.md`).
6. **Read side** - read-model table and migration, projection, query records, one handler per query, query controller.
7. **Test** - `references/testing.md`; run `./mvnw clean verify` and confirm the integration tests ran.
8. **Tell the user** what eventual consistency means for their API: a query right after a command may return the old state.

With `style: cqrs-event-sourcing`, steps 3 to 6 follow `references/event-sourcing.md`: event store and migration, events, a plain aggregate that returns events, handlers that load, decide and append, then projections with a checkpoint. With `event-store: axon`, follow `references/axon.md` from its first step, before any code.

## Reference Guide

| Topic | Reference | When to Load |
|-------|-----------|-------------|
| Topology | `references/topology.md` | Always: modules, packages, databases, Compose, deployment, CI |
| CQRS rules | `references/cqrs.md` | Always: commands, queries, handlers, read models; for `style: cqrs` also events, projections, in-process events; review signals |
| Event Sourcing | `references/event-sourcing.md` | `style: cqrs-event-sourcing`: event store, events and upcasting, aggregates, handlers, projections with checkpoints, snapshots, personal data, review signals |
| Axon Framework | `references/axon.md` | `event-store: axon`: version selection, event store question, what Axon replaces, what still applies |
| Kafka | `references/kafka.md` | `transport: kafka`: outbox, relay, topics, consumers, dead-letter topic; relay from the event store for Event Sourcing |
| Testing | `references/testing.md` | Always: handler, outbox, projection and end-to-end tests; aggregate, event store and upcaster tests for Event Sourcing |

## Constraints

### MUST DO
- Commands and queries are records; one handler class per command or query
- Events are immutable records under one sealed interface per aggregate, named in the past tense
- CQRS: the write side keeps JPA, `@Version` and 409 on a stale `version` (`jpa-patterns` skill, Optimistic Locking); every event carries the aggregate id, the aggregate's `version` and its state after the change
- Event Sourcing: aggregates have no JPA mapping, are rebuilt from their events, and return new events from a command; a version conflict on append is 409
- Projections are idempotent: by the event's version (CQRS) or by a checkpoint moved in the read model's transaction (Event Sourcing), and they can be rebuilt
- With Kafka, events leave the write side only through the transactional outbox, or through the relay of the event store
- Query endpoints return `<Resource>Response` records in a `PagedModel`, with the `version` a client sends back on an update
- Flyway owns every table, including the outbox, the event store and the Spring Modulith event publication table
- All rules of the `conventions` skill that the architecture decision does not replace

### MUST NOT DO
- Publish to Kafka from a command handler, a controller or any class other than the relay
- Let `query` or `projection` code depend on `command` code or on the write-side entities
- Let the query service call the command service, or depend on its module
- Return a JPA entity, a `Page` or a read model from a command endpoint
- Change or remove a field of an event that is already published; Event Sourcing evolves events with a new type and an upcaster
- Update or delete a row of the event store
- Put personal data into an event payload without an erasure strategy documented in the README
- Add snapshots without a measured load problem
- Update a read model in the command's transaction
- Use Kafka as the source of truth: the write database is, and the topic only replays events for projections
