# Spring Boot 4 Upgrade - Implementation Notes

**Last Updated:** 2026-02-13
**Target Version:** Spring Boot 4.0.2

---

## Key Finding: Jackson 2 Compatibility Module

Spring Boot 4 uses Jackson 3 by default, but provides a **Jackson 2 compatibility module** (`spring-boot-jackson2`) that allows keeping Jackson 2.x API. This is the recommended approach for migration.

### How to Use Jackson 2 Compatibility

Add to main application `build.gradle`:
```groovy
implementation 'org.springframework.boot:spring-boot-jackson2'
```

This provides:
- Jackson 2.x API (`com.fasterxml.jackson.*`) keeps working
- Properties available under `spring.jackson2.*` (instead of `spring.jackson.*`)

---

## Status: IN PROGRESS

### Completed ✓

| Task | Date | Notes |
|------|------|-------|
| Update Spring Boot plugin to 4.0.2 | 2026-02-13 | settings.gradle:4 |
| Update Spring Boot BOM to 4.0.2 | 2026-02-13 | contentgrid-appserver-platform/build.gradle:11 |
| Fix ActuatorConfiguration security | 2026-02-13 | Simplified, removed deprecated EndpointRequest API |
| Identified Jackson 2 compatibility solution | 2026-02-13 | Use spring-boot-jackson2 module |

### Pending 📋

| Task | Notes |
|------|-------|
| Add spring-boot-jackson2 dependency | To contentgrid-appserver-app/build.gradle |
| Verify Jackson imports work | No import changes needed with compatibility module |
| Fix Spring Web MVC changes | Jackson2ObjectMapperBuilderCustomizer, HttpMessageConverters removed |
| Run full build | `-x test` to identify remaining issues |
| Run integration tests | Verify functionality |

---

## Breaking Changes Identified

### Jackson 2 Compatibility ✓ SOLVED
- **Solution:** Add `spring-boot-jackson2` dependency
- **No import changes needed** - keep using `com.fasterxml.jackson.*`
- Properties move from `spring.jackson.*` to `spring.jackson2.*`

### Spring Web MVC
- `Jackson2ObjectMapperBuilderCustomizer` - removed (use `ObjectMapper` bean directly)
- `HttpMessageConverters` - removed (inject `ObjectMapper` directly)
- `WebMvcRegistrations` - removed (define `RequestMappingHandlerMapping` bean directly)

### Actuator Security
- `EndpointRequest.to()` - removed
- `EndpointRequestMatcher` - removed

### Spring HATEOAS
- `HypermediaMediaTypeConfiguration` - removed in Spring 7
- `HalMediaTypeConfiguration` - removed in Spring 7
- Need to review HAL Forms configuration

---

## Next Steps

1. **Add Jackson 2 dependency** to app module
2. Run build to check for remaining issues
3. Fix Spring Web MVC changes as needed
4. Test application startup
