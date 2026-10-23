---
name: jpa-patterns
description: JPA/Hibernate patterns and common pitfalls (N+1, lazy loading, transactions, queries). Use when user has JPA performance issues, LazyInitializationException, or asks about entity relationships and fetching strategies.
---

# JPA Patterns Skill

Best practices and common pitfalls for JPA/Hibernate in Spring applications.

## When to Use
- User mentions "N+1 problem" / "too many queries"
- LazyInitializationException errors
- Questions about fetch strategies (EAGER vs LAZY)
- Transaction management issues
- Entity relationship design
- Query optimization

---

## Quick Reference: Common Problems

| Problem | Symptom | Solution |
|---------|---------|----------|
| N+1 queries | Many SELECT statements | JOIN FETCH, @EntityGraph |
| LazyInitializationException | Error outside transaction | DTO projection, JOIN FETCH (not Open Session in View) |
| Slow queries | Performance issues | Pagination, projections, indexes |
| Dirty checking overhead | Slow updates | Read-only transactions, DTOs |
| Lost updates | Concurrent modifications | Optimistic locking (@Version) |
| Entity lost from `HashSet` after persist | `equals`/`hashCode` on generated id | Natural key, or id-based with constant `hashCode` |
| Stale entities after bulk update | Old values read in same transaction | `@Modifying(clearAutomatically = true)` |

---

## N+1 Problem

> The #1 JPA performance killer

### The Problem

```java
// ❌ BAD: N+1 queries
@Entity
public class Author {
    @Id private Long id;
    private String name;

    @OneToMany(mappedBy = "author", fetch = FetchType.LAZY)
    private List<Book> books;
}

// This innocent code...
List<Author> authors = authorRepository.findAll();  // 1 query
for (Author author : authors) {
    System.out.println(author.getBooks().size());   // N queries!
}
// Result: 1 + N queries (if 100 authors = 101 queries)
```

### Solution 1: JOIN FETCH (JPQL)

```java
// ✅ GOOD: Single query with JOIN FETCH
public interface AuthorRepository extends JpaRepository<Author, Long> {

    @Query("SELECT a FROM Author a JOIN FETCH a.books")
    List<Author> findAllWithBooks();
}

// Usage - single query
List<Author> authors = authorRepository.findAllWithBooks();
```

### Solution 2: @EntityGraph

```java
// ✅ GOOD: EntityGraph for declarative fetching
public interface AuthorRepository extends JpaRepository<Author, Long> {

    @EntityGraph(attributePaths = {"books"})
    List<Author> findAll();

    // Or with named graph
    @EntityGraph(value = "Author.withBooks")
    List<Author> findAllWithBooks();
}

// Define named graph on entity
@Entity
@NamedEntityGraph(
    name = "Author.withBooks",
    attributeNodes = @NamedAttributeNode("books")
)
public class Author {
    // ...
}
```

### Solution 3: Batch Fetching

```java
// ✅ GOOD: Batch fetching (Hibernate-specific)
@Entity
public class Author {

    @OneToMany(mappedBy = "author")
    @BatchSize(size = 25)  // Fetch 25 at a time
    private List<Book> books;
}

// Or globally in application.properties
spring.jpa.properties.hibernate.default_batch_fetch_size=25
```

### Detecting N+1

```yaml
# Enable SQL logging to detect N+1
spring:
  jpa:
    show-sql: true
    properties:
      hibernate:
        format_sql: true

logging:
  level:
    org.hibernate.SQL: DEBUG
    org.hibernate.orm.jdbc.bind: TRACE
```

---

## Lazy Loading

### FetchType Basics

```java
@Entity
public class Order {

    // LAZY: Load only when accessed (default for collections)
    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY)
    private List<OrderItem> items;

    // EAGER: Always load immediately (default for @ManyToOne, @OneToOne)
    @ManyToOne(fetch = FetchType.EAGER)  // ⚠️ Usually bad
    private Customer customer;
}
```

### Best Practice: Default to LAZY

```java
// ✅ GOOD: Always use LAZY, fetch when needed
@Entity
public class Order {

    @ManyToOne(fetch = FetchType.LAZY)  // Override EAGER default
    private Customer customer;

    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY)
    private List<OrderItem> items;
}
```

### LazyInitializationException

```java
// ❌ BAD: Accessing lazy field outside transaction
@Service
public class OrderService {

    public Order getOrder(Long id) {
        return orderRepository.findById(id).orElseThrow();
    }
}

// In controller (no transaction)
Order order = orderService.getOrder(1L);
order.getItems().size();  // 💥 LazyInitializationException!
```

### Solutions for LazyInitializationException

**Solution 1: JOIN FETCH in query**
```java
// ✅ Fetch needed associations in query
@Query("SELECT o FROM Order o JOIN FETCH o.items WHERE o.id = :id")
Optional<Order> findByIdWithItems(@Param("id") Long id);
```

**Solution 2: @Transactional on service method**
```java
// ✅ Keep transaction open while accessing
@Service
public class OrderService {

    @Transactional(readOnly = true)
    public OrderDTO getOrderWithItems(Long id) {
        Order order = orderRepository.findById(id).orElseThrow();
        // Access within transaction - lazy collection loads here
        int itemCount = order.getItems().size();
        return new OrderDTO(order, itemCount);
    }
}
```

**Solution 3: DTO projection (best for read-only)**
```java
// ✅ Avoid lazy loading entirely - query only what you need
public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("SELECT new pl.piomin.services.dto.OrderSummary(o.id, o.status, SIZE(o.items)) " +
           "FROM Order o WHERE o.id = :id")
    Optional<OrderSummary> findSummaryById(@Param("id") Long id);
}
```

### Disable Open Session in View

Spring Boot enables OSIV by default (and logs a warning at startup). It keeps the persistence context and a DB connection open until the response is written, so lazy loads in controllers or JSON serialization "work" while hiding N+1 queries.

```yaml
spring:
  jpa:
    open-in-view: false  # fetch what the response needs in the service layer
```

---

## Transaction Management

### Read-Only Optimization

```java
@Service
@Transactional(readOnly = true)  // Default for all methods
public class ProductService {
    private final ProductRepository repo;

    public ProductService(ProductRepository repo) {
        this.repo = repo;
    }

    public List<Product> findAll() {
        return repo.findAll();  // Read-only: skips dirty checking
    }

    @Transactional  // Override: read-write for mutations
    public Product create(ProductRequest request) {
        var product = new Product();
        product.setName(request.name());
        return repo.save(product);
    }
}
```

### Self-Invocation Pitfall

```java
// ❌ BAD: @Transactional ignored on self-invocation
@Service
public class OrderService {

    public void processOrders(List<Long> ids) {
        for (Long id : ids) {
            processOrder(id);  // Calls via 'this', not proxy - no transaction!
        }
    }

    @Transactional
    public void processOrder(Long id) {
        // This @Transactional is ignored when called from processOrders()
    }
}

// ✅ GOOD: Extract to separate bean
@Service
public class OrderService {
    private final OrderProcessor processor;

    public OrderService(OrderProcessor processor) {
        this.processor = processor;
    }

    public void processOrders(List<Long> ids) {
        for (Long id : ids) {
            processor.processOrder(id);  // Calls via proxy
        }
    }
}

@Service
public class OrderProcessor {
    @Transactional
    public void processOrder(Long id) {
        // Transaction works correctly
    }
}
```

---

## Optimistic Locking

```java
@Entity
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private BigDecimal price;

    @Version
    private Long version;  // Hibernate checks this on update

    // getters/setters
}

// Handling optimistic lock failure
@Service
public class ProductService {
    private final ProductRepository repo;

    public ProductService(ProductRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public Product updatePrice(Long id, BigDecimal newPrice) {
        Product product = repo.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Product not found"));
        product.setPrice(newPrice);
        try {
            return repo.save(product);
        } catch (OptimisticLockingFailureException e) {
            throw new ConflictException("Product was modified by another user");
        }
    }
}
```

---

## Entity Design

### equals() and hashCode()

```java
// ✅ Business key: stable before and after persist
@Entity
public class Book {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NaturalId  // org.hibernate.annotations.NaturalId
    @Column(nullable = false, unique = true, updatable = false)
    private String isbn;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof Book other && isbn != null && isbn.equals(other.isbn);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(isbn);
    }
}

// ✅ No natural key: id-based equality, constant hashCode
@Override
public boolean equals(Object o) {
    if (this == o) return true;
    return o instanceof Order other && id != null && id.equals(other.id);
}

@Override
public int hashCode() {
    return getClass().hashCode();  // unchanged when the id is assigned on persist
}
```

- Never include all fields: mutable fields change the hash, lazy associations trigger loads
- Use `instanceof` (not `getClass()`) in `equals` so Hibernate proxies compare equal

### Bidirectional Relationships

```java
@Entity
public class Author {

    @OneToMany(mappedBy = "author", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Book> books = new ArrayList<>();

    // ✅ Keep both sides in sync - the @ManyToOne side owns the FK
    public void addBook(Book book) {
        books.add(book);
        book.setAuthor(this);
    }

    public void removeBook(Book book) {
        books.remove(book);
        book.setAuthor(null);
    }
}

@Entity
public class Book {

    @ManyToOne(fetch = FetchType.LAZY)  // ✅ No cascade on the child side
    @JoinColumn(name = "author_id")
    private Author author;
}
```

```java
// ✅ @ManyToMany: Set (a List makes Hibernate delete and re-insert the whole join table)
//    and PERSIST/MERGE only - never REMOVE or ALL
@ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
@JoinTable(name = "student_course",
    joinColumns = @JoinColumn(name = "student_id"),
    inverseJoinColumns = @JoinColumn(name = "course_id"))
private Set<Course> courses = new HashSet<>();
```

If the link carries data (enrolment date, grade), map the join table as its own entity with two `@ManyToOne`.

### Common Mapping Mistakes

```java
// ❌ BAD: CascadeType.ALL on @ManyToOne - removing a book removes its shared author
@ManyToOne(cascade = CascadeType.ALL)
private Author author;

// ❌ BAD: toString() touching a lazy collection - extra query,
//    LazyInitializationException outside a transaction, StackOverflowError on bidirectional links
@Override
public String toString() {
    return "Author{id=" + id + ", books=" + books + "}";
}

// ✅ GOOD: basic columns only
@Override
public String toString() {
    return "Author{id=" + id + ", name='" + name + "'}";
}
```

---

## Modern Hibernate Features

### Jakarta Persistence

```java
// Always use jakarta.persistence.* (not javax.persistence.*)
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
```

### UUID Generation

```java
@Entity
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)  // Native UUID generation
    private UUID id;

    private String name;

    // getters/setters
}
```

### Soft Delete

```java
// Built-in soft delete support
@Entity
@SoftDelete  // Adds 'deleted' boolean column, filters automatically
public class Customer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    // getters/setters
}

// Custom column name
@Entity
@SoftDelete(columnName = "is_active", converter = NumericBooleanConverter.class)
public class Product {
    // ...
}
```

### Record as DTO in Queries

```java
// Records work natively in JPQL constructor expressions
public record ProductSummary(Long id, String name, BigDecimal price) {}

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query("SELECT new pl.piomin.services.dto.ProductSummary(p.id, p.name, p.price) " +
           "FROM Product p WHERE p.price > :minPrice")
    List<ProductSummary> findExpensiveProducts(@Param("minPrice") BigDecimal minPrice);
}
```

---

## Query Best Practices

### Pagination

```java
// ✅ Always paginate unbounded queries
public interface UserRepository extends JpaRepository<User, Long> {

    Page<User> findByActiveTrue(Pageable pageable);

    Slice<User> findByRole(String role, Pageable pageable);  // No COUNT query
}

// Usage
Page<User> page = userRepository.findByActiveTrue(
    PageRequest.of(0, 20, Sort.by("createdAt").descending())
);
```

### Spring Data Projections

```java
// Interface-based projection - fetches only declared fields
public interface UserNameOnly {
    String getFirstName();
    String getLastName();
}

public interface UserRepository extends JpaRepository<User, Long> {
    List<UserNameOnly> findAllByActiveTrue();
}
```

### Bulk Updates and Deletes

Bulk JPQL bypasses the persistence context, lifecycle callbacks, cascades and `@Version` checks.

```java
public interface OrderRepository extends JpaRepository<Order, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Order o SET o.status = :status WHERE o.createdAt < :before")
    int archiveOlderThan(@Param("status") OrderStatus status, @Param("before") Instant before);
}
```

- `flushAutomatically` writes pending changes before the query; `clearAutomatically` detaches stale entities after it
- Call from a `@Transactional` service method
- Increment the version explicitly (`SET o.version = o.version + 1`) if concurrent writers rely on optimistic locking

---

## Quick Decision Guide

| Situation | Solution |
|-----------|----------|
| N+1 in loop | JOIN FETCH or @EntityGraph |
| LazyInitializationException | JOIN FETCH in query or @Transactional on service |
| Read-only queries slow | `@Transactional(readOnly = true)` |
| Concurrent updates | `@Version` + optimistic locking |
| Entity equality | `@NaturalId`, or id-based `equals` with constant `hashCode` |
| Bidirectional association | `add*`/`remove*` helper methods; `Set` for `@ManyToMany` |
| Bulk UPDATE/DELETE | `@Modifying(clearAutomatically = true, flushAutomatically = true)` |
| Lazy loads in web layer | `spring.jpa.open-in-view=false` |
| Unbounded result set | Pagination with `PageRequest` |
| Need only few columns | DTO projection or interface projection |
| Soft delete required | `@SoftDelete` |
| UUID primary key | `@GeneratedValue(strategy = GenerationType.UUID)` |
