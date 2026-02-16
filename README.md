# Person Service

A Spring Boot REST API application with PostgreSQL database, secured with JWT and OAuth2.

## Version

1.0.1

## Features

- REST API for Person entity management
- PostgreSQL database integration
- JWT and OAuth2 security
- Kubernetes deployment with Skaffold
- Docker Compose for local development

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | /api/persons | Get all persons |
| GET | /api/persons/{id} | Get person by ID |
| GET | /api/persons/pesel/{pesel} | Get person by PESEL |
| POST | /api/persons | Create new person |
| PUT | /api/persons/{id} | Update person |
| DELETE | /api/persons/{id} | Delete person |

## Person Entity Fields

- id (Long) - Unique identifier
- firstName (String) - Person's first name
- lastName (String) - Person's last name
- pesel (String) - Polish national ID (11 digits)
- birthDate (LocalDate) - Date of birth
- email (String) - Email address
- phone (String) - Phone number
- address (String) - Home address

## Running Locally

### With Docker Compose

```bash
docker-compose up --build
```

### Without Docker

1. Start PostgreSQL database
2. Run the application:
```bash
mvn spring-boot:run
```

## Running with Skaffold

```bash
skaffold dev
```

## Configuration

Environment variables:
- `POSTGRES_HOST` - PostgreSQL host (default: localhost)
- `POSTGRES_PORT` - PostgreSQL port (default: 5432)
- `POSTGRES_DB` - Database name (default: persondb)
- `POSTGRES_USER` - Database user (default: postgres)
- `POSTGRES_PASSWORD` - Database password
- `JWT_ISSUER_URI` - JWT issuer URI
- `JWT_JWK_SET_URI` - JWK set URI

## Security

All endpoints are protected with JWT authentication. Include the JWT token in the Authorization header:

```
Authorization: Bearer <token>
```

## Testing

```bash
mvn test
```
