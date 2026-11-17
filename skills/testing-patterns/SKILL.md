---
name: testing-patterns
description: Testing patterns for Spring Boot - JUnit 5, Mockito, MockMvc, Testcontainers, positive and negative test cases. Use when user asks about testing, writing tests, test coverage, or test failures.
---

# Testing Patterns Skill

Testing strategies for Spring Boot applications. Always write both positive and negative test cases.

## When to Use
- "write tests" / "add test coverage" / "test this"
- Test failures or debugging test issues
- Choosing between unit, slice, and integration tests

---

## Test Pyramid

| Level | Annotation | Speed | Scope | When to Use |
|-------|-----------|-------|-------|-------------|
| Unit | `@ExtendWith(MockitoExtension.class)` | Fast | Single class | Business logic in services |
| Slice | `@WebMvcTest`, `@DataJpaTest` | Medium | One layer | Controllers, repositories |
| Integration | `@SpringBootTest` | Slow | Full app | End-to-end flows, cross-layer |

---

## Unit Tests (Service Layer)

```java
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductService productService;

    @InjectMocks
    private OrderService orderService;

    // ✅ Positive test
    @Test
    @DisplayName("Should create order with valid items")
    void shouldCreateOrder() {
        // Given
        var request = new OrderRequest(List.of(
            new OrderItem(1L, 2)
        ));
        var product = new Product();
        product.setId(1L);
        product.setPrice(new BigDecimal("10.00"));

        when(productService.findById(1L)).thenReturn(product);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(1L);
            return o;
        });

        // When
        Order result = orderService.create(request);

        // Then
        assertThat(result.getId()).isEqualTo(1L);
        verify(orderRepository).save(any(Order.class));
    }

    // ✅ Negative test
    @Test
    @DisplayName("Should throw when product not found")
    void shouldThrowWhenProductNotFound() {
        // Given
        var request = new OrderRequest(List.of(
            new OrderItem(999L, 1)
        ));
        when(productService.findById(999L))
            .thenThrow(new EntityNotFoundException("Product not found"));

        // When & Then
        assertThatThrownBy(() -> orderService.create(request))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("Product not found");

        verify(orderRepository, never()).save(any());
    }

    // ✅ Negative test - empty input
    @Test
    @DisplayName("Should throw when order has no items")
    void shouldThrowWhenNoItems() {
        var request = new OrderRequest(List.of());

        assertThatThrownBy(() -> orderService.create(request))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
```

---

Spring Boot 4 imports: `@WebMvcTest` is in `org.springframework.boot.webmvc.test.autoconfigure`, `@DataJpaTest` in `org.springframework.boot.data.jpa.test.autoconfigure`, `@MockitoBean` in `org.springframework.test.context.bean.override.mockito`, `PostgreSQLContainer` in `org.testcontainers.postgresql` (Testcontainers 2, not generic), `JsonMapper` in `tools.jackson.databind.json` (Jackson 3). Full list and test dependencies: `spring-boot` → `references/testing.md`.

## Controller Slice Tests (@WebMvcTest)

```java
@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @Autowired
    private JsonMapper jsonMapper;

    // ✅ Positive - successful creation
    @Test
    @DisplayName("POST /api/v1/products - returns 201")
    void createProduct_validRequest_returns201() throws Exception {
        var request = new ProductRequest("Widget", new BigDecimal("10.00"));
        var product = new Product();
        product.setId(1L);
        product.setName("Widget");

        when(productService.create(any())).thenReturn(product);

        mockMvc.perform(post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Widget"));
    }

    // ✅ Negative - validation failure
    @Test
    @DisplayName("POST /api/v1/products - blank name returns 400")
    void createProduct_blankName_returns400() throws Exception {
        var request = new ProductRequest("", new BigDecimal("10.00"));

        mockMvc.perform(post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.errors[0].field").value("name"))
            .andExpect(jsonPath("$.errors[0].code").value("required"));

        verify(productService, never()).create(any());
    }

    // ✅ Negative - not found
    @Test
    @DisplayName("GET /api/v1/products/999 - returns 404")
    void getProduct_notFound_returns404() throws Exception {
        when(productService.findById(999L))
            .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Product 999 not found"));

        mockMvc.perform(get("/api/v1/products/999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
    }

    // ✅ Negative - stale version
    @Test
    @DisplayName("PUT /api/v1/products/1 - stale version returns 409 without errors")
    void updateProduct_staleVersion_returns409() throws Exception {
        when(productService.update(eq(1L), any()))
            .thenThrow(new ObjectOptimisticLockingFailureException(Product.class, 1L));

        mockMvc.perform(put("/api/v1/products/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Widget","price":10.0,"version":0}
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errors").doesNotExist());
    }
}
```

- Assert error bodies as Problem Details (`$.status`, `$.errors[*].field`, `$.errors[*].code`), never on `message` texts.
- `@WebMvcTest` creates no repositories or services: every bean the controller injects needs a `@MockitoBean`, including a repository newly added to an existing controller.

---

## Repository Slice Tests (@DataJpaTest)

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ProductRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("Should find products by name containing")
    void shouldFindByNameContaining() {
        // Given
        var product = new Product();
        product.setName("Spring Widget");
        product.setPrice(new BigDecimal("10.00"));
        entityManager.persistAndFlush(product);

        // When
        List<Product> results = productRepository
            .findByNameContainingIgnoreCase("widget");

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("Spring Widget");
    }

    @Test
    @DisplayName("Should return empty list when no match")
    void shouldReturnEmptyWhenNoMatch() {
        List<Product> results = productRepository
            .findByNameContainingIgnoreCase("nonexistent");

        assertThat(results).isEmpty();
    }
}
```

---

## Integration Tests (@SpringBootTest)

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class ProductIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private BuildProperties buildProperties;

    @Test
    @DisplayName("Should apply all Flyway migrations")
    void migrationsApplied() {
        assertThat(flyway.info().current()).isNotNull();
        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    @DisplayName("Should reject row violating DB constraint")
    void databaseRejectsNegativePrice() {
        // Migration defines CONSTRAINT ck_products_price CHECK (price >= 0)
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO products (name, price, version) VALUES ('Widget', -1, 0)"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
    }

    @Test
    @DisplayName("Full CRUD flow works end-to-end")
    void crudFlow() {
        // Create
        var request = new ProductRequest("Widget", new BigDecimal("10.00"));
        var created = restTestClient.post()
            .uri("/api/v1/products")
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .exchange()
            .expectStatus().isCreated()
            .expectBody(ProductResponse.class)
            .returnResult()
            .getResponseBody();

        Long id = created.id();

        // Read
        restTestClient.get()
            .uri("/api/v1/products/{id}", id)
            .exchange()
            .expectStatus().isOk()
            .expectBody(ProductResponse.class)
            .value(product -> assertThat(product.name()).isEqualTo("Widget"));

        // Delete
        restTestClient.delete()
            .uri("/api/v1/products/{id}", id)
            .exchange()
            .expectStatus().isNoContent();

        restTestClient.get()
            .uri("/api/v1/products/{id}", id)
            .exchange()
            .expectStatus().isNotFound();
    }

    // Only when the API is built for a UI (public /actuator/info, see conventions)
    @Test
    @DisplayName("Health and info are public; other Actuator endpoints are not")
    void statusEndpointsArePublic() {
        restTestClient.get().uri("/actuator/health").exchange()
            .expectStatus().isOk()
            .expectBody().jsonPath("$.components.db.status").isEqualTo("UP");
        restTestClient.get().uri("/actuator/info").exchange()
            .expectStatus().isOk()
            .expectBody().jsonPath("$.build.version").isEqualTo(buildProperties.getVersion());
        restTestClient.get().uri("/actuator/env").exchange()
            .expectStatus().isUnauthorized();
    }
}
```

What the integration tests cover besides CRUD (the last two only when the API is built for a UI, see `conventions`):
- **Relations:** none → A, A → B and A → none for every `@ManyToOne` the API lets a client change, against the real database. A → B is the case that fails with `LazyInitializationException` when the write is not `@Transactional`; a mocked `@WebMvcTest` cannot see it.
- **Referenced delete:** deleting a record that is still referenced returns 409, and the row is still there.
- **Stale version:** two updates with the same `version`; the second returns 409.
- **Contract export:** the test that writes `contract/openapi.json` (`spring-boot` skill, `references/ui-contract.md`).
- **Status endpoints:** `health` and `info` answer without a token, everything else under `/actuator` does not.

Run them with `./mvnw clean verify`. With `@Testcontainers(disabledWithoutDocker = true)` they are skipped silently when Docker is not available, so check the test report shows them as run, not skipped. With Podman instead of Docker:

```bash
# macOS (podman machine); on Linux: unix:///run/user/$(id -u)/podman/podman.sock
export DOCKER_HOST=unix://$(podman machine inspect --format '{{.ConnectionInfo.PodmanSocket.Path}}')
export TESTCONTAINERS_RYUK_DISABLED=true
./mvnw clean verify
```

---

## Testcontainers Reusable Config

```java
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    @Bean
    @ServiceConnection
    public PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("postgres:16-alpine");
    }
}

// Use with @Import
@SpringBootTest
@Import(TestcontainersConfig.class)
class AnyIntegrationTest {
    // ...
}
```

---

## Parameterized Tests

```java
@ParameterizedTest
@ValueSource(strings = {"", " ", "ab"})
@DisplayName("Should reject invalid usernames")
void shouldRejectInvalidUsername(String username) {
    var request = new UserRequest(username, "valid@email.com");

    assertThatThrownBy(() -> userService.create(request))
        .isInstanceOf(ConstraintViolationException.class);
}

@ParameterizedTest
@CsvSource({
    "admin@test.com, ADMIN, true",
    "user@test.com, USER, true",
    "guest@test.com, GUEST, false"
})
@DisplayName("Should check access by role")
void shouldCheckAccessByRole(String email, String role, boolean expected) {
    assertThat(securityService.hasAccess(email, role)).isEqualTo(expected);
}
```

---

## Quick Reference

| Pattern | Use For |
|---------|---------|
| `@ExtendWith(MockitoExtension.class)` | Unit tests with mocks |
| `@WebMvcTest(Controller.class)` | Controller tests with MockMvc |
| `@DataJpaTest` | Repository tests with real DB |
| `@SpringBootTest` + `RANDOM_PORT` | Full integration tests |
| `@ServiceConnection` | Auto-configure Testcontainers |
| `@ParameterizedTest` | Multiple inputs, same assertion |
| `assertThatThrownBy()` | Negative test - expect exception |
| `verify(mock, never())` | Verify method was NOT called |

## Naming Convention

```
methodUnderTest_condition_expectedResult
```

Examples: `createUser_validRequest_returns201`, `createUser_duplicateEmail_throws409`
