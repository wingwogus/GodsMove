# Backend Kakao OIDC Member Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Initialize `backend` from the Spring Boot Kotlin template and adapt it to PostgreSQL, UUID `Member` identity, singular tables, and Kakao OIDC login.

**Architecture:** Copy the existing multi-module template into `backend`, then make focused adaptation passes: package/project metadata, PostgreSQL runtime config, UUID member identity, `memberId` auth naming, MDC logging, and tests. Keep Kakao provider identity in `ExternalIdentity.providerSubject` and service identity in `Member.id`.

**Tech Stack:** Kotlin 1.9.25, Spring Boot 3.5.4, Gradle multi-module, Spring Security, JPA/Hibernate 6, PostgreSQL JDBC, Redis, JJWT, Kakao OIDC/JWKS verification.

---

## File Structure

The implementation creates a self-contained backend under `/Users/wingwogus/Projects/ChamChamCham/backend`.

Created by copying the template:

- `backend/settings.gradle.kts`: Gradle root name and module includes.
- `backend/build.gradle.kts`: root Gradle plugin versions, group, Kotlin compiler config.
- `backend/gradlew`, `backend/gradlew.bat`, `backend/gradle/wrapper/*`: Gradle wrapper.
- `backend/api`: Spring Boot HTTP/API module.
- `backend/application`: application service/security/Redis module.
- `backend/domain`: JPA domain module.
- `backend/batch`: batch skeleton module.

Key files to modify after copy:

- `backend/settings.gradle.kts`: set `rootProject.name = "chamchamcham-backend"`.
- `backend/build.gradle.kts`, `backend/api/build.gradle.kts`, `backend/application/build.gradle.kts`, `backend/domain/build.gradle.kts`, `backend/batch/build.gradle.kts`: set `group = "com.chamchamcham"` and switch DB dependencies.
- `backend/api/src/main/resources/application.yml`: change log pattern from `userId` to `memberId`.
- `backend/api/src/main/resources/application-local.yml`: use PostgreSQL datasource.
- `backend/api/src/main/resources/application-dev.yml`: use PostgreSQL datasource.
- `backend/api/src/main/resources/application-prod.yml`: use PostgreSQL datasource.
- `backend/api/src/test/resources/application-test.yml`: add H2 PostgreSQL-mode test datasource so tests do not require a running local DB.
- `backend/domain/src/main/kotlin/com/chamchamcham/domain/member/Member.kt`: UUID ID and table name `member`.
- `backend/domain/src/main/kotlin/com/chamchamcham/domain/member/ExternalIdentity.kt`: UUID ID and `member_id` FK.
- `backend/domain/src/main/kotlin/com/chamchamcham/domain/member/MemberRepository.kt`: repository key type `UUID`.
- `backend/domain/src/main/kotlin/com/chamchamcham/domain/member/ExternalIdentityRepository.kt`: repository key type `UUID`.
- `backend/application/src/main/kotlin/com/chamchamcham/application/security/TokenProvider.kt`: `memberId: UUID` token subject API.
- `backend/application/src/main/kotlin/com/chamchamcham/application/redis/RefreshTokenRepository.kt`: `memberId: UUID` API.
- `backend/application/src/main/kotlin/com/chamchamcham/application/redis/RefreshTokenRedisRepository.kt`: Redis key from UUID member ID.
- `backend/application/src/main/kotlin/com/chamchamcham/application/auth/AuthService.kt`: reissue/logout use UUID `memberId`.
- `backend/application/src/main/kotlin/com/chamchamcham/application/auth/KakaoLoginService.kt`: issue tokens with persisted UUID member ID.
- `backend/application/src/main/kotlin/com/chamchamcham/application/exception/ErrorCode.kt`: `MEMBER_NOT_FOUND`.
- `backend/application/src/main/kotlin/com/chamchamcham/application/common/LoggingUtil.kt`: MDC message uses `memberId`.
- `backend/api/src/main/kotlin/com/chamchamcham/config/MDCLoggingFilter.kt`: MDC key `memberId`.
- `backend/api/src/main/kotlin/com/chamchamcham/api/auth/controller/AuthController.kt`: principal variable `memberId`.
- `backend/api/src/main/kotlin/com/chamchamcham/api/controller/TestController.kt`: response text says member ID.
- Existing tests under `backend/api/src/test/kotlin/com/chamchamcham` and `backend/application/src/test/kotlin/com/chamchamcham`: update imports, UUID values, names, and assertions.

## Task 1: Copy Template Into Backend

**Files:**

- Create: `backend/`
- Source: `/Users/wingwogus/IdeaProjects/springboot-kotlin-initial-template/`

- [ ] **Step 1: Copy current template source files and exclude generated artifacts**

Run:

```bash
rsync -a \
  --exclude '.git' \
  --exclude '.gradle' \
  --exclude '.idea' \
  --exclude '.omx' \
  --exclude '.worktrees' \
  --exclude 'build' \
  --exclude '.DS_Store' \
  /Users/wingwogus/IdeaProjects/springboot-kotlin-initial-template/ \
  backend/
```

Expected: `backend/gradlew`, `backend/settings.gradle.kts`, `backend/api`, `backend/application`, `backend/domain`, and `backend/batch` exist.

- [ ] **Step 2: Verify generated artifacts were not copied**

Run:

```bash
find backend -maxdepth 3 \( -name build -o -name .gradle -o -name .git -o -name .idea -o -name .omx -o -name .worktrees -o -name .DS_Store \) -print
```

Expected: no output.

- [ ] **Step 3: Commit the clean template import**

Run:

```bash
git add backend
git commit \
  -m "Import backend template as the implementation base" \
  -m "The backend starts from the existing Spring Boot Kotlin multi-module template so the project inherits working auth, security, Redis, exception, and test structure before domain-specific adaptation." \
  -m "Constraint: Template source is /Users/wingwogus/IdeaProjects/springboot-kotlin-initial-template" \
  -m "Confidence: high" \
  -m "Scope-risk: moderate" \
  -m "Directive: Keep this commit as a clean import baseline; put project-specific rewrites in follow-up commits" \
  -m "Tested: Verified generated template artifacts were excluded" \
  -m "Not-tested: Build not run before project-specific package and dependency adaptation"
```

Expected: a commit containing only copied backend template source files and no generated or local artifacts.

## Task 2: Rename Project Package And Metadata

**Files:**

- Modify: `backend/settings.gradle.kts`
- Modify: `backend/build.gradle.kts`
- Modify: `backend/api/build.gradle.kts`
- Modify: `backend/application/build.gradle.kts`
- Modify: `backend/domain/build.gradle.kts`
- Modify: `backend/batch/build.gradle.kts`
- Move package directories under `backend/*/src/{main,test}/kotlin/com/example` to `backend/*/src/{main,test}/kotlin/com/chamchamcham`

- [ ] **Step 1: Move Kotlin package directories**

Run:

```bash
mkdir -p backend/api/src/main/kotlin/com/chamchamcham
mkdir -p backend/api/src/test/kotlin/com/chamchamcham
mkdir -p backend/application/src/main/kotlin/com/chamchamcham
mkdir -p backend/application/src/test/kotlin/com/chamchamcham
mkdir -p backend/domain/src/main/kotlin/com/chamchamcham
mkdir -p backend/domain/src/test/kotlin/com/chamchamcham
mkdir -p backend/batch/src/main/kotlin/com/chamchamcham
cp -R backend/api/src/main/kotlin/com/example/. backend/api/src/main/kotlin/com/chamchamcham/
cp -R backend/api/src/test/kotlin/com/example/. backend/api/src/test/kotlin/com/chamchamcham/
cp -R backend/application/src/main/kotlin/com/example/. backend/application/src/main/kotlin/com/chamchamcham/
cp -R backend/application/src/test/kotlin/com/example/. backend/application/src/test/kotlin/com/chamchamcham/
cp -R backend/domain/src/main/kotlin/com/example/. backend/domain/src/main/kotlin/com/chamchamcham/
cp -R backend/domain/src/test/kotlin/com/example/. backend/domain/src/test/kotlin/com/chamchamcham/
cp -R backend/batch/src/main/kotlin/com/example/. backend/batch/src/main/kotlin/com/chamchamcham/
```

Expected: new `com/chamchamcham` directories contain the copied Kotlin files.

- [ ] **Step 2: Remove old package directories after verifying the copy**

Run:

```bash
find backend -path '*/src/*/kotlin/com/chamchamcham' -type d -print
rm -rf backend/api/src/main/kotlin/com/example
rm -rf backend/api/src/test/kotlin/com/example
rm -rf backend/application/src/main/kotlin/com/example
rm -rf backend/application/src/test/kotlin/com/example
rm -rf backend/domain/src/main/kotlin/com/example
rm -rf backend/domain/src/test/kotlin/com/example
rm -rf backend/batch/src/main/kotlin/com/example
```

Expected: no `backend/*/src/*/kotlin/com/example` directory remains.

- [ ] **Step 3: Replace package and import declarations**

Run:

```bash
perl -pi -e 's/com\.example/com.chamchamcham/g' $(find backend -name '*.kt' -o -name '*.kts')
```

Expected: `rg -n "com\\.example|com/example" backend` has no output.

- [ ] **Step 4: Update Gradle project metadata**

Change `backend/settings.gradle.kts` to:

```kotlin
rootProject.name = "chamchamcham-backend"

include("api", "application", "domain", "batch")
```

Set `group = "com.chamchamcham"` in:

```kotlin
// backend/build.gradle.kts
group = "com.chamchamcham"
```

```kotlin
// backend/api/build.gradle.kts
group = "com.chamchamcham"
```

```kotlin
// backend/application/build.gradle.kts
group = "com.chamchamcham"
```

```kotlin
// backend/domain/build.gradle.kts
group = "com.chamchamcham"
```

```kotlin
// backend/batch/build.gradle.kts
group = "com.chamchamcham"
```

- [ ] **Step 5: Run package rename verification**

Run:

```bash
rg -n "com\\.example|com/example|rootProject.name = \"example\"|group = \"com.example\"" backend
```

Expected: no output.

- [ ] **Step 6: Commit package rename**

Run:

```bash
git add backend
git commit \
  -m "Align backend package identity with ChamChamCham" \
  -m "The imported template used placeholder package and Gradle metadata. Renaming the package root before domain edits prevents new code from depending on template identity." \
  -m "Constraint: Default approved package root is com.chamchamcham" \
  -m "Confidence: high" \
  -m "Scope-risk: narrow" \
  -m "Tested: rg scan for com.example and example project metadata" \
  -m "Not-tested: Build deferred until database and UUID changes are applied"
```

Expected: package and Gradle metadata changes are committed.

## Task 3: Switch Runtime Database Configuration To PostgreSQL

**Files:**

- Modify: `backend/domain/build.gradle.kts`
- Modify: `backend/api/build.gradle.kts`
- Modify: `backend/api/src/main/resources/application-local.yml`
- Modify: `backend/api/src/main/resources/application-dev.yml`
- Modify: `backend/api/src/main/resources/application-prod.yml`
- Create: `backend/api/src/test/resources/application-test.yml`

- [ ] **Step 1: Change domain JDBC dependency**

In `backend/domain/build.gradle.kts`, replace:

```kotlin
implementation("com.mysql:mysql-connector-j:8.0.33")
```

with:

```kotlin
runtimeOnly("org.postgresql:postgresql")
```

- [ ] **Step 2: Move H2 to test scope in API module**

In `backend/api/build.gradle.kts`, replace:

```kotlin
implementation("com.h2database:h2")
```

with:

```kotlin
testRuntimeOnly("com.h2database:h2")
```

Rationale: runtime profiles use PostgreSQL; controller/security tests use H2 only under the `test` profile.

- [ ] **Step 3: Set local PostgreSQL datasource**

In `backend/api/src/main/resources/application-local.yml`, replace the datasource block with:

```yaml
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/chamchamcham_local}
    username: ${SPRING_DATASOURCE_USERNAME:chamchamcham}
    password: ${SPRING_DATASOURCE_PASSWORD:chamchamcham}
    driver-class-name: org.postgresql.Driver
```

Keep local JPA settings:

```yaml
  jpa:
    hibernate:
      ddl-auto: create
    properties:
      hibernate:
        format_sql: true
        show_sql: true
    open-in-view: false
```

- [ ] **Step 4: Set dev and prod PostgreSQL datasource**

In both `backend/api/src/main/resources/application-dev.yml` and `backend/api/src/main/resources/application-prod.yml`, use this datasource shape:

```yaml
  datasource:
    url: ${SPRING_DATASOURCE_URL}
    username: ${SPRING_DATASOURCE_USERNAME}
    password: ${SPRING_DATASOURCE_PASSWORD}
    driver-class-name: org.postgresql.Driver
```

Keep dev/prod JPA safe:

```yaml
  jpa:
    hibernate:
      ddl-auto: none
    properties:
      hibernate:
        format_sql: true
        show_sql: true
    open-in-view: false
```

- [ ] **Step 5: Add test profile datasource**

Create `backend/api/src/test/resources/application-test.yml`:

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH
    username: sa
    password:
    driver-class-name: org.h2.Driver

  jpa:
    hibernate:
      ddl-auto: create-drop
    properties:
      hibernate:
        format_sql: false
        show_sql: false
    open-in-view: false

  mail:
    host: localhost
    port: 2525
    username: test@example.com
    password: test-password
    auth-code-expiration-millis: 1800000
    verified-state-expiration-millis: 1800000

jwt:
  secret: t2oRk29vBQZWS8GEt4xr8AJznlPK0ipBKUwdyqe10SOGZB26vVBMjzqualdJsjcOY1wX9DOqJC9V1DFl58F0tQ==

redis:
  host: localhost
  port: 6379

app:
  mail:
    from-name: ChamChamCham

auth:
  kakao:
    oidc:
      issuer: https://kauth.kakao.com
      audience: test-kakao-native-app-key
      discovery-uri: https://kauth.kakao.com/.well-known/openid-configuration
      allowed-clock-skew-seconds: 60
      nonce-replay-ttl-seconds: 600
```

- [ ] **Step 6: Switch Spring Boot integration tests to test profile**

In `backend/api/src/test/kotlin/com/chamchamcham/api/security/AuthSecurityIntegrationTest.kt`, replace:

```kotlin
@ActiveProfiles("local")
```

with:

```kotlin
@ActiveProfiles("test")
```

- [ ] **Step 7: Verify database dependency references**

Run:

```bash
rg -n "mysql|MySQL|com.mysql|jdbc:mysql|org.h2.Driver|jdbc:h2" backend
```

Expected:

- no MySQL results.
- H2 results only in `backend/api/src/test/resources/application-test.yml`.

- [ ] **Step 8: Commit PostgreSQL configuration**

Run:

```bash
git add backend
git commit \
  -m "Use PostgreSQL for backend runtime persistence" \
  -m "The service stores UUID member IDs in PostgreSQL, so runtime profiles should use the PostgreSQL driver and datasource shape from the start. H2 remains test-scoped for web/security tests until a Testcontainers pass is introduced." \
  -m "Constraint: User selected PostgreSQL for the backend database" \
  -m "Rejected: Keep MySQL connector from template | conflicts with target database" \
  -m "Confidence: high" \
  -m "Scope-risk: moderate" \
  -m "Directive: Do not reintroduce runtime H2/MySQL configuration without an explicit database strategy change" \
  -m "Tested: rg scan for MySQL and runtime H2 references" \
  -m "Not-tested: Local PostgreSQL connection not required for config-only pass"
```

Expected: PostgreSQL runtime config is committed.

## Task 4: Convert Member Persistence To UUID And Singular Tables

**Files:**

- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/member/Member.kt`
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/member/ExternalIdentity.kt`
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/member/MemberRepository.kt`
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/member/ExternalIdentityRepository.kt`
- Test: `backend/domain/src/test/kotlin/com/chamchamcham/domain/member/MemberMappingTest.kt`

- [ ] **Step 1: Add a mapping test for UUID IDs and table names**

Create `backend/domain/src/test/kotlin/com/chamchamcham/domain/member/MemberMappingTest.kt`:

```kotlin
package com.chamchamcham.domain.member

import jakarta.persistence.Table
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID
import kotlin.reflect.full.findAnnotation

class MemberMappingTest {

    @Test
    fun `member uses singular table name`() {
        val table = Member::class.findAnnotation<Table>()

        assertEquals("member", table?.name)
    }

    @Test
    fun `external identity uses singular table name`() {
        val table = ExternalIdentity::class.findAnnotation<Table>()

        assertEquals("external_identity", table?.name)
    }

    @Test
    fun `repositories use UUID identifiers`() {
        val memberRepositoryType = MemberRepository::class.java.genericInterfaces.single().typeName
        val externalIdentityRepositoryType = ExternalIdentityRepository::class.java.genericInterfaces.single().typeName

        assertEquals(
            "org.springframework.data.jpa.repository.JpaRepository<com.chamchamcham.domain.member.Member, java.util.UUID>",
            memberRepositoryType
        )
        assertEquals(
            "org.springframework.data.jpa.repository.JpaRepository<com.chamchamcham.domain.member.ExternalIdentity, java.util.UUID>",
            externalIdentityRepositoryType
        )
        assertEquals(UUID::class.java.name, UUID.randomUUID()::class.java.name)
        assertEquals(JpaRepository::class.java.name, JpaRepository::class.java.name)
    }
}
```

- [ ] **Step 2: Run the mapping test and confirm it fails**

Run:

```bash
cd backend
./gradlew :domain:test --tests com.chamchamcham.domain.member.MemberMappingTest
```

Expected: FAIL because `Member` lacks `@Table(name = "member")` and repositories still use `Long`.

- [ ] **Step 3: Update `Member.kt`**

Replace `backend/domain/src/main/kotlin/com/chamchamcham/domain/member/Member.kt` with:

```kotlin
package com.chamchamcham.domain.member

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "member")
class Member(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @Column(nullable = false, unique = true)
    val email: String,

    @Column(nullable = true)
    val passwordHash: String?,

    @Column(nullable = false)
    val role: String = "ROLE_USER",
)
```

- [ ] **Step 4: Update `ExternalIdentity.kt`**

Replace `backend/domain/src/main/kotlin/com/chamchamcham/domain/member/ExternalIdentity.kt` with:

```kotlin
package com.chamchamcham.domain.member

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.util.UUID

@Entity
@Table(
    name = "external_identity",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_external_identity_provider_subject",
            columnNames = ["provider", "provider_subject"]
        )
    ]
)
class ExternalIdentity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    val member: Member,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    val provider: AuthProvider,

    @Column(name = "provider_subject", nullable = false, length = 128)
    val providerSubject: String,

    @Column(name = "email_at_link_time", nullable = false)
    val emailAtLinkTime: String
)
```

- [ ] **Step 5: Update repositories to UUID**

Set `backend/domain/src/main/kotlin/com/chamchamcham/domain/member/MemberRepository.kt` to:

```kotlin
package com.chamchamcham.domain.member

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MemberRepository : JpaRepository<Member, UUID> {
    fun findByEmail(email: String): Member?
    fun existsByEmail(email: String): Boolean
}
```

Set `backend/domain/src/main/kotlin/com/chamchamcham/domain/member/ExternalIdentityRepository.kt` to:

```kotlin
package com.chamchamcham.domain.member

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ExternalIdentityRepository : JpaRepository<ExternalIdentity, UUID> {
    fun findByProviderAndProviderSubject(
        provider: AuthProvider,
        providerSubject: String
    ): ExternalIdentity?
}
```

- [ ] **Step 6: Run domain tests**

Run:

```bash
cd backend
./gradlew :domain:test
```

Expected: PASS.

- [ ] **Step 7: Commit UUID persistence**

Run:

```bash
git add backend/domain
git commit \
  -m "Store member identity as PostgreSQL UUIDs" \
  -m "The service account identifier is a Member UUID, not the template Long ID. Updating entities and repositories first gives the auth layer a stable persistence contract to target." \
  -m "Constraint: Member IDs must be UUIDs and table names singular" \
  -m "Rejected: Keep Long IDs and adapt only API names | would keep the wrong identity shape below the service boundary" \
  -m "Confidence: high" \
  -m "Scope-risk: moderate" \
  -m "Directive: Keep Kakao providerSubject separate from Member.id" \
  -m "Tested: ./gradlew :domain:test" \
  -m "Not-tested: PostgreSQL DDL generation against a live database"
```

Expected: domain UUID changes are committed.

## Task 5: Convert Token And Refresh APIs To `memberId`

**Files:**

- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/security/TokenProvider.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/redis/RefreshTokenRepository.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/redis/RefreshTokenRedisRepository.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/security/TokenProviderTest.kt`

- [ ] **Step 1: Replace token provider tests with UUID/memberId expectations**

Replace `backend/application/src/test/kotlin/com/chamchamcham/application/security/TokenProviderTest.kt` with:

```kotlin
package com.chamchamcham.application.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class TokenProviderTest {

    private val tokenProvider = TokenProvider(
        "t2oRk29vBQZWS8GEt4xr8AJznlPK0ipBKUwdyqe10SOGZB26vVBMjzqualdJsjcOY1wX9DOqJC9V1DFl58F0tQ=="
    )

    @Test
    fun `generateToken embeds member id and role in access token`() {
        val memberId = UUID.fromString("00000000-0000-0000-0000-000000000007")
        val tokenPair = tokenProvider.generateToken(memberId, "ROLE_USER")

        assertTrue(tokenProvider.validateToken(tokenPair.accessToken))
        assertTrue(tokenProvider.isAccessToken(tokenPair.accessToken))
        assertEquals(memberId, tokenProvider.getMemberId(tokenPair.accessToken))
        assertEquals("ROLE_USER", tokenProvider.getRole(tokenPair.accessToken))
    }

    @Test
    fun `refresh token is bound to the member id`() {
        val memberId = UUID.fromString("00000000-0000-0000-0000-000000000009")
        val refreshToken = tokenProvider.createRefreshToken(memberId)
        val authentication = tokenProvider.getAuthentication(
            tokenProvider.createAccessToken(memberId, "ROLE_ADMIN")
        )

        assertTrue(tokenProvider.validateToken(refreshToken))
        assertTrue(tokenProvider.isRefreshToken(refreshToken))
        assertEquals(memberId, tokenProvider.getMemberId(refreshToken))
        assertEquals(memberId.toString(), authentication.principal)
        assertEquals("ROLE_ADMIN", authentication.authorities.first().authority)
    }
}
```

- [ ] **Step 2: Run the token test and confirm it fails**

Run:

```bash
cd backend
./gradlew :application:test --tests com.chamchamcham.application.security.TokenProviderTest
```

Expected: FAIL because `generateToken`, `createRefreshToken`, and `getMemberId` do not yet use UUID/memberId.

- [ ] **Step 3: Replace `TokenProvider.kt`**

Replace `backend/application/src/main/kotlin/com/chamchamcham/application/security/TokenProvider.kt` with:

```kotlin
package com.chamchamcham.application.security

import com.chamchamcham.application.auth.AuthResult
import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.stereotype.Component
import java.util.Base64
import java.util.Date
import java.util.UUID

@Component
class TokenProvider(
    @Value("\${jwt.secret}") secretKey: String
) {

    companion object {
        private const val ACCESS_TOKEN_VALIDITY =
            1000L * 60 * 60 * 24 * 7
        private const val REFRESH_TOKEN_VALIDITY =
            1000L * 60 * 60 * 24 * 14
        private const val ROLE_CLAIM = "role"
        private const val TOKEN_TYPE_CLAIM = "tokenType"
        private const val ACCESS_TOKEN_TYPE = "access"
        private const val REFRESH_TOKEN_TYPE = "refresh"
    }

    private val decodedSecretKey = Base64.getDecoder().decode(secretKey)

    init {
        require(decodedSecretKey.size >= 64) {
            "jwt.secret is too short for HS512: ${decodedSecretKey.size * 8} bits. " +
                "Set JWT secret to a Base64 value that decodes to at least 64 bytes."
        }
    }

    private val key = Keys.hmacShaKeyFor(decodedSecretKey)

    fun createAccessToken(memberId: UUID, role: String): String {
        val now = Date()

        return Jwts.builder()
            .setSubject(memberId.toString())
            .claim(ROLE_CLAIM, role)
            .claim(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE)
            .setIssuedAt(now)
            .setExpiration(Date(now.time + ACCESS_TOKEN_VALIDITY))
            .signWith(key, SignatureAlgorithm.HS512)
            .compact()
    }

    fun createRefreshToken(memberId: UUID): String {
        val now = Date()

        return Jwts.builder()
            .setSubject(memberId.toString())
            .claim(TOKEN_TYPE_CLAIM, REFRESH_TOKEN_TYPE)
            .setIssuedAt(now)
            .setExpiration(Date(now.time + REFRESH_TOKEN_VALIDITY))
            .signWith(key, SignatureAlgorithm.HS512)
            .compact()
    }

    fun generateToken(memberId: UUID, role: String): AuthResult.TokenPair {
        return AuthResult.TokenPair(
            accessToken = createAccessToken(memberId, role),
            refreshToken = createRefreshToken(memberId)
        )
    }

    fun getRefreshTokenValiditySeconds(): Long = REFRESH_TOKEN_VALIDITY / 1000

    fun validateToken(token: String): Boolean {
        return try {
            Jwts.parserBuilder().setSigningKey(key).build()
                .parseClaimsJws(token)
            true
        } catch (e: JwtException) {
            false
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    fun parseClaims(token: String): Claims {
        return try {
            Jwts.parserBuilder().setSigningKey(key).build()
                .parseClaimsJws(token).body
        } catch (e: ExpiredJwtException) {
            e.claims
        }
    }

    fun getMemberId(token: String): UUID {
        return UUID.fromString(parseClaims(token).subject)
    }

    fun getRole(token: String): String? {
        return parseClaims(token)[ROLE_CLAIM] as? String
    }

    fun isAccessToken(token: String): Boolean {
        return parseClaims(token)[TOKEN_TYPE_CLAIM] == ACCESS_TOKEN_TYPE
    }

    fun isRefreshToken(token: String): Boolean {
        return parseClaims(token)[TOKEN_TYPE_CLAIM] == REFRESH_TOKEN_TYPE
    }

    fun getAuthentication(token: String): Authentication {
        val memberId = getMemberId(token)
        val role = getRole(token)
        val authorities = role?.let { listOf(SimpleGrantedAuthority(it)) } ?: emptyList()
        val principal = memberId.toString()

        return UsernamePasswordAuthenticationToken(
            principal,
            null,
            authorities
        )
    }
}
```

- [ ] **Step 4: Replace refresh token repository interfaces**

Set `backend/application/src/main/kotlin/com/chamchamcham/application/redis/RefreshTokenRepository.kt` to:

```kotlin
package com.chamchamcham.application.redis

import java.util.UUID

interface RefreshTokenRepository {
    fun save(memberId: UUID, refreshToken: String, expiresInSeconds: Long)

    fun get(memberId: UUID): String?

    fun delete(memberId: UUID)
}
```

Set `backend/application/src/main/kotlin/com/chamchamcham/application/redis/RefreshTokenRedisRepository.kt` to:

```kotlin
package com.chamchamcham.application.redis

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository
import java.util.UUID
import java.util.concurrent.TimeUnit

@Repository
class RefreshTokenRedisRepository(
    private val redis: StringRedisTemplate
) : RefreshTokenRepository {

    companion object {
        private const val PREFIX = "refresh:"
    }

    override fun save(
        memberId: UUID,
        refreshToken: String,
        expiresInSeconds: Long
    ) {
        redis.opsForValue().set(
            PREFIX + memberId,
            refreshToken,
            expiresInSeconds,
            TimeUnit.SECONDS
        )
    }

    override fun get(memberId: UUID): String? {
        return redis.opsForValue().get(PREFIX + memberId)
    }

    override fun delete(memberId: UUID) {
        redis.delete(PREFIX + memberId)
    }
}
```

- [ ] **Step 5: Run application token tests**

Run:

```bash
cd backend
./gradlew :application:test --tests com.chamchamcham.application.security.TokenProviderTest
```

Expected: PASS for `TokenProviderTest`.

- [ ] **Step 6: Commit token and refresh API rename**

Run:

```bash
git add backend/application
git commit \
  -m "Make JWT and refresh token APIs memberId-based" \
  -m "The authentication boundary should expose service Member UUIDs, not template userId Longs. Token subjects and Redis refresh keys now share the same memberId contract." \
  -m "Constraint: JWT subject stores the service Member UUID string" \
  -m "Rejected: Add a separate memberId JWT claim | subject already represents the authenticated principal" \
  -m "Confidence: high" \
  -m "Scope-risk: moderate" \
  -m "Directive: Keep TokenProvider method names memberId-based" \
  -m "Tested: ./gradlew :application:test --tests com.chamchamcham.application.security.TokenProviderTest" \
  -m "Not-tested: Full application tests pending service-layer UUID adaptation"
```

Expected: token/Redis API changes are committed.

## Task 6: Adapt Auth Services To UUID `Member.id`

**Files:**

- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/auth/AuthService.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/auth/KakaoLoginService.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/exception/ErrorCode.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/auth/AuthServiceTest.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/auth/KakaoLoginServiceTest.kt`

- [ ] **Step 1: Update application auth tests to use UUIDs**

In `AuthServiceTest.kt` and `KakaoLoginServiceTest.kt`:

```kotlin
import java.util.UUID
```

Replace hard-coded Long IDs with deterministic UUIDs:

```kotlin
private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
```

Replace token provider stubs:

```kotlin
`when`(tokenProvider.getMemberId("refresh-token")).thenReturn(memberId)
```

Replace repository lookups:

```kotlin
`when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))
```

Replace `ExternalIdentity(1L, member, ...)` with:

```kotlin
ExternalIdentity(
    id = UUID.fromString("00000000-0000-0000-0000-000000000101"),
    member = member,
    provider = AuthProvider.KAKAO,
    providerSubject = "kakao-sub",
    emailAtLinkTime = "user@example.com"
)
```

- [ ] **Step 2: Run auth service tests and confirm they fail**

Run:

```bash
cd backend
./gradlew :application:test --tests com.chamchamcham.application.auth.AuthServiceTest --tests com.chamchamcham.application.auth.KakaoLoginServiceTest
```

Expected: FAIL because services still call `getUserId`, pass Long IDs, and reference `USER_NOT_FOUND`.

- [ ] **Step 3: Rename `USER_NOT_FOUND` error**

In `backend/application/src/main/kotlin/com/chamchamcham/application/exception/ErrorCode.kt`, replace:

```kotlin
USER_NOT_FOUND("AUTH_006", "error.user_not_found", 404),
```

with:

```kotlin
MEMBER_NOT_FOUND("AUTH_006", "error.member_not_found", 404),
```

- [ ] **Step 4: Add persisted member ID helper in `AuthService.kt`**

In `AuthService.kt`, import UUID:

```kotlin
import java.util.UUID
```

Add a private helper inside the class:

```kotlin
private fun persistedMemberId(member: Member): UUID {
    return member.id ?: throw BusinessException(ErrorCode.MEMBER_NOT_FOUND)
}
```

Update `reissue`:

```kotlin
val memberId = tokenProvider.getMemberId(command.refreshToken)
val storedRefreshToken = refreshTokenRepository.get(memberId)
    ?: throw BusinessException(ErrorCode.UNAUTHORIZED)

if (storedRefreshToken != command.refreshToken) {
    throw BusinessException(ErrorCode.UNAUTHORIZED)
}

val member = memberRepository.findById(memberId).orElseThrow {
    BusinessException(ErrorCode.MEMBER_NOT_FOUND)
}
```

Update `logout` signature and body:

```kotlin
fun logout(memberId: String) {
    val parsedMemberId = runCatching { UUID.fromString(memberId) }
        .getOrElse { throw BusinessException(ErrorCode.UNAUTHORIZED) }

    if (refreshTokenRepository.get(parsedMemberId) == null) {
        throw BusinessException(ErrorCode.ALREADY_LOGGED_OUT)
    }

    refreshTokenRepository.delete(parsedMemberId)
    logger.info("Member logged out. memberHash={}", memberId.hashCode())
}
```

Update `issueAndStoreTokens`:

```kotlin
private fun issueAndStoreTokens(member: Member): AuthResult.TokenPair {
    val memberId = persistedMemberId(member)
    val tokenPair = tokenProvider.generateToken(memberId, member.role)
    refreshTokenRepository.save(
        memberId,
        tokenPair.refreshToken,
        tokenProvider.getRefreshTokenValiditySeconds()
    )
    return tokenPair
}
```

- [ ] **Step 5: Add persisted member ID helper in `KakaoLoginService.kt`**

In `KakaoLoginService.kt`, import UUID:

```kotlin
import java.util.UUID
```

Add:

```kotlin
private fun persistedMemberId(member: Member): UUID {
    return member.id ?: throw BusinessException(ErrorCode.MEMBER_NOT_FOUND)
}
```

Update `issueAndStoreTokens`:

```kotlin
private fun issueAndStoreTokens(member: Member): AuthResult.TokenPair {
    val memberId = persistedMemberId(member)
    val tokenPair = tokenProvider.generateToken(memberId, member.role)
    refreshTokenRepository.save(
        memberId,
        tokenPair.refreshToken,
        tokenProvider.getRefreshTokenValiditySeconds()
    )
    return tokenPair
}
```

- [ ] **Step 6: Run application auth tests**

Run:

```bash
cd backend
./gradlew :application:test --tests com.chamchamcham.application.auth.AuthServiceTest --tests com.chamchamcham.application.auth.KakaoLoginServiceTest
```

Expected: PASS for both auth test classes.

- [ ] **Step 7: Commit service UUID adaptation**

Run:

```bash
git add backend/application
git commit \
  -m "Adapt auth services to persisted Member UUIDs" \
  -m "Auth reissue, logout, Kakao login, and refresh-token storage now share the Member UUID identity contract introduced in the domain and token layers." \
  -m "Constraint: Generated JPA IDs are nullable before persistence" \
  -m "Rejected: Force non-null sentinel UUIDs on entities | hides missing-persistence mistakes" \
  -m "Confidence: high" \
  -m "Scope-risk: moderate" \
  -m "Directive: Throw MEMBER_NOT_FOUND when token issuance sees an unpersisted Member" \
  -m "Tested: AuthServiceTest and KakaoLoginServiceTest" \
  -m "Not-tested: Full Gradle test suite pending API principal/logging updates"
```

Expected: auth service UUID changes are committed.

## Task 7: Rename API Principal And MDC Logging To `memberId`

**Files:**

- Modify: `backend/api/src/main/kotlin/com/chamchamcham/api/auth/controller/AuthController.kt`
- Modify: `backend/api/src/main/kotlin/com/chamchamcham/api/controller/TestController.kt`
- Modify: `backend/api/src/main/kotlin/com/chamchamcham/config/MDCLoggingFilter.kt`
- Modify: `backend/api/src/main/resources/application.yml`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/common/LoggingUtil.kt`
- Test: `backend/api/src/test/kotlin/com/chamchamcham/api/security/AuthSecurityIntegrationTest.kt`
- Test: `backend/api/src/test/kotlin/com/chamchamcham/config/MDCLoggingFilterTest.kt`

- [ ] **Step 1: Update logging tests first**

In `AuthSecurityIntegrationTest.kt`, replace:

```kotlin
.contains("[userId=GUEST]")
assertThat(MDC.get("userId")).isNull()
val accessToken = tokenProvider.createAccessToken(42L, "ROLE_USER")
val refreshToken = tokenProvider.createRefreshToken(42L)
```

with:

```kotlin
.contains("[memberId=GUEST]")
assertThat(MDC.get("memberId")).isNull()
val memberId = UUID.fromString("00000000-0000-0000-0000-000000000042")
val accessToken = tokenProvider.createAccessToken(memberId, "ROLE_USER")
val refreshToken = tokenProvider.createRefreshToken(memberId)
```

Add:

```kotlin
import java.util.UUID
```

In `MDCLoggingFilterTest.kt`, replace all `MDC.get("userId")` assertions with `MDC.get("memberId")`.

- [ ] **Step 2: Run API logging tests and confirm they fail**

Run:

```bash
cd backend
./gradlew :api:test --tests com.chamchamcham.api.security.AuthSecurityIntegrationTest --tests com.chamchamcham.config.MDCLoggingFilterTest
```

Expected: FAIL because production code still uses `userId`.

- [ ] **Step 3: Update application log formatting utility**

In `backend/application/src/main/kotlin/com/chamchamcham/application/common/LoggingUtil.kt`, replace the MDC lookup and output label:

```kotlin
val memberId = MDC.get("memberId")
appendLine("eventId=$eventId traceId=$traceId memberId=$memberId ip=$clientIp")
```

- [ ] **Step 4: Update Spring logging pattern**

In `backend/api/src/main/resources/application.yml`, replace:

```yaml
console: "%d{yyyy-MM-dd HH:mm:ss} [traceId=%X{traceId}] [eventId=%X{eventId}] [userId=%X{userId}] [ip=%X{clientIp}] %-5level %logger{36} - %msg%n"
```

with:

```yaml
console: "%d{yyyy-MM-dd HH:mm:ss} [traceId=%X{traceId}] [eventId=%X{eventId}] [memberId=%X{memberId}] [ip=%X{clientIp}] %-5level %logger{36} - %msg%n"
```

- [ ] **Step 5: Update `MDCLoggingFilter.kt`**

Rename `refreshUserId()` to `refreshMemberId()` and use:

```kotlin
private fun refreshMemberId() {
    val authentication = SecurityContextHolder.getContext().authentication
    val memberId = authentication
        ?.takeIf { it.isAuthenticated }
        ?.takeUnless { it is AnonymousAuthenticationToken }
        ?.principal
        ?.toString()
        ?.takeUnless { it == "anonymousUser" }
        ?.takeIf { it.isNotBlank() }
        ?: GUEST_MEMBER_ID

    MDC.put("memberId", memberId)
}
```

Update companion constants:

```kotlin
private companion object {
    const val GUEST_MEMBER_ID = "GUEST"
    val MDC_KEYS = listOf("traceId", "eventId", "clientIp", "memberId")
```

Update both call sites:

```kotlin
refreshMemberId()
```

- [ ] **Step 6: Update auth controller principal**

In `AuthController.kt`, replace:

```kotlin
fun logout(
    @AuthenticationPrincipal userId: String
): ResponseEntity<ApiResponse<Unit>> {
    authService.logout(userId)
```

with:

```kotlin
fun logout(
    @AuthenticationPrincipal memberId: String
): ResponseEntity<ApiResponse<Unit>> {
    authService.logout(memberId)
```

- [ ] **Step 7: Update protected test controller output**

In `TestController.kt`, replace:

```kotlin
fun getMyInfo(@AuthenticationPrincipal userId: String): String {
    return "인증 성공, 현재 로그인한 유저의 ID(PK)는: $userId"
}
```

with:

```kotlin
fun getMyInfo(@AuthenticationPrincipal memberId: String): String {
    return "인증 성공, 현재 로그인한 회원의 ID(PK)는: $memberId"
}
```

- [ ] **Step 8: Run API tests**

Run:

```bash
cd backend
./gradlew :api:test
```

Expected: PASS or fail only in tests not yet adapted to UUID/memberId. If additional tests fail because they call Long token APIs, update those tests with deterministic UUIDs and rerun.

- [ ] **Step 9: Commit API memberId logging**

Run:

```bash
git add backend/api backend/application/src/main/kotlin/com/chamchamcham/application/common/LoggingUtil.kt
git commit \
  -m "Expose authenticated principal as memberId in API logs" \
  -m "API controllers, MDC, and log patterns now use memberId consistently, matching the service identity contract used by JWT and Redis." \
  -m "Constraint: Spring Security framework type names stay unchanged" \
  -m "Rejected: Keep userId as an MDC compatibility alias | would preserve the wrong domain term in logs" \
  -m "Confidence: high" \
  -m "Scope-risk: moderate" \
  -m "Directive: New request logs must use memberId, not userId" \
  -m "Tested: ./gradlew :api:test" \
  -m "Not-tested: Manual log inspection against a running server"
```

Expected: API logging and principal changes are committed.

## Task 8: Finish Test Suite And Stray Naming Cleanup

**Files:**

- Modify: any file under `backend/` reported by the Step 1 forbidden-name scan
- Test: entire backend test suite

- [ ] **Step 1: Search for forbidden project-owned identity names**

Run:

```bash
rg -n "userId|getUserId|USER_NOT_FOUND|userHash|\\[userId=|MDC\\.get\\(\"userId\"\\)|MDC\\.put\\(\"userId\"" backend
```

Expected: no output.

- [ ] **Step 2: Search for allowed external/framework user terms**

Run:

```bash
rg -n "UsernamePasswordAuthenticationToken|anonymousUser|userinfo|user@example.com" backend
```

Expected: results are allowed because they are framework terms, Spring anonymous principal, OIDC terminology, or test email values.

- [ ] **Step 3: Run full backend checks**

Run:

```bash
cd backend
./gradlew test
```

Expected: PASS.

- [ ] **Step 4: Run compile/build check**

Run:

```bash
cd backend
./gradlew build
```

Expected: PASS.

- [ ] **Step 5: Record any skipped PostgreSQL runtime verification**

If no local PostgreSQL instance is available, append this exact note to the implementation final report:

```markdown
PostgreSQL runtime connection was not manually exercised because no local `chamchamcham_local` PostgreSQL service was available in this session. Configuration is set for PostgreSQL, and tests use the `test` profile with H2 PostgreSQL mode. A follow-up Testcontainers or local PostgreSQL smoke test is recommended before deploy.
```

- [ ] **Step 6: Commit final cleanup**

Run:

```bash
git add backend
git commit \
  -m "Verify backend member identity migration end to end" \
  -m "The imported backend now builds and tests with UUID member identity, PostgreSQL runtime configuration, singular auth tables, and memberId logging across project-owned auth surfaces." \
  -m "Constraint: Runtime DB target is PostgreSQL while automated tests use H2 PostgreSQL mode" \
  -m "Confidence: high" \
  -m "Scope-risk: moderate" \
  -m "Directive: Add migration tooling before expanding the domain schema beyond auth foundation tables" \
  -m "Tested: ./gradlew test and ./gradlew build" \
  -m "Not-tested: Live PostgreSQL connection unless explicitly exercised during execution"
```

Expected: final implementation cleanup is committed.

## Self-Review

Spec coverage:

- Template import is covered in Task 1.
- Package root adaptation is covered in Task 2.
- PostgreSQL runtime configuration is covered in Task 3.
- UUID `Member.id` and singular auth tables are covered in Task 4.
- JWT subject and Redis refresh token UUID `memberId` are covered in Task 5.
- Auth service/Kakao login UUID handling is covered in Task 6.
- API principal and MDC `memberId` logging are covered in Task 7.
- Naming scan, full tests, and build verification are covered in Task 8.

Type consistency:

- `Member.id` is `UUID?` because Hibernate generates it.
- Auth token APIs accept `UUID`.
- Refresh token repositories accept `UUID`.
- Controller principal stays `String` because Spring Security principal is the JWT subject string.
- Service logout parses principal `String` to `UUID`.

Known verification boundary:

- Runtime profiles target PostgreSQL.
- Automated tests use H2 PostgreSQL mode unless a later execution pass adds Testcontainers.
