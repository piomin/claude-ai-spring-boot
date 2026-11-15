# Cloud Native - Spring Cloud

## Spring Cloud Config Server

```java
// Config Server
@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}

// application.yml
server:
  port: 8888

spring:
  cloud:
    config:
      server:
        git:
          uri: https://github.com/example/config-repo
          default-label: main
          search-paths: '{application}'
          # credentials: configure via environment variables
        native:
          search-locations: classpath:/config

// Config Client
@SpringBootApplication
public class ClientApplication {
    public static void main(String[] args) {
        SpringApplication.run(ClientApplication.class, args);
    }
}

// application.yml (Config Client)
spring:
  application:
    name: user-service
  config:
    import: "configserver:http://localhost:8888"
  cloud:
    config:
      # credentials: configure via environment variables
      fail-fast: true
      retry:
        max-attempts: 6
        initial-interval: 1000
```

## Dynamic Configuration Refresh

```java
@RestController
@RefreshScope
public class ConfigController {
    @Value("${app.feature.enabled:false}")
    private boolean featureEnabled;

    @Value("${app.max-connections:100}")
    private int maxConnections;

    @GetMapping("/config")
    public Map<String, Object> getConfig() {
        return Map.of(
            "featureEnabled", featureEnabled,
            "maxConnections", maxConnections
        );
    }
}

// Refresh configuration via Actuator endpoint:
// POST /actuator/refresh
```

## Service Discovery - Eureka

```java
// Eureka Server
@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}

// application.yml (Eureka Server)
server:
  port: 8761

eureka:
  instance:
    hostname: localhost
  client:
    register-with-eureka: false
    fetch-registry: false
    service-url:
      defaultZone: http://${eureka.instance.hostname}:${server.port}/eureka/

// Eureka Client
@SpringBootApplication
@EnableDiscoveryClient
public class UserServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}

// application.yml (Eureka Client)
spring:
  application:
    name: user-service

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
    registry-fetch-interval-seconds: 5
  instance:
    prefer-ip-address: true
    lease-renewal-interval-in-seconds: 10
    lease-expiration-duration-in-seconds: 30
```

## Spring Cloud Gateway

```java
@SpringBootApplication
public class GatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
            .route("user-service", r -> r
                .path("/api/users/**")
                .filters(f -> f
                    .rewritePath("/api/users/(?<segment>.*)", "/users/${segment}")
                    .addRequestHeader("X-Gateway", "Spring-Cloud-Gateway")
                    .circuitBreaker(config -> config
                        .setName("userServiceCircuitBreaker")
                        .setFallbackUri("forward:/fallback/users")
                    )
                    .retry(config -> config
                        .setRetries(3)
                        .setStatuses(HttpStatus.SERVICE_UNAVAILABLE)
                    )
                )
                .uri("lb://user-service")
            )
            .route("order-service", r -> r
                .path("/api/orders/**")
                .filters(f -> f
                    .rewritePath("/api/orders/(?<segment>.*)", "/orders/${segment}")
                    .requestRateLimiter(config -> config
                        .setRateLimiter(redisRateLimiter())
                        .setKeyResolver(userKeyResolver())
                    )
                )
                .uri("lb://order-service")
            )
            .build();
    }

    @Bean
    public RedisRateLimiter redisRateLimiter() {
        return new RedisRateLimiter(10, 20); // replenishRate, burstCapacity
    }

    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> Mono.justOrEmpty(
            exchange.getRequest().getHeaders().getFirst("X-User-Id")
        ).defaultIfEmpty("anonymous");
    }
}

// application.yml (Gateway)
spring:
  cloud:
    gateway:
      discovery:
        locator:
          enabled: true
          lower-case-service-id: true
      default-filters:
        - DedupeResponseHeader=Access-Control-Allow-Origin
      globalcors:
        cors-configurations:
          '[/**]':
            allowed-origins: "*"
            allowed-methods:
              - GET
              - POST
              - PUT
              - DELETE
            allowed-headers: "*"
```

## Circuit Breaker - Resilience4j

```java
@Service
public class ExternalApiService {
    private static final Logger log = LoggerFactory.getLogger(ExternalApiService.class);

    private final RestClient restClient;

    public ExternalApiService(RestClient.Builder builder) {
        this.restClient = builder
            .baseUrl("https://api.external.com")
            .build();
    }

    @CircuitBreaker(name = "externalApi", fallbackMethod = "getFallbackData")
    @Retry(name = "externalApi")
    @RateLimiter(name = "externalApi")
    public ExternalData getData(String id) {
        return restClient
            .get()
            .uri("/data/{id}", id)
            .retrieve()
            .body(ExternalData.class);
    }

    private ExternalData getFallbackData(String id, Exception e) {
        log.warn("Fallback triggered for id: {}, error: {}", id, e.getMessage());
        return new ExternalData(id, "Fallback data", LocalDateTime.now());
    }
}

// application.yml
resilience4j:
  circuitbreaker:
    instances:
      externalApi:
        register-health-indicator: true
        sliding-window-size: 10
        minimum-number-of-calls: 5
        permitted-number-of-calls-in-half-open-state: 3
        automatic-transition-from-open-to-half-open-enabled: true
        wait-duration-in-open-state: 5s
        failure-rate-threshold: 50
        event-consumer-buffer-size: 10

  retry:
    instances:
      externalApi:
        max-attempts: 3
        wait-duration: 1s
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2

  ratelimiter:
    instances:
      externalApi:
        limit-for-period: 10
        limit-refresh-period: 1s
        timeout-duration: 0s
```

## Distributed Tracing - Micrometer + OpenTelemetry

### OTLP Exporter (Preferred)

```yaml
# application.yml - OpenTelemetry Protocol (recommended over Zipkin)
management:
  tracing:
    sampling:
      probability: 1.0
  otlp:
    tracing:
      endpoint: http://localhost:4318/v1/traces

logging:
  pattern:
    level: "%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]"
```

```xml
<!-- pom.xml -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>
```

### Zipkin Exporter (Alternative)

```yaml
management:
  tracing:
    sampling:
      probability: 1.0
  zipkin:
    tracing:
      endpoint: http://localhost:9411/api/v2/spans
```

### Custom Spans with Observation API

```java
@Service
public class OrderService {
    private final ObservationRegistry observationRegistry;
    private final OrderRepository orderRepository;

    public OrderService(ObservationRegistry observationRegistry, OrderRepository orderRepository) {
        this.observationRegistry = observationRegistry;
        this.orderRepository = orderRepository;
    }

    public Order processOrder(OrderRequest request) {
        return Observation.createNotStarted("order.process", observationRegistry)
            .lowCardinalityKeyValue("order.type", request.type())
            .observe(() -> {
                Order order = createOrder(request);
                return order;
            });
    }
}
```

## Load Balancing with Spring Cloud LoadBalancer

```java
@Configuration
@LoadBalancerClient(name = "user-service", configuration = UserServiceLoadBalancerConfig.class)
public class LoadBalancerConfiguration {
}

@Configuration
public class UserServiceLoadBalancerConfig {

    @Bean
    public ReactorLoadBalancer<ServiceInstance> randomLoadBalancer(
            LoadBalancerClientFactory clientFactory,
            ObjectProvider<LoadBalancerProperties> properties) {
        return new RandomLoadBalancer(
            clientFactory.getLazyProvider("user-service", ServiceInstanceListSupplier.class),
            "user-service"
        );
    }
}

@Service
public class UserClientService {
    private final RestClient restClient;

    public UserClientService(RestClient.Builder builder) {
        this.restClient = builder
            .baseUrl("http://user-service")
            .build();
    }

    public User getUser(Long id) {
        return restClient
            .get()
            .uri("/users/{id}", id)
            .retrieve()
            .body(User.class);
    }
}
```

## Health Checks, Actuator & Prometheus

```xml
<!-- pom.xml - Prometheus metrics endpoint -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-components: always      # each component's status (db, diskSpace, ...) for anonymous callers
      show-details: when-authorized # details (host names, versions) only with a token
      probes:
        enabled: true
      group:
        readiness:
          include: readinessState,db
  health:
    livenessState:
      enabled: true
    readinessState:
      enabled: true
  metrics:
    tags:
      application: ${spring.application.name}
```

Status endpoints. Permitting `/actuator/health/**` (with the probes) applies to every app; the rest is **required only when the API is built for a UI** that shows the API status and version (`conventions` skill):
- `GET /actuator/health` answers `{"status": "UP", "components": {...}}`, and a non-2xx status with `DOWN`/`OUT_OF_SERVICE`. Components without details tell a client or a dashboard what is down without leaking infrastructure.
- `GET /actuator/info` answers `{"build": {"version": ...}}` once the `spring-boot-maven-plugin` runs the `build-info` goal (see `ui-contract.md`). It is the running version, the same as the OpenAPI `info.version`.
- `SecurityConfig` permits `/actuator/health/**` (including the `liveness`/`readiness` probes) and `/actuator/info`, before any other rule. Every other Actuator endpoint (`env`, `metrics`, `prometheus`, ...) needs a token or a separate management port.
- An integration test asserts that both answer without a token, that `health` lists `db`, that `info` `build.version` equals the contract's `info.version`, and that `/actuator/env` returns 401.

```java
@Component
public class CustomHealthIndicator implements HealthIndicator {

    @Override
    public Health health() {
        boolean serviceUp = checkExternalService();

        if (serviceUp) {
            return Health.up()
                .withDetail("externalService", "Available")
                .withDetail("timestamp", LocalDateTime.now())
                .build();
        } else {
            return Health.down()
                .withDetail("externalService", "Unavailable")
                .withDetail("error", "Connection timeout")
                .build();
        }
    }

    private boolean checkExternalService() {
        // Check external dependency
        return true;
    }
}
```

## Kubernetes Deployment

### ConfigMap and Secret

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: user-service-config
data:
  SPRING_DATASOURCE_URL: "jdbc:postgresql://postgres-service:5432/mydb"
  MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE: "health,info,metrics,prometheus"
  SERVER_SHUTDOWN: "graceful"
```

Configure datasource credentials via Kubernetes Secrets mounted as environment variables or using Config Tree (see below).

### Config Tree for Mounted Secrets

Instead of env vars, read secrets from mounted files using Config Tree (more secure — secrets don't leak via `/proc/environ`):

```yaml
# application.yml
spring:
  config:
    import: "optional:configtree:/run/secrets/"
```

With a Kubernetes Secret mounted at `/run/secrets/`, each key becomes a file. Spring Boot reads the file content as the property value.

```yaml
# Kubernetes volume mount for Config Tree
spec:
  containers:
  - name: app
    volumeMounts:
    - name: secrets
      mountPath: /run/secrets
      readOnly: true
  volumes:
  - name: secrets
    secret:
      secretName: user-service-secrets
```

### Deployment Manifest

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: user-service
spec:
  replicas: 2
  selector:
    matchLabels:
      app: user-service
  template:
    metadata:
      labels:
        app: user-service
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "8080"
        prometheus.io/path: "/actuator/prometheus"
    spec:
      containers:
      - name: user-service
        image: user-service:1.0.0
        ports:
        - containerPort: 8080
        envFrom:
        - configMapRef:
            name: user-service-config
        - secretRef:
            name: user-service-secrets
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "kubernetes"
        - name: JAVA_TOOL_OPTIONS
          value: "-XX:MaxRAMPercentage=75.0"
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 15
          periodSeconds: 5
        startupProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 10
          periodSeconds: 5
          failureThreshold: 20
        lifecycle:
          preStop:
            exec:
              command: ["sh", "-c", "sleep 10"]
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
      securityContext:
        runAsNonRoot: true
        runAsUser: 1000
---
apiVersion: v1
kind: Service
metadata:
  name: user-service
spec:
  selector:
    app: user-service
  ports:
  - port: 80
    targetPort: 8080
  type: ClusterIP
```

### Stack Dependencies in the Same Namespace (PostgreSQL, Keycloak)

When the API's own database and identity provider are deployed next to it, give them the same three probes as the API. Otherwise a redeploy routes traffic to a pod that is not ready yet, or the API connects before the database listens.

```yaml
# PostgreSQL: data on a PVC; pg_isready over TCP (during the first initdb the server listens only on the Unix socket)
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: postgres
spec:
  accessModes: [ReadWriteOnce]
  resources:
    requests:
      storage: 1Gi
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: postgres
spec:
  strategy:
    type: Recreate            # ReadWriteOnce: the old pod releases the volume before the new one mounts it
  selector:
    matchLabels:
      app: postgres
  template:
    metadata:
      labels:
        app: postgres
    spec:
      containers:
        - name: postgres
          image: postgres:18
          env:
            - name: POSTGRES_DB
              valueFrom:
                secretKeyRef: { name: postgres, key: database }
            - name: POSTGRES_USER
              valueFrom:
                secretKeyRef: { name: postgres, key: username }
            - name: POSTGRES_PASSWORD
              valueFrom:
                secretKeyRef: { name: postgres, key: password }
          startupProbe:         # every 2 s, so the database is ready before the API's first connection
            exec:
              command: ["sh", "-c", 'pg_isready -h 127.0.0.1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"']
            initialDelaySeconds: 2
            periodSeconds: 2
            failureThreshold: 60
          readinessProbe:
            exec:
              command: ["sh", "-c", 'pg_isready -h 127.0.0.1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"']
            periodSeconds: 10
          livenessProbe:
            exec:
              command: ["sh", "-c", 'pg_isready -h 127.0.0.1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"']
            periodSeconds: 10
            timeoutSeconds: 5
            failureThreshold: 6
          volumeMounts:
            - name: data
              mountPath: /var/lib/postgresql      # postgres:18 and later; /var/lib/postgresql/data before 18
      volumes:
        - name: data
          persistentVolumeClaim:
            claimName: postgres
```

Without a volume, every `skaffold run` or `kubectl apply` that replaces the pod starts with an empty database.

```yaml
# Keycloak: health endpoints are off by default and live on the management port 9000, not 8080
env:
  - name: KC_HEALTH_ENABLED
    value: "true"
  - name: KC_PROXY_HEADERS          # behind a Route/Ingress/proxy: build https URLs from X-Forwarded-*
    value: xforwarded
startupProbe:                       # start plus realm import takes about 30 s
  httpGet: { path: /health/started, port: 9000 }
  initialDelaySeconds: 30
  periodSeconds: 5
  failureThreshold: 24
readinessProbe:                     # without it, token requests fail for seconds after every redeploy
  httpGet: { path: /health/ready, port: 9000 }
  periodSeconds: 5
livenessProbe:
  httpGet: { path: /health/live, port: 9000 }
  periodSeconds: 10
```

Do not set `KC_HOSTNAME` when the public host is generated (an OpenShift Route without `spec.host`). Keycloak then builds every URL, the token issuer included, from the host the request came through. The API validates tokens against the JWK set inside the cluster (`http://keycloak:8080/realms/<realm>/protocol/openid-connect/certs`), not through the public host.

**Prove a probe can fail.** A probe or healthcheck that always passes is worse than none. After adding one, watch the service start: the probe must report failures before the service is up (`kubectl get events --field-selector type=Warning`, or `podman inspect --format '{{json .State.Health}}' <container>`), and the pods that depend on it must show no restarts.

### Ingress TLS with cert-manager

cert-manager automatically provisions and renews TLS certificates on Kubernetes.

```yaml
# ClusterIssuer for Let's Encrypt
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: letsencrypt-prod
spec:
  acme:
    server: https://acme-v02.api.letsencrypt.org/directory
    email: admin@example.com
    privateKeySecretRef:
      name: letsencrypt-prod-key
    solvers:
    - http01:
        ingress:
          class: nginx
```

```yaml
# Ingress with automatic TLS
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: user-service-ingress
  annotations:
    cert-manager.io/cluster-issuer: "letsencrypt-prod"
spec:
  ingressClassName: nginx
  tls:
  - hosts:
    - api.example.com
    secretName: user-service-tls
  rules:
  - host: api.example.com
    http:
      paths:
      - path: /api/users
        pathType: Prefix
        backend:
          service:
            name: user-service
            port:
              number: 80
```

cert-manager creates and renews the `user-service-tls` Secret automatically. For end-to-end TLS (not just ingress termination), mount the same Secret into the pod and configure Spring Boot SSL Bundles with `reload-on-update: true` (see security reference).

### Certificate for mTLS between services

```yaml
# cert-manager Certificate for pod-level TLS
apiVersion: cert-manager.io/v1
kind: Certificate
metadata:
  name: user-service-cert
spec:
  secretName: user-service-tls-pod
  issuerRef:
    name: internal-ca
    kind: ClusterIssuer
  dnsNames:
  - user-service
  - user-service.default.svc.cluster.local
  duration: 720h
  renewBefore: 48h
```

```yaml
# Mount into pod for Spring Boot SSL Bundle
spec:
  containers:
  - name: user-service
    volumeMounts:
    - name: tls-certs
      mountPath: /etc/ssl/certs
      readOnly: true
  volumes:
  - name: tls-certs
    secret:
      secretName: user-service-tls-pod
```

## Virtual Threads

```yaml
# application.yml - one line enables virtual threads for all request handling
spring:
  threads:
    virtual:
      enabled: true
```

Virtual threads eliminate the need for WebFlux in most cases. With `spring.threads.virtual.enabled=true`:
- Tomcat handles each request on a virtual thread
- Blocking I/O (JDBC, RestClient, file I/O) no longer wastes platform threads
- `@Async` tasks run on virtual threads automatically
- No code changes required - existing blocking code scales like reactive

**When to still use WebFlux:** streaming responses, SSE, WebSocket, or when you need backpressure.

## HTTP Interface Clients (Spring 6+)

```java
// Declarative HTTP client - replaces OpenFeign
public interface UserClient {

    @GetExchange("/users/{id}")
    UserResponse getUser(@PathVariable Long id);

    @GetExchange("/users")
    List<UserResponse> getAllUsers();

    @PostExchange("/users")
    UserResponse createUser(@RequestBody UserCreateRequest request);
}

// Configuration
@Configuration
public class ClientConfig {

    @Bean
    public UserClient userClient(RestClient.Builder builder) {
        RestClient restClient = builder
            .baseUrl("http://user-service")
            .build();
        return HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(restClient))
            .build()
            .createClient(UserClient.class);
    }
}
```

## GraalVM Native Image

```xml
<!-- pom.xml - add native profile support -->
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.1.1</version> <!-- use the latest Spring Boot 4.x release -->
</parent>

<plugin>
    <groupId>org.graalvm.buildtools</groupId>
    <artifactId>native-maven-plugin</artifactId>
</plugin>
```

```bash
# Build native image
./mvnw -Pnative native:compile

# Build native Docker image with Buildpacks
./mvnw -Pnative spring-boot:build-image
```

Benefits: ~50ms startup, ~50MB RSS memory. Trade-offs: longer build time, reflection requires hints.

## Graceful Shutdown

```yaml
# application.yml - required for zero-downtime K8s rolling updates
server:
  shutdown: graceful

spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
```

```yaml
# Kubernetes - add preStop hook to allow deregistration
spec:
  containers:
  - name: app
    lifecycle:
      preStop:
        exec:
          command: ["sh", "-c", "sleep 10"]
```

## Docker Configuration

### Multi-stage Build

```dockerfile
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace/app

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN ./mvnw dependency:go-offline -B

COPY src src
RUN ./mvnw package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app && apk add --no-cache curl
WORKDIR /app
COPY --from=build /workspace/app/target/*.jar app.jar
USER app
HEALTHCHECK --interval=30s --timeout=3s CMD curl -sf http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Paketo Buildpacks (Recommended)

Paketo Buildpacks build OCI images without a Dockerfile. Integrated into Spring Boot Maven plugin.

```bash
# Build image (uses Paketo by default)
./mvnw spring-boot:build-image \
  -Dspring-boot.build-image.imageName=myapp:1.0.0

# Build native image with Paketo
./mvnw -Pnative spring-boot:build-image \
  -Dspring-boot.build-image.imageName=myapp:1.0.0-native
```

```xml
<!-- pom.xml - Paketo configuration -->
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <configuration>
        <image>
            <name>${project.artifactId}:${project.version}</name>
            <env>
                <BP_JVM_VERSION>21</BP_JVM_VERSION>
                <BPE_JAVA_TOOL_OPTIONS>-XX:MaxRAMPercentage=75.0</BPE_JAVA_TOOL_OPTIONS>
            </env>
        </image>
    </configuration>
</plugin>
```

Advantages over Dockerfile:
- Reproducible builds, no Dockerfile to maintain
- Automatic security patching of base image layers
- Optimized layering (dependencies cached separately from app code)
- Supported by Spring Boot Maven plugin out of the box

### Docker Compose Healthchecks for Local Dependencies

Give PostgreSQL and Keycloak healthchecks, so `docker compose up -d --wait` returns only once they are usable:

```yaml
services:
  postgres:
    image: postgres:18
    healthcheck:
      test: ["CMD", "pg_isready", "-h", "127.0.0.1", "-U", "app", "-d", "app"]
      interval: 5s
      start_period: 10s
  keycloak:
    image: quay.io/keycloak/keycloak:26.8.1
    command: ["start-dev", "--import-realm"]
    environment:
      KC_HEALTH_ENABLED: "true"
    healthcheck:
      # The image has no curl: ask /health/ready on the management port 9000 through bash's /dev/tcp.
      test: ["CMD-SHELL", "exec 3<>/dev/tcp/127.0.0.1/9000 && printf 'GET /health/ready HTTP/1.1\\r\\nHost: localhost\\r\\nConnection: close\\r\\n\\r\\n' >&3 && grep -q UP <&3"]
      interval: 5s
      start_period: 60s
```

Write a shell healthcheck as `["CMD-SHELL", "<script>"]`, never `["CMD", "bash", "-c", "<script>"]`. Podman splits that array on spaces, runs `bash -c exec`, and reports the service healthy before it has started.

## Kubernetes and OpenShift with Skaffold and Kustomize Overlays

One set of manifests deploys to a local cluster, any Kubernetes and OpenShift. Nothing in git names a cluster, host, registry, namespace or secret value: what differs per environment comes from an overlay or a Skaffold flag.

```
deploy/
  base/                   plain Kubernetes, valid anywhere: Deployments, Services, ConfigMaps, PVCs, probes,
                          resources, app.kubernetes.io/* labels. No Route, Ingress, host, registry or secret value.
  overlays/
    local/                default: kind, minikube, Docker Desktop (port forwarding)
    openshift/            + Routes without spec.host (OpenShift generates one), Topology labels
    kubernetes/           + Ingresses without host (an environment overlay sets it)
  environments/
    example/              committed: how an environment overlay looks
    <env>/                git-ignored (deploy/environments/* with !deploy/environments/example/): host, TLS, settings
  ensure-secrets.sh       Skaffold before-deploy hook: creates missing Secrets
skaffold.yaml
```

- An overlay starts from `../../base` and adds only what its target needs. An environment overlay starts from one overlay and patches only what that environment needs.
- Use only kustomize features that Skaffold's kustomize, `kubectl -k` and `oc -k` all support (kustomize 3.8): `resources`, `patches` (a file, or inline JSON 6902 with `target`), `configMapGenerator`, `secretGenerator`. Do not use `labels`, `replacements` or `components`; write labels on the objects.
- Never put `namespace:` in a manifest or kustomization: it comes from `-n` or the current context.
- Selectors stay `app: <name>`. A selector cannot be changed later, so never add labels to it.
- Runtime settings live in a ConfigMap in `base`, with defaults that hold inside any cluster (Service names, not hosts).
- Secrets are referenced by name only. The before-deploy hook creates each missing one with a random value (`openssl rand -hex 16`) in the deploy's namespace and context (`$SKAFFOLD_NAMESPACES`, `$SKAFFOLD_KUBE_CONTEXT`). It never changes an existing Secret, because PostgreSQL keeps the password of its first start. An environment with its own secret management creates them before the first deploy.

```yaml
# skaffold.yaml
apiVersion: skaffold/v4beta13
kind: Config
metadata:
  name: user-service
build:
  artifacts:
    - image: user-service          # bare name in the manifests; the registry comes only from --default-repo
      jib: {}                      # no container engine needed; or buildpacks / spring-boot:build-image
  tagPolicy:
    gitCommit: {}
manifests:
  kustomize:
    paths:
      - '{{ default "deploy/overlays/local" .DEPLOY_OVERLAY }}'
deploy:
  kubectl:
    hooks:
      before:
        - host:
            command: ["sh", "deploy/ensure-secrets.sh"]
            os: [darwin, linux]
portForward:                       # local profile only
  - resourceType: service
    resourceName: user-service
    port: 8080
    localPort: 8080
profiles:
  - name: openshift
    patches:
      - op: replace
        path: /manifests/kustomize/paths/0
        value: '{{ default "deploy/overlays/openshift" .DEPLOY_OVERLAY }}'
      - op: remove
        path: /portForward
  - name: kubernetes
    patches:
      - op: replace
        path: /manifests/kustomize/paths/0
        value: '{{ default "deploy/overlays/kubernetes" .DEPLOY_OVERLAY }}'
      - op: remove
        path: /portForward
```

```bash
skaffold dev                                                              # local cluster, watch loop
skaffold run -p openshift --default-repo=quay.io/<org> -n <namespace>     # or -p kubernetes
DEPLOY_OVERLAY=deploy/environments/<env> skaffold run -p openshift ...    # an environment overlay
skaffold delete -p openshift -n <namespace>
```

Advise for the target platform, not the local default. Before giving deployment steps, check the target (`kubectl config current-context`; on OpenShift `oc whoami --show-server`). On OpenShift, services are exposed through Routes with `-p openshift`; port forwarding fits only a local cluster.

CI renders every overlay and the example environment, so a broken patch fails the build, not the deploy:

```bash
for dir in deploy/overlays/* deploy/environments/example; do
  echo "== $dir"; kustomize build "$dir" > /dev/null
done
```

## Quick Reference

| Component | Purpose |
|-----------|---------|
| **Config Server** | Centralized configuration management |
| **Eureka** | Service discovery and registration |
| **Gateway** | API gateway with routing, filtering, load balancing |
| **Circuit Breaker** | Fault tolerance and fallback patterns |
| **Load Balancer** | Client-side load balancing |
| **Tracing (OTLP)** | Distributed tracing with OpenTelemetry |
| **Actuator** | Production-ready monitoring and management |
| **Virtual Threads** | High-throughput blocking I/O without WebFlux |
| **HTTP Interfaces** | Declarative HTTP clients (replaces Feign) |
| **Native Image** | Fast startup, low memory with GraalVM |
| **Paketo Buildpacks** | Build OCI images without Dockerfile (Spring Boot plugin) |
| **Skaffold + Kustomize** | Build and deploy one set of manifests to local, Kubernetes and OpenShift through overlays |
| **Config Tree** | Read secrets from mounted files instead of env vars |
| **cert-manager** | Automatic TLS certificate provisioning and renewal on K8s |
| **SSL Bundles** | Unified SSL config with hot reload (see security reference) |
| **Kubernetes** | Container orchestration, ConfigMap/Secret, probes, Prometheus |
