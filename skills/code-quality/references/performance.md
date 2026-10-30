# Performance

Applies when the diff touches repositories, queries, loops over entities or collections returned from the database.

- **N+1 queries**: lazy collections accessed in loops without `JOIN FETCH` or `@EntityGraph`; repository call inside a loop
- **Unbounded queries**: `findAll()` without pagination on large tables
- **`LIKE` from user input** without escaping `%`, `_` and `\`
- **Missing indexes**: queries filtering on columns without `@Index` / an index in the migration

```java
// ❌ N queries
for (User user : userRepository.findAll()) {
    orderRepository.findByUserId(user.getId());
}

// ✅ One query
@Query("SELECT u FROM User u LEFT JOIN FETCH u.orders")
List<User> findAllWithOrders();
```

Deep dive: `jpa-patterns` skill.
