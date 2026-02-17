# Spring Boot REST API with JWT/OAuth2 Security and Kubernetes Deployment

This project is a Spring Boot application that exposes REST API endpoints for managing persons, with JWT and OAuth2 security, and supports deployment on Kubernetes.

## Features

- REST API for CRUD operations on Person entities
- JWT authentication and authorization
- OAuth2 resource server support
- PostgreSQL database integration
- Docker support for containerization
- Kubernetes manifests for deployment
- Skaffold configuration for local development
- CircleCI pipeline for CI/CD
- Comprehensive test coverage

## Prerequisites

- Java 17
- Maven 3.8+
- Docker
- Kubernetes cluster (Minikube, Kind, or cloud provider)
- Skaffold
- PostgreSQL (for local development)

## Getting Started

### Clone the Repository

```bash
git clone <repository-url>
cd claude-ai-spring-boot
```

### Build the Application

```bash
mvn clean package
```

### Run with Docker Compose

```bash
docker-compose up
```

### Run Tests

```bash
mvn test
```

## API Endpoints

### Authentication

- `POST /api/auth/login` - Authenticate user and get JWT token

### Person Management

- `GET /api/persons` - Get all persons
- `GET /api/persons/{id}` - Get person by ID
- `POST /api/persons` - Create new person
- `PUT /api/persons/{id}` - Update person
- `DELETE /api/persons/{id}` - Delete person

## Security

The application uses JWT for authentication. To access protected endpoints, include the JWT token in the Authorization header:

```
Authorization: Bearer <your-jwt-token>
```

For testing purposes, you can use the following credentials:
- Username: user
- Password: password

## Kubernetes Deployment

### With Skaffold (Local Development)

```bash
skaffold dev
```

### Manual Deployment

```bash
kubectl apply -f kubernetes/
```

## Project Structure

```
claude-ai-spring-boot/
├── src/
│   ├── main/
│   │   ├── java/pl/piomin/services/
│   │   │   ├── Application.java
│   │   │   ├── config/
│   │   │   │   ├── SecurityConfig.java
│   │   │   │   └── WebConfig.java
│   │   │   ├── controller/
│   │   │   │   ├── PersonController.java
│   │   │   │   └── AuthController.java
│   │   │   ├── model/
│   │   │   │   └── Person.java
│   │   │   ├── repository/
│   │   │   │   └── PersonRepository.java
│   │   │   ├── service/
│   │   │   │   └── PersonService.java
│   │   │   └── security/
│   │   │       ├── JwtTokenProvider.java
│   │   │       ├── JwtAuthenticationFilter.java
│   │   │       └── CustomUserDetailsService.java
│   │   └── resources/
│   │       ├── application.yml
│   │       └── data.sql
│   └── test/
│       └── java/pl/piomin/services/
│           ├── controller/
│           │   └── PersonControllerTest.java
│           └── service/
│               └── PersonServiceTest.java
├── .circleci/
│   └── config.yml
├── kubernetes/
│   ├── deployment.yaml
│   ├── service.yaml
│   └── ingress.yaml
├── skaffold.yaml
├── docker-compose.yml
├── Dockerfile
├── pom.xml
└── README.md
```

## Version History

- 1.0.1 - Initial implementation with REST API, JWT/OAuth2 security, PostgreSQL, Docker, Kubernetes, and CircleCI

## License

This project is licensed under the MIT License.