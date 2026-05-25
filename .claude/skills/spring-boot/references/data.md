# Data Access - Spring Data JPA

> Covers relational/JPA access. For document databases see `references/mongodb.md`.
> Spring Boot 4.x ships **Hibernate 7.1** and Spring Data 2025.1; the patterns below are
> unchanged from 3.x, but verify generated DDL when upgrading (Hibernate 7 tightens some
> defaults). Use JSpecify `@Nullable` (`org.jspecify.annotations`) rather than Spring's on 4.x.
> Entities below use explicit accessors; Lombok `@Getter/@Setter` is a fine substitute — but
> never `@Data` on an entity (see the spring-boot skill's "Boilerplate" section).

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
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    // Constructors
    public User() {}

    public User(Long id, String email, String password, String username, Boolean active,
                List<Address> addresses, Set<Role> roles, LocalDateTime createdAt,
                LocalDateTime updatedAt, Long version) {
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

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

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
    @Query("SELECT new com.example.dto.UserSummary(u.id, u.username, u.email) " +
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

## Transaction Management

```java
@Service
@Transactional(readOnly = true)
public class OrderService {
    private final OrderRepository orderRepository;
    private final PaymentService paymentService;
    private final InventoryService inventoryService;
    private final NotificationService notificationService;

    public OrderService(OrderRepository orderRepository, PaymentService paymentService, InventoryService inventoryService, NotificationService notificationService) {
        this.orderRepository = orderRepository;
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
    private LocalDateTime createdAt;

    @CreatedBy
    @Column(nullable = false, updatable = false, length = 100)
    private String createdBy;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @LastModifiedBy
    @Column(nullable = false, length = 100)
    private String updatedBy;

    // Getters and setters (no Lombok)
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
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

Fetching strategies belong on the **repository**; the service composes the results. Putting
`@Query`/`@EntityGraph` on a `@Service` does nothing — they are only honored on repository
methods.

```java
public interface UserRepository extends JpaRepository<User, Long> {

    // N+1 solved with JOIN FETCH (DISTINCT avoids duplicate rows from collection joins)
    @Query("SELECT DISTINCT u FROM User u " +
           "LEFT JOIN FETCH u.addresses " +
           "LEFT JOIN FETCH u.roles " +
           "WHERE u.active = true")
    List<User> findAllActiveWithAssociations();

    // EntityGraph for declarative fetching on a derived query
    @EntityGraph(attributePaths = {"addresses", "roles"})
    List<User> findAllByActiveTrue();

    // Native query for complex aggregation
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

@Service
@Transactional(readOnly = true)
public class UserQueryService {
    private final UserRepository userRepository;

    public UserQueryService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // Pagination keeps result sets bounded
    public Page<User> findAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable);
    }
}
```

Batch fetching is configured on the entity association itself, not the repository:

```java
@OneToMany(mappedBy = "user")
@BatchSize(size = 25)  // Hibernate loads pending associations 25 at a time
private List<Order> orders;
```

## Database Migrations (Flyway)

```sql
-- V1__create_users_table.sql
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    username VARCHAR(50) NOT NULL UNIQUE,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_active ON users(active);

-- V2__create_addresses_table.sql
CREATE TABLE addresses (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    street VARCHAR(200) NOT NULL,
    city VARCHAR(100) NOT NULL,
    country VARCHAR(2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_addresses_user_id ON addresses(user_id);
```

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
