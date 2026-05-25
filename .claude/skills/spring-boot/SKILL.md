---
name: spring-boot
description: Spring Boot 4.x development (3.x compatible) - REST APIs, JPA, MongoDB, Security, Testing, and Cloud-native patterns. Use for building enterprise Java applications with Spring Boot.
metadata:
  version: "2.1.0"
  domain: backend
  triggers: Spring Boot, Spring Framework, Spring Security, Spring Data JPA, Spring Data MongoDB, Spring WebFlux, Java REST API, Microservices Java
  role: specialist
  scope: implementation
  output-format: code
---

# Spring Boot Skill

Enterprise Spring Boot 4.x development with focus on clean architecture and production-ready
code. Examples target Spring Boot 4.x by default and note where Spring Boot 3.x differs.

## Spring Boot 4.x at a Glance

Spring Boot 4.0 (GA Nov 2025) builds on **Spring Framework 7** and pulls in Spring Security 7,
Spring Data 2025.1, and Hibernate 7.1. Key changes versus 3.x:

| Area | Spring Boot 4.x | Spring Boot 3.x |
|------|-----------------|-----------------|
| Java baseline | Java 17 min; first-class Java 25 | Java 17 min |
| JSON | **Jackson 3** (`tools.jackson.*`, `JsonMapper`) | Jackson 2 (`com.fasterxml.jackson.*`) |
| Null safety | **JSpecify** (`@NullMarked`, `org.jspecify.annotations.@Nullable`) | Spring `@Nullable` |
| API versioning | **Built into MVC/WebFlux** (`@GetMapping(version = ...)`) | Manual (paths/headers) |
| HTTP clients | **Declarative `@HttpExchange` + `@ImportHttpServices`** | `HttpServiceProxyFactory` by hand |
| Resilience | **`@Retryable` / `@ConcurrencyLimit` in spring-context** | Spring Retry / Resilience4j libs |
| Test mocks | `@MockitoBean` / `@MockitoSpyBean` | `@MockBean` / `@SpyBean` (removed in 4.x) |
| Modules | Autoconfigure split into focused jars; some starters renamed | Monolithic autoconfigure jar |

Migrating 3.x → 4.x: run the official OpenRewrite recipes and add the
`spring-boot-properties-migrator` dependency to surface renamed properties at startup.

## Core Workflow

1. **Analyze** - Understand requirements, identify service boundaries, APIs, data models
2. **Design** - Plan architecture, confirm design before coding
3. **Implement** - Build with constructor injection and layered architecture
4. **Secure** - Add Spring Security, OAuth2, method security; verify tests pass
5. **Test** - Write unit, integration tests; run `./mvnw test` and confirm all pass
6. **Deploy** - Configure health checks via Actuator; validate `/actuator/health` returns UP

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

    // Getters and setters — or Lombok @Getter/@Setter; see "Boilerplate" below
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

    public List<Product> search(String name) {
        return repo.findByNameContainingIgnoreCase(name);
    }

    @Transactional
    public Product create(ProductRequest request) {
        var product = new Product();
        product.setName(request.name());
        product.setPrice(request.price());
        return repo.save(product);
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
    public List<Product> search(@RequestParam(defaultValue = "") String name) {
        return service.search(name);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Product create(@Valid @RequestBody ProductRequest request) {
        return service.create(request);
    }
}
```

### DTO (Record)
```java
public record ProductRequest(
    @NotBlank String name,
    @DecimalMin("0.0") BigDecimal price
) {}
```

### Global Exception Handler
```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleValidation(MethodArgumentNotValidException ex) {
        return ex.getBindingResult().getFieldErrors().stream()
            .collect(Collectors.toMap(FieldError::getField,
                    error -> error.getDefaultMessage() != null ? error.getDefaultMessage() : "Invalid"));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleNotFound(EntityNotFoundException ex) {
        return Map.of("error", ex.getMessage());
    }
}
```

### Test Slice
```java
@WebMvcTest(ProductController.class)
class ProductControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean ProductService service;   // @MockBean was removed in Spring Boot 4.x

    @Test
    void createProduct_validRequest_returns201() throws Exception {
        var product = new Product();
        product.setName("Widget");
        when(service.create(any())).thenReturn(product);

        mockMvc.perform(post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Widget\",\"price\":10.0}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Widget"));
    }
}
```

## Reference Guide

Load detailed patterns based on context:

| Topic | Reference | When to Load |
|-------|-----------|-------------|
| Web/REST | `references/web.md` | Controllers, validation, API versioning, HTTP clients |
| Data Access (JPA) | `references/data.md` | JPA, repositories, transactions, queries |
| Data Access (MongoDB) | `references/mongodb.md` | `@Document`, Mongo repositories, aggregation, MongoTemplate |
| Security | `references/security.md` | Spring Security 6/7, OAuth2, JWT, auth |
| Cloud/Config | `references/cloud.md` | Config server, discovery, resilience |
| Testing | `references/testing.md` | Unit, integration, slice tests |

For data-store-specific pitfalls, the standalone `jpa-patterns` and `mongodb-patterns` skills
cover performance and schema-design traps in depth.

## Constraints

### MUST DO
- Constructor injection (no field injection)
- `@Valid` on all request bodies
- `@Transactional` for multi-step writes
- `@Transactional(readOnly = true)` for reads
- Type-safe config with `@ConfigurationProperties`
- Global exception handling with `@RestControllerAdvice`
- Externalize secrets (use env vars, not properties files)

### MUST NOT DO
- Field injection (`@Autowired` on fields)
- Skip input validation on endpoints
- Mix blocking and reactive code
- Store secrets in application.properties
- Use Spring Boot 3.x APIs removed in 4.x (`@MockBean`/`@SpyBean`, `com.fasterxml.jackson.*` assumptions)
- Hardcode URLs, credentials, environment values
- Put Lombok `@Data`/`@EqualsAndHashCode`/`@ToString` on a JPA/Mongo entity (see Boilerplate)

## Boilerplate: Records, Modern Java & Lombok

Both styles are supported. **Default to modern Java to minimize boilerplate; use Lombok when a
project already does.** Match the surrounding codebase and stay consistent within a module.

| Type | Default (no extra deps) | Lombok equivalent |
|------|-------------------------|-------------------|
| DTOs / requests / responses | **`record`** — immutable, zero boilerplate | (records need no Lombok) |
| Value objects | **`record`** | `@Value` |
| JPA/Mongo entities | explicit getters/setters (entities **can't** be records) | `@Getter @Setter @NoArgsConstructor` |
| Services / components | explicit constructor injection | `@RequiredArgsConstructor` (over `final` fields) |
| Loggers | `private static final Logger log = LoggerFactory.getLogger(X.class);` | `@Slf4j` |

Records already remove DTO boilerplate, so most apps only consider Lombok for **entities**
(getters/setters) and **constructor injection**.

### Lombok on entities — dos & don'ts

```java
// ✅ GOOD: accessors + the no-arg constructor JPA/Hibernate require
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;

    @OneToMany(mappedBy = "product")
    private List<Variant> variants = new ArrayList<>();
}
```

- **DO** use `@Getter`/`@Setter` and `@RequiredArgsConstructor` (pairs naturally with `final`
  dependency fields and constructor injection).
- **DON'T** put `@Data`, `@EqualsAndHashCode`, or `@ToString` on an entity. `@Data` generates
  `equals`/`hashCode`/`toString` across **all** fields, which: triggers lazy loading, recurses
  infinitely on bidirectional relationships, and breaks entity identity semantics (entities
  should compare by `@Id`, not by every column).
- **DON'T** rely on `@Builder` for an entity without also adding `@NoArgsConstructor` — JPA needs
  a no-arg constructor. Prefer setters or a factory method instead.
- If you must include `@ToString`/`@EqualsAndHashCode`, exclude associations:
  `@ToString(exclude = "variants")`, `@EqualsAndHashCode(onlyExplicitlyIncluded = true)`.

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
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }
}
```

## Spring Boot 4 Features

> Spring Boot 4.x / Spring Framework 7.x only. On 3.x, use the prior equivalents noted in the
> "at a glance" table above.

### API Versioning (native)

```java
@RestController
@RequestMapping("/api/products")
public class ProductController {

    @GetMapping(value = "/{id}", version = "1.0")
    public ProductV1 getV1(@PathVariable String id) { /* ... */ }

    @GetMapping(value = "/{id}", version = "2.0")
    public ProductV2 getV2(@PathVariable String id) { /* ... */ }
}

@Configuration
public class ApiVersionConfig implements WebMvcConfigurer {
    @Override
    public void configureApiVersioning(ApiVersionConfigurer configurer) {
        configurer.useRequestHeader("X-API-Version");  // or path/query-param/media-type strategy
    }
}
```

### Declarative HTTP Service Client

```java
@HttpExchange(url = "/users", accept = "application/json")
public interface UserClient {
    @GetExchange("/{id}")
    User getById(@PathVariable Long id);

    @PostExchange
    User create(@RequestBody CreateUserRequest request);
}

@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = "users", types = UserClient.class)  // Boot builds the proxy bean
public class HttpClientConfig {
    @Bean
    RestClientHttpServiceGroupConfigurer usersBaseUrl() {
        return groups -> groups.filterByName("users")
            .forEachClient((g, builder) -> builder.baseUrl("https://api.example.com"));
    }
}
```

### Built-in Resilience (no Resilience4j needed)

```java
@Configuration
@EnableResilientMethods
public class ResilienceConfig { }

@Service
public class InventoryService {

    @Retryable(maxAttempts = 4, delay = 500, multiplier = 2.0, maxDelay = 5000, jitter = 100)
    public StockLevel fetch(String sku) { /* retried with exponential backoff + jitter */ }

    @ConcurrencyLimit(5)  // cap concurrent invocations
    public Report buildHeavyReport() { /* ... */ }
}
```

### JSpecify Null Safety

```java
// package-info.java — everything in the package is non-null by default
@NullMarked
package pl.piomin.services.order;

import org.jspecify.annotations.NullMarked;

// Opt specific values back into nullable
public Order find(Long id, @Nullable String tenant) { /* ... */ }  // org.jspecify.annotations.Nullable
```

## Knowledge Base

Spring Boot 4.x (3.x compatible), Spring Framework 7, Java 17–25, Spring WebFlux, Project
Reactor, Spring Data JPA, Spring Data MongoDB, Spring Security 7, OAuth2/JWT, Hibernate 7,
R2DBC, Jackson 3, JSpecify, Spring Cloud, built-in resilience (`@Retryable`), Micrometer, JUnit
5, Testcontainers, Mockito, Maven/Gradle
