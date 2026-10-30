# Security

Applies to controllers, repositories, configuration and anything handling user input. For a deeper audit of authentication, authorization or infrastructure, hand off to the `security-engineer` agent.

- **Input validation**: missing `@Valid` on `@RequestBody`, unvalidated path variables and request parameters
- **SQL injection**: string concatenation in `@Query`, raw JDBC without parameters
- **Entity exposure**: JPA entities returned from controllers instead of DTOs (leaks internals, lazy-loading and N+1 risk)
- **Error leakage**: a catch-all handler returning `ex.getMessage()` or a stack trace
- **Secrets**: hardcoded credentials, secrets in `application.properties`
- **Actuator**: `/actuator/health/**` probes behind authentication; other endpoints exposed without need

```java
// ❌ Exposes the entity
@GetMapping("/{id}")
public User getUser(@PathVariable Long id) {
    return userRepository.findById(id).get();
}

// ✅ DTO from the service
@GetMapping("/{id}")
public UserResponse getUser(@PathVariable Long id) {
    return userService.findById(id);
}
```
