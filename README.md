# Claude AI Spring Boot Application

This project provides a simple Spring Boot REST API for managing `Person` entities. It uses PostgreSQL for persistence and Keycloak for JWT-based authentication.

## Prerequisites
- Java 17
- Maven
- Docker (for Compose)
- Keycloak instance (the docker-compose file sets up Keycloak on port 8081)

## Running locally
```bash
# Build the application
mvn clean package

# Start services with Docker Compose
docker compose up -d

# The API will be available at http://localhost:8080
# Keycloak UI at http://localhost:8081
```

## Using the API
All endpoints are protected. Obtain a JWT from Keycloak for the `claude-ai-spring-boot` audience.

```bash
# Example: GET all people
curl -H "Authorization: Bearer <token>" http://localhost:8080/api/people
```

## Testing
```bash
mvn test
```

## CI
CircleCI is configured to run tests and build/push a Docker image on each commit.

---

Generated with [Claude Code](https://claude.com/claude-code)
