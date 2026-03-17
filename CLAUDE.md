# contentgrid-appserver

## Testing

Always run `./gradlew check` before considering work done.

## JOOQ

Use `DSL.name(...)` instead of plain SQL strings for table and field references (e.g. `DSL.field(DSL.name("col"), ...)`
not `DSL.field("col", ...)`). This avoids the need for `@Allow.PlainSQL` annotations.
