# REST API Built for a UI (OpenAPI Contract)

Rules for an API whose web UI (or other client) is **generated from its OpenAPI contract**, for example a UI made from [crud-ui-blueprint](https://github.com/mmartofel/crud-ui-blueprint). The UI builds its client code, forms, validation, error messages and login from the contract and from the values listed here. The UI never works around the contract, so every rule below is a requirement.

**When it applies** (see the `conventions` skill, "REST API Built for a UI"): the user says the API backs a UI or generated clients; the project's `CLAUDE.md` names a UI; or the repo has `contract/openapi.json`, `docs/API-CONTRACT.md` or `docs/DEPLOY-CONVENTION.md`. The project's copies of these documents (taken unchanged from the UI template, never edited in the API repo) win over this file: `docs/API-CONTRACT.md` for the contract, `docs/DEPLOY-CONVENTION.md` for the deployment (section 11). A plain API does not load this file.

Examples use a resource `Person` (tag `Persons`, path `/api/persons`, realm `person`) and a `Task` with an optional `assignee` (a Person). Replace them with your resources.

## 1. Endpoints

- Base path `/api` (not `/api/v1`). The UI calls relative URLs and production serves UI and API from one origin: **no CORS configuration**.
- Five operations per resource; the method names become the operationIds and the names of the UI's generated hooks:

  | Method and path | Controller method | Success |
  |---|---|---|
  | `GET /api/persons` | `listPersons` | 200 `PagedModel` |
  | `GET /api/persons/{id}` | `getPerson` | 200 record |
  | `POST /api/persons` | `createPerson` | 201 record, `@ResponseStatus(HttpStatus.CREATED)` |
  | `PUT /api/persons/{id}` | `updatePerson` | 200 record |
  | `DELETE /api/persons/{id}` | `deletePerson` | 204, `@ResponseStatus(HttpStatus.NO_CONTENT)` |

- `{id}` is `Long` (`int64`). One `@Tag(name = "Persons")` per controller; `@SecurityRequirement(name = "bearer")`.
- Schemas per direction: `PersonResponse`, `PersonCreateRequest`, `PersonUpdateRequest`. Entities never appear.

## 2. OpenAPI Contract (springdoc 3)

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>${springdoc.version}</version> <!-- 3.x for Spring Boot 4 -->
</dependency>
<!-- spring-boot-maven-plugin with the build-info goal: BuildProperties and /actuator/info build.version -->
```

```yaml
springdoc:
  default-produces-media-type: application/json   # otherwise success responses are */*
  writer-with-default-pretty-printer: true
  writer-with-order-by-keys: true                  # stable file, readable diffs
```

```java
@Configuration
@SecurityScheme(name = "bearer", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {

    /** Resources other records point to; deleting them returns 409 {@code in-use} while referenced. */
    private static final Set<String> REFERENCED_PATHS = Set.of("/api/persons/{id}");

    /** The contract version is the Maven project version; the server URL is relative, so the file is stable. */
    @Bean
    OpenAPI openApi(BuildProperties build) {
        return new OpenAPI().info(new Info().title("Person API").version(build.getVersion()))
                .servers(List.of(new Server().url("/")));
    }

    /**
     * Problem Details error responses by convention: 400 and 409 on POST/PUT, 400 on list GETs, 404 on {id} paths,
     * 409 on DELETE of a referenced resource. @ApiResponse on methods would drop the generated success response.
     */
    @Bean
    OpenApiCustomizer problemResponses() {
        return openApi -> {
            openApi.getComponents().getSchemas().putAll(ModelConverters.getInstance(true).readAll(Problem.class));
            openApi.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
                if (method == HttpMethod.POST || method == HttpMethod.PUT) {
                    addProblem(operation, "400", "Invalid request; field errors in `errors` (code `not-found` for an unknown reference)");
                    addProblem(operation, "409", "Value already in use (`errors` with code `unique`) or stale `version` (no `errors`)");
                }
                if (method == HttpMethod.GET && !path.contains("{id}")) {
                    addProblem(operation, "400", "Invalid filter (field errors in `errors`) or sort");
                }
                if (path.contains("{id}")) {
                    addProblem(operation, "404", "Not found");
                }
                if (method == HttpMethod.DELETE && REFERENCED_PATHS.contains(path)) {
                    addProblem(operation, "409", "Still referenced by other records (`errors` with code `in-use`, no `field`)");
                }
            }));
        };
    }

    private static void addProblem(Operation operation, String status, String description) {
        Schema<?> problem = new Schema<>().$ref("#/components/schemas/Problem");
        operation.getResponses().addApiResponse(status, new ApiResponse().description(description)
                .content(new Content().addMediaType("application/problem+json", new MediaType().schema(problem))));
    }
}
```

Schema rules (the UI generates TypeScript types and Zod validation from them):

| Rule | How |
|------|-----|
| Always-present response fields are required (at least `id`, `version`) | `@Schema(requiredMode = REQUIRED)` |
| Server-set fields only in responses | `createdAt`/`updatedAt` as `Instant` (`@CreationTimestamp`/`@UpdateTimestamp`), `@Schema(accessMode = READ_ONLY)` |
| Enums | `UPPER_CASE` Java enum constants → `type: string, enum: [...]`; the UI shows translated labels |
| Dates | `LocalDate` → `format: date`, `Instant` → `format: date-time` |
| Required text | `@NotBlank @Size(min = 1, max = N)`: `@Size(max = N)` alone overwrites the `minLength: 1` of `@NotBlank` with `0`, and `@Schema(minLength = 1)` does not win |
| Patterns | `@Pattern` must be a valid JavaScript regex (the UI runs `new RegExp`): no `(?s)`, `(?i)`, possessive quantifiers, atomic groups, `\A`, `\Z`, `\z`, `\p{javaLowerCase}` |
| A rule Bean Validation cannot express | Custom constraint + matching `@Schema` keyword (e.g. `@MultipleOf("0.5")` with `@Schema(multipleOf = 0.5)`) + the same DB `CHECK` |
| Field with a default | `@Schema(defaultValue = "AVAILABLE")` in the request, the entity setter maps null to it (`Objects.requireNonNullElse`), the migration has the same `DEFAULT` |
| Version | `info.version` from `BuildProperties`; every contract change bumps the project version, a breaking change bumps MAJOR |

springdoc 3 pitfalls:
- Any `@ApiResponse` on a controller method (also through a meta-annotation) replaces the generated responses and the success response disappears; that is why errors are added in the `OpenApiCustomizer`.
- Manually registered schemas need `ModelConverters.getInstance(true)` (OpenAPI 3.1), or `"type": "object"` is missing.
- The success status comes from `@ResponseStatus`, not from the returned `ResponseEntity`: a create without `@ResponseStatus(CREATED)` is documented as 200.
- `Pageable` and filter records without `@ParameterObject` are documented as one object parameter.
- Without a fixed `servers` URL, a contract exported from a `RANDOM_PORT` test contains the random port and changes on every build.

**Export and CI.** An integration test writes `contract/openapi.json` on every `mvn clean verify`, first writing and then asserting, so a failing assertion still leaves the file to inspect:

```java
@Test
void exportOpenApiContract() throws Exception {
    String json = restTestClient.get().uri("/v3/api-docs").exchange()
            .expectStatus().isOk()
            .expectBody(String.class).returnResult().getResponseBody();
    Path file = Path.of("contract/openapi.json");
    Files.createDirectories(file.getParent());
    Files.writeString(file, json + System.lineSeparator());

    DocumentContext contract = JsonPath.parse(json);
    assertThat(contract.read("$.info.version", String.class)).isEqualTo(buildProperties.getVersion());
    assertThat(contract.read("$.paths['/api/persons'].post.operationId", String.class)).isEqualTo("createPerson");
    assertThat(contract.read("$.paths['/api/persons'].post.responses", Map.class)).containsKey("201");
    assertThat(contract.read("$.components.schemas.PersonCreateRequest.properties.firstName.minLength", Integer.class))
            .isEqualTo(1);
    assertThat(contract.read("$.components.schemas.PersonResponse.required", List.class)).contains("id", "version");
}
```

`SecurityConfig` permits `/v3/api-docs/**`. CI fails when the committed contract is out of date (`mvn -B verify`, then `test -z "$(git status --porcelain contract/)"`). The test needs Testcontainers: without Docker/Podman it is skipped and the contract is not regenerated.

## 3. Errors

RFC 9457 Problem Details (`application/problem+json`, schema `Problem` with `status` and `title` required), from the `ApiExceptionHandler` in `web.md`. Field errors go into `errors: [{field, code, message}]`, schema `FieldError` with `code` and `message` required.

- `code` is a documented string enum the UI translates. Use exactly this vocabulary and do not invent synonyms (`too-long`, `pattern`, ...); a constraint without its own code maps to `invalid`:

  | Code | Source |
  |---|---|
  | `required` | `@NotBlank`, `@NotNull` (an empty required text breaks `@NotBlank` and `@Size(min = 1)`: report only `required`) |
  | `email` | `@Email` |
  | `past` | `@Past` |
  | `unique` | unique constraint violation, by constraint name (409) |
  | `invalid` | every other constraint (`@Size`, `@Pattern`, `@Min`, `@Max`, ...), bad filter values |
  | `date-range` | cross-field rule, reported on the later field (`@DateRange` in `web.md`) |
  | `not-found` | unknown `<relation>Id` in a request (400) |
  | `in-use` | delete of a record that is still referenced (409, no `field`) |

  List them in `@Schema(allowableValues = ...)` on `FieldError.code`. A new code is a contract change: add it to the mapping, the enum, the README and the tests, and tell the UI team (it needs a translation).
- `message` is an English fallback only. `field` is absent only for record-level errors (`in-use`).
- Status codes the UI handles: 400 (field errors on the form), 401 (re-login), 403 ("no access"), 404 ("not found" page), **409 with `errors`** (field conflict, e.g. `unique`, or `in-use`), **409 without `errors`** (stale `version`: the UI offers a reload). Never return a stale-version 409 with `errors`.

Prepared errors for relations, rendered by `ResponseEntityExceptionHandler` without an extra `@ExceptionHandler`:

```java
/** 400: {@code field} references a record that does not exist. */
public static ErrorResponseException notFound(String field) {
    return problem(HttpStatus.BAD_REQUEST, "Referenced record does not exist", new FieldMessage(field, NOT_FOUND, "does not exist"));
}

/** 409: the record is still referenced by other records and cannot be deleted. */
public static ErrorResponseException inUse(String detail, String message) {
    return problem(HttpStatus.CONFLICT, detail, new FieldMessage(null, IN_USE, message));
}

private static ErrorResponseException problem(HttpStatus status, String detail, FieldMessage error) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setProperty("errors", List.of(error));
    return new ErrorResponseException(status, problem, null);
}
```

## 4. Updates and Concurrency

- Responses include `version`. `<Resource>CreateRequest` has no `id` and no `version`.
- `PUT` replaces the whole record: `<Resource>UpdateRequest` has the same fields and constraints as the create request plus a required `version` from the last read. Optional fields sent as `null` or left out are cleared (the UI leaves a cleared field out of the request); a field with a default gets the default again. Optional fields are therefore not typed nullable in the contract (no `"null"` in `type`).
- The service compares the request's `version` with the loaded entity and throws `ObjectOptimisticLockingFailureException` on mismatch (409 without `errors`); see `jpa-patterns`, Optimistic Locking. `PUT` uses `saveAndFlush`, so `version` and `updatedAt` in the response are current.

## 5. Lists, Search and Filters

- `GET /api/persons?q=&page=&size=&sort=` plus optional filters, each a separate, typed query parameter (enum, `format: date`, boolean, `int64` id). Never one generic filter expression. Response: `PagedModel` `{content, page: {number, size, totalElements, totalPages}}`.
- `q` is a free-text search over the fields the API chooses, with `LIKE` wildcards escaped (`data.md`, Specifications). An empty or missing filter means "no filter"; filters combine with AND; an invalid value returns 400 with a field error named after the parameter.

```java
/** Optional list filters, combined with AND. */
public record TaskFilter(
        @Parameter(description = "Case-insensitive search in title and description") String q,
        @Parameter(description = "Done or open tasks") Boolean done,
        @Parameter(description = "Exact priority") TaskPriority priority,
        @Parameter(description = "Id of the assigned person") Long assigneeId) {

    public Specification<Task> toSpecification() {
        List<Specification<Task>> specs = new ArrayList<>();
        if (q != null && !q.isBlank()) {
            specs.add(TaskSpecifications.search(q, "title", "description"));
        }
        if (done != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("done"), done));
        }
        if (priority != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("priority"), priority));
        }
        if (assigneeId != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("assignee").get("id"), assigneeId));
        }
        return Specification.allOf(specs);
    }
}

// Controller
@GetMapping
public PagedModel<TaskResponse> listTasks(@ParameterObject @Valid TaskFilter filter, @ParameterObject Pageable pageable) {
    return new PagedModel<>(taskService.list(filter.toSpecification(), pageable));
}
```

## 6. Relations

- **Requests** reference a related record by id: `<relation>Id` (e.g. `assigneeId`), not required when the relation is optional and a plain `int64` (no nullable type): it is cleared like any optional field (section 4). An unknown id returns 400 with a field error on `<relation>Id`, code `not-found`.
- **Responses** embed a summary `<relation>: {id, label}` (both required), where `label` is the display name, so a list never needs one request per row:

  ```java
  /** Summary of a related record embedded in a response. */
  public record Reference(@Schema(requiredMode = REQUIRED) Long id,
                          @Schema(requiredMode = REQUIRED, description = "Display name") String label) {

      public static Reference of(Person p) {
          return p == null ? null : new Reference(p.getId(), p.getDisplayName());
      }
  }
  ```

- **Filter** on a relation: `<relation>Id` (the UI lists "tasks of this person" with it).
- **Delete** of a record that is still referenced: count the references and throw `inUse(...)` (409, `errors: [{code: "in-use", message}]`, no `field`); never cascade. The foreign key is `ON DELETE RESTRICT` as a backstop. Add the path to `REFERENCED_PATHS`.
- **Loading:** `@EntityGraph(attributePaths = "assignee")` on the repository's `findAll(Specification, Pageable)` and `findById` (`open-in-view` is off). Writes are `@Transactional` and map the response inside the transaction (`jpa-patterns`, "Writing a Record with a Relation").
- **Tests:** none → A, A → B and A → none, unknown id (400 `not-found`) and delete in use (409 `in-use`), in integration tests against the real database.

## 7. Security and Keycloak

The UI logs in against a Keycloak realm with Authorization Code + PKCE, and its setup (`crud-ui-init`) expects this shape. For realm `<realm>`:

- **Roles:** realm roles `<realm>-viewer` (read) and `<realm>-editor` (read and write). Reads need either role; writes need `<realm>-editor`.
- **Clients:**
  - `<realm>-ui`: public, standard flow only, PKCE `S256`, `rootUrl` `${authBaseUrl}`, redirect URIs `/*` and `http://localhost:5173/*`, post-logout redirect URIs `/*##http://localhost:5173/*`, web origins `+`. The relative URIs resolve to the host the request came through, so sign-in works on any generated Route or Ingress host with no realm change, and a foreign host is rejected.
  - `<realm>-api`: confidential, service account with `<realm>-editor`, for `client_credentials` smoke tests. Its secret is a placeholder (`${<REALM>_API_CLIENT_SECRET}`): a fixed demo value in `docker-compose.yml`, a random one in the cluster Secret `<project>-oidc-client` (key `client-secret`).
- **Demo users:** `viewer` with `<realm>-viewer`, `editor` with `<realm>-editor`. The UI's e2e tests log in as them. Their passwords are placeholders too (`${DEMO_VIEWER_PASSWORD}`, `${DEMO_EDITOR_PASSWORD}`). They default to the user name in Docker Compose and on clusters, and are set on the first deploy of a publicly reachable cluster.
- **Internationalization** on: `en` (default) and `pl`; the login page follows the UI's `ui_locales`.
- **One realm file** `deploy/base/realm.json`, imported with `--import-realm` by Docker Compose (Keycloak `start-dev`, volume `./deploy/base/realm.json:/opt/keycloak/data/import/realm.json:ro`) and by Kubernetes (`configMapGenerator` in `deploy/base/kustomization.yaml`, so a changed file rolls a new pod). It holds **no host and no secret value**: Keycloak substitutes the `${ENV}` placeholders at import. `--import-realm` skips an existing realm: after a change run `docker compose up -d --force-recreate keycloak`.
- **In a cluster** Keycloak runs with `KC_PROXY_HEADERS=xforwarded` and no `KC_HOSTNAME`. It gets no Route or Ingress of its own: the browser reaches the realm's public paths (`/realms/<realm>/`, `/resources/`) through the UI's host, whose nginx proxies them; the master realm and `/admin` stay private. Keycloak builds every URL, the token issuer included, from the host the browser used. A fixed `KC_HOSTNAME` or absolute redirect URIs break sign-in as soon as the Route gets a generated host.
- **Ports:** Keycloak 8080, API 8081 (`server.port: ${SERVER_PORT:8081}`), PostgreSQL 5432.

```java
@Bean
SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/actuator/health/**", "/actuator/info", "/v3/api-docs/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/**").hasAnyRole(VIEWER, EDITOR)
                    .requestMatchers("/api/**").hasRole(EDITOR)
                    .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(realmRoles())))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(csrf -> csrf.disable())
            .build();
}

/** Maps Keycloak realm roles ({@code realm_access.roles}) to {@code ROLE_<role>} authorities. */
private static JwtAuthenticationConverter realmRoles() {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(jwt -> {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null || !(realmAccess.get("roles") instanceof List<?> roles)) {
            return List.of();
        }
        return roles.stream().map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role)).toList();
    });
    return converter;
}
```

The JWT is validated with `spring.security.oauth2.resourceserver.jwt.jwk-set-uri: ${JWK_SET_URI:http://localhost:8080/realms/<realm>/protocol/openid-connect/certs}`. In a cluster the Deployment builds `JWK_SET_URI` from the ConfigMap keys `KEYCLOAK_URL` (`http://keycloak:8080`) and `REALM`: `value: $(KEYCLOAK_URL)/realms/$(REALM)/protocol/openid-connect/certs`. It points inside the cluster. Do not derive it from the issuer: the issuer is the public UI host, which the API pod may not reach. When the API also checks `iss`, the public issuer is a single value (`OIDC_ISSUER`) set in the environment overlay, never in `base`.

## 8. Status Endpoints

The UI footer polls `/actuator/health` for the API status and reads `/actuator/info` for its running version, both without a token:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-components: always   # components (db, ...) without details
      probes:
        enabled: true
      group:
        readiness:
          include: readinessState,db
```

`build-info` fills `/actuator/info` `build.version`, equal to the contract's `info.version`. An integration test asserts both answer without a token, `health` lists `db`, `build.version` equals `info.version`, and `/actuator/env` returns 401 (`testing-patterns`).

## 9. README "Connect a UI"

The README keeps a section with the values the UI's `crud-ui-init` asks for. Update it whenever a port, realm, client, user or role changes:

````markdown
## Connect a UI
`.env.local` of a crud-ui-blueprint UI for this API's local stack:
```
VITE_OIDC_AUTHORITY=http://localhost:8080/realms/<realm>
VITE_OIDC_CLIENT_ID=<realm>-ui
VITE_OIDC_ROLES_CLAIM=realm_access.roles
API_PROXY_TARGET=http://localhost:8081
CONTRACT_SOURCE=../<project>/contract/openapi.json
E2E_WRITER_USER=editor
E2E_WRITER_PASSWORD=editor
E2E_READER_USER=viewer
E2E_READER_PASSWORD=viewer
```
Read role `<realm>-viewer`, write role `<realm>-editor` (realm roles in `realm_access.roles`). The UI footer reads the
public `/actuator/health` (components) and `/actuator/info` (`build.version`).
````

## 10. Build Order and Definition of Done

Build order for a new API: copy `docs/API-CONTRACT.md` and `docs/DEPLOY-CONVENTION.md` from the UI template unchanged (crud-ui-blueprint's `docs/API-KICKSTART.md` also hands over a `CLAUDE.md` template); then the skeleton (`pom.xml` with `build-info` and actuator, `docker-compose.yml` with PostgreSQL and Keycloak and their healthchecks, `deploy/` as section 11 lays it out with `deploy/base/realm.json`, `skaffold.yaml`, `application.yml`, CI with the contract check and the overlay render), then the shared infrastructure (`SecurityConfig`, `OpenApiConfig`, `ApiExceptionHandler` with `Problem`/`FieldError`/`notFound`/`inUse`, `Reference`, the contract export test), then one resource at a time, referenced resources first (migration, entity, DTOs, filter, repository, service, controller, tests, README).

Done means:
1. `mvn clean verify` passes with the integration tests running, not skipped.
2. `contract/openapi.json` is regenerated, its `info.version` bumped, and part of the change.
3. Positive and negative tests; a relation change has an integration test.
4. The local API was smoke-tested with a `client_credentials` token from `<realm>-api` and the smoke data deleted.
5. README updated: changelog, API and error tables, "Connect a UI".
6. A schema change also updates the demo data seed (`data.md`, Flyway, "Demo Data Seed"), so a fresh deploy still shows records in the UI.

## 11. Deployment (Kubernetes and OpenShift)

The API and the UI are separate repos, but they deploy as one application. They share one convention, `docs/DEPLOY-CONVENTION.md`. It lives in the UI template, every repo has an identical copy, and a change goes into all copies in the same session. When the project has the file, follow it; this section is its summary. Generic patterns (overlays, probes, PVC, Compose healthchecks) are in `cloud.md`.

- **Nothing in git names a cluster, host, registry, namespace or secret value.** The same files deploy to any cluster and namespace. What differs per environment comes from an environment overlay or a Skaffold flag.
- **Layout:** `deploy/base` (plain Kubernetes: PostgreSQL, Keycloak, the API, its ConfigMap, the realm `configMapGenerator`), `deploy/overlays/{local,openshift,kubernetes}`, `deploy/environments/example` (committed) and `deploy/environments/<env>` (git-ignored). Skaffold profiles: none (local, with port forwarding), `openshift`, `kubernetes`. See `cloud.md` for the `skaffold.yaml`.
- **Names:** the stack's dependencies use the fixed Service names `postgres` and `keycloak`, which the UI repo relies on (`http://<api>:8080`, `http://keycloak:8080`). The whole application is installed into **one namespace**, API repo first, then UI repo. Each `skaffold run` and `skaffold delete` covers only its own repo's objects.
- **Labels** on every Deployment, Service, Route and Ingress: `app.kubernetes.io/name`, `app.kubernetes.io/component` (`backend`, `database`, `auth`, `frontend`) and `app.kubernetes.io/part-of` (the same in every repo). Selectors stay `app: <name>`. `app.kubernetes.io/version` is set only at deploy time (`--label app.kubernetes.io/version=$(git rev-parse --short HEAD)`).
- **OpenShift Topology** (only in `overlays/openshift`): one metadata-only patch per Deployment (`topology-<app>.yaml`). It carries `app.openshift.io/runtime` (`spring`, `postgresql`; Keycloak has no console icon, so it gets `app.openshift.io/custom-icon`), `app.openshift.io/vcs-uri`, and `app.openshift.io/connects-to` arrows from each caller to what it calls. Each repo declares only its own arrows: API → `postgres`, `keycloak`. Check a new runtime value against the console's icon list; an unknown value silently shows a generic icon.
- **Secrets:** `base` references Secrets by name only: `postgres` (`database`, `username`, `password`), `keycloak-admin` (`username`, `password`), `<api>-oidc-client` (`client-secret`), `<api>-demo-users`. A Skaffold `before` deploy hook, `deploy/ensure-secrets.sh`, creates each missing Secret in the deploy's namespace and context (`$SKAFFOLD_NAMESPACES`, `$SKAFFOLD_KUBE_CONTEXT`). Real secrets get random values (`openssl rand -hex 16`). It **never changes an existing Secret**, because PostgreSQL keeps the password of its first start. An environment that manages its own Secrets creates them first, and the hook leaves them alone.
- **One public host,** the UI's. Its nginx proxies `/api/` and `/actuator/{health,info}` to the API, and `/realms/<realm>/` and `/resources/` to Keycloak. The API may have its own Route (no `spec.host`: OpenShift generates one) for Swagger UI and machine clients. Keycloak has none (section 7).
- **Checks:** CI renders every overlay and the example environment (`kustomize build`), and a `grep` of the rendered output finds no cluster domain, registry or namespace. `localhost` may appear only in the realm's development redirect.
