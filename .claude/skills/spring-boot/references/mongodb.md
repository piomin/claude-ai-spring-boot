# Data Access - Spring Data MongoDB

> Spring Boot 4.x ships Spring Data MongoDB 5.x (Spring Data 2025.1). Patterns below
> apply to Spring Boot 3.x as well; version-specific notes are called out inline.

## Dependencies

```xml
<!-- Imperative (blocking) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-mongodb</artifactId>
</dependency>

<!-- Reactive (non-blocking) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-mongodb-reactive</artifactId>
</dependency>
```

## Document Mapping

```java
@Document(collection = "products")
@CompoundIndex(name = "category_price_idx", def = "{'category': 1, 'price': -1}")
public class Product {

    @Id
    private String id;                 // String or ObjectId; never an auto-increment Long

    @Indexed(unique = true)
    private String sku;

    @Field("product_name")             // maps to a different field name in the document
    private String name;

    @Indexed
    private String category;

    private BigDecimal price;

    // Prefer embedding small, owned, bounded sub-documents
    private List<Variant> variants = new ArrayList<>();

    // Reference large/shared aggregates instead of embedding them
    @DocumentReference(lazy = true)
    private Supplier supplier;

    @Version
    private Long version;              // optimistic locking

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    public Product() {}

    // Getters and setters (or Lombok @Getter/@Setter @NoArgsConstructor — never @Data on a
    // document; see the spring-boot skill's "Boilerplate" section)
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public List<Variant> getVariants() { return variants; }
    public void setVariants(List<Variant> variants) { this.variants = variants; }
    public Supplier getSupplier() { return supplier; }
    public void setSupplier(Supplier supplier) { this.supplier = supplier; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
```

`Variant` is a plain class (no `@Document`, no `@Id`) — it is stored inline as part of the
parent document.

### `@DocumentReference` vs `@DBRef`

Prefer `@DocumentReference` (Spring Data MongoDB 3.3+). It stores only the referenced id, is
lazy-loadable, and unlike the legacy `@DBRef` it does not pin you to driver-level `DBRef`
resolution. Reach for `@DBRef` only when interoperating with existing `DBRef` data.

## Repositories

```java
public interface ProductRepository extends MongoRepository<Product, String> {

    Optional<Product> findBySku(String sku);

    boolean existsBySku(String sku);

    // Derived query with paging
    Page<Product> findByCategory(String category, Pageable pageable);

    // JSON query; field projection keeps payloads small
    @Query(value = "{ 'price': { $gte: ?0, $lte: ?1 } }", fields = "{ 'name': 1, 'price': 1 }")
    List<Product> findInPriceRange(BigDecimal min, BigDecimal max);

    // Declarative aggregation pipeline
    @Aggregation(pipeline = {
        "{ $match: { 'category': ?0 } }",
        "{ $group: { _id: '$category', avgPrice: { $avg: '$price' }, count: { $sum: 1 } } }"
    })
    CategoryStats averagePriceByCategory(String category);
}
```

**Reactive variant** — extend `ReactiveMongoRepository` and return `Mono`/`Flux`. Reactive
repositories do not support `Page` (no count round-trip); use `Flux` with `Limit`/`Sort`.

```java
public interface ProductReactiveRepository extends ReactiveMongoRepository<Product, String> {
    Flux<Product> findByCategory(String category, Sort sort, Limit limit);
    Mono<Product> findBySku(String sku);
}
```

## Projections

```java
// Interface projection — Mongo only ships the listed fields over the wire
public interface ProductSummary {
    String getId();
    String getName();
    BigDecimal getPrice();
}

public interface ProductRepository extends MongoRepository<Product, String> {
    List<ProductSummary> findByCategory(String category);
}

// Record (DTO) projection
public record ProductView(String id, String name, BigDecimal price) {}
```

## MongoTemplate for Dynamic Queries

Drop to `MongoTemplate` when criteria are built at runtime or a pipeline outgrows
`@Aggregation`.

```java
@Service
public class ProductSearchService {
    private final MongoTemplate mongoTemplate;

    public ProductSearchService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public List<Product> search(ProductFilter filter, Pageable pageable) {
        Query query = new Query().with(pageable);
        if (filter.category() != null) {
            query.addCriteria(Criteria.where("category").is(filter.category()));
        }
        if (filter.maxPrice() != null) {
            query.addCriteria(Criteria.where("price").lte(filter.maxPrice()));
        }
        return mongoTemplate.find(query, Product.class);
    }

    // Atomic partial update — no read-modify-write race
    public void adjustStock(String id, int delta) {
        mongoTemplate.updateFirst(
            Query.query(Criteria.where("id").is(id)),
            new Update().inc("stock", delta),
            Product.class);
    }
}
```

## Auditing

```java
@Configuration
@EnableMongoAuditing
public class MongoAuditingConfig {
    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
            .filter(Authentication::isAuthenticated)
            .map(Authentication::getName)
            .or(() -> Optional.of("system"));
    }
}
```

`@CreatedDate`, `@LastModifiedDate`, `@CreatedBy`, `@LastModifiedBy`, and `@Version` are then
populated automatically on save.

## Transactions

MongoDB multi-document transactions require a **replica set** (a single-node replica set works
for local dev/Testcontainers — a standalone `mongod` does not). Register a transaction manager,
then `@Transactional` works as usual.

```java
@Configuration
public class MongoTransactionConfig {
    @Bean
    public MongoTransactionManager transactionManager(MongoDatabaseFactory dbFactory) {
        return new MongoTransactionManager(dbFactory);
    }
}
```

Single-document writes are always atomic and need no transaction. Model around the document
boundary first — reach for multi-document transactions only when an operation genuinely spans
collections.

## Configuration

```yaml
spring:
  data:
    mongodb:
      uri: ${MONGODB_URI:mongodb://localhost:27017/store}
      # auto-index-creation is OFF by default in production-grade setups;
      # create indexes via migrations rather than relying on annotations.
      auto-index-creation: false
```

## Testing with Testcontainers

```java
@DataMongoTest
@Testcontainers
class ProductRepositoryTest {

    @Container
    @ServiceConnection                 // Spring Boot 3.1+/4.x wires the URI automatically
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7");

    @Autowired ProductRepository repository;

    @Test
    void findsBySku() {
        Product p = new Product();
        p.setSku("ABC-1");
        p.setName("Widget");
        repository.save(p);

        assertThat(repository.findBySku("ABC-1")).isPresent();
    }
}
```

`@ServiceConnection` replaces manual `@DynamicPropertySource` wiring for the connection URI.

## Quick Reference

| Annotation | Purpose |
|------------|---------|
| `@Document` | Marks a class as a MongoDB document; sets collection name |
| `@Id` | Document identifier (`String` or `ObjectId`) |
| `@Field` | Maps a property to a different document field name |
| `@Indexed` | Single-field index (`unique = true` for uniqueness) |
| `@CompoundIndex` | Multi-field index declared on the document |
| `@TextIndexed` | Full-text search index |
| `@DocumentReference` | Lazy, id-based reference to another document (preferred) |
| `@DBRef` | Legacy driver-level reference (avoid for new code) |
| `@Version` | Optimistic locking field |
| `@CreatedDate`/`@LastModifiedDate` | Auditing timestamps (needs `@EnableMongoAuditing`) |
| `@Query` | JSON query with optional `fields` projection |
| `@Aggregation` | Declarative aggregation pipeline on a repository method |

See `mongodb-patterns` skill for schema-design and performance pitfalls (embedding vs
referencing, indexing, aggregation cost, pagination).
