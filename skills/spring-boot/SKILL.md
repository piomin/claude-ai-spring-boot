---
name: spring-boot
description: Spring Boot 4.x development - REST APIs, JPA, Security, Testing, and Cloud-native patterns. Use for building enterprise Java applications with Spring Boot.
metadata:
  version: "2.0.0"
  domain: backend
  triggers: Spring Boot, Spring Framework, Spring Security, Spring Data JPA, Spring WebFlux, Java REST API, Microservices Java
  role: specialist
  scope: implementation
  output-format: code
---

# Spring Boot Skill

Enterprise Spring Boot 4.x development with focus on clean architecture and production-ready code.

## Core Workflow

1. **Analyze** - Understand requirements, identify service boundaries, APIs, data models
2. **Design** - Plan architecture, confirm design before coding
3. **Implement** - Build with constructor injection and layered architecture; for a relational database, write Flyway migrations alongside entities (`ddl-auto: validate`)
4. **Secure** - Add Spring Security, OAuth2, method security; verify tests pass
5. **Test** - Write unit, integration tests; run `./mvnw clean verify` and confirm all pass, with integration tests running (not skipped)
6. **Deploy** - Configure health checks via Actuator; validate `/actuator/health` returns UP and `/actuator/info` shows `build.version`

## Quick Start Templates

### Entity
```java
@Entity
@Table(name = "products")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    private String name;

    @DecimalMin("0.0")
    private BigDecimal price;

    // Getters/Setters (no Lombok)
}
```

### Repository
```java
public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByNameContainingIgnoreCase(String name);
}
```

### Service
```java
@Service
@Transactional(readOnly = true)
public class ProductService {
    private final ProductRepository repo;

    public ProductService(ProductRepository repo) {
        this.repo = repo;
    }

    public List<ProductResponse> search(String name) {
        return repo.findByNameContainingIgnoreCase(name).stream()
            .map(ProductResponse::from)
            .toList();
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        var product = new Product();
        product.setName(request.name());
        product.setPrice(request.price());
        return ProductResponse.from(repo.save(product));
    }
}
```

### REST Controller
```java
@RestController
@RequestMapping("/api/v1/products")
@Validated
public class ProductController {
    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @GetMapping
    public List<ProductResponse> search(@RequestParam(defaultValue = "") String name) {
        return service.search(name);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody ProductRequest request) {
        return service.create(request);
    }
}
```

### DTOs (Records)
```java
public record ProductRequest(
    @NotBlank String name,
    @DecimalMin("0.0") BigDecimal price
) {}

public record ProductResponse(Long id, String name, BigDecimal price) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(product.getId(), product.getName(), product.getPrice());
    }
}
```

### Global Exception Handler (Problem Details)
```java
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    public record FieldMessage(String field, String code, String message) {}

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        ex.getBody().setProperty("errors", ex.getFieldErrors().stream()
                .map(e -> new FieldMessage(e.getField(), code(e.getCode()), e.getDefaultMessage()))
                .toList());
        return super.handleMethodArgumentNotValid(ex, headers, status, request);
    }

    private static String code(String constraint) {
        return switch (constraint) {
            case "NotBlank", "NotNull" -> "required";
            case null, default -> "invalid";
        };
    }

    @ExceptionHandler
    ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Record was changed by someone else; reload and retry");
    }

    @ExceptionHandler
    ProblemDetail handleUnexpected(Exception ex) {
        logger.error("Unexpected error", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error");
    }
}
```
Every error is RFC 9457 Problem Details with field errors in `errors: [{field, code, message}]`. Full handler (unique constraints, bad sort, documented codes): `references/web.md`.

### Test Slice
```java
@WebMvcTest(ProductController.class)
class ProductControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean ProductService service;

    @Test
    void createProduct_validRequest_returns201() throws Exception {
        var response = new ProductResponse(1L, "Widget", new BigDecimal("10.00"));
        when(service.create(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Widget","price":10.0}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Widget"));
    }
}
```

## Reference Guide

Load detailed patterns based on context:

| Topic | Reference | When to Load |
|-------|-----------|-------------|
| Web/REST | `references/web.md` | Controllers, paged lists, validation, PUT and `version`, Problem Details (RFC 9457) with `errors[]` |
| API built for a UI | `references/ui-contract.md` | Only when a UI or generated client consumes the OpenAPI contract (see `conventions`): `/api`, springdoc 3 contract export, error codes, filters, relations, Keycloak, "Connect a UI" |
| Data Access | `references/data.md` | JPA, repositories, transactions, queries, Flyway migrations |
| Security | `references/security.md` | Spring Security 6, OAuth2, JWT, auth |
| Cloud/Config | `references/cloud.md` | Config server, discovery, resilience, virtual threads, HTTP interfaces, GraalVM native, OTLP tracing |
| AI | `references/ai.md` | Only when the app calls an LLM: Spring AI 2.x `ChatClient`, structured output, tool calling, RAG, chat memory, MCP server, testing, 1.x to 2.0 renames |
| Testing | `references/testing.md` | Unit, integration, slice tests, Testcontainers @ServiceConnection |

## Constraints

### MUST DO
- Constructor injection (no field injection)
- `@Valid` on all request bodies
- `@Transactional` for multi-step writes
- `@Transactional(readOnly = true)` for reads
- Type-safe config with `@ConfigurationProperties`
- Global exception handling with `@RestControllerAdvice`
- Externalize secrets (use env vars, not properties files)
- Flyway migrations + `ddl-auto: validate` when persisting to a relational database (skip Flyway otherwise)
- Errors as Problem Details; list endpoints return `PagedModel`
- Permit `/actuator/health/**` so the liveness and readiness probes work behind security
- **Only when the API is built for a UI** (see `conventions`, "REST API Built for a UI"): follow `references/ui-contract.md`

### MUST NOT DO
- Field injection (`@Autowired` on fields)
- Skip input validation on endpoints
- Mix blocking and reactive code
- Store secrets in application.properties
- Use deprecated Spring Boot 2.x/3.x patterns
- Hardcode URLs, credentials, environment values
- Use `ddl-auto: update`/`create` outside tests, or edit an already-applied migration
- Return `Page`/`PageImpl` from a controller, or put `@EnableSpringDataWebSupport` on the application class (`@WebMvcTest` registers it twice)
- Put `@ApiResponse` on controller methods (springdoc 3 then drops the success response; use an `OpenApiCustomizer`)
- Return an exception's message from the catch-all handler

### Spring Boot 4 / Spring Data 4 Notes
- `PropertyReferenceException` and `TypeInformation` moved to `org.springframework.data.core`
- `@MockBean`/`@SpyBean` are removed: use `@MockitoBean`/`@MockitoSpyBean`
- `@WebMvcTest` creates no repositories: every repository a controller injects needs a `@MockitoBean` in its test
- Build with `./mvnw clean verify`: classes compiled by the IDE in `target` can hide `javac` errors

## Architecture Patterns

**Project Structure:**
```
src/main/java/pl/piomin/services/
├── controller/     # REST endpoints
├── service/        # Business logic
├── repository/     # Data access
├── model/          # Entities
├── dto/            # Request/Response DTOs
├── config/         # Configuration
└── exception/      # Custom exceptions + handler
```

**Layering:**
- Controller → Service → Repository
- Controller handles HTTP, validation
- Service handles business logic, transactions
- Repository handles data persistence

**Clean Architecture Principles:**
- Domain models independent of frameworks
- Use case driven design
- Dependency inversion (interfaces)
- Clear boundaries between layers

## Common Annotations

| Annotation | Purpose |
|------------|---------|
| `@RestController` | REST controller (combines @Controller + @ResponseBody) |
| `@Service` | Business logic component |
| `@Repository` | Data access component |
| `@Transactional` | Transaction management |
| `@Valid` | Trigger validation |
| `@ConfigurationProperties` | Bind properties to class |
| `@EnableMethodSecurity` | Enable method security |

## Reactive WebFlux Endpoint

```java
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<OrderDto>> getOrder(@PathVariable UUID id) {
        return orderService.findById(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<OrderDto> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return orderService.create(request);
    }
}
```

## Spring Security JWT

```java
@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**").permitAll()  // + "/actuator/info" when built for a UI
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }
}
```

## Knowledge Base

Spring Boot 4.x, Java 21+, Spring WebFlux, Project Reactor, Spring Data JPA, Spring Security, OAuth2/JWT, Hibernate, R2DBC, Spring Cloud, Resilience4j, Micrometer, JUnit 5, Testcontainers, Mockito, Maven, Paketo Buildpacks, Skaffold
