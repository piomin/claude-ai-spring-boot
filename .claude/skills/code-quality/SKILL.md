---
name: code-quality
description: Comprehensive code review for Java - clean code principles, API contracts, null safety, exception handling, and performance. Use when user says "review code", "refactor", "check API", or before merging changes.
---

# Code Quality Review Skill

Systematic code review combining clean code principles, API design, and Java best practices.

## When to Use
- "review this code" / "code review" / "check this PR"
- "refactor" / "clean this code" / "improve readability"
- "review API" / "check endpoints" / "REST review"
- Before merging PR or releasing API changes

## Review Strategy

1. **Quick scan** - Understand intent, identify scope
2. **Checklist pass** - Apply relevant categories below
3. **Summary** - List findings by severity (Critical → Minor → Good)

---

## Clean Code Principles

### DRY - Don't Repeat Yourself

**Violation:**
```java
// ❌ Duplicated validation logic
public void createUser(UserRequest req) {
    if (req.getEmail() == null || !req.getEmail().contains("@")) {
        throw new ValidationException("Invalid email");
    }
}

public void updateUser(UserRequest req) {
    if (req.getEmail() == null || !req.getEmail().contains("@")) {
        throw new ValidationException("Invalid email");
    }
}
```

**Fix:**
```java
// ✅ Single source of truth
public class EmailValidator {
    public void validate(String email) {
        if (email == null || !email.contains("@")) {
            throw new ValidationException("Invalid email");
        }
    }
}
```

### KISS - Keep It Simple

**Violation:**
```java
// ❌ Over-engineered
public interface UserFactory {
    User createUser();
}
public class ConcreteUserFactory implements UserFactory {
    public User createUser() { return new User(); }
}
```

**Fix:**
```java
// ✅ Simple
public User createUser() { return new User(); }
```

### YAGNI - You Aren't Gonna Need It

**Violation:**
```java
// ❌ Premature abstraction
public class ConfigurableUserServiceFactoryProvider { }
```

**Fix:**
```java
// ✅ Implement when actually needed
public class UserService { }
```

---

## API Contract Review

### HTTP Verb Semantics

| Verb | Use For | Idempotent | Safe |
|------|---------|------------|------|
| GET | Retrieve resource | Yes | Yes |
| POST | Create new resource | No | No |
| PUT | Replace entire resource | Yes | No |
| PATCH | Partial update | No* | No |
| DELETE | Remove resource | Yes | No |

**Common Mistakes:**
```java
// ❌ POST for retrieval
@PostMapping("/users/search")
public List<User> search(@RequestBody SearchCriteria criteria) { }

// ✅ GET with query params
@GetMapping("/users")
public List<User> search(@RequestParam String name) { }

// ❌ GET for state change
@GetMapping("/users/{id}/activate")
public void activate(@PathVariable Long id) { }

// ✅ POST/PATCH for state change
@PostMapping("/users/{id}/activate")
public ResponseEntity<Void> activate(@PathVariable Long id) { }
```

### API Versioning

```java
// ✅ URL path versioning (recommended)
@RestController
@RequestMapping("/api/v1/users")
public class UserControllerV1 { }

// ❌ No versioning
@RequestMapping("/users")  // Breaking changes affect all clients
```

### Response Status Codes

| Code | Use Case | Example |
|------|----------|---------|
| 200 OK | Successful GET/PUT/PATCH | Found resource |
| 201 Created | Successful POST | New resource created |
| 204 No Content | Successful DELETE | Resource deleted |
| 400 Bad Request | Validation failure | Invalid input |
| 404 Not Found | Resource doesn't exist | User not found |
| 409 Conflict | State conflict | Duplicate email |
| 500 Server Error | Unexpected error | Database down |

### DTO vs Entity Exposure

```java
// ❌ Exposing JPA entity
@GetMapping("/{id}")
public User getUser(@PathVariable Long id) {
    return userRepository.findById(id).get();  // Exposes internals, N+1 risk
}

// ✅ Use DTO
@GetMapping("/{id}")
public UserResponse getUser(@PathVariable Long id) {
    return userService.findById(id);  // Returns DTO
}
```

---

## Java Code Review Checklist

### Null Safety

**Check for:**
```java
// ❌ NPE risk
String name = user.getName().toUpperCase();

// ✅ Safe with Optional
String name = Optional.ofNullable(user.getName())
    .map(String::toUpperCase)
    .orElse("");

// ✅ Safe with early return
if (user.getName() == null){
        return "";
        }
return user.getName().toUpperCase();
```

**Flags:**
- Chained calls without null checks
- `Optional.get()` without `isPresent()`
- Returning `null` instead of `Optional` or empty collection
- Missing `@Nullable`/`@NonNull` on public APIs

### Exception Handling

**Check for:**
```java
// ❌ Swallowing exceptions
try {
    process();
} catch (Exception e) { }  // Silent failure

// ❌ Losing stack trace
catch (IOException e) {
    throw new RuntimeException(e.getMessage());  // Lost context
}

// ✅ Proper handling
catch (IOException e) {
    log.error("Failed to process file: {}", filename, e);
    throw new ProcessingException("File processing failed", e);
}
```

**Flags:**
- Empty catch blocks
- Catching `Exception` or `Throwable` (too broad)
- Not logging exceptions
- Creating new exception without original cause

### Resource Management

**Check for:**
```java
// ❌ Resource leak
FileInputStream fis = new FileInputStream(file);
String content = read(fis);
fis.close();  // Won't execute if read() throws

// ✅ Try-with-resources
try (FileInputStream fis = new FileInputStream(file)) {
    return read(fis);
}  // Auto-closed
```

### Transaction Boundaries

**Check for:**
```java
// ❌ Missing transaction
public void createUser(UserRequest request) {
    User user = new User();
    userRepository.save(user);
    roleRepository.save(new Role(user));  // Two separate transactions
}

// ✅ Proper transaction
@Transactional
public void createUser(UserRequest request) {
    User user = new User();
    userRepository.save(user);
    roleRepository.save(new Role(user));  // Single atomic transaction
}
```

### Naming Conventions

**Good:**
```java
// ✅ Clear intent
public List<User> findActiveUsersByRole(String role) { }
public boolean isEmailValid(String email) { }
public void activateUser(Long userId) { }
```

**Bad:**
```java
// ❌ Unclear
public List<User> get(String s) { }
public boolean check(String str) { }
public void doStuff(Long id) { }
```

### Performance

**Check for:**
```java
// ❌ N+1 query problem
List<User> users = userRepository.findAll();
for (User user : users) {
    List<Order> orders = orderRepository.findByUserId(user.getId());  // N queries
}

// ✅ Join fetch
@Query("SELECT u FROM User u LEFT JOIN FETCH u.orders")
List<User> findAllWithOrders();

// ❌ Loading all data
List<User> allUsers = userRepository.findAll();  // Could be millions

// ✅ Pagination
Page<User> users = userRepository.findAll(PageRequest.of(0, 20));
```

---

## Review Output Format

```markdown
## Code Review: [Component/Feature Name]

### Critical Issues
- **Null safety violation** (UserService.java:42) - `user.getName().toUpperCase()` can NPE. Use Optional or null check.
- **Resource leak** (FileHandler.java:15) - FileInputStream not closed. Use try-with-resources.

### Important Improvements
- **API design** - POST used for idempotent update (UserController.java:28). Use PUT instead.
- **Transaction missing** - Multi-step operation needs @Transactional (OrderService.java:56).
- **N+1 query** - Loop fetches orders individually (line 89). Use JOIN FETCH.

### Code Smells
- **Long method** - extractUserData() is 80 lines. Consider extracting sub-methods.
- **Magic number** - Use named constant instead of `86400` (line 123).
- **Inconsistent naming** - Mix of camelCase and snake_case in variables.

### Good Practices Observed
- ✅ Constructor injection used throughout
- ✅ DTOs properly separate from entities
- ✅ Comprehensive validation on all endpoints
- ✅ Good test coverage (87%)
```

---

## Quick Reference Flags

| Category | Red Flags |
|----------|-----------|
| **Null Safety** | Chained calls, Optional.get(), returning null |
| **Exceptions** | Empty catch, broad catch, lost stack trace |
| **Resources** | Manual close(), missing try-with-resources |
| **API Design** | Wrong HTTP verb, no versioning, entity exposure |
| **Transactions** | Multi-step writes without @Transactional |
| **Performance** | N+1 queries, loading all data, missing indexes |
| **Clean Code** | Code duplication, magic numbers, unclear names |

---

## Severity Levels

- **Critical** - Security, data loss, crash risk → Must fix before merge
- **Important** - Performance, maintainability, correctness → Should fix
- **Code Smell** - Style, complexity, minor issues → Nice to have
- **Good** - Positive feedback to reinforce good practices
