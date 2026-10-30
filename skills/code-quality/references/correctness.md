# Correctness

Generic Java checks. They are not project rules, so they live only here.

## Null safety
- Chained calls without null checks (`user.getName().toUpperCase()`)
- `Optional.get()` without `isPresent()`; prefer `orElseThrow(...)`
- Returning `null` instead of `Optional` or an empty collection
- Missing `@Nullable` / `@NonNull` on public APIs

## Exception handling
- Empty catch blocks
- Catching `Exception` / `Throwable` (too broad)
- Swallowed or lost stack trace: `throw new RuntimeException(e.getMessage())`
- New exception created without the original cause
- Exception neither logged nor rethrown

```java
// ❌ Lost context
catch (IOException e) {
    throw new RuntimeException(e.getMessage());
}

// ✅ Cause kept
catch (IOException e) {
    throw new ProcessingException("File processing failed: " + filename, e);
}
```

## Resource management
- Manual `close()` instead of try-with-resources (skipped when the read throws)

## Transactions
- Multi-step writes without `@Transactional`; missing `readOnly = true` on reads (rule: `conventions`, Code Quality Standards)
- Self-invocation: `@Transactional` or `@Async` called via `this` (bypasses the proxy)

## Lost updates and lazy loading
Rules and examples are in `jpa-patterns` (Optimistic Locking, Transaction Management, Lazy Loading) and `spring-boot` → `references/ui-contract.md` §4. Flag:
- `PUT` without a `version` compared to the entity's; `try/catch (OptimisticLockingFailureException)` around `save()` inside `@Transactional` (thrown at commit)
- Relation writes with `open-in-view: false` outside `@Transactional`, or the response mapped after the transaction
