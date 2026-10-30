# Spring Boot Conventions and Outdated Patterns

The rules are in the `conventions` skill (Mandatory Rules, Code Quality Standards); do not restate them in findings, cite the rule. This file only says what to grep for and lists outdated APIs `conventions` does not name.

## Where the rules are
| Topic | Source |
|-------|--------|
| No Lombok, constructor injection, RestClient, RestTestClient, `@ServiceConnection`, Jakarta, records for DTOs | `conventions` → Mandatory Rules |
| `@RestControllerAdvice`, Problem Details, `PagedModel` (never `Page`/`PageImpl`), `@Version`, `@Valid`, transactions, actuator probes | `conventions` → Code Quality Standards |
| Controller → Service → Repository layering | `spring-boot` → Architecture Patterns |

## Signals
- Any Lombok annotation (`@Data`, `@Getter`, `@Setter`, `@RequiredArgsConstructor`, `@Slf4j`, `@Builder`, ...)
- `@Autowired` on a field
- `Page` / `PageImpl` returned from a controller; `@EnableSpringDataWebSupport` on the application class (breaks `@WebMvcTest`)
- Controller using a repository directly, or a service returning an entity to the web layer

## Outdated APIs not named in `conventions`
| Found | Use instead |
|-------|-------------|
| `WebSecurityConfigurerAdapter` | `SecurityFilterChain` bean |
| `@EnableWebSecurity` without `@Configuration` | add `@Configuration` |
| `spring-boot-starter-parent` below 4.x | 4.x |
| `WebClient` for blocking calls | `RestClient` (keep `WebClient` for reactive/streaming) |
| `@MockBean` / `@SpyBean` (removed in Spring Boot 4) | `@MockitoBean` / `@MockitoSpyBean` (`spring-boot` → `references/testing.md`) |
