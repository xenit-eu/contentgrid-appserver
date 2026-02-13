# Spring Boot 4 Upgrade - Implementation Notes

**Last Updated:** 2026-02-13
**Target Version:** Spring Boot 4.0.2

---

## Key Finding: Jackson 2 Compatibility Module

Spring Boot 4 uses Jackson 3 by default, but provides a **Jackson 2 compatibility module** (`spring-boot-jackson2`) that allows keeping Jackson 2.x API. This is the recommended approach for migration.

### How to Use Jackson 2 Compatibility

Add to modules that use Jackson:
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
| Fix ActuatorConfiguration security | 2026-02-13 | Simplified, using standard Spring Security |
| Identified Jackson 2 compatibility solution | 2026-02-13 | Use spring-boot-jackson2 module |
| Add Jackson 2 dependency | 2026-02-13 | Added to multiple modules |
| Update starter POM names | 2026-02-13 | web → webmvc, oauth2 → security-oauth2 |
| Fix removed Spring Boot auto-config classes | 2026-02-13 | Removed deprecated references |
| Fix HttpMessageConverters | 2026-02-13 | Use ObjectMapper directly |
| Fix WebMvcRegistrations | 2026-02-13 | Define RequestMappingHandlerMapping bean directly |
| Fix Jackson2ObjectMapperBuilderCustomizer | 2026-02-13 | Removed (simplified) |
| Fix AnonymousHttpConfigurer | 2026-02-13 | Removed throws Exception |

### Pending 📋

| Task | Notes |
|------|-------|
| Run full build | ✓ Done - SUCCESS |
| Run integration tests | Verify functionality |
| Fix any remaining deprecation warnings | Various in DynamicDispatchApplicationHandlerMapping |

---

## Breaking Changes Identified

### Jackson 2 Compatibility ✓ SOLVED
- **Solution:** Add `spring-boot-jackson2` dependency
- **No import changes needed** - keep using `com.fasterxml.jackson.*`
- Properties move from `spring.jackson.*` to `spring.jackson2.*`

### Spring Web MVC
- `Jackson2ObjectMapperBuilderCustomizer` - removed (simplified)
- `HttpMessageConverters` - replaced with direct ObjectMapper injection
- `WebMvcRegistrations` - replaced with direct RequestMappingHandlerMapping bean

### Actuator Security ✓ FIXED
- `EndpointRequest.to()` - removed → Use standard Spring Security requestMatchers
- `EndpointRequestMatcher` - removed → Use AntPathRequestMatcher

### Spring HATEOAS
- `HypermediaMediaTypeConfiguration` - removed in Spring 7 → Simplified configuration
- `HalMediaTypeConfiguration.configureObjectMapper()` - changed → Simplified

### Spring Security 7
- `init(HttpSecurity)` no longer throws Exception
- Removed deprecated method signatures

### Starter POMs ✓ UPDATED
- `spring-boot-starter-web` → `spring-boot-starter-webmvc`
- `spring-boot-starter-oauth2-resource-server` → `spring-boot-starter-security-oauth2-resource-server`

### Testcontainers ✓ ADDED
- Added explicit testcontainers versions to platform BOM

---

## Files Modified

### Build Files
- `settings.gradle` - Spring Boot 4.0.2
- `contentgrid-appserver-platform/build.gradle` - Spring Boot BOM 4.0.2, testcontainers versions
- `contentgrid-appserver-spring-boot-starter/build.gradle` - webmvc, jackson2, security-oauth2
- `contentgrid-appserver-rest/build.gradle` - webmvc, jackson2
- `contentgrid-appserver-autoconfigure/build.gradle` - webmvc, jackson2
- `contentgrid-appserver-events/build.gradle` - jackson2
- `contentgrid-appserver-actuators/build.gradle` - webmvc
- `contentgrid-appserver-integration-test/build.gradle` - webmvc
- `contentgrid-appserver-query-engine-impl-jooq/build.gradle` - testcontainers

### Source Files
- `contentgrid-appserver-actuators/.../ActuatorConfiguration.java` - Rewrote security
- `contentgrid-appserver-rest/.../ContentGridRestConfiguration.java` - Removed Jackson2ObjectMapperBuilderCustomizer
- `contentgrid-appserver-rest/.../ContentGridRestFormatterConfiguration.java` - Use ObjectMapper directly
- `contentgrid-appserver-rest/.../HalFormsMediaTypeConfiguration.java` - Simplified
- `contentgrid-appserver-rest/.../ContentGridHandlerMappingConfiguration.java` - Direct bean
- `contentgrid-appserver-rest/.../ContentRestController.java` - Fixed HttpHeaders
- `contentgrid-appserver-autoconfigure/.../JOOQQueryEngineAutoConfiguration.java` - Removed deprecated refs
- `contentgrid-appserver-autoconfigure/.../DefaultSecurityAutoConfiguration.java` - Removed deprecated refs
- `contentgrid-appserver-autoconfigure/.../FlywayPostgresAutoConfiguration.java` - Simplified
- `contentgrid-appserver-autoconfigure/.../AnonymousHttpConfigurer.java` - Fixed init signature

---

## Next Steps

1. Run integration tests
2. Verify application starts correctly
3. Check actuator endpoints
4. Fix any remaining deprecation warnings
