# Performance

Signals to flag in repositories, queries and entity loops. Fixes and examples: `jpa-patterns` (N+1 Problem, Query Best Practices).

- Repository call or lazy collection access inside a loop (N+1)
- `findAll()` without pagination on a large table
- `LIKE` built from user input without escaping `%`, `_` and `\`
- Filter or sort on a column with no index (index in the migration, not only `@Index`)
