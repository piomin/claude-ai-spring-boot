# Data Access - Spring Data JPA

## JPA Entity Pattern

```java
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_email", columnList = "email", unique = true),
    @Index(name = "idx_username", columnList = "username")
})
@EntityListeners(AuditingEntityListener.class)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false, length = 100)
    private String password;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false)
    private Boolean active = true;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Address> addresses = new ArrayList<>();

    @ManyToMany
    @JoinTable(
        name = "user_roles",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    // Constructors
    public User() {}

    public User(Long id, String email, String password, String username, Boolean active,
                List<Address> addresses, Set<Role> roles, Instant createdAt,
                Instant updatedAt, Long version) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.username = username;
        this.active = active != null ? active : true;
        this.addresses = addresses != null ? addresses : new ArrayList<>();
        this.roles = roles != null ? roles : new HashSet<>();
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    public List<Address> getAddresses() { return addresses; }
    public void setAddresses(List<Address> addresses) { this.addresses = addresses; }

    public Set<Role> getRoles() { return roles; }
    public void setRoles(Set<Role> roles) { this.roles = roles; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    // Helper methods for bidirectional relationships
    public void addAddress(Address address) {
        addresses.add(address);
        address.setUser(this);
    }

    public void removeAddress(Address address) {
        addresses.remove(address);
        address.setUser(null);
    }
}
```

## Spring Data JPA Repository

```java
@Repository
public interface UserRepository extends JpaRepository<User, Long>,
                                       JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.roles WHERE u.email = :email")
    Optional<User> findByEmailWithRoles(@Param("email") String email);

    @Query("SELECT u FROM User u WHERE u.active = true AND u.createdAt >= :since")
    List<User> findActiveUsersSince(@Param("since") LocalDateTime since);

    @Modifying
    @Query("UPDATE User u SET u.active = false WHERE u.lastLoginAt < :threshold")
    int deactivateInactiveUsers(@Param("threshold") LocalDateTime threshold);

    // Projection for read-only DTOs
    @Query("SELECT new pl.piomin.services.dto.UserSummary(u.id, u.username, u.email) " +
           "FROM User u WHERE u.active = true")
    List<UserSummary> findAllActiveSummaries();
}
```

## Repository with Specifications

```java
public class UserSpecifications {

    public static Specification<User> hasEmail(String email) {
        return (root, query, cb) ->
            email == null ? null : cb.equal(root.get("email"), email);
    }

    public static Specification<User> isActive() {
        return (root, query, cb) -> cb.isTrue(root.get("active"));
    }

    public static Specification<User> createdAfter(LocalDateTime date) {
        return (root, query, cb) ->
            date == null ? null : cb.greaterThanOrEqualTo(root.get("createdAt"), date);
    }

    public static Specification<User> hasRole(String roleName) {
        return (root, query, cb) -> {
            Join<User, Role> roles = root.join("roles", JoinType.INNER);
            return cb.equal(roles.get("name"), roleName);
        };
    }

    /** Case-insensitive "contains" on any of the attributes; LIKE wildcards in {@code q} match literally. */
    public static Specification<User> search(String q, String... attributes) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String like = "%" + q.toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return (root, query, cb) -> cb.or(Arrays.stream(attributes)
                .map(attribute -> cb.like(cb.lower(root.get(attribute)), like, '\\'))
                .toArray(Predicate[]::new));
    }
}

// Usage in service
@Service
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    
    public Page<User> searchUsers(UserSearchCriteria criteria, Pageable pageable) {
        Specification<User> spec = Specification
            .where(UserSpecifications.hasEmail(criteria.email()))
            .and(UserSpecifications.isActive())
            .and(UserSpecifications.createdAfter(criteria.createdAfter()));

        return userRepository.findAll(spec, pageable);
    }
}
```

- Escape `%`, `_` and `\` in user input passed to `LIKE`, otherwise `q=%` matches everything and `q=a_c` matches `abc`.
- Bind query filters to a record (`@ParameterObject @Valid UserFilter filter` in the controller) that builds the `Specification`; filters combine with AND, and an invalid value returns 400 with a field error named after the parameter.
- A `Specification` query that maps a relation into the response loads it with `@EntityGraph` on `findAll(Specification, Pageable)` (see `jpa-patterns`).

## Transaction Management

```java
@Service
@Transactional(readOnly = true)
public class OrderService {
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final OrderEventRepository orderEventRepository;
    private final PaymentService paymentService;
    private final InventoryService inventoryService;
    private final NotificationService notificationService;

    public OrderService(OrderRepository orderRepository, OrderEventRepository orderEventRepository,
                        PaymentService paymentService, InventoryService inventoryService,
                        NotificationService notificationService) {
        this.orderRepository = orderRepository;
        this.orderEventRepository = orderEventRepository;
        this.paymentService = paymentService;
        this.inventoryService = inventoryService;
        this.notificationService = notificationService;
    }
    
    @Transactional
    public Order createOrder(OrderCreateRequest request) {
        // All operations in single transaction
        Order order = new Order();
        order.setCustomerId(request.customerId());
        order.setStatus(OrderStatus.PENDING);

        request.items().forEach(item -> {
            inventoryService.reserveStock(item.productId(), item.quantity());
            order.addItem(item);
        });

        order = orderRepository.save(order);

        try {
            paymentService.processPayment(order);
            order.setStatus(OrderStatus.PAID);
        } catch (PaymentException e) {
            order.setStatus(OrderStatus.PAYMENT_FAILED);
            throw e; // Transaction will rollback
        }

        return orderRepository.save(order);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logOrderEvent(Long orderId, String event) {
        // Separate transaction - will commit even if parent rolls back
        OrderEvent orderEvent = new OrderEvent(orderId, event);
        orderEventRepository.save(orderEvent);
    }

    @Transactional(noRollbackFor = NotificationException.class)
    public void completeOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        order.setStatus(OrderStatus.COMPLETED);
        orderRepository.save(order);

        // Won't rollback transaction if notification fails
        try {
            notificationService.sendCompletionEmail(order);
        } catch (NotificationException e) {
            log.error("Failed to send notification for order {}", orderId, e);
        }
    }
}
```

## Auditing Configuration

```java
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> {
            Authentication authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();

            if (authentication == null || !authentication.isAuthenticated()) {
                return Optional.of("system");
            }

            return Optional.of(authentication.getName());
        };
    }
}

@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableEntity {

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(nullable = false, updatable = false, length = 100)
    private String createdBy;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    @LastModifiedBy
    @Column(nullable = false, length = 100)
    private String updatedBy;

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
```

## Projections

```java
// Interface-based projection
public interface UserSummary {
    Long getId();
    String getUsername();
    String getEmail();

    @Value("#{target.firstName + ' ' + target.lastName}")
    String getFullName();
}

// Class-based projection (DTO)
public record UserSummaryDto(
    Long id,
    String username,
    String email
) {}

// Usage
public interface UserRepository extends JpaRepository<User, Long> {
    List<UserSummary> findAllBy();

    <T> List<T> findAllBy(Class<T> type);
}

// Service usage
List<UserSummary> summaries = userRepository.findAllBy();
List<UserSummaryDto> dtos = userRepository.findAllBy(UserSummaryDto.class);
```

## Query Optimization

```java
// Entity-level optimization: batch fetching on collections
@Entity
public class User {
    // ...
    @BatchSize(size = 25)
    @OneToMany(mappedBy = "user")
    private List<Order> orders;
}

// Repository-level optimization: JOIN FETCH, EntityGraph, native queries
public interface UserRepository extends JpaRepository<User, Long> {

    @Query("SELECT DISTINCT u FROM User u " +
           "LEFT JOIN FETCH u.addresses " +
           "LEFT JOIN FETCH u.roles " +
           "WHERE u.active = true")
    List<User> findAllActiveWithAssociations();

    @EntityGraph(attributePaths = {"addresses", "roles"})
    List<User> findAllByActiveTrue();

    @Query(value = """
        SELECT u.* FROM users u
        INNER JOIN orders o ON u.id = o.user_id
        WHERE o.created_at >= :since
        GROUP BY u.id
        HAVING COUNT(o.id) >= :minOrders
        """, nativeQuery = true)
    List<User> findFrequentBuyers(@Param("since") LocalDateTime since,
                                  @Param("minOrders") int minOrders);
}

// Service-level: use repository with pagination
@Service
@Transactional(readOnly = true)
public class UserQueryService {
    private final UserRepository userRepository;

    public UserQueryService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Page<User> findAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable);
    }
}
```

## Database Migrations (Flyway)

Use Flyway only when the application persists data to a relational database that Flyway supports (PostgreSQL, MySQL, MariaDB, Oracle, SQL Server, DB2, H2, ...). Skip it entirely - no dependency, no `db/migration`, no `spring.flyway.*` - when the API has no persistence, uses only NoSQL stores (MongoDB, Redis, Cassandra, Elasticsearch), or the project already uses Liquibase. Rules are in the `conventions` skill.

### Dependencies

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-flyway</artifactId>
</dependency>
<!-- Database-specific Flyway module (see table below) -->
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
</dependency>
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
```

Versions come from the Spring Boot BOM.

| Database | Flyway module |
|----------|---------------|
| PostgreSQL | `flyway-database-postgresql` |
| MySQL / MariaDB | `flyway-mysql` |
| SQL Server | `flyway-sqlserver` |
| Oracle | `flyway-database-oracle` |
| H2 | none (included in core) |

### Configuration

```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/orders}
    username: ${SPRING_DATASOURCE_USERNAME:orders}
    password: ${SPRING_DATASOURCE_PASSWORD:orders}
  jpa:
    hibernate:
      ddl-auto: validate   # Flyway owns the schema; Hibernate only validates it
    open-in-view: false

management:
  endpoint:
    health:
      probes:
        enabled: true
      group:
        readiness:
          include: readinessState,db
```

Flyway runs on startup and reads `classpath:db/migration` by default, so no `spring.flyway.*` keys are needed. Any mismatch between entities and migrations fails startup.

### Migration Files

- Location: `src/main/resources/db/migration`
- Naming: `V<n>__<description>.sql` (e.g. `V1__create_users.sql`, `V2__add_user_status.sql`)
- Never edit an applied migration; every change is one new file
- Every entity change ships with its migration in the same commit
- Name constraints and indexes explicitly (`pk_`, `uk_`, `ck_`, `fk_`, `ix_`) and index every foreign key column
- A new `NOT NULL` column gets a `DEFAULT` or a backfill, so existing rows keep working
- Column `DEFAULT`s match entity defaults; validation rules (`@Size`, enums) get a matching `CHECK`

```sql
-- V1__create_users.sql
CREATE TABLE users (
    id         BIGINT GENERATED BY DEFAULT AS IDENTITY,
    email      VARCHAR(100) NOT NULL,
    username   VARCHAR(50)  NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version    BIGINT       NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_username UNIQUE (username)
);

-- V2__create_addresses.sql
CREATE TABLE addresses (
    id         BIGINT GENERATED BY DEFAULT AS IDENTITY,
    user_id    BIGINT       NOT NULL,
    street     VARCHAR(200) NOT NULL,
    city       VARCHAR(100) NOT NULL,
    country    VARCHAR(2)   NOT NULL,
    CONSTRAINT pk_addresses PRIMARY KEY (id),
    CONSTRAINT fk_addresses_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_addresses_country CHECK (country ~ '^[A-Z]{2}$')
);

CREATE INDEX ix_addresses_user_id ON addresses (user_id);

-- V3__add_user_status.sql
ALTER TABLE users
    ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    ADD CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'SUSPENDED'));
```

### Existing Database

For a database created before Flyway, export the current schema into `V1__initial_schema.sql`, or set `spring.flyway.baseline-on-migrate: true` (with `baseline-version`) so Flyway marks the existing schema as baseline and applies only newer migrations.

### Demo Data Seed

When a fresh deploy should show records (a UI demo, UI performance tests), seed them with a Flyway **callback**, not a migration. A migration runs once and stays in the schema history. A callback runs after every migrate and can check whether the data is still needed.

```yaml
spring:
  flyway:
    # db/demo seeds demo data into an empty database; switch it off with
    # SPRING_FLYWAY_LOCATIONS=classpath:db/migration
    locations: classpath:db/migration,classpath:db/demo
```

```sql
-- src/main/resources/db/demo/afterMigrate__seed_demo_data.sql
-- Runs after every migrate in one transaction; inserts only when the tables are empty, so it never touches real data.
SELECT setseed(0.42);                     -- the same data on every run

CREATE TEMP TABLE seed_run ON COMMIT DROP AS
SELECT NOT EXISTS (SELECT 1 FROM users) AS empty;

INSERT INTO users (email, username, active, created_at, updated_at, version)
SELECT 'user' || i || '@seed.example.com', 'user' || i, random() < 0.9, now(), now(), 0
FROM generate_series(1, 250) AS i
WHERE (SELECT empty FROM seed_run);
```

- Mark the seed rows (e.g. the e-mail domain `seed.example.com`) and document the cleanup `DELETE`s in the file header.
- Include edge cases the UI must handle: long names, non-ASCII characters, apostrophes, optional fields left empty.
- Update the seed in the same commit as any schema change.
- A dedicated integration test keeps it working. It seeds an empty database, a second `flyway.migrate()` changes nothing, and partly filled tables are not seeded again. The other integration tests turn it off with `@SpringBootTest(properties = "spring.flyway.locations=classpath:db/migration")`, so their assertions don't depend on demo rows.

### Testing

Run integration tests against the production engine with Testcontainers and `@ServiceConnection`, so Flyway applies the real migrations and Hibernate validates them. Assert that all migrations were applied - see `testing.md`.

## Quick Reference

| Annotation | Purpose |
|------------|---------|
| `@Entity` | Marks class as JPA entity |
| `@Table` | Specifies table details and indexes |
| `@Id` | Marks primary key field |
| `@GeneratedValue` | Auto-generated primary key strategy |
| `@Column` | Column constraints and mapping |
| `@OneToMany/@ManyToOne` | One-to-many/many-to-one relationships |
| `@ManyToMany` | Many-to-many relationships |
| `@JoinColumn/@JoinTable` | Join column/table configuration |
| `@Transactional` | Declares transaction boundaries |
| `@Query` | Custom JPQL/native queries |
| `@Modifying` | Marks query as UPDATE/DELETE |
| `@EntityGraph` | Defines fetch graph for associations |
| `@Version` | Optimistic locking version field |
