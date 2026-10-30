# Security

Checks that no project skill states as a rule. For a deeper audit of authentication, authorization or infrastructure, hand off to the `security-engineer` agent.

- **Input validation**: unvalidated path variables and request parameters (`@Valid @RequestBody` is a `conventions` rule)
- **SQL injection**: string concatenation in `@Query`, raw JDBC without parameters
- **Secrets**: hardcoded credentials, secrets in `application.properties`
- **Authorization**: endpoints reachable without a matching security rule, role checks done only in the UI

Already covered elsewhere, check against the source instead of restating:
- Entity exposure, `@Valid`, Problem Details, no `ex.getMessage()` in the catch-all, `/actuator/health/**` → `conventions` (Mandatory Rules, Code Quality Standards)
- Public `health` / `info` and Keycloak layout for a UI → `spring-boot` → `references/ui-contract.md` §7
- Security configuration patterns → `spring-boot` → `references/security.md`
