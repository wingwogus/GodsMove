# Crop Onboarding Catalog Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current crop/member/farm onboarding schema with a medicinal-crop catalogue, use-part categories, first-farm creation, and initial member-crop creation during onboarding.

**Architecture:** Keep the existing module boundaries: JPA entities and repositories live in `domain`, use-case orchestration lives in `application`, and HTTP DTO/controller/seed bootstrapping live in `api`. Crop catalogue reads are exposed through a small application service, while onboarding remains a single transaction that updates `Member`, creates `Farm`, validates selected `Crop` rows, and inserts `MemberCrop` links. The checked-in crop seed is generated from the Forest Service JSON export and is loaded idempotently at runtime for `local`, `dev`, and `prod` profiles.

**Tech Stack:** Spring Boot 3.5, Kotlin 1.9, Spring Data JPA, Jakarta Bean Validation, Spring MVC, Jackson Kotlin module in the `api` module, JUnit 5, Mockito, MockMvc, Gradle.

---

## Spec And Branch Context

Spec: `docs/superpowers/specs/2026-07-03-crop-onboarding-schema-redesign.md`

Current worktree: `/Users/wingwogus/Projects/ChamChamCham`

Current branch: `feat/rag-pgvector-service`

Run backend commands from `backend/`.

Do not commit `outputs/forest-mcllt-api` or `data/` unless the user explicitly asks. The implementation should create a checked-in seed resource under `backend/api/src/main/resources/seed/`.

Use Korean Conventional Commit messages with Lore trailers. Example:

```bash
git commit -m "feat(crop): 작물 카탈로그와 온보딩 작물 선택 추가" \
  -m "약용작물 기본 목록을 서비스 데이터로 제공하고 온보딩에서 농장과 선택 작물을 함께 생성한다." \
  -m "Constraint: 기존 운영 DB는 마이그레이션 체계가 없어 배포 후 별도 초기화가 필요함" \
  -m "Rejected: crop category 다대다 모델 | 현재 서비스는 이용부위 하나만 필요함" \
  -m "Confidence: high" \
  -m "Scope-risk: broad" \
  -m "Tested: ./gradlew test"
```

## File Map

Domain:

- Modify `backend/domain/src/main/kotlin/com/chamchamcham/domain/member/ManagementType.kt`
  - Replace the old registered/unregistered enum with the three onboarding choices.
- Modify `backend/domain/src/main/kotlin/com/chamchamcham/domain/member/Member.kt`
  - Remove `region`.
  - Change `experienceLevel` to `Int?`.
  - Change `managementType` to mutable nullable `ManagementType?`.
  - Update `completeOnboarding` to accept profile, experience, and management-type fields.
- Modify `backend/domain/src/main/kotlin/com/chamchamcham/domain/farm/Farm.kt`
  - Replace `region`, `city`, and `street` with `address`.
- Create `backend/domain/src/main/kotlin/com/chamchamcham/domain/crop/CropUsePartCategory.kt`
  - Enum-backed use-part category plus Korean label.
- Modify `backend/domain/src/main/kotlin/com/chamchamcham/domain/crop/Crop.kt`
  - Add `externalNo`.
  - Replace `category`, `lifecycleType`, `defaultUnit` with `usePartCategory`.
- Modify `backend/domain/src/main/kotlin/com/chamchamcham/domain/crop/CropRepository.kt`
  - Add list and seed lookup methods.
- Modify `backend/domain/src/main/kotlin/com/chamchamcham/domain/crop/MemberCrop.kt`
  - Remove `plantingYear`, `status`, and `startedOn`.
- Modify `backend/domain/src/main/kotlin/com/chamchamcham/domain/crop/MemberCropRepository.kt`
  - Add lookup/count helpers used by tests and onboarding status checks if needed.

Application:

- Modify `backend/application/src/main/kotlin/com/chamchamcham/application/exception/ErrorCode.kt`
  - Add `CROP_NOT_FOUND`.
- Create `backend/application/src/main/kotlin/com/chamchamcham/application/crop/CropResult.kt`
  - Application result types for crop list and category list.
- Create `backend/application/src/main/kotlin/com/chamchamcham/application/crop/CropCatalogService.kt`
  - Read-only crop catalogue use cases.
- Modify `backend/application/src/main/kotlin/com/chamchamcham/application/auth/common/AuthCommand.kt`
  - Update onboarding command fields.
- Modify `backend/application/src/main/kotlin/com/chamchamcham/application/auth/common/AuthResult.kt`
  - Remove member `region`.
  - Change `experienceLevel` to `Int?`.
  - Change `managementType` to `String?`.
  - Add farm and crop summaries to onboarding completion.
- Modify `backend/application/src/main/kotlin/com/chamchamcham/application/auth/common/OnboardingStatusResolver.kt`
  - Remove `REGION`; add `MANAGEMENT_TYPE`.
- Modify `backend/application/src/main/kotlin/com/chamchamcham/application/auth/common/OnboardingService.kt`
  - Create farm and member-crop rows transactionally.
- Modify `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/CoachingContextProvider.kt`
  - Use farm `address`, crop `usePartCategory`, numeric experience, and nullable management type.

API:

- Modify `backend/api/src/main/kotlin/com/chamchamcham/api/auth/dto/AuthRequests.kt`
  - Update onboarding validation fields and add `cropIds`.
- Modify `backend/api/src/main/kotlin/com/chamchamcham/api/auth/dto/AuthResponses.kt`
  - Remove member region and include onboarding farm/crops.
- Modify `backend/api/src/main/kotlin/com/chamchamcham/api/auth/controller/AuthController.kt`
  - Map new onboarding request to command.
- Create `backend/api/src/main/kotlin/com/chamchamcham/api/crop/dto/CropResponses.kt`
  - HTTP response DTOs for crops and categories.
- Create `backend/api/src/main/kotlin/com/chamchamcham/api/crop/controller/CropController.kt`
  - `GET /api/v1/crops` and `GET /api/v1/crops/categories`.
- Create `backend/api/src/main/kotlin/com/chamchamcham/config/MedicinalCropSeedRunner.kt`
  - Profile-scoped idempotent seed loader.
- Create `backend/api/src/main/resources/seed/medicinal-crops.json`
  - Normalized crop seed generated from Forest Service export.
- Modify `backend/api/src/test/resources/application-test.yml`
  - Keep the seed runner inactive under the `test` profile.

Tests:

- Modify `backend/domain/src/test/kotlin/com/chamchamcham/domain/member/MemberTest.kt`
- Modify `backend/application/src/test/kotlin/com/chamchamcham/application/auth/common/OnboardingStatusResolverTest.kt`
- Modify `backend/application/src/test/kotlin/com/chamchamcham/application/auth/common/OnboardingServiceTest.kt`
- Create `backend/application/src/test/kotlin/com/chamchamcham/application/crop/CropCatalogServiceTest.kt`
- Modify `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/CoachingContextProviderTest.kt`
- Create `backend/api/src/test/kotlin/com/chamchamcham/api/crop/controller/CropControllerTest.kt`
- Modify `backend/api/src/test/kotlin/com/chamchamcham/api/auth/controller/AuthControllerBusinessTest.kt`
- Modify `backend/api/src/test/kotlin/com/chamchamcham/api/auth/controller/AuthControllerValidationTest.kt`
- Modify `backend/api/src/test/kotlin/com/chamchamcham/api/security/AuthSecurityIntegrationTest.kt`

---

### Task 1: Domain Schema Foundation

**Files:**
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/member/ManagementType.kt`
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/member/Member.kt`
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/farm/Farm.kt`
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/crop/CropUsePartCategory.kt`
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/crop/Crop.kt`
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/crop/CropRepository.kt`
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/crop/MemberCrop.kt`
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/crop/MemberCropRepository.kt`
- Modify tests: `backend/domain/src/test/kotlin/com/chamchamcham/domain/member/MemberTest.kt`

- [ ] **Step 1: Update the member domain test first**

Replace the onboarding-focused assertions in `backend/domain/src/test/kotlin/com/chamchamcham/domain/member/MemberTest.kt` so the test uses no `region` and asserts numeric experience plus nullable-to-set management type:

```kotlin
@Test
fun `complete onboarding stores profile without member region`() {
    val member = Member(
        id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
        email = "member@example.com",
        passwordHash = null
    )

    member.completeOnboarding(
        name = "홍길동",
        phone = "010-1234-5678",
        birthDate = LocalDate.of(1990, 1, 1),
        nickname = "길동",
        experienceLevel = 72,
        managementType = ManagementType.AGRICULTURAL_INDIVIDUAL
    )

    assertEquals("홍길동", member.name)
    assertEquals("010-1234-5678", member.phone)
    assertEquals(LocalDate.of(1990, 1, 1), member.birthDate)
    assertEquals("길동", member.nickname)
    assertEquals(72, member.experienceLevel)
    assertEquals(ManagementType.AGRICULTURAL_INDIVIDUAL, member.managementType)
}
```

- [ ] **Step 2: Run the failing domain test**

Run:

```bash
./gradlew :domain:test --tests 'com.chamchamcham.domain.member.MemberTest'
```

Expected: FAIL because `completeOnboarding` still takes `region` and `experienceLevel` is still `String?`.

- [ ] **Step 3: Replace `ManagementType`**

Replace `backend/domain/src/main/kotlin/com/chamchamcham/domain/member/ManagementType.kt`:

```kotlin
package com.chamchamcham.domain.member

enum class ManagementType {
    AGRICULTURAL_INDIVIDUAL,
    AGRICULTURAL_CORPORATION,
    NON_REGISTERED_FARMER
}
```

- [ ] **Step 4: Update `Member` profile fields and onboarding method**

In `backend/domain/src/main/kotlin/com/chamchamcham/domain/member/Member.kt`, remove the `region` property and replace the experience/management fields plus method with:

```kotlin
@Column(name = "experience_level")
var experienceLevel: Int? = null,

@Enumerated(EnumType.STRING)
@Column(name = "management_type", length = 64)
var managementType: ManagementType? = null,
```

```kotlin
fun completeOnboarding(
    name: String,
    phone: String,
    birthDate: LocalDate,
    nickname: String,
    experienceLevel: Int,
    managementType: ManagementType
) {
    this.name = name
    this.phone = phone
    this.birthDate = birthDate
    this.nickname = nickname
    this.experienceLevel = experienceLevel
    this.managementType = managementType
}
```

- [ ] **Step 5: Create the use-part enum**

Create `backend/domain/src/main/kotlin/com/chamchamcham/domain/crop/CropUsePartCategory.kt`:

```kotlin
package com.chamchamcham.domain.crop

enum class CropUsePartCategory(val label: String) {
    WHOLE_HERB("전초"),
    ROOT_BARK("뿌리·껍질"),
    RHIZOME("뿌리줄기"),
    LEAF("잎"),
    FLOWER("꽃"),
    FRUIT("열매/과실"),
    SEED("종자"),
    STEM_BRANCH("줄기/가지"),
    UNKNOWN("기타")
}
```

- [ ] **Step 6: Replace crop entity fields**

Replace `backend/domain/src/main/kotlin/com/chamchamcham/domain/crop/Crop.kt` with:

```kotlin
package com.chamchamcham.domain.crop

import com.chamchamcham.domain.common.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.util.UUID

@Entity
@Table(
    name = "crop",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_crop_external_no", columnNames = ["external_no"])
    ]
)
class Crop(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @Column(name = "external_no", nullable = false)
    val externalNo: Int,

    @Column(nullable = false, length = 128)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "use_part_category", nullable = false, length = 64)
    var usePartCategory: CropUsePartCategory
) : BaseTimeEntity() {
    fun updateCatalogData(name: String, usePartCategory: CropUsePartCategory) {
        this.name = name
        this.usePartCategory = usePartCategory
    }
}
```

- [ ] **Step 7: Replace farm address fields**

Replace the location fields in `backend/domain/src/main/kotlin/com/chamchamcham/domain/farm/Farm.kt` with:

```kotlin
@Column(nullable = false, length = 255)
val address: String
```

The constructor should be:

```kotlin
class Farm(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    val owner: Member,

    @Column(nullable = false, length = 128)
    val name: String,

    @Column(nullable = false, length = 255)
    val address: String
) : BaseTimeEntity()
```

- [ ] **Step 8: Simplify member-crop**

Replace `backend/domain/src/main/kotlin/com/chamchamcham/domain/crop/MemberCrop.kt` with:

```kotlin
package com.chamchamcham.domain.crop

import com.chamchamcham.domain.common.BaseTimeEntity
import com.chamchamcham.domain.farm.Farm
import com.chamchamcham.domain.member.Member
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "member_crop")
class MemberCrop(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    val member: Member,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "farm_id", nullable = false)
    val farm: Farm,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "crop_id", nullable = false)
    val crop: Crop
) : BaseTimeEntity()
```

- [ ] **Step 9: Extend repositories**

Replace `backend/domain/src/main/kotlin/com/chamchamcham/domain/crop/CropRepository.kt` with:

```kotlin
package com.chamchamcham.domain.crop

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CropRepository : JpaRepository<Crop, UUID> {
    fun findAllByOrderByNameAscExternalNoAsc(): List<Crop>
    fun findByExternalNoIn(externalNos: Collection<Int>): List<Crop>
}
```

Replace `backend/domain/src/main/kotlin/com/chamchamcham/domain/crop/MemberCropRepository.kt` with:

```kotlin
package com.chamchamcham.domain.crop

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MemberCropRepository : JpaRepository<MemberCrop, UUID> {
    fun countByMember_Id(memberId: UUID): Long
}
```

- [ ] **Step 10: Run domain tests**

Run:

```bash
./gradlew :domain:test
```

Expected: PASS for domain tests, or fail only in downstream modules that still reference removed fields.

---

### Task 2: Crop Catalogue Application Service

**Files:**
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/exception/ErrorCode.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/crop/CropResult.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/crop/CropCatalogService.kt`
- Create: `backend/application/src/test/kotlin/com/chamchamcham/application/crop/CropCatalogServiceTest.kt`

- [ ] **Step 1: Write the crop catalogue service test**

Create `backend/application/src/test/kotlin/com/chamchamcham/application/crop/CropCatalogServiceTest.kt`:

```kotlin
package com.chamchamcham.application.crop

import com.chamchamcham.domain.crop.Crop
import com.chamchamcham.domain.crop.CropRepository
import com.chamchamcham.domain.crop.CropUsePartCategory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class CropCatalogServiceTest {
    @Mock
    private lateinit var cropRepository: CropRepository

    private lateinit var service: CropCatalogService

    @BeforeEach
    fun setUp() {
        service = CropCatalogService(cropRepository)
    }

    @Test
    fun `list crops returns catalogue summaries in repository order`() {
        val crop = Crop(
            id = UUID.fromString("00000000-0000-0000-0000-000000000159"),
            externalNo = 159,
            name = "가락지나물",
            usePartCategory = CropUsePartCategory.WHOLE_HERB
        )
        `when`(cropRepository.findAllByOrderByNameAscExternalNoAsc()).thenReturn(listOf(crop))

        val result = service.listCrops()

        assertThat(result).containsExactly(
            CropResult.CropSummary(
                id = requireNotNull(crop.id),
                externalNo = 159,
                name = "가락지나물",
                usePartCategory = "WHOLE_HERB",
                usePartCategoryLabel = "전초"
            )
        )
        verify(cropRepository).findAllByOrderByNameAscExternalNoAsc()
    }

    @Test
    fun `list categories returns every enum in declaration order`() {
        val result = service.listCategories()

        assertThat(result.first()).isEqualTo(
            CropResult.CategorySummary(code = "WHOLE_HERB", label = "전초")
        )
        assertThat(result.last()).isEqualTo(
            CropResult.CategorySummary(code = "UNKNOWN", label = "기타")
        )
        assertThat(result).hasSize(CropUsePartCategory.entries.size)
    }
}
```

- [ ] **Step 2: Run the failing crop application test**

Run:

```bash
./gradlew :application:test --tests 'com.chamchamcham.application.crop.CropCatalogServiceTest'
```

Expected: FAIL because `CropResult` and `CropCatalogService` do not exist.

- [ ] **Step 3: Add crop result types**

Create `backend/application/src/main/kotlin/com/chamchamcham/application/crop/CropResult.kt`:

```kotlin
package com.chamchamcham.application.crop

import com.chamchamcham.domain.crop.Crop
import com.chamchamcham.domain.crop.CropUsePartCategory
import java.util.UUID

object CropResult {
    data class CropSummary(
        val id: UUID,
        val externalNo: Int,
        val name: String,
        val usePartCategory: String,
        val usePartCategoryLabel: String
    ) {
        companion object {
            fun from(crop: Crop): CropSummary {
                return CropSummary(
                    id = requireNotNull(crop.id) { "Persisted crop id is required" },
                    externalNo = crop.externalNo,
                    name = crop.name,
                    usePartCategory = crop.usePartCategory.name,
                    usePartCategoryLabel = crop.usePartCategory.label
                )
            }
        }
    }

    data class CategorySummary(
        val code: String,
        val label: String
    ) {
        companion object {
            fun from(category: CropUsePartCategory): CategorySummary {
                return CategorySummary(code = category.name, label = category.label)
            }
        }
    }
}
```

- [ ] **Step 4: Add crop catalogue service**

Create `backend/application/src/main/kotlin/com/chamchamcham/application/crop/CropCatalogService.kt`:

```kotlin
package com.chamchamcham.application.crop

import com.chamchamcham.domain.crop.CropRepository
import com.chamchamcham.domain.crop.CropUsePartCategory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class CropCatalogService(
    private val cropRepository: CropRepository
) {
    fun listCrops(): List<CropResult.CropSummary> {
        return cropRepository.findAllByOrderByNameAscExternalNoAsc()
            .map(CropResult.CropSummary::from)
    }

    fun listCategories(): List<CropResult.CategorySummary> {
        return CropUsePartCategory.entries.map(CropResult.CategorySummary::from)
    }
}
```

- [ ] **Step 5: Add crop error code**

Add this enum value to `backend/application/src/main/kotlin/com/chamchamcham/application/exception/ErrorCode.kt` near resource/member errors:

```kotlin
CROP_NOT_FOUND("CROP_001", "error.crop_not_found", 404),
```

- [ ] **Step 6: Run crop application tests**

Run:

```bash
./gradlew :application:test --tests 'com.chamchamcham.application.crop.CropCatalogServiceTest'
```

Expected: PASS.

---

### Task 3: Crop Catalogue HTTP API

**Files:**
- Create: `backend/api/src/main/kotlin/com/chamchamcham/api/crop/dto/CropResponses.kt`
- Create: `backend/api/src/main/kotlin/com/chamchamcham/api/crop/controller/CropController.kt`
- Create: `backend/api/src/test/kotlin/com/chamchamcham/api/crop/controller/CropControllerTest.kt`

- [ ] **Step 1: Write crop controller tests**

Create `backend/api/src/test/kotlin/com/chamchamcham/api/crop/controller/CropControllerTest.kt`:

```kotlin
package com.chamchamcham.api.crop.controller

import com.chamchamcham.api.exception.GlobalExceptionHandler
import com.chamchamcham.application.crop.CropCatalogService
import com.chamchamcham.application.crop.CropResult
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@WebMvcTest(CropController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler::class)
class CropControllerTest(
    @Autowired private val mockMvc: MockMvc
) {
    @MockBean
    private lateinit var cropCatalogService: CropCatalogService

    @Test
    fun `list crops returns crop catalogue`() {
        `when`(cropCatalogService.listCrops()).thenReturn(
            listOf(
                CropResult.CropSummary(
                    id = UUID.fromString("00000000-0000-0000-0000-000000000159"),
                    externalNo = 159,
                    name = "가락지나물",
                    usePartCategory = "WHOLE_HERB",
                    usePartCategoryLabel = "전초"
                )
            )
        )

        mockMvc.perform(get("/api/v1/crops"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].externalNo", equalTo(159)))
            .andExpect(jsonPath("$.data[0].name", equalTo("가락지나물")))
            .andExpect(jsonPath("$.data[0].usePartCategory", equalTo("WHOLE_HERB")))
            .andExpect(jsonPath("$.data[0].usePartCategoryLabel", equalTo("전초")))
    }

    @Test
    fun `list categories returns enum-backed categories`() {
        `when`(cropCatalogService.listCategories()).thenReturn(
            listOf(CropResult.CategorySummary(code = "ROOT_BARK", label = "뿌리·껍질"))
        )

        mockMvc.perform(get("/api/v1/crops/categories"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].code", equalTo("ROOT_BARK")))
            .andExpect(jsonPath("$.data[0].label", equalTo("뿌리·껍질")))
    }
}
```

- [ ] **Step 2: Run the failing API test**

Run:

```bash
./gradlew :api:test --tests 'com.chamchamcham.api.crop.controller.CropControllerTest'
```

Expected: FAIL because the crop controller and DTOs do not exist.

- [ ] **Step 3: Create crop response DTOs**

Create `backend/api/src/main/kotlin/com/chamchamcham/api/crop/dto/CropResponses.kt`:

```kotlin
package com.chamchamcham.api.crop.dto

import com.chamchamcham.application.crop.CropResult
import java.util.UUID

object CropResponses {
    data class CropResponse(
        val id: UUID,
        val externalNo: Int,
        val name: String,
        val usePartCategory: String,
        val usePartCategoryLabel: String
    ) {
        companion object {
            fun from(result: CropResult.CropSummary): CropResponse {
                return CropResponse(
                    id = result.id,
                    externalNo = result.externalNo,
                    name = result.name,
                    usePartCategory = result.usePartCategory,
                    usePartCategoryLabel = result.usePartCategoryLabel
                )
            }
        }
    }

    data class CategoryResponse(
        val code: String,
        val label: String
    ) {
        companion object {
            fun from(result: CropResult.CategorySummary): CategoryResponse {
                return CategoryResponse(code = result.code, label = result.label)
            }
        }
    }
}
```

- [ ] **Step 4: Create crop controller**

Create `backend/api/src/main/kotlin/com/chamchamcham/api/crop/controller/CropController.kt`:

```kotlin
package com.chamchamcham.api.crop.controller

import com.chamchamcham.api.common.ApiResponse
import com.chamchamcham.api.crop.dto.CropResponses
import com.chamchamcham.application.crop.CropCatalogService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/crops")
class CropController(
    private val cropCatalogService: CropCatalogService
) {
    @GetMapping
    fun listCrops(): ResponseEntity<ApiResponse<List<CropResponses.CropResponse>>> {
        val crops = cropCatalogService.listCrops().map(CropResponses.CropResponse::from)
        return ResponseEntity.ok(ApiResponse.ok(crops))
    }

    @GetMapping("/categories")
    fun listCategories(): ResponseEntity<ApiResponse<List<CropResponses.CategoryResponse>>> {
        val categories = cropCatalogService.listCategories().map(CropResponses.CategoryResponse::from)
        return ResponseEntity.ok(ApiResponse.ok(categories))
    }
}
```

- [ ] **Step 5: Run crop API tests**

Run:

```bash
./gradlew :api:test --tests 'com.chamchamcham.api.crop.controller.CropControllerTest'
```

Expected: PASS.

---

### Task 4: Onboarding Transaction With Farm And Crops

**Files:**
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/auth/common/AuthCommand.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/auth/common/AuthResult.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/auth/common/OnboardingStatusResolver.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/auth/common/OnboardingService.kt`
- Modify tests: `backend/application/src/test/kotlin/com/chamchamcham/application/auth/common/OnboardingStatusResolverTest.kt`
- Modify tests: `backend/application/src/test/kotlin/com/chamchamcham/application/auth/common/OnboardingServiceTest.kt`

- [ ] **Step 1: Update onboarding status tests**

Replace `REGION` expectations in `backend/application/src/test/kotlin/com/chamchamcham/application/auth/common/OnboardingStatusResolverTest.kt` with `MANAGEMENT_TYPE`. The complete-member fixture should set:

```kotlin
experienceLevel = 72,
managementType = ManagementType.AGRICULTURAL_INDIVIDUAL,
```

Add this test:

```kotlin
@Test
fun `status is required when management type is missing`() {
    val member = completeMember(managementType = null)

    val result = resolver.resolve(member)

    assertEquals(AuthResult.OnboardingStatus.REQUIRED, result.status)
    assertEquals(listOf(AuthResult.OnboardingField.MANAGEMENT_TYPE), result.missingFields)
}
```

- [ ] **Step 2: Update onboarding service tests for farm, crops, duplicate IDs, and missing crop**

Replace `backend/application/src/test/kotlin/com/chamchamcham/application/auth/common/OnboardingServiceTest.kt` with a Mockito test that owns all four repositories:

```kotlin
@ExtendWith(MockitoExtension::class)
class OnboardingServiceTest {
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val farmId = UUID.fromString("10000000-0000-0000-0000-000000000001")
    private val cropId = UUID.fromString("20000000-0000-0000-0000-000000000001")
    private val secondCropId = UUID.fromString("20000000-0000-0000-0000-000000000002")

    @Mock private lateinit var memberRepository: MemberRepository
    @Mock private lateinit var farmRepository: FarmRepository
    @Mock private lateinit var cropRepository: CropRepository
    @Mock private lateinit var memberCropRepository: MemberCropRepository

    private lateinit var service: OnboardingService

    @BeforeEach
    fun setUp() {
        service = OnboardingService(
            memberRepository = memberRepository,
            farmRepository = farmRepository,
            cropRepository = cropRepository,
            memberCropRepository = memberCropRepository,
            onboardingStatusResolver = OnboardingStatusResolver()
        )
    }
}
```

Add a success test with these assertions:

```kotlin
@Test
fun `complete stores profile creates farm and creates one member crop per unique crop`() {
    val member = Member(id = memberId, email = "user@example.com", passwordHash = null)
    val crop = crop(cropId, 159, "가락지나물")
    val secondCrop = crop(secondCropId, 160, "각시둥굴레")
    val savedFarm = Farm(id = farmId, owner = member, name = "하늘들 약초농장", address = "강원특별자치도 평창군 진부면")

    `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))
    `when`(cropRepository.findAllById(listOf(cropId, secondCropId))).thenReturn(listOf(crop, secondCrop))
    `when`(farmRepository.save(any(Farm::class.java))).thenReturn(savedFarm)
    `when`(memberCropRepository.saveAll(anyList<MemberCrop>())).thenAnswer { it.arguments[0] }

    val result = service.complete(completeOnboardingCommand(cropIds = listOf(cropId, cropId, secondCropId)))

    assertEquals(72, member.experienceLevel)
    assertEquals(ManagementType.AGRICULTURAL_INDIVIDUAL, member.managementType)
    assertEquals(farmId, result.farm.id)
    assertEquals("하늘들 약초농장", result.farm.name)
    assertEquals("강원특별자치도 평창군 진부면", result.farm.address)
    assertEquals(listOf(cropId, secondCropId), result.crops.map { it.id })
    assertEquals(AuthResult.OnboardingStatus.COMPLETE, result.onboarding.status)
    verify(memberCropRepository).saveAll(argThat<List<MemberCrop>> { links ->
        links.size == 2 && links.map { it.crop.id } == listOf(cropId, secondCropId)
    })
}
```

Add a missing crop test:

```kotlin
@Test
fun `complete fails when any requested crop id does not exist`() {
    val member = Member(id = memberId, email = "user@example.com", passwordHash = null)
    val crop = crop(cropId, 159, "가락지나물")

    `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))
    `when`(cropRepository.findAllById(listOf(cropId, secondCropId))).thenReturn(listOf(crop))

    val exception = assertThrows(BusinessException::class.java) {
        service.complete(completeOnboardingCommand(cropIds = listOf(cropId, secondCropId)))
    }

    assertEquals(ErrorCode.CROP_NOT_FOUND, exception.errorCode)
    verify(farmRepository, never()).save(any(Farm::class.java))
    verify(memberCropRepository, never()).saveAll(anyList<MemberCrop>())
}
```

- [ ] **Step 3: Run the failing onboarding application tests**

Run:

```bash
./gradlew :application:test --tests 'com.chamchamcham.application.auth.common.Onboarding*'
```

Expected: FAIL because command/result/service signatures still use the old schema.

- [ ] **Step 4: Update `AuthCommand.CompleteOnboarding`**

Replace the onboarding command in `backend/application/src/main/kotlin/com/chamchamcham/application/auth/common/AuthCommand.kt` with:

```kotlin
data class CompleteOnboarding(
    val memberId: UUID,
    val name: String,
    val phone: String,
    val birthDate: LocalDate,
    val nickname: String,
    val experienceLevel: Int,
    val managementType: ManagementType,
    val farmName: String,
    val farmAddress: String,
    val cropIds: List<UUID>
)
```

Add the import:

```kotlin
import com.chamchamcham.domain.member.ManagementType
```

- [ ] **Step 5: Update auth result types**

In `backend/application/src/main/kotlin/com/chamchamcham/application/auth/common/AuthResult.kt`, replace `OnboardingComplete` and `MemberProfile` with:

```kotlin
data class OnboardingComplete(
    val member: MemberProfile,
    val farm: FarmSummary,
    val crops: List<CropResult.CropSummary>,
    val onboarding: Onboarding
)

data class MemberProfile(
    val id: UUID,
    val email: String?,
    val name: String?,
    val phone: String?,
    val birthDate: LocalDate?,
    val nickname: String?,
    val experienceLevel: Int?,
    val managementType: String?
) {
    companion object {
        fun from(member: Member): MemberProfile {
            return MemberProfile(
                id = requireNotNull(member.id) { "Persisted member id is required" },
                email = member.email,
                name = member.name,
                phone = member.phone,
                birthDate = member.birthDate,
                nickname = member.nickname,
                experienceLevel = member.experienceLevel,
                managementType = member.managementType?.name
            )
        }
    }
}

data class FarmSummary(
    val id: UUID,
    val name: String,
    val address: String
) {
    companion object {
        fun from(farm: Farm): FarmSummary {
            return FarmSummary(
                id = requireNotNull(farm.id) { "Persisted farm id is required" },
                name = farm.name,
                address = farm.address
            )
        }
    }
}
```

Update `OnboardingField` to:

```kotlin
enum class OnboardingField {
    NAME,
    PHONE,
    BIRTH_DATE,
    NICKNAME,
    EXPERIENCE_LEVEL,
    MANAGEMENT_TYPE
}
```

- [ ] **Step 6: Update onboarding status resolver**

Replace the removed `region` and string experience checks in `OnboardingStatusResolver` with:

```kotlin
if (member.experienceLevel == null) {
    add(AuthResult.OnboardingField.EXPERIENCE_LEVEL)
}
if (member.managementType == null) {
    add(AuthResult.OnboardingField.MANAGEMENT_TYPE)
}
```

- [ ] **Step 7: Update onboarding service transaction**

Replace `backend/application/src/main/kotlin/com/chamchamcham/application/auth/common/OnboardingService.kt` with:

```kotlin
package com.chamchamcham.application.auth.common

import com.chamchamcham.application.crop.CropResult
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.crop.CropRepository
import com.chamchamcham.domain.crop.MemberCrop
import com.chamchamcham.domain.crop.MemberCropRepository
import com.chamchamcham.domain.farm.Farm
import com.chamchamcham.domain.farm.FarmRepository
import com.chamchamcham.domain.member.MemberRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class OnboardingService(
    private val memberRepository: MemberRepository,
    private val farmRepository: FarmRepository,
    private val cropRepository: CropRepository,
    private val memberCropRepository: MemberCropRepository,
    private val onboardingStatusResolver: OnboardingStatusResolver
) {
    fun complete(command: AuthCommand.CompleteOnboarding): AuthResult.OnboardingComplete {
        val member = memberRepository.findById(command.memberId).orElseThrow {
            BusinessException(ErrorCode.MEMBER_NOT_FOUND)
        }
        val cropIds = command.cropIds.distinct()
        val crops = cropRepository.findAllById(cropIds)
        if (crops.size != cropIds.size) {
            throw BusinessException(ErrorCode.CROP_NOT_FOUND)
        }
        val cropsById = crops.associateBy { requireNotNull(it.id) { "Persisted crop id is required" } }
        val orderedCrops = cropIds.map { cropsById.getValue(it) }

        member.completeOnboarding(
            name = command.name,
            phone = command.phone,
            birthDate = command.birthDate,
            nickname = command.nickname,
            experienceLevel = command.experienceLevel,
            managementType = command.managementType
        )

        val farm = farmRepository.save(
            Farm(
                owner = member,
                name = command.farmName,
                address = command.farmAddress
            )
        )
        memberCropRepository.saveAll(
            orderedCrops.map { crop ->
                MemberCrop(member = member, farm = farm, crop = crop)
            }
        )

        return AuthResult.OnboardingComplete(
            member = AuthResult.MemberProfile.from(member),
            farm = AuthResult.FarmSummary.from(farm),
            crops = orderedCrops.map(CropResult.CropSummary::from),
            onboarding = onboardingStatusResolver.resolve(member)
        )
    }
}
```

- [ ] **Step 8: Run onboarding application tests**

Run:

```bash
./gradlew :application:test --tests 'com.chamchamcham.application.auth.common.Onboarding*'
```

Expected: PASS.

---

### Task 5: Onboarding HTTP Contract

**Files:**
- Modify: `backend/api/src/main/kotlin/com/chamchamcham/api/auth/dto/AuthRequests.kt`
- Modify: `backend/api/src/main/kotlin/com/chamchamcham/api/auth/dto/AuthResponses.kt`
- Modify: `backend/api/src/main/kotlin/com/chamchamcham/api/auth/controller/AuthController.kt`
- Modify tests: `backend/api/src/test/kotlin/com/chamchamcham/api/auth/controller/AuthControllerBusinessTest.kt`
- Modify tests: `backend/api/src/test/kotlin/com/chamchamcham/api/auth/controller/AuthControllerValidationTest.kt`
- Modify tests: `backend/api/src/test/kotlin/com/chamchamcham/api/security/AuthSecurityIntegrationTest.kt`

- [ ] **Step 1: Update auth controller business test for new response**

In `AuthControllerBusinessTest`, replace the onboarding command expectation with:

```kotlin
AuthCommand.CompleteOnboarding(
    memberId = memberId,
    name = "홍길동",
    phone = "010-1234-5678",
    birthDate = LocalDate.parse("1990-01-01"),
    nickname = "길동",
    experienceLevel = 72,
    managementType = ManagementType.AGRICULTURAL_INDIVIDUAL,
    farmName = "하늘들 약초농장",
    farmAddress = "강원특별자치도 평창군 진부면",
    cropIds = listOf(
        UUID.fromString("20000000-0000-0000-0000-000000000001"),
        UUID.fromString("20000000-0000-0000-0000-000000000002")
    )
)
```

Assert the new response fields:

```kotlin
.andExpect(jsonPath("$.data.member.region").doesNotExist())
.andExpect(jsonPath("$.data.member.experienceLevel", equalTo(72)))
.andExpect(jsonPath("$.data.member.managementType", equalTo("AGRICULTURAL_INDIVIDUAL")))
.andExpect(jsonPath("$.data.farm.name", equalTo("하늘들 약초농장")))
.andExpect(jsonPath("$.data.farm.address", equalTo("강원특별자치도 평창군 진부면")))
.andExpect(jsonPath("$.data.crops[0].name", equalTo("가락지나물")))
```

- [ ] **Step 2: Update auth validation tests**

In `AuthControllerValidationTest`, replace the blank nickname request body with the new schema and add these cases:

```kotlin
@Test
fun `complete onboarding rejects experience level below zero`() {
    mockMvc.perform(
        post("/api/v1/auth/onboarding/complete")
            .contentType(MediaType.APPLICATION_JSON)
            .content(validOnboardingRequestBody(experienceLevel = -1))
    )
        .andExpect(status().isBadRequest)
        .andExpect(jsonPath("$.error.code", equalTo("COMMON_001")))
        .andExpect(jsonPath("$.error.detail.field", equalTo("experienceLevel")))
}

@Test
fun `complete onboarding rejects empty crop ids`() {
    mockMvc.perform(
        post("/api/v1/auth/onboarding/complete")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {
                  "name":"홍길동",
                  "phone":"010-1234-5678",
                  "birthDate":"1990-01-01",
                  "nickname":"길동",
                  "experienceLevel":72,
                  "managementType":"AGRICULTURAL_INDIVIDUAL",
                  "farmName":"하늘들 약초농장",
                  "farmAddress":"강원특별자치도 평창군 진부면",
                  "cropIds":[]
                }
                """.trimIndent()
            )
    )
        .andExpect(status().isBadRequest)
        .andExpect(jsonPath("$.error.code", equalTo("COMMON_001")))
        .andExpect(jsonPath("$.error.detail.field", equalTo("cropIds")))
}
```

- [ ] **Step 3: Run failing auth API tests**

Run:

```bash
./gradlew :api:test --tests 'com.chamchamcham.api.auth.controller.AuthController*'
```

Expected: FAIL because request/response/controller still use old fields.

- [ ] **Step 4: Update request validation DTO**

In `backend/api/src/main/kotlin/com/chamchamcham/api/auth/dto/AuthRequests.kt`, add imports:

```kotlin
import com.chamchamcham.domain.member.ManagementType
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotEmpty
import java.util.UUID
```

Replace `CompleteOnboardingRequest` with:

```kotlin
data class CompleteOnboardingRequest(
    @field:NotBlank(message = "이름을 입력해주세요")
    val name: String,
    @field:NotBlank(message = "전화번호를 입력해주세요")
    val phone: String,
    @field:NotNull(message = "생년월일을 입력해주세요")
    val birthDate: LocalDate?,
    @field:NotBlank(message = "닉네임을 입력해주세요")
    val nickname: String,
    @field:NotNull(message = "경험 수준을 입력해주세요")
    @field:Min(value = 0, message = "경험 수준은 0 이상이어야 합니다")
    @field:Max(value = 100, message = "경험 수준은 100 이하여야 합니다")
    val experienceLevel: Int?,
    @field:NotNull(message = "경영 형태를 입력해주세요")
    val managementType: ManagementType?,
    @field:NotBlank(message = "농장 이름을 입력해주세요")
    val farmName: String,
    @field:NotBlank(message = "농장 주소를 입력해주세요")
    val farmAddress: String,
    @field:NotEmpty(message = "작물을 하나 이상 선택해주세요")
    val cropIds: List<UUID>
)
```

- [ ] **Step 5: Update auth response DTOs**

In `AuthResponses.kt`, update `OnboardingCompleteResponse`:

```kotlin
data class OnboardingCompleteResponse(
    val member: MemberProfileResponse,
    val farm: FarmResponse,
    val crops: List<CropResponse>,
    val onboarding: OnboardingResponse
) {
    companion object {
        fun from(result: AuthResult.OnboardingComplete): OnboardingCompleteResponse {
            return OnboardingCompleteResponse(
                member = MemberProfileResponse.from(result.member),
                farm = FarmResponse.from(result.farm),
                crops = result.crops.map(CropResponse::from),
                onboarding = OnboardingResponse.from(result.onboarding)
            )
        }
    }
}
```

Replace `MemberProfileResponse` fields with no region and numeric experience:

```kotlin
data class MemberProfileResponse(
    val id: UUID,
    val email: String?,
    val name: String?,
    val phone: String?,
    val birthDate: LocalDate?,
    val nickname: String?,
    val experienceLevel: Int?,
    val managementType: String?
)
```

Add nested response DTOs:

```kotlin
data class FarmResponse(
    val id: UUID,
    val name: String,
    val address: String
) {
    companion object {
        fun from(result: AuthResult.FarmSummary): FarmResponse {
            return FarmResponse(id = result.id, name = result.name, address = result.address)
        }
    }
}

data class CropResponse(
    val id: UUID,
    val externalNo: Int,
    val name: String,
    val usePartCategory: String,
    val usePartCategoryLabel: String
) {
    companion object {
        fun from(result: CropResult.CropSummary): CropResponse {
            return CropResponse(
                id = result.id,
                externalNo = result.externalNo,
                name = result.name,
                usePartCategory = result.usePartCategory,
                usePartCategoryLabel = result.usePartCategoryLabel
            )
        }
    }
}
```

- [ ] **Step 6: Update controller mapping**

In `AuthController.completeOnboarding`, replace old fields with:

```kotlin
AuthCommand.CompleteOnboarding(
    memberId = parseMemberId(memberId),
    name = request.name,
    phone = request.phone,
    birthDate = requireNotNull(request.birthDate),
    nickname = request.nickname,
    experienceLevel = requireNotNull(request.experienceLevel),
    managementType = requireNotNull(request.managementType),
    farmName = request.farmName,
    farmAddress = request.farmAddress,
    cropIds = request.cropIds
)
```

- [ ] **Step 7: Update login response test fixtures**

Where tests still expect `"UNREGISTERED"` or missing `REGION`, update fixtures to:

```kotlin
managementType = null
```

and missing fields to:

```kotlin
listOf(
    AuthResult.OnboardingField.NAME,
    AuthResult.OnboardingField.PHONE,
    AuthResult.OnboardingField.BIRTH_DATE,
    AuthResult.OnboardingField.NICKNAME,
    AuthResult.OnboardingField.EXPERIENCE_LEVEL,
    AuthResult.OnboardingField.MANAGEMENT_TYPE
)
```

- [ ] **Step 8: Run auth API tests**

Run:

```bash
./gradlew :api:test --tests 'com.chamchamcham.api.auth.controller.AuthController*' --tests 'com.chamchamcham.api.security.AuthSecurityIntegrationTest'
```

Expected: PASS.

---

### Task 6: Seed Resource And Runtime Loader

**Files:**
- Create: `backend/api/src/main/resources/seed/medicinal-crops.json`
- Create: `backend/api/src/main/kotlin/com/chamchamcham/config/MedicinalCropSeedRunner.kt`
- Modify: `backend/api/src/test/resources/application-test.yml`

- [ ] **Step 1: Generate normalized seed JSON from the Forest Service export**

Run from repository root:

```bash
node -e '
const fs = require("fs");
const source = JSON.parse(fs.readFileSync("outputs/forest-mcllt-api/forest_mcllt_records.json", "utf8"));
const items = source.items;
function categoryOf(text) {
  const value = text || "";
  if (/(뿌리줄기|근경|덩이줄기|괴경|구경|인경|비늘줄기)/.test(value)) return "RHIZOME";
  if (/(뿌리|근피|뿌리껍질|나무껍질|수피|껍질)/.test(value)) return "ROOT_BARK";
  if (/(전초)/.test(value)) return "WHOLE_HERB";
  if (/(잎|엽)/.test(value)) return "LEAF";
  if (/(꽃|화서|꽃봉오리|화뢰)/.test(value)) return "FLOWER";
  if (/(열매|과실|핵과|장과)/.test(value)) return "FRUIT";
  if (/(종자|씨앗|씨를)/.test(value)) return "SEED";
  if (/(줄기|가지|목부|덩굴)/.test(value)) return "STEM_BRANCH";
  return "UNKNOWN";
}
const crops = items
  .map((item) => ({
    externalNo: Number(item.mclltNo),
    name: item.mclltSpecsNm,
    usePartCategory: categoryOf(item.usMthodDscrt)
  }))
  .filter((item) => Number.isInteger(item.externalNo) && item.name)
  .sort((a, b) => a.name.localeCompare(b.name, "ko") || a.externalNo - b.externalNo);
fs.mkdirSync("backend/api/src/main/resources/seed", { recursive: true });
fs.writeFileSync(
  "backend/api/src/main/resources/seed/medicinal-crops.json",
  JSON.stringify(crops, null, 2) + "\n"
);
console.log(`wrote ${crops.length} crops`);
'
```

Expected: `wrote 534 crops`.

- [ ] **Step 2: Inspect seed category distribution**

Run:

```bash
node -e '
const fs = require("fs");
const crops = JSON.parse(fs.readFileSync("backend/api/src/main/resources/seed/medicinal-crops.json", "utf8"));
const counts = crops.reduce((acc, crop) => {
  acc[crop.usePartCategory] = (acc[crop.usePartCategory] || 0) + 1;
  return acc;
}, {});
console.log(JSON.stringify(counts, null, 2));
'
```

Expected: output contains all category keys that occur in the seed and the total count sums to 534.

- [ ] **Step 3: Add runtime seed runner**

Create `backend/api/src/main/kotlin/com/chamchamcham/config/MedicinalCropSeedRunner.kt`:

```kotlin
package com.chamchamcham.config

import com.chamchamcham.domain.crop.Crop
import com.chamchamcham.domain.crop.CropRepository
import com.chamchamcham.domain.crop.CropUsePartCategory
import com.fasterxml.jackson.databind.ObjectMapper
import mu.KotlinLogging
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Component
@Profile("local", "dev", "prod")
class MedicinalCropSeedRunner(
    private val cropRepository: CropRepository,
    private val objectMapper: ObjectMapper
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        seed()
    }

    @Transactional
    fun seed() {
        val seedRows = objectMapper.readValue(
            ClassPathResource("seed/medicinal-crops.json").inputStream,
            Array<MedicinalCropSeedRow>::class.java
        ).toList()
        val existingByExternalNo = cropRepository.findByExternalNoIn(seedRows.map { it.externalNo })
            .associateBy { it.externalNo }
        val crops = seedRows.map { row ->
            existingByExternalNo[row.externalNo]?.apply {
                updateCatalogData(row.name, row.usePartCategory)
            } ?: Crop(
                externalNo = row.externalNo,
                name = row.name,
                usePartCategory = row.usePartCategory
            )
        }
        cropRepository.saveAll(crops)
        logger.info { "Medicinal crop seed synchronized: ${crops.size}" }
    }
}

data class MedicinalCropSeedRow(
    val externalNo: Int,
    val name: String,
    val usePartCategory: CropUsePartCategory
)
```

- [ ] **Step 4: Confirm the test profile does not run the loader**

Confirm `backend/api/src/test/resources/application-test.yml` uses `test` profile during integration tests. The runner is scoped to `local`, `dev`, and `prod`, so no property is required for tests.

- [ ] **Step 5: Compile API module**

Run:

```bash
./gradlew :api:compileKotlin
```

Expected: PASS.

---

### Task 7: Coaching Context And Remaining References

**Files:**
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/CoachingContextProvider.kt`
- Modify tests: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/CoachingContextProviderTest.kt`
- Search and modify any remaining references to removed fields.

- [ ] **Step 1: Update coaching context test fixtures**

In `CoachingContextProviderTest`, update fixtures:

```kotlin
private fun member() = Member(
    id = memberId,
    email = "farmer@example.com",
    name = "박민서",
    experienceLevel = 72,
    managementType = ManagementType.AGRICULTURAL_INDIVIDUAL,
    passwordHash = null
)

private fun farm(owner: Member) = Farm(
    id = farmId,
    owner = owner,
    name = "하늘들 약초농장",
    address = "강원특별자치도 평창군 진부면"
)

private fun crop() = Crop(
    id = cropId,
    externalNo = 422,
    name = "참당귀",
    usePartCategory = CropUsePartCategory.ROOT_BARK
)
```

Replace context expectations:

```kotlin
assertThat(context.text).contains("- 영농 경력 점수: 72")
assertThat(context.text).contains("- 경영 형태: AGRICULTURAL_INDIVIDUAL")
assertThat(context.text).contains("- 농장: 하늘들 약초농장 (강원특별자치도 평창군 진부면)")
assertThat(context.text).contains("- 작물: 참당귀 / 뿌리·껍질")
```

- [ ] **Step 2: Update context provider implementation**

Replace the removed-field lines in `CoachingContextProvider` with:

```kotlin
appendLine("- 영농 경력 점수: ${member.experienceLevel?.toString() ?: "미입력"}")
appendLine("- 경영 형태: ${member.managementType?.name ?: "미입력"}")
farm?.let { appendLine("- 농장: ${it.name} (${it.address})") }
crop?.let { appendLine("- 작물: ${it.name} / ${it.usePartCategory.label}") }
```

- [ ] **Step 3: Search for removed field references**

Run:

```bash
rg -n "member\\.region|\\.region|lifecycleType|defaultUnit|plantingYear|startedOn|status = .*MemberCrop|category =|experienceLevel = \"|REGISTERED|UNREGISTERED" backend/domain backend/application backend/api
```

Expected: no remaining references to removed crop/farm/member fields. `MemberStatus` references may remain and are allowed.

- [ ] **Step 4: Run coaching tests**

Run:

```bash
./gradlew :application:test --tests 'com.chamchamcham.application.coaching.rag.CoachingContextProviderTest'
```

Expected: PASS.

---

### Task 8: Full Verification And Branch Handoff

**Files:**
- Review all changed backend files and plan/spec docs.

- [ ] **Step 1: Run all backend tests**

Run from `backend/`:

```bash
./gradlew test
```

Expected: PASS.

- [ ] **Step 2: Inspect working tree**

Run from repository root:

```bash
git status --short
```

Expected: tracked code/doc/seed changes are visible. Untracked `outputs/` and `data/` may remain from the API export and must not be staged unless the user explicitly asks.

- [ ] **Step 3: Commit implementation**

Stage only backend code, seed resource, spec correction, and plan:

```bash
git add docs/superpowers/specs/2026-07-03-crop-onboarding-schema-redesign.md docs/superpowers/plans/2026-07-03-crop-onboarding-catalog-plan.md backend/domain backend/application backend/api
```

Commit:

```bash
git commit -m "feat(crop): 작물 카탈로그 온보딩 연동" \
  -m "온보딩에서 회원 프로필, 농장, 선택 작물을 한 트랜잭션으로 생성하고 공공데이터 기반 약용작물 목록을 서비스 카탈로그로 제공한다." \
  -m "Constraint: 운영 DB는 마이그레이션 없이 초기화 후 재생성하는 방향으로 합의됨" \
  -m "Rejected: 작물 카테고리 다대다 모델 | 현재 서비스는 이용부위 단일 카테고리만 사용함" \
  -m "Confidence: high" \
  -m "Scope-risk: broad" \
  -m "Directive: lifecycle/status/startedOn 필드는 실제 기능 요구가 생기기 전 재도입하지 말 것" \
  -m "Tested: ./gradlew test"
```

- [ ] **Step 4: Dev and main reflection**

After implementation tests pass on the feature branch, integrate to `dev` first, then reflect the same verified change to `main`. Do not run the home-server DB reset until code is deployed and the user has approved the destructive reset command.

Recommended non-destructive checks before DB reset:

```bash
ssh wingwogus@hyunserver.iptime.org 'cd /home/wingwogus/apps/chamchamcham && docker compose ps'
```

Expected: identifies the app/database services without printing secrets.

---

## Self-Review Checklist

- Spec coverage:
  - Crop `externalNo`, `name`, `usePartCategory`: Task 1.
  - Single category enum with root/bark merge and RHIZOME kept separate: Task 1 and Task 6.
  - Remove crop lifecycle/default unit: Task 1 and Task 7 search.
  - Remove member crop planting/status/started fields: Task 1 and Task 7 search.
  - Remove member region and numeric experience: Task 1, Task 4, Task 5, Task 7.
  - Three management types: Task 1, Task 4, Task 5.
  - Farm single road address: Task 1, Task 4, Task 5, Task 7.
  - Crop list and category APIs: Task 2 and Task 3.
  - Onboarding creates farm and multiple member-crop rows atomically: Task 4 and Task 5.
  - Seed generated from Forest Service export and checked into backend resources: Task 6.
  - DB reset separated from code implementation because it is destructive: Task 8.
- Placeholder scan:
  - No unresolved marker words or unresolved file paths are used.
  - Every code-changing step includes concrete code or a concrete replacement snippet.
- Type consistency:
  - `experienceLevel` is `Int?` in entity/result/response and `Int` in command.
  - `managementType` is nullable on `Member` and result/response, non-null in onboarding command.
  - `cropIds` is `List<UUID>` in request and command.
  - `usePartCategory` uses `CropUsePartCategory` in domain and string code/label in responses.
