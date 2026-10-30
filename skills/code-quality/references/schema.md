# Database Schema

Applies to relational databases, when the diff touches entities, migrations or `spring.jpa.*` / Flyway configuration.

- `spring.jpa.hibernate.ddl-auto: update|create|create-drop` outside test configuration → Flyway migrations + `validate`
- Modified `V<n>__*.sql` that was already applied (checksum mismatch, startup failure) → add a new migration instead
- Entity change without a matching migration in the same change
- New `NOT NULL` column without `DEFAULT` or backfill
- Unnamed constraints/indexes, or foreign key columns without an index
- Built for a UI: `ON DELETE CASCADE` on a foreign key between independent resources (deleting a referenced record returns 409 instead)
- Flyway dependency in a project with no relational database (unnecessary)
