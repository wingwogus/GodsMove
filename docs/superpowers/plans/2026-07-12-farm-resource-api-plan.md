# Farm Resource API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

**Goal:** Farm을 회원 소유 REST 리소스로 분리하고 작물 연결을 원자적으로 동기화하며, 프로필 수정과 단일 Farm 온보딩의 책임을 명확히 한다.

**Architecture:** application.farm에 명령, 검증기, 결과, 서비스를 둔다. Farm 생성과 교체는 Farm과 member_crop를 같은 트랜잭션에서 처리한다. 현재 dev에는 FarmingCycleReport 코드가 없으므로 이번 브랜치는 FarmingRecord 참조만 409으로 차단한다.

**Tech Stack:** Kotlin 1.9, Spring Boot 3.5, Spring MVC, Spring Data JPA, Jakarta Bean Validation, JUnit 5, Mockito, MockMvc.

## Global Constraints

- 브랜치 fix/farm-resource-api에서 작업하고 미추적 .claude/와 .worktrees/는 스테이징하지 않는다.
- 의존성, 테이블, 마이그레이션, Farm soft delete, 감사 이력을 추가하지 않는다.
- domain 용어는 member를 유지한다.
- 온보딩은 단일 Farm과 최상위 cropIds 계약 및 기존 단일 farm/crops 응답을 유지한다.
- 생성, 교체, 온보딩은 이름·도로명 주소·위도·경도, 유효 좌표 범위, 양수 면적, 완전한 경계점, 서로 다른 1~5개 cropIds를 요구한다.
- Farm 삭제와 작물 연결 해제는 FarmingRecord만 검사하며 isDeleted=true인 기록도 참조로 본다.
- FARM_NOT_FOUND는 없는 Farm과 타 회원 Farm 모두에 사용한다. FARM_IN_USE와 FARM_CROP_IN_USE는 409이다.
- Farm 전체 draft 저장에는 PUT을 사용하고 DELETE는 본문 없는 204를 반환한다.
- GET /api/v1/members/me와 /api/v1/members/me/farm-crops의 읽기 모델은 유지한다. PUT /api/v1/members/me/profile에서만 farms 쓰기를 제거한다.
- 구현은 테스트를 먼저 바꾸고 각 Task의 집중 Gradle 테스트와 마지막 전체 ./gradlew test를 통과해야 한다.

## File Structure

| File | Responsibility |
| --- | --- |
| backend/application/src/main/kotlin/com/chamchamcham/application/farm/FarmCommand.kt | Farm draft와 create, replace, delete 명령 |
| backend/application/src/main/kotlin/com/chamchamcham/application/farm/FarmResult.kt | 편집 가능한 전체 Farm 결과 |
| backend/application/src/main/kotlin/com/chamchamcham/application/farm/FarmService.kt | list/create/replace/delete 트랜잭션 |
| backend/application/src/test/kotlin/com/chamchamcham/application/farm/FarmServiceTest.kt | Farm 유스케이스 회귀 |
| backend/domain/src/main/kotlin/com/chamchamcham/domain/crop/MemberCropRepository.kt | Farm별 crop link fetch |
| backend/domain/src/main/kotlin/com/chamchamcham/domain/farming/FarmingRecordRepository.kt | Farm과 member/Farm/crop record 존재 검사 |
| backend/application/src/main/kotlin/com/chamchamcham/application/exception/ErrorCode.kt | FARM_002, FARM_003 |
| backend/api/src/main/kotlin/com/chamchamcham/api/farm/dto/FarmRequests.kt | Farm draft와 flat save JSON |
| backend/api/src/main/kotlin/com/chamchamcham/api/farm/dto/FarmResponses.kt | Farm list/detail JSON |
| backend/api/src/main/kotlin/com/chamchamcham/api/farm/controller/FarmController.kt | GET/POST/PUT/DELETE Farm routes |
| backend/api/src/test/kotlin/com/chamchamcham/api/farm/controller/FarmControllerTest.kt | Farm MVC contract |
| backend/application/src/main/kotlin/com/chamchamcham/application/member/MemberProfileCommand.kt | nested Farm write 제거 |
| backend/application/src/main/kotlin/com/chamchamcham/application/member/MemberProfileService.kt | Farm write 제거, read 유지 |
| backend/api/src/main/kotlin/com/chamchamcham/api/member/dto/MemberRequests.kt | profile request의 farms 제거 |
| backend/api/src/main/kotlin/com/chamchamcham/api/member/controller/MemberController.kt | profile-only command mapping |
| backend/application/src/test/kotlin/com/chamchamcham/application/member/MemberProfileServiceTest.kt | profile-only unit regression |
| backend/api/src/test/kotlin/com/chamchamcham/api/member/controller/MemberControllerTest.kt | profile-only MVC regression |
| backend/application/src/main/kotlin/com/chamchamcham/application/auth/common/AuthCommand.kt | FarmCommand.Draft와 nullable nickname |
| backend/application/src/main/kotlin/com/chamchamcham/application/auth/common/OnboardingService.kt | nickname fallback과 onboarding 트랜잭션 |
| backend/api/src/main/kotlin/com/chamchamcham/api/auth/dto/AuthRequests.kt | shared nested Farm request와 crop bounds |
| backend/api/src/main/kotlin/com/chamchamcham/api/auth/controller/AuthController.kt | onboarding Farm command mapping |
| backend/application/src/test/kotlin/com/chamchamcham/application/auth/common/OnboardingServiceTest.kt | nickname fallback과 작물 존재 검증 |
| backend/api/src/test/kotlin/com/chamchamcham/api/auth/controller/AuthControllerBusinessTest.kt | nickname response |
| backend/api/src/test/kotlin/com/chamchamcham/api/auth/controller/AuthControllerValidationTest.kt | crop max/location validation |

---

### Task 1: Create the Farm application lifecycle

**Files:**

- Create: backend/application/src/main/kotlin/com/chamchamcham/application/farm/FarmCommand.kt
- Create: backend/application/src/main/kotlin/com/chamchamcham/application/farm/FarmResult.kt
- Create: backend/application/src/main/kotlin/com/chamchamcham/application/farm/FarmService.kt
- Create: backend/application/src/test/kotlin/com/chamchamcham/application/farm/FarmServiceTest.kt
- Modify: backend/domain/src/main/kotlin/com/chamchamcham/domain/crop/MemberCropRepository.kt
- Modify: backend/domain/src/main/kotlin/com/chamchamcham/domain/farming/FarmingRecordRepository.kt
- Modify: backend/application/src/main/kotlin/com/chamchamcham/application/exception/ErrorCode.kt

**Interfaces:** Consumes Farm.updateProfile, FarmRepository.findByIdAndOwnerId, CropRepository.findAllById, MemberCrop, CropResult.CropSummary. Produces FarmService.list, create, replace, delete and FarmCommand.Draft.

- [ ] **Step 1: Write failing FarmService tests**

Cover: create stores Farm and links; list returns full draft/crops; unknown crop fails CROP_NOT_FOUND; non-owned Farm fails FARM_NOT_FOUND; replacing removes an unused link and adds a new link; replacing a recorded crop fails FARM_CROP_IN_USE; deleting a recorded Farm fails FARM_IN_USE; deleting an unreferenced Farm deletes links then Farm. Request-shape failures for crop count and duplicates belong to MVC validation tests.

~~~kotlin
@Test
fun replace_rejects_removal_of_recorded_crop() {
    given(farmRepository.findByIdAndOwnerId(farmId, memberId)).willReturn(farm)
    given(memberCropRepository.findAllWithCropByMemberIdAndFarmId(memberId, farmId))
        .willReturn(listOf(memberCrop(farm, oldCrop)))
    given(farmingRecordRepository.existsByMember_IdAndFarm_IdAndCrop_Id(memberId, farmId, oldCropId))
        .willReturn(true)

    val error = assertThrows(BusinessException::class.java) {
        service.replace(replaceCommand(listOf(newCropId)))
    }

    assertEquals(ErrorCode.FARM_CROP_IN_USE, error.errorCode)
    verify(memberCropRepository, never()).deleteAll(any<Iterable<MemberCrop>>())
}
~~~

- [ ] **Step 2: Run the focused test and observe failure**

~~~bash
cd backend && ./gradlew :application:test --tests "com.chamchamcham.application.farm.FarmServiceTest"
~~~

Expected: compilation fails because FarmService, FarmCommand, and the new repository contracts do not exist.

- [ ] **Step 3: Add persistence and error contracts**

~~~kotlin
// MemberCropRepository
@Query(
    """
    select mc
    from MemberCrop mc
    join fetch mc.crop
    where mc.member.id = :memberId
      and mc.farm.id = :farmId
    """
)
fun findAllWithCropByMemberIdAndFarmId(
    @Param("memberId") memberId: UUID,
    @Param("farmId") farmId: UUID,
): List<MemberCrop>

// FarmingRecordRepository
fun existsByFarm_Id(farmId: UUID): Boolean
fun existsByMember_IdAndFarm_IdAndCrop_Id(memberId: UUID, farmId: UUID, cropId: UUID): Boolean

// ErrorCode immediately after FARM_NOT_FOUND
FARM_IN_USE("FARM_002", "error.farm_in_use", 409),
FARM_CROP_IN_USE("FARM_003", "error.farm_crop_in_use", 409),
~~~

The record queries intentionally do not include isDeleted in their method names.

- [ ] **Step 4: Implement Farm commands, result, and service**

~~~kotlin
object FarmCommand {
    data class Create(val memberId: UUID, val draft: Draft, val cropIds: List<UUID>)
    data class Replace(val memberId: UUID, val farmId: UUID, val draft: Draft, val cropIds: List<UUID>)
    data class Delete(val memberId: UUID, val farmId: UUID)

    data class Draft(
        val name: String,
        val roadAddress: String,
        val jibunAddress: String?,
        val latitude: Double,
        val longitude: Double,
        val pnu: String?,
        val landCategory: String?,
        val areaSqm: BigDecimal?,
        val areaIsManualEntry: Boolean,
        val boundaryCoordinates: List<BoundaryCoordinate>,
        val dataSource: DataSource,
    )

    data class BoundaryCoordinate(val latitude: Double, val longitude: Double)
    data class DataSource(val address: String?, val coordinate: String?, val parcel: String?, val landCharacteristic: String?)
}
~~~

`FarmRequests` owns blank name/address, Farm/boundary coordinate range, positive area, crop count, and repeated crop ID validation through Bean Validation and controller `@Valid`. `FarmService` owns only state-dependent rules such as crop existence, Farm ownership, and farming-record references.

Implement the central helpers exactly:

~~~kotlin
private fun findOwnedFarm(memberId: UUID, farmId: UUID): Farm =
    farmRepository.findByIdAndOwnerId(farmId, memberId)
        ?: throw BusinessException(ErrorCode.FARM_NOT_FOUND)

private fun loadCrops(cropIds: List<UUID>): List<Crop> {
    val byId = cropRepository.findAllById(cropIds).associateBy { requireNotNull(it.id) }
    if (byId.size != cropIds.size) throw BusinessException(ErrorCode.CROP_NOT_FOUND)
    return cropIds.map(byId::getValue)
}

private fun assertRemovable(memberId: UUID, farmId: UUID, links: List<MemberCrop>) {
    links.forEach { link ->
        if (farmingRecordRepository.existsByMember_IdAndFarm_IdAndCrop_Id(memberId, farmId, requireNotNull(link.crop.id))) {
            throw BusinessException(ErrorCode.FARM_CROP_IN_USE)
        }
    }
}
~~~

create validates before repository reads, persists a Farm owned by the found member, then saves one link per crop. replace validates and loads all target crops before deleting any link, protects only removed links, applies Farm.updateProfile, deletes removed links, and saves only new links. delete checks existsByFarm_Id, calls deleteByMemberIdAndFarmId, then deletes Farm. list uses findAllWithBoundaryCoordinatesByOwnerId plus findAllWithCropByMemberId and returns FarmResult.Detail objects, never JPA entities.

- [ ] **Step 5: Run the FarmService test**

~~~bash
cd backend && ./gradlew :application:test --tests "com.chamchamcham.application.farm.FarmServiceTest"
~~~

Expected: PASS.

- [ ] **Step 6: Commit**

~~~bash
git add backend/application/src/main/kotlin/com/chamchamcham/application/farm   backend/application/src/test/kotlin/com/chamchamcham/application/farm/FarmServiceTest.kt   backend/application/src/main/kotlin/com/chamchamcham/application/exception/ErrorCode.kt   backend/domain/src/main/kotlin/com/chamchamcham/domain/crop/MemberCropRepository.kt   backend/domain/src/main/kotlin/com/chamchamcham/domain/farming/FarmingRecordRepository.kt
git commit -m "feat(farm): 재배지와 작물 연결을 독립적으로 관리한다"   -m "Constraint: soft-deleted farming records retain farm foreign keys
Rejected: profile update 안에서 farm mutation 유지 | 삭제와 이력 보호를 표현할 수 없음
Confidence: high
Scope-risk: moderate
Directive: cycle report가 dev에 추가되면 동일한 409 보호를 확장할 것
Tested: :application:test --tests FarmServiceTest
Not-tested: HTTP contract tests"
~~~

### Task 2: Expose the Farm REST resource

**Files:**

- Modify: backend/api/src/main/kotlin/com/chamchamcham/api/farm/dto/FarmRequests.kt
- Create: backend/api/src/main/kotlin/com/chamchamcham/api/farm/dto/FarmResponses.kt
- Create: backend/api/src/main/kotlin/com/chamchamcham/api/farm/controller/FarmController.kt
- Create: backend/api/src/test/kotlin/com/chamchamcham/api/farm/controller/FarmControllerTest.kt

**Interfaces:** Consumes Task 1 FarmService, FarmCommand, FarmResult, ApiResponse, CropResponses.CropResponse. Produces authenticated GET/POST/PUT/DELETE /api/v1/farms. Existing /api/v1/farms/{farmId}/weather remains unchanged.

- [ ] **Step 1: Write failing MVC tests**

Use @WebMvcTest(FarmController::class), @AutoConfigureMockMvc(addFilters = false), GlobalExceptionHandler, mocked FarmService and TokenProvider, and the existing authenticatedMember post-processor.

~~~kotlin
@Test
fun create_farm_returns_201_and_crops() {
    given(farmService.create(any())).willReturn(farmDetail())

    mockMvc.perform(
        post("/api/v1/farms")
            .with(authenticatedMember(memberId.toString()))
            .contentType(MediaType.APPLICATION_JSON)
            .content(validFarmJson())
    )
        .andExpect(status().isCreated)
        .andExpect(jsonPath("$.data.farmId", equalTo(farmId.toString())))
        .andExpect(jsonPath("$.data.crops[0].id", equalTo(cropId.toString())))
}

@Test
fun delete_farm_returns_204() {
    mockMvc.perform(
        delete("/api/v1/farms/{farmId}", farmId)
            .with(authenticatedMember(memberId.toString()))
    ).andExpect(status().isNoContent)

    verify(farmService).delete(FarmCommand.Delete(memberId, farmId))
}
~~~

Add contracts for full list mapping, PUT mapping, unauthenticated access, all invalid field cases, FARM_IN_USE, and FARM_CROP_IN_USE.

- [ ] **Step 2: Run and observe failure**

~~~bash
cd backend && ./gradlew :api:test --tests "com.chamchamcham.api.farm.controller.FarmControllerTest"
~~~

Expected: compilation fails because controller and DTOs do not exist.

- [ ] **Step 3: Add request/response DTOs**

FarmRequests keeps BoundaryCoordinateRequest and DataSourceRequest. Add FarmDraftRequest for onboarding and a flat SaveFarmRequest for Farm endpoints. Both contain name, roadAddress, jibunAddress, latitude, longitude, pnu, landCategory, areaSqm, areaIsManualEntry, boundaryCoordinates, dataSource. SaveFarmRequest additionally contains:

~~~kotlin
@field:NotEmpty(message = "작물을 하나 이상 선택해주세요")
@field:Size(max = 5, message = "작물은 최대 5개까지 선택할 수 있습니다")
@field:UniqueElements(message = "작물은 중복해서 선택할 수 없습니다")
val cropIds: List<UUID>
~~~

Both use NotBlank for name and roadAddress, NotNull plus DecimalMin/DecimalMax for coordinates, DecimalMin exclusive zero for supplied area, and Valid for child objects. FarmResponses.FarmResponse maps every FarmResult.Detail field and crop summary. FarmResponses.FarmListResponse exposes farms: List<FarmResponse>.

- [ ] **Step 4: Implement FarmController**

~~~kotlin
@RestController
@RequestMapping("/api/v1/farms")
class FarmController(private val farmService: FarmService) {
    @GetMapping
    fun list(@AuthenticationPrincipal memberId: String?): ResponseEntity<ApiResponse<FarmResponses.FarmListResponse>> =
        ResponseEntity.ok(ApiResponse.ok(FarmResponses.FarmListResponse.from(farmService.list(parseMemberId(memberId)))))

    @PostMapping
    fun create(@AuthenticationPrincipal memberId: String?, @Valid @RequestBody request: FarmRequests.SaveFarmRequest): ResponseEntity<ApiResponse<FarmResponses.FarmResponse>> =
        ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(FarmResponses.FarmResponse.from(farmService.create(request.toCreateCommand(parseMemberId(memberId))))))

    @PutMapping("/{farmId}")
    fun replace(@AuthenticationPrincipal memberId: String?, @PathVariable farmId: UUID, @Valid @RequestBody request: FarmRequests.SaveFarmRequest): ResponseEntity<ApiResponse<FarmResponses.FarmResponse>> =
        ResponseEntity.ok(ApiResponse.ok(FarmResponses.FarmResponse.from(farmService.replace(request.toReplaceCommand(parseMemberId(memberId), farmId)))))

    @DeleteMapping("/{farmId}")
    fun delete(@AuthenticationPrincipal memberId: String?, @PathVariable farmId: UUID): ResponseEntity<Void> {
        farmService.delete(FarmCommand.Delete(parseMemberId(memberId), farmId))
        return ResponseEntity.noContent().build()
    }
}
~~~

Use FarmWeatherController principal parsing behavior. Request extensions map every field into FarmCommand.Draft and use requireNotNull only after MVC validation.

- [ ] **Step 5: Run API tests**

~~~bash
cd backend && ./gradlew :api:test --tests "com.chamchamcham.api.farm.controller.FarmControllerTest"
~~~

Expected: PASS with 200 list/replace, 201 create, 204 delete, and 400/401/409 paths.

- [ ] **Step 6: Commit**

~~~bash
git add backend/api/src/main/kotlin/com/chamchamcham/api/farm   backend/api/src/test/kotlin/com/chamchamcham/api/farm/controller/FarmControllerTest.kt
git commit -m "feat(farm): 재배지 CRUD API를 제공한다"   -m "Constraint: Farm update submits a complete draft and crop set
Rejected: PATCH와 profile 중첩 mutation | 최종 작물 상태와 삭제 의도를 명확히 할 수 없음
Confidence: high
Scope-risk: moderate
Directive: Farm weather route와 동일한 /api/v1/farms 경로를 유지할 것
Tested: :api:test --tests FarmControllerTest
Not-tested: 전체 backend test"
~~~

### Task 3: Narrow profile writes to profile and media

**Files:**

- Modify: backend/application/src/main/kotlin/com/chamchamcham/application/member/MemberProfileCommand.kt
- Modify: backend/application/src/main/kotlin/com/chamchamcham/application/member/MemberProfileService.kt
- Modify: backend/api/src/main/kotlin/com/chamchamcham/api/member/dto/MemberRequests.kt
- Modify: backend/api/src/main/kotlin/com/chamchamcham/api/member/controller/MemberController.kt
- Modify: backend/application/src/test/kotlin/com/chamchamcham/application/member/MemberProfileServiceTest.kt
- Modify: backend/api/src/test/kotlin/com/chamchamcham/api/member/controller/MemberControllerTest.kt

**Interfaces:** Consumes existing profile member/media updates and Task 2 for all Farm writes. Produces a profile-write contract with no farms property.

- [ ] **Step 1: Rewrite profile tests first**

Replace Farm upsert/crop replacement/new Farm/foreign Farm/missing crop tests with profile-only tests. Controller request JSON contains only name, phone, birthDate, nickname, experienceLevel, managementType, profileMediaId. Keep all GET profile assertions.

~~~kotlin
@Test
fun update_profile_changes_basic_fields_and_removes_media() {
    given(memberRepository.findById(memberId)).willReturn(Optional.of(member))

    service.updateMyProfile(updateCommand(profileMediaId = null))

    assertEquals("수정 이름", member.name)
    assertEquals("수정닉", member.nickname)
    assertNull(member.profileMedia)
}
~~~

- [ ] **Step 2: Run and observe failure**

~~~bash
cd backend && ./gradlew :application:test --tests "com.chamchamcham.application.member.MemberProfileServiceTest" && ./gradlew :api:test --tests "com.chamchamcham.api.member.controller.MemberControllerTest"
~~~

Expected: FAIL until nested Farm types/mappings are removed.

- [ ] **Step 3: Remove nested Farm writes**

UpdateMyProfile has memberId, name, phone, birthDate, nickname, experienceLevel, managementType, profileMediaId only. Delete MemberProfileCommand.Farm and nested types, MemberRequests.FarmRequest, request.farms, controller Farm mapping, MemberProfileService CropRepository injection, Farm validation, upsertFarm, syncFarmCrops, and loadCrops. Remove the CropRepository mock from the service test fixture. Keep FarmRepository and MemberCropRepository because existing GET projections need them.

- [ ] **Step 4: Run profile regressions**

~~~bash
cd backend && ./gradlew :application:test --tests "com.chamchamcham.application.member.MemberProfileServiceTest" && ./gradlew :api:test --tests "com.chamchamcham.api.member.controller.MemberControllerTest"
~~~

Expected: PASS.

- [ ] **Step 5: Commit**

~~~bash
git add backend/application/src/main/kotlin/com/chamchamcham/application/member   backend/application/src/test/kotlin/com/chamchamcham/application/member/MemberProfileServiceTest.kt   backend/api/src/main/kotlin/com/chamchamcham/api/member   backend/api/src/test/kotlin/com/chamchamcham/api/member/controller/MemberControllerTest.kt
git commit -m "refactor(profile): 재배지 수정을 독립 API로 분리한다"   -m "Constraint: My Page 조회는 계속 Farm 요약을 제공한다
Rejected: profile farms 배열을 호환용으로 유지 | Farm API와 규칙이 이중화됨
Confidence: high
Scope-risk: moderate
Directive: 새 Farm 쓰기는 /api/v1/farms만 사용할 것
Tested: MemberProfileServiceTest, MemberControllerTest
Not-tested: onboarding regression"
~~~

### Task 4: Validate single-Farm onboarding and nickname fallback

**Files:**

- Modify: backend/application/src/main/kotlin/com/chamchamcham/application/auth/common/AuthCommand.kt
- Modify: backend/application/src/main/kotlin/com/chamchamcham/application/auth/common/OnboardingService.kt
- Modify: backend/api/src/main/kotlin/com/chamchamcham/api/auth/dto/AuthRequests.kt
- Modify: backend/api/src/main/kotlin/com/chamchamcham/api/auth/controller/AuthController.kt
- Modify: backend/application/src/test/kotlin/com/chamchamcham/application/auth/common/OnboardingServiceTest.kt
- Modify: backend/api/src/test/kotlin/com/chamchamcham/api/auth/controller/AuthControllerBusinessTest.kt
- Modify: backend/api/src/test/kotlin/com/chamchamcham/api/auth/controller/AuthControllerValidationTest.kt

**Interfaces:** Consumes Task 1 FarmCommand.Draft and Task 2 FarmRequests.FarmDraftRequest. Produces the existing single-Farm onboarding response with API-validated draft and stored nickname fallback.

- [ ] **Step 1: Update onboarding tests first**

Replace duplicate de-duplication with INVALID_INPUT rejection. Add null and blank nickname tests that persist name. At MVC level, replace blank nickname 400 with 200 and $.data.member.nickname equal to name. Add six crop IDs and missing Farm latitude 400 tests.

- [ ] **Step 2: Run and observe failure**

~~~bash
cd backend && ./gradlew :application:test --tests "com.chamchamcham.application.auth.common.OnboardingServiceTest" && ./gradlew :api:test --tests "com.chamchamcham.api.auth.controller.AuthControllerBusinessTest" --tests "com.chamchamcham.api.auth.controller.AuthControllerValidationTest"
~~~

Expected: FAIL because nickname is mandatory and the nested Farm request and crop selection lack the required Bean Validation constraints.

- [ ] **Step 3: Reuse nested API request validation**

Make AuthCommand.CompleteOnboarding.nickname a nullable String and farm a FarmCommand.Draft. The controller validates the nested `FarmDraftRequest` and crop IDs with `@Valid`; OnboardingService does not repeat request-shape validation and applies the nickname fallback plus domain-state checks:

~~~kotlin
val nickname = command.nickname?.trim().takeUnless { it.isNullOrBlank() } ?: command.name

member.completeOnboarding(
    name = command.name,
    phone = command.phone,
    birthDate = command.birthDate,
    nickname = nickname,
    experienceLevel = command.experienceLevel,
    managementType = command.managementType,
)
~~~

AuthRequests uses FarmRequests.FarmDraftRequest as the nested farm, nickname: String? = null, and root cropIds with Size(max = 5) and UniqueElements. AuthController maps its nested Farm request to FarmCommand.Draft. Do not change AuthResult.OnboardingComplete or AuthResponses.OnboardingCompleteResponse.

- [ ] **Step 4: Run onboarding regressions**

~~~bash
cd backend && ./gradlew :application:test --tests "com.chamchamcham.application.auth.common.OnboardingServiceTest" && ./gradlew :api:test --tests "com.chamchamcham.api.auth.controller.AuthControllerBusinessTest" --tests "com.chamchamcham.api.auth.controller.AuthControllerValidationTest"
~~~

Expected: PASS.

- [ ] **Step 5: Commit**

~~~bash
git add backend/application/src/main/kotlin/com/chamchamcham/application/auth/common/AuthCommand.kt   backend/application/src/main/kotlin/com/chamchamcham/application/auth/common/OnboardingService.kt   backend/application/src/test/kotlin/com/chamchamcham/application/auth/common/OnboardingServiceTest.kt   backend/api/src/main/kotlin/com/chamchamcham/api/auth   backend/api/src/test/kotlin/com/chamchamcham/api/auth/controller/AuthControllerBusinessTest.kt   backend/api/src/test/kotlin/com/chamchamcham/api/auth/controller/AuthControllerValidationTest.kt
git commit -m "feat(onboarding): 첫 재배지 검증과 닉네임 기본값을 맞춘다"   -m "Constraint: 온보딩은 단일 Farm과 최상위 cropIds 계약을 유지한다
Rejected: 다중 Farm 온보딩 | 재배지 추가는 독립 Farm API가 담당함
Confidence: high
Scope-risk: narrow
Directive: nickname fallback은 onboarding에만 적용하고 profile 수정의 필수 닉네임 정책을 바꾸지 말 것
Tested: OnboardingServiceTest, AuthControllerBusinessTest, AuthControllerValidationTest
Not-tested: 전체 backend test"
~~~

### Task 5: Verify the integrated branch

**Files:**

- Modify: docs/superpowers/specs/2026-07-12-farm-resource-api-design.md only when implementation forces an approved contract correction.
- Test: all backend modules.

**Interfaces:** Consumes Tasks 1–4. Produces a verified Farm API without schema or report-module changes.

- [ ] **Step 1: Compile production modules**

~~~bash
cd backend && ./gradlew :domain:compileKotlin :application:compileKotlin :api:compileKotlin
~~~

Expected: PASS.

- [ ] **Step 2: Run focused regressions**

~~~bash
cd backend && ./gradlew :application:test --tests "com.chamchamcham.application.farm.FarmServiceTest" --tests "com.chamchamcham.application.member.MemberProfileServiceTest" --tests "com.chamchamcham.application.auth.common.OnboardingServiceTest" && ./gradlew :api:test --tests "com.chamchamcham.api.farm.controller.FarmControllerTest" --tests "com.chamchamcham.api.member.controller.MemberControllerTest" --tests "com.chamchamcham.api.auth.controller.AuthControllerBusinessTest" --tests "com.chamchamcham.api.auth.controller.AuthControllerValidationTest"
~~~

Expected: PASS.

- [ ] **Step 3: Run full backend tests**

~~~bash
cd backend && ./gradlew test
~~~

Expected: PASS across domain, application, api, batch.

- [ ] **Step 4: Inspect final scope**

~~~bash
git diff dev...HEAD --check
git diff --name-only dev...HEAD
git status --short
~~~

Expected: no whitespace errors, no migration, no report-module file, and .claude/.worktrees remain untracked and unstaged.

- [ ] **Step 5: Commit documentation only if approved behavior required a correction**

Do not create an empty verification commit.

## Self-Review

- Spec coverage: Task 1 supplies Farm lifecycle, crop synchronization, record-only guards, and errors. Task 2 exposes every approved Farm endpoint. Task 3 removes profile-nested Farm writes while retaining reads. Task 4 keeps single-Farm onboarding and adds nickname fallback. Task 5 covers compilation, focused tests, full tests, and scope inspection.
- Placeholder scan: Every Task names concrete files, interfaces, behaviors, commands, expected output, and commit contents. The plan does not reference FarmingCycleReport because that module is absent from dev.
- Type consistency: Task 1 defines FarmCommand.Draft, Create, Replace, Delete, FarmService, and FarmResult.Detail. Tasks 2 and 4 consume those exact types.
