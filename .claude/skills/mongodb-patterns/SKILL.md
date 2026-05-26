---
name: mongodb-patterns
description: Spring Data MongoDB patterns and common pitfalls (schema design, embedding vs referencing, indexing, aggregation cost, pagination, transactions). Use when user has MongoDB performance issues, designs document schemas, or asks about @Document relationships and query optimization.
---

# MongoDB Patterns Skill

Best practices and common pitfalls for Spring Data MongoDB in Spring applications.

## When to Use
- Designing document schemas (embed vs reference decisions)
- "Slow query" / "MongoDB is doing a collection scan"
- Documents approaching the 16 MB limit / unbounded arrays
- Many round-trips when loading references (the Mongo "N+1")
- Aggregation pipeline performance
- Multi-document transaction questions

---

## Quick Reference: Common Problems

| Problem | Symptom | Solution |
|---------|---------|----------|
| Unbounded embedded arrays | Documents grow toward 16 MB limit | Reference instead of embed; bucket pattern |
| Missing index | `COLLSCAN` in `explain()`, slow reads | `@Indexed` / `@CompoundIndex`, follow ESR rule |
| Reference N+1 | Many queries resolving `@DocumentReference` | Embed, or resolve with one `$lookup` aggregation |
| Loading whole collection | OOM / huge payloads | `Pageable`, projections, streaming |
| Over-fetching fields | Large network payloads | Field projection (`fields`, interface projection) |
| Read-modify-write races | Lost updates | Atomic operators (`$inc`, `$set`) via `Update` |
| Cross-collection writes | Inconsistent data | Model to document boundary; transactions need a replica set |

---

## Schema Design: Embed vs Reference

> The single most important MongoDB decision. Get it right and most "performance"
> problems never appear.

**Embed when** the data is owned by the parent, bounded in size, and read together:

```java
// ✅ GOOD: order lines are owned by, and always read with, the order
@Document("orders")
public class Order {
    @Id private String id;
    private String customerId;
    private List<OrderLine> lines = new ArrayList<>();  // embedded, bounded
}
```

**Reference when** the data is shared, large, or grows without bound:

```java
// ❌ BAD: embedding every order inside a customer — unbounded growth toward 16 MB
@Document("customers")
public class Customer {
    @Id private String id;
    private List<Order> orders = new ArrayList<>();  // never embed unbounded history
}

// ✅ GOOD: orders are their own collection, linked by id
@Document("orders")
public class Order {
    @Id private String id;
    @Indexed private String customerId;   // query orders by customer
}
```

Rules of thumb:
- **One-to-few, owned, read together** → embed.
- **One-to-many / many-to-many, shared, or unbounded** → reference.
- Optimize the schema for the queries you actually run, not for normalization.

---

## The Reference "N+1"

```java
// ❌ BAD: resolving references in a loop = 1 + N queries
List<Order> orders = orderRepository.findAll();
for (Order order : orders) {
    order.getCustomer().getName();   // @DocumentReference lazy load → one query each
}
```

### Solution 1: Embed the few fields you read

If you only ever need the customer name, store a denormalized copy on the order and skip the
reference entirely.

### Solution 2: Resolve with a single `$lookup`

```java
public interface OrderRepository extends MongoRepository<Order, String> {
    @Aggregation(pipeline = {
        "{ $match: { 'status': ?0 } }",
        "{ $lookup: { from: 'customers', localField: 'customerId', foreignField: '_id', as: 'customer' } }",
        "{ $unwind: '$customer' }"
    })
    List<OrderWithCustomer> findWithCustomer(String status);
}
```

`$lookup` is a left outer join — keep the joined collection indexed on `foreignField` and the
`$match` early in the pipeline so it runs against an index before the join.

---

## Indexing

```java
@Document("products")
@CompoundIndex(name = "category_price_idx", def = "{'category': 1, 'price': -1}")
public class Product {
    @Indexed(unique = true) private String sku;
    @Indexed private String category;
}
```

### The ESR rule for compound indexes

Order compound-index fields **Equality → Sort → Range**. A query filtering on `category`
(equality), sorting by `createdAt`, and ranging on `price` wants `{category:1, createdAt:1, price:1}`.

### Verify with `explain()`

```java
Document plan = mongoTemplate.getCollection("products")
    .find(new org.bson.Document("category", "books"))
    .explain();
// winningPlan.stage should be IXSCAN, not COLLSCAN
```

> In production, create indexes through migrations, not `auto-index-creation`. Annotation-driven
> index creation is convenient in dev but risks surprise foreground builds on large collections.

---

## Pagination & Projections

```java
// ✅ Page through results instead of findAll()
Page<Product> page = repository.findByCategory("books", PageRequest.of(0, 20));

// ✅ Project only needed fields — less I/O and network
public interface ProductSummary {
    String getId();
    String getName();
    BigDecimal getPrice();
}
List<ProductSummary> summaries = repository.findByCategory("books");
```

Reactive repositories have no `Page` (a count round-trip breaks the stream) — use `Flux` with
`Limit` and `Sort`, or a separate count query when a total is truly required.

---

## Atomic Updates

```java
// ❌ BAD: read-modify-write loses concurrent updates
Product p = repository.findById(id).orElseThrow();
p.setStock(p.getStock() - 1);
repository.save(p);

// ✅ GOOD: single atomic operator on the server
mongoTemplate.updateFirst(
    Query.query(Criteria.where("id").is(id)),
    new Update().inc("stock", -1),
    Product.class);
```

Use `@Version` for optimistic locking when you must read-then-write a whole document.

---

## Transactions

- Single-document writes are **always atomic** — no transaction needed. Model around the
  document boundary first.
- Multi-document/multi-collection transactions require a **replica set** (a single-node replica
  set is fine for local dev and Testcontainers; a standalone `mongod` is not) plus a
  `MongoTransactionManager` bean. Only then does `@Transactional` apply.
- Keep transactions short; long-running transactions hold locks and hurt throughput.

---

## Detecting Slow Queries

```yaml
logging:
  level:
    org.springframework.data.mongodb.core.MongoTemplate: DEBUG  # logs issued commands
```

Enable the MongoDB profiler (`db.setProfilingLevel(1, { slowms: 100 })`) to capture slow
operations server-side, then add the missing index revealed by `explain()`.
