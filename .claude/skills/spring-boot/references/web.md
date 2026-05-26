# Web Layer - Controllers & REST APIs

> Controller, validation, and exception-handling patterns below are version-neutral. The
> "Spring Boot 4 Web Features" section near the end covers native API versioning and declarative
> HTTP clients (4.x / Spring Framework 7 only).

## REST Controller Pattern

```java
@RestController
@RequestMapping("/api/users")
@Validated
public class UserController {
    private final UserService userService;
    
    public UserController(UserService userService) {
        this.userService = userService;
    }
    
    @GetMapping
    public ResponseEntity<Page<UserResponse>> getUsers(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        Page<UserResponse> users = userService.findAll(pageable);
        return ResponseEntity.ok(users);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUser(@PathVariable Long id) {
        UserResponse user = userService.findById(id);
        return ResponseEntity.ok(user);
    }

    @PostMapping
    public ResponseEntity<UserResponse> createUser(
            @Valid @RequestBody UserCreateRequest request) {
        UserResponse user = userService.create(request);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(user.id())
                .toUri();
        return ResponseEntity.created(location).body(user);
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UserUpdateRequest request) {
        UserResponse user = userService.update(id, request);
        return ResponseEntity.ok(user);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable Long id) {
        userService.delete(id);
    }
}
```

## Request DTOs with Validation

```java
public record UserCreateRequest(
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    String email,

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be 8-100 characters")
    @Pattern(regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d).*$",
             message = "Password must contain uppercase, lowercase, and digit")
    String password,

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50)
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "Username must be alphanumeric")
    String username,

    @Min(value = 18, message = "Must be at least 18")
    @Max(value = 120, message = "Must be at most 120")
    Integer age
) {}

public record UserUpdateRequest(
    @Email(message = "Email must be valid")
    String email,

    @Size(min = 3, max = 50)
    String username
) {}
```

## Response DTOs

```java
public record UserResponse(
    Long id,
    String email,
    String username,
    Integer age,
    Boolean active,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
            user.getId(),
            user.getEmail(),
            user.getUsername(),
            user.getAge(),
            user.getActive(),
            user.getCreatedAt(),
            user.getUpdatedAt()
        );
    }
}
```

## Global Exception Handling

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            ResourceNotFoundException ex, WebRequest request) {
        log.error("Resource not found: {}", ex.getMessage());
        ErrorResponse error = new ErrorResponse(
            HttpStatus.NOT_FOUND.value(),
            ex.getMessage(),
            request.getDescription(false),
            LocalDateTime.now()
        );
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidation(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .collect(Collectors.toMap(
                FieldError::getField,
                error -> error.getDefaultMessage() != null
                    ? error.getDefaultMessage()
                    : "Invalid value"
            ));

        ValidationErrorResponse response = new ValidationErrorResponse(
            HttpStatus.BAD_REQUEST.value(),
            "Validation failed",
            errors,
            LocalDateTime.now()
        );
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(
            DataIntegrityViolationException ex, WebRequest request) {
        log.error("Data integrity violation", ex);
        ErrorResponse error = new ErrorResponse(
            HttpStatus.CONFLICT.value(),
            "Data integrity violation - resource may already exist",
            request.getDescription(false),
            LocalDateTime.now()
        );
        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(
            Exception ex, WebRequest request) {
        log.error("Unexpected error", ex);
        ErrorResponse error = new ErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "An unexpected error occurred",
            request.getDescription(false),
            LocalDateTime.now()
        );
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}

record ErrorResponse(
    int status,
    String message,
    String path,
    LocalDateTime timestamp
) {}

record ValidationErrorResponse(
    int status,
    String message,
    Map<String, String> errors,
    LocalDateTime timestamp
) {}
```

## Custom Validation

```java
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = UniqueEmailValidator.class)
public @interface UniqueEmail {
    String message() default "Email already exists";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

@Component
public class UniqueEmailValidator implements ConstraintValidator<UniqueEmail, String> {
    private final UserRepository userRepository;

    public UniqueEmailValidator(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    
    @Override
    public boolean isValid(String email, ConstraintValidatorContext context) {
        if (email == null) return true;
        return !userRepository.existsByEmail(email);
    }
}
```

## WebClient for External APIs

```java
@Configuration
public class WebClientConfig {
    @Bean
    public WebClient webClient(WebClient.Builder builder) {
        return builder
            .baseUrl("https://api.example.com")
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .filter(logRequest())
            .build();
    }

    private ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(request -> {
            log.info("Request: {} {}", request.method(), request.url());
            return Mono.just(request);
        });
    }
}

@Service
public class ExternalApiService {
    private final WebClient webClient;

    public ExternalApiService(WebClient webClient) {
        this.webClient = webClient;
    }
    
    public Mono<ExternalDataResponse> fetchData(String id) {
        return webClient
            .get()
            .uri("/data/{id}", id)
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, response ->
                Mono.error(new ResourceNotFoundException("External resource not found")))
            .onStatus(HttpStatusCode::is5xxServerError, response ->
                Mono.error(new ServiceUnavailableException("External service unavailable")))
            .bodyToMono(ExternalDataResponse.class)
            .timeout(Duration.ofSeconds(5))
            .retry(3);
    }
}
```

## CORS Configuration

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOrigins("http://localhost:3000", "https://example.com")
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true)
            .maxAge(3600);
    }
}
```

## Spring Boot 4 Web Features

> Spring Boot 4.x / Spring Framework 7.x only.

### Native API Versioning

Versioning is part of the MVC/WebFlux routing model — no custom `RequestCondition` needed.

```java
@RestController
@RequestMapping("/api/users")
public class UserController {

    @GetMapping(value = "/{id}", version = "1.0")
    public UserV1 getV1(@PathVariable Long id) { /* ... */ }

    @GetMapping(value = "/{id}", version = "2.0")   // matched only for v2 requests
    public UserV2 getV2(@PathVariable Long id) { /* ... */ }
}

@Configuration
public class ApiVersionConfig implements WebMvcConfigurer {
    @Override
    public void configureApiVersioning(ApiVersionConfigurer configurer) {
        configurer
            .useRequestHeader("X-API-Version")        // header strategy
            .setVersionRequired(false)                // fall back to the default version
            .setDefaultVersion("1.0");
        // Alternatives: usePathSegment(int), useQueryParam("version"),
        //               useMediaTypeParameter(MediaType.APPLICATION_JSON, "version")
    }
}
```

### Declarative HTTP Service Clients

Feign-style interface clients are native in 4.x. Annotate an interface; Boot generates and
registers the proxy.

```java
@HttpExchange(url = "/orders", accept = "application/json")
public interface OrderClient {
    @GetExchange("/{id}")
    OrderResponse getById(@PathVariable Long id);

    @PostExchange
    OrderResponse create(@RequestBody CreateOrderRequest request);
}

@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = "orders", types = OrderClient.class)
public class HttpClientsConfig {

    // Configure the shared RestClient builder for the "orders" group
    @Bean
    RestClientHttpServiceGroupConfigurer ordersConfigurer() {
        return groups -> groups.filterByName("orders")
            .forEachClient((group, builder) -> builder
                .baseUrl("https://api.example.com")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));
    }
}
```

Inject `OrderClient` like any other bean. For reactive apps, back the group with a `WebClient`
builder via `WebClientHttpServiceGroupConfigurer` instead.

## Quick Reference

| Annotation | Purpose |
|------------|---------|
| `@GetMapping(version = "...")` | Route by API version (Spring Boot 4.x) |
| `@HttpExchange`/`@GetExchange` | Declarative HTTP service client interface (4.x) |
| `@ImportHttpServices` | Register HTTP client proxies as beans (4.x) |
| `@RestController` | Marks class as REST controller (combines @Controller + @ResponseBody) |
| `@RequestMapping` | Maps HTTP requests to handler methods |
| `@GetMapping/@PostMapping` | HTTP method-specific mappings |
| `@PathVariable` | Extracts values from URI path |
| `@RequestParam` | Extracts query parameters |
| `@RequestBody` | Binds request body to method parameter |
| `@Valid` | Triggers validation on request body |
| `@RestControllerAdvice` | Global exception handling for REST controllers |
| `@ResponseStatus` | Sets HTTP status code for method |
