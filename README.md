# Spring Boot REST API with JWT Authentication

A production-ready Spring Boot 3.4.1 application featuring a RESTful API for person management with JWT authentication, PostgreSQL database, and Kubernetes deployment support.

## Features

- **Spring Boot 3.4.1** with Java 21
- **JWT Authentication** for secure API access
- **PostgreSQL** database with Flyway migrations
- **RESTful API** with CRUD operations for Person entity
- **Pagination** support for list endpoints
- **MapStruct** for DTO mapping (No Lombok)
- **Comprehensive Testing** with JUnit 5, Mockito, and Testcontainers
- **Docker** and **Docker Compose** support
- **Kubernetes** deployment with Skaffold
- **CircleCI** pipeline for CI/CD
- **Health Checks** and monitoring with Spring Actuator
- **85%+ Test Coverage** with JaCoCo

## Prerequisites

- Java 21 or higher
- Maven 3.8+
- Docker and Docker Compose
- PostgreSQL 16 (optional for local development)
- Kubernetes cluster (Minikube, Kind, or cloud provider)
- Skaffold (for Kubernetes deployment)

## Quick Start

### 1. Clone the Repository

```bash
git clone <repository-url>
cd claude-ai-spring-boot
```

### 2. Run with Docker Compose

The easiest way to get started:

```bash
docker-compose up
```

This will start:
- PostgreSQL database on port 5432
- Spring Boot application on port 8080

### 3. Access the API

The application will be available at `http://localhost:8080`

## API Endpoints

### Authentication

#### Login
```bash
POST /api/auth/login
Content-Type: application/json

{
  "email": "test@example.com",
  "password": "password"
}
```

Response:
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer"
}
```

#### Refresh Token
```bash
POST /api/auth/refresh
Authorization: Bearer <refresh-token>
```

### Person Management (All protected with JWT)

#### Create Person
```bash
POST /api/persons
Authorization: Bearer <access-token>
Content-Type: application/json

{
  "firstName": "John",
  "lastName": "Doe",
  "email": "john.doe@example.com",
  "phoneNumber": "+1234567890",
  "street": "123 Main St",
  "city": "New York",
  "postalCode": "10001",
  "country": "USA",
  "dateOfBirth": "1990-01-15",
  "active": true
}
```

#### Get All Persons (Paginated)
```bash
GET /api/persons?page=0&size=20&sort=lastName,asc
Authorization: Bearer <access-token>
```

#### Get Person by ID
```bash
GET /api/persons/{id}
Authorization: Bearer <access-token>
```

#### Update Person
```bash
PUT /api/persons/{id}
Authorization: Bearer <access-token>
Content-Type: application/json

{
  "firstName": "Jane",
  "lastName": "Doe",
  "email": "jane.doe@example.com"
}
```

#### Delete Person
```bash
DELETE /api/persons/{id}
Authorization: Bearer <access-token>
```

#### Search by Email
```bash
GET /api/persons/search?email=john.doe@example.com
Authorization: Bearer <access-token>
```

### Health Check
```bash
GET /actuator/health
```

## Local Development

### With PostgreSQL Running Locally

1. Start PostgreSQL:
```bash
docker-compose up postgres -d
```

2. Run the application:
```bash
mvn spring-boot:run
```

### With Maven

```bash
# Build the application
mvn clean package

# Run tests
mvn test

# Run integration tests with Testcontainers
mvn verify

# Generate coverage report
mvn jacoco:report
```

The coverage report will be available at `target/site/jacoco/index.html`

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `DATABASE_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/persondb` |
| `DATABASE_USER` | Database username | `postgres` |
| `DATABASE_PASSWORD` | Database password | `postgres` |
| `JWT_SECRET` | Base64-encoded JWT secret key (min 256 bits) | Default test key |
| `SPRING_PROFILES_ACTIVE` | Active Spring profile | `dev` |

## Docker Deployment

### Build Docker Image

```bash
docker build -t claude-ai-spring-boot .
```

### Run with Docker Compose

```bash
# Start all services
docker-compose up -d

# View logs
docker-compose logs -f app

# Stop all services
docker-compose down
```

## Kubernetes Deployment

### Prerequisites

- Kubernetes cluster (Minikube, Kind, or cloud provider)
- Skaffold installed

### Deploy with Skaffold

```bash
# Deploy to Kubernetes
skaffold dev

# Or for production deployment
skaffold run
```

### Access the Application

```bash
# Port forward the service
kubectl port-forward svc/claude-ai-spring-boot 8080:8080

# Check pod status
kubectl get pods

# View logs
kubectl logs -f deployment/claude-ai-spring-boot

# Check health probes
kubectl describe pod <pod-name>
```

## Database Schema

The application uses Flyway for database migrations. Schema is automatically created on application startup.

### Person Table

| Column | Type | Constraints |
|--------|------|-------------|
| id | BIGSERIAL | PRIMARY KEY |
| first_name | VARCHAR(100) | NOT NULL |
| last_name | VARCHAR(100) | NOT NULL |
| email | VARCHAR(150) | NOT NULL, UNIQUE |
| phone_number | VARCHAR(20) | |
| street | VARCHAR(200) | |
| city | VARCHAR(100) | |
| postal_code | VARCHAR(10) | |
| country | VARCHAR(100) | |
| date_of_birth | DATE | |
| active | BOOLEAN | NOT NULL, DEFAULT true |
| created_at | TIMESTAMP | NOT NULL |
| updated_at | TIMESTAMP | NOT NULL |
| version | BIGINT | NOT NULL, DEFAULT 0 |

Indexes:
- `idx_persons_email` on email (unique)
- `idx_persons_phone` on phone_number
- `idx_persons_active` on active
- `idx_persons_created_at` on created_at

## Testing

The project includes comprehensive test coverage:

- **Unit Tests**: Service and controller layer tests with Mockito
- **Repository Tests**: JPA repository tests with @DataJpaTest
- **Integration Tests**: Full-stack tests with Testcontainers
- **Security Tests**: Authentication and authorization tests

```bash
# Run all tests
mvn test

# Run integration tests
mvn verify

# Check coverage (requires 85% minimum)
mvn jacoco:check
```

## CI/CD Pipeline

The project uses CircleCI for continuous integration and deployment.

### Pipeline Stages

1. **Build and Test**: Compile, run tests, generate coverage report
2. **Security Scan**: OWASP dependency check
3. **Docker Build**: Build and push Docker image (main branch only)

### Setup CircleCI

1. Connect your repository to CircleCI
2. Add environment variables:
   - `DOCKER_USERNAME`: Docker Hub username
   - `DOCKER_PASSWORD`: Docker Hub password
3. Pipeline will run automatically on each commit

## Security

- All `/api/persons/**` endpoints are protected with JWT authentication
- JWT tokens expire after 15 minutes
- Refresh tokens expire after 7 days
- Passwords are encrypted with BCrypt
- CORS is configured for cross-origin requests
- Sensitive data is stored in Kubernetes secrets

## Monitoring

Spring Boot Actuator endpoints:

- `/actuator/health` - Application health status
- `/actuator/health/liveness` - Kubernetes liveness probe
- `/actuator/health/readiness` - Kubernetes readiness probe
- `/actuator/metrics` - Application metrics
- `/actuator/info` - Application information

## Troubleshooting

### Application won't start

1. Check if PostgreSQL is running:
```bash
docker-compose ps
```

2. Check application logs:
```bash
docker-compose logs app
```

### Database connection errors

1. Verify database credentials in environment variables
2. Check PostgreSQL is accessible:
```bash
docker exec -it persondb psql -U postgres -d persondb
```

### JWT authentication fails

1. Ensure JWT_SECRET is properly set (Base64-encoded, min 256 bits)
2. Check token expiration
3. Verify user credentials (default: test@example.com / password)

### Tests failing

1. Ensure Docker is running (for Testcontainers)
2. Run tests with verbose output:
```bash
mvn test -X
```

## Project Structure

```
src/
├── main/
│   ├── java/pl/piomin/services/
│   │   ├── config/              # Security and JPA configuration
│   │   ├── domain/              # Entities and repositories
│   │   ├── application/         # DTOs, mappers, services
│   │   ├── infrastructure/      # Security, exceptions
│   │   └── presentation/        # REST controllers
│   └── resources/
│       ├── application.yml      # Main configuration
│       └── db/migration/        # Flyway migrations
└── test/
    ├── java/pl/piomin/services/
    │   ├── controller/          # Controller tests
    │   ├── service/             # Service tests
    │   ├── repository/          # Repository tests
    │   └── integration/         # Integration tests
    └── resources/
        └── application-test.yml # Test configuration
```

## Technology Stack

- **Framework**: Spring Boot 3.4.1
- **Language**: Java 21
- **Security**: Spring Security 6, JWT (jjwt 0.12.6)
- **Database**: PostgreSQL 16, Spring Data JPA, Flyway
- **Mapping**: MapStruct 1.6.3
- **Testing**: JUnit 5, Mockito, Testcontainers, Spring Security Test
- **Build**: Maven
- **Containerization**: Docker, Docker Compose
- **Orchestration**: Kubernetes, Skaffold
- **CI/CD**: CircleCI
- **Monitoring**: Spring Actuator

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Ensure tests pass and coverage is ≥ 85%
5. Submit a pull request

## License

This project is licensed under the MIT License.

## Version

Current version: **1.0.0**
