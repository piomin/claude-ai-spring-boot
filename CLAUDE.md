### 1. Plan Mode Default
- Enter plan mode for ANY not-trivial task (3+ steps or architectural decisions)
- Use plan mode for verification steps, not just building
- Write detailed specs upfront to reduce ambiguity

### 2. Self-Improvement Loop
- After ANY correction from the user: update `tasks/lessons.md` with the pattern
- Write rules for yourself that prevent the same mistake
- Ruthlessly iterate on these lessons until the mistake rate drops
- Review lessons at session start for a project

### 3. Verification Before Done
- Never mark a task complete without proving it works
- Diff behavior between main and your changes when relevant
- Ask yourself: "Would a staff engineer approve this?"
- Run tests, check logs, demonstrate correctness

### 4. Demand Elegance (Balanced)
- For non-trivial changes: pause and ask "is there a more elegant way?"
- If a fix feels hacky: "Knowing everything I know now, implement the elegant solution"
- Skip this for simple, obvious fixes. Don't overengineer
- Challenge your own work before presenting it

## Core Principles
- **Simplicity First**: Make every change as simple as possible. Impact minimal code
- **No Laziness**: Find root causes. No temporary fixes. Senior developer standards

## Layered Architecture Rules
- **Always** use a Facade between the Controller and Services.
- **Controller**: dumb — parse request, call facade, return response. No business logic.
- **Facade**: orchestrates multiple services, assembles and converts data into DTOs. Owns the response shape.
- **Service**: single-responsibility domain logic. Knows nothing about DTOs or HTTP.
- Package convention: `controller` → `facade` → `service` → `repository`

## Project General Instructions

- Always use the latest versions of dependencies.
- Always write Java code as the Spring Boot application.
- Always use Gradle for dependency management.
- Always create test cases for the generated code both positive and negative.
## (- Always generate the CircleCI pipeline in the .circleci directory to verify the code.)
- Minimize the amount of code generated.
- The Gradle artifact name must be the same as the parent directory name.
- Use semantic versioning for the Gradle project. Each time you generate a new version, bump the PATCH section of the version number.
- Use `com.active.dot` as the group ID for the Gradle project and base Java package.
- Generate the Docker Compose file to run all components used by the application.
- Update README.md each time you generate a new version.
