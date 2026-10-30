# Spring Boot Conventions and Outdated Patterns

Project rules live in the `conventions` skill; this file lists how to spot a violation in a diff. Do not restate the rule, cite `conventions`.

## Conventions
- Field injection (`@Autowired` on fields) instead of constructor injection
- **Lombok**: any `@Data`, `@Getter`, `@Setter`, `@RequiredArgsConstructor`, `@Slf4j`, `@Builder`, etc. Explicit constructors, accessors and `LoggerFactory.getLogger()` are required
- Missing `@RestControllerAdvice` for global exception handling
- Error bodies that are not Problem Details (`Map`, custom `ErrorResponse`)
- `Page` / `PageImpl` returned from a controller; `@EnableSpringDataWebSupport` on the application class (breaks `@WebMvcTest`)
- Layering broken: Controller → Service → Repository (controller calling a repository, service returning an entity to the web layer)

## Deprecated / outdated
| Found | Use instead |
|-------|-------------|
| `javax.persistence.*` | `jakarta.persistence.*` |
| `WebSecurityConfigurerAdapter` | `SecurityFilterChain` bean |
| `@EnableWebSecurity` without `@Configuration` | add `@Configuration` |
| `spring-boot-starter-parent` below 4.x | 4.x |
| `RestTemplate` | `RestClient` or HTTP interface clients |
| `WebClient` for blocking calls | `RestClient` (keep `WebClient` for reactive/streaming) |
| `TestRestTemplate` | `RestTestClient` with `@AutoConfigureRestTestClient` |
| `@DynamicPropertySource` for Testcontainers | `@ServiceConnection` |
| `@MockBean` / `@SpyBean` (removed in Spring Boot 4) | `@MockitoBean` / `@MockitoSpyBean` from `org.springframework.test.context.bean.override.mockito` |
