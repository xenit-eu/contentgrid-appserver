# Spring Boot 4 Upgrade Plan

## Target Version: Spring Boot 4.0.2

## Current State

| Component | Current Version | Spring Boot 4 Target |
|-----------|-----------------|---------------------|
| Spring Boot | 3.5.10 | 4.0.2 |
| Java | 21 | 17+ (recommended 21) |
| Gradle | 9.3.1 | 8.14+ or 9.x |

## Repository Structure

This is a multi-module Gradle project with 16 subprojects:
- `contentgrid-appserver-platform` - BOM (Bill of Materials)
- `contentgrid-appserver-app` - Main application
- Various library modules (autoconfigure, rest, domain, contentstore, etc.)

---

## Upgrade Plan

### Phase 1: Update Versions

1. **Update Spring Boot plugin** in `settings.gradle:4`:
   ```groovy
   id 'org.springframework.boot' version '4.0.2'
   ```

2. **Update Spring Boot BOM** in `contentgrid-appserver-platform/build.gradle:11`:
   ```groovy
   api platform('org.springframework.boot:spring-boot-dependencies:4.0.2')
   ```

3. **Verify Gradle wrapper** - Already at 9.3.1 (compatible with Spring Boot 4)

### Phase 2: Jackson 2 Compatibility ⭐ RECOMMENDED

Instead of migrating 140+ files to Jackson 3.x API, use the **Jackson 2 compatibility module**:

Add to `contentgrid-appserver-app/build.gradle`:
```groovy
implementation 'org.springframework.boot:spring-boot-jackson2'
```

Benefits:
- Keep all Jackson imports as `com.fasterxml.jackson.*`
- No code changes needed for Jackson usage
- Properties available under `spring.jackson2.*`

### Phase 3: Dependency & Compatibility Updates

| Area | Status | Notes |
|------|--------|-------|
| Jakarta EE 10 | ✓ Done | Already migrated in SB3 |
| Spring Framework 7 | Pending | Major breaking changes |
| Spring Security 7 | Pending | May need actuator config updates |
| Jackson 2.x | ⭐ Use compatibility module | No import changes needed |

### Phase 4: Build & Test

1. Run `./gradlew build -x test` to identify compilation errors
2. Fix any remaining deprecated API usage
3. Run integration tests
4. Verify actuator endpoints work
5. Check application startup

---

## Breaking Changes to Address

### Jackson 2 Compatibility ⭐ SOLVED
- Use `spring-boot-jackson2` dependency
- No import changes required
- Properties move from `spring.jackson.*` to `spring.jackson2.*`

### Spring Web MVC
- `Jackson2ObjectMapperBuilderCustomizer` - removed
- `HttpMessageConverters` - removed
- `WebMvcRegistrations` - removed

### Actuator Security
- `EndpointRequest.to()` - removed
- `EndpointRequestMatcher` - removed

### Spring HATEOAS
- `HypermediaMediaTypeConfiguration` - removed in Spring 7
- `HalMediaTypeConfiguration` - removed in Spring 7

---

## Verification Checklist

- [ ] Gradle build succeeds (with `-x test`)
- [ ] All tests pass
- [ ] Application starts successfully
- [ ] Actuator endpoints respond
- [ ] No deprecation warnings
