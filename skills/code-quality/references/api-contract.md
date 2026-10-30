# API Contract

Two parts: general REST semantics (any controller change) and the UI contract (only when the API is built for a UI or generated clients, see `conventions`; rules in the `spring-boot` skill, `references/ui-contract.md`).

## REST semantics
- `POST` used for retrieval (`/users/search` with a body) → `GET` with query params
- `GET` changing state (`/users/{id}/activate`) → `POST` / `PATCH`
- `PUT` that is not a full replacement, or a non-idempotent `PUT`
- Wrong status: `201` for create, `204` for delete, `400` for validation, `404` for missing, `409` for conflict; `500` for something the client caused
- Breaking change to an existing contract without bumping the version (see `conventions`, semantic versioning)

Do not suggest URL versioning like `/api/v1/...`: the base path is defined by `conventions`.

## UI contract (conditional)
Check only when the project serves a UI or generated clients.

- Base path other than `/api`, or CORS configured (the UI is served from the same origin)
- Error codes outside the documented vocabulary (`required`, `email`, `past`, `unique`, `invalid`, `date-range`, `not-found`, `in-use`)
- A relation exposed as flat fields instead of `Reference {id, label}`; unknown `<relation>Id` not returning 400 `not-found`; deleting a referenced record cascading or returning 409 without `in-use`
- Keycloak realm, clients, roles or demo users that differ from `ui-contract.md`; README "Connect a UI" out of date
- Field errors without `errors: [{field, code, message}]` and documented codes
- A new validation constraint or error code missing from the code mapping, the `FieldError` schema enum, the README or the tests
- `Pageable` without `@ParameterObject`
- `@ApiResponse` on controller methods (springdoc 3 drops the success response)
- Response fields without `@Schema(requiredMode = REQUIRED)` that are always present; missing `version` in responses
- Hard-coded `info.version`; `contract/openapi.json` not regenerated with an API change
- `@Pattern` with Java-only regex syntax (`(?s)`, `(?i)`, possessive quantifiers, `\p{...}`); `@NotBlank` with `@Size(max = N)` but no `min = 1`
- `/actuator/info` not public, or other Actuator endpoints besides `health` and `info` public
