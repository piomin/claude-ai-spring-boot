# Security

Checks that no project skill states as a rule. For a deeper audit of authentication, authorization or infrastructure, hand off to the `security-engineer` agent.

- **Input validation**: unvalidated path variables and request parameters (`@Valid @RequestBody` is a `conventions` rule)
- **SQL injection**: string concatenation in `@Query`, raw JDBC without parameters
- **Secrets**: hardcoded credentials, secrets in `application.properties`, secret values in Kubernetes manifests, kustomizations or a Keycloak realm file (Compose may keep fixed local demo values)
- **Authorization**: endpoints reachable without a matching security rule, role checks done only in the UI

Already covered elsewhere, check against the source instead of restating:
- Entity exposure, `@Valid`, Problem Details, no `ex.getMessage()` in the catch-all, `/actuator/health/**` → `conventions` (Mandatory Rules, Code Quality Standards)
- Public `health` / `info` and Keycloak layout for a UI (realm without host or secret, no `KC_HOSTNAME`, no Route for Keycloak) → `spring-boot` → `references/ui-contract.md` §7
- Deployment manifests (no host, registry, namespace or secret in git; Secrets created by the deploy hook) → `spring-boot` → `references/cloud.md`, and `references/ui-contract.md` §11 for an API built for a UI
- Security configuration patterns → `spring-boot` → `references/security.md`
