# API Contract

## REST semantics (any controller change)
Not covered by a project skill, so it lives here.

- `POST` used for retrieval (`/users/search` with a body) → `GET` with query params
- `GET` changing state (`/users/{id}/activate`) → `POST` / `PATCH`
- `PUT` that is not a full replacement, or a non-idempotent `PUT`
- Wrong status: `201` create, `204` delete, `400` validation, `404` missing, `409` conflict; `500` for something the client caused
- Breaking contract change without a version bump (rule: `conventions`, semantic versioning)

Do not suggest `/api/v1/...` URL versioning: the base path is defined by `conventions`.

## UI contract (conditional)
Only when the API is built for a UI or generated clients (decision table: `conventions` → REST API Built for a UI). Then review the change against `spring-boot` → `references/ui-contract.md`, in particular §10 "Build Order and Definition of Done", and §11 "Deployment" when `deploy/` or `skaffold.yaml` changes. Do not flag an optional relation id for lacking a nullable type: a field left out of a `PUT` is cleared (§4, §6). With `style: cqrs` or `cqrs-event-sourcing`, review the aggregates in `scope` against §12 (command resources). Flag:
- `PUT` or `DELETE` on a command resource
- a command that returns the record
- a read model without `version`, `status` or `allowedActions`
- `allowedActions` stored in the read model instead of computed per user
- a broken rule returned as 409 without `invalid-state`, or as 422
- separate services without a gateway or without a merged contract

Do not copy its rules into findings; cite the section.
