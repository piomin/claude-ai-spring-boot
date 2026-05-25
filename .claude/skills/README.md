# Skills

Skills are reusable prompts that teach Claude specific patterns for Java development.

## Structure Convention

Each skill folder contains:

| File | Purpose | Audience |
|------|---------|----------|
| `SKILL.md` | Instructions for Claude | AI (loaded with `view`) 

## Available Skills

### Framework & Data
| Skill | Description |
|-------|-------------|
| [spring-boot](spring-boot/) | Spring Boot 4.x (3.x compatible) - REST, JPA, MongoDB, Security, Testing, Cloud |
| [jpa-patterns](jpa-patterns/) | JPA/Hibernate patterns (N+1, lazy loading, transactions) |
| [mongodb-patterns](mongodb-patterns/) | Spring Data MongoDB patterns (schema design, indexing, aggregation) |
| [logging-patterns](logging-patterns/) | Structured logging (JSON), SLF4J, MDC, AI-friendly formats |

### Code Quality & Design
| Skill | Description |
|-------|-------------|
| [code-quality](code-quality/) | Clean-code review: API contracts, null safety, exception handling, performance |
| [design-patterns](design-patterns/) | Factory, Builder, Strategy, Observer, Decorator, etc. |

## Adding a New Skill

### Before You Start

Validate your skill idea against existing skills:

- [ ] **No significant overlap** - Check the table above for similar skills
- [ ] **Clear level** - Micro (functions) / Meso (classes) / Macro (packages) / Framework / Cross-cutting
- [ ] **Clear type** - Audit (review existing code) or Template (show how to write)
- [ ] **Unique value** - What does it add that doesn't exist?
- [ ] **Focused scope** - Can be applied in one session (<15 checklist items)

### Implementation Steps

1. Create folder: `.claude/skills/<skill-name>/`
2. Create `SKILL.md` with YAML frontmatter (`name`, `description`) and instructions for Claude
3. Update the table above
4. Update the main [README.md](../../README.md)

## Usage

Skills are automatically loaded by Claude Code based on context. You can also invoke them directly:

```bash
# Automatic - Claude detects when to use skills
> "Build a REST API for products"   # Loads spring-boot
> "Why is this query slow?"         # Loads jpa-patterns or mongodb-patterns

# Manual - invoke with slash command
> /spring-boot
> /code-quality
```

## Learn More

- [Claude Code Skills Documentation](https://code.claude.com/docs/en/skills) - Official guide on creating and using skills
