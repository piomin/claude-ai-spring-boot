# Correctness

Always applies to Java code in the diff.

## Null safety
- Chained calls without null checks (`user.getName().toUpperCase()`)
- `Optional.get()` without `isPresent()`; prefer `orElseThrow(...)`
- Returning `null` instead of `Optional` or an empty collection
- Missing `@Nullable` / `@NonNull` on public APIs

```java
// ❌ NPE risk
String name = user.getName().toUpperCase();

// ✅ Early return
if (user.getName() == null) return "";
return user.getName().toUpperCase();
```

## Exception handling
- Empty catch blocks
- Catching `Exception` / `Throwable` (too broad)
- Swallowed or lost stack trace: `throw new RuntimeException(e.getMessage())`
- New exception created without the original cause
- Exception not logged and not rethrown

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
- Manual `close()` instead of try-with-resources (not executed when the read throws)

## Transaction boundaries
- Multi-step writes without `@Transactional`
- Missing `readOnly = true` for reads
- Self-invocation: `@Transactional` or `@Async` called via `this` (bypasses the proxy)

```java
// ❌ Two separate transactions
public void createUser(UserCreateRequest request) {
    User user = userRepository.save(new User());
    roleRepository.save(new Role(user));
}
```

## Concurrency and consistency (JPA)
- Lost updates: `PUT` without a required `version` compared to the entity's, or `try/catch (OptimisticLockingFailureException)` around `save()` inside `@Transactional` (it is thrown at commit)
- Relation writes with `open-in-view: false` outside `@Transactional`, or the response mapped after the transaction (`LazyInitializationException`, 500 after a successful write)

Deep dive: `jpa-patterns` skill.
