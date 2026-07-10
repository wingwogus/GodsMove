# 작업별 타입 JSONB 주기 리포트 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 마지막 수확으로 영농기록을 주기별로 나누고, 모든 작업 유형의 공통·전용 통계를 타입이 고정된 JSONB 객체로 저장해 현재·완료·직전 주기 조회 API를 제공한다.

**Architecture:** `FarmingCycleReport`는 관계·상태·경계·revision을 관계형 컬럼에 저장하고, 불변 `CycleReportStatistics` 값 객체를 Hibernate JSON 타입으로 한 컬럼에 직접 저장한다. 영농기록 생성·수정·삭제 때 회원·밭·작물 범위의 원본을 다시 분할·집계하고, 같은 밭의 재계산을 짧은 비관적 잠금으로 순차 처리해 원본과 저장 통계를 같은 트랜잭션에서 일치시킨다.

**Tech Stack:** Kotlin 1.9, Spring Boot 3.5.4, Spring Data JPA, Hibernate 6.6.22, PostgreSQL JSONB, H2 PostgreSQL mode, JUnit 5, Mockito, MockMvc, Gradle

## Global Constraints

- 이 계획은 1단계 주기 리포트 기반만 구현한다. LLM, RAG, 코칭 상태·생성은 포함하지 않는다.
- 병행 PR이 시작되지 않아 사용자 승인으로 Task 0에서 비료 카테고리, 약제 카테고리, 마지막 수확 여부를 선행 구현한다.
- Task 0의 enum을 `FarmingCycleReportSourceLoader` 한 곳에서 정규화하며 자유 문자열 `materialName`·`pesticideName`을 남기거나 임시 집계 키로 사용하지 않는다.
- 주기는 회원·밭·작물별로 계산하고 같은 범위의 중첩 주기는 지원하지 않는다.
- 심기는 주기 시작점이 아니며 심기 전 준비 작업도 같은 주기에 포함한다.
- 마지막 수확은 해당 기록을 포함해 주기를 닫고, 일부 수확은 현재 주기를 유지한다.
- 통계는 증감하지 않고 영향 범위의 삭제되지 않은 원본 기록 전체를 다시 계산한다.
- 최상위 `common` 통계는 만들지 않는다. `PLANTING`부터 `ETC`까지 모든 작업 객체가 9개 공통 필드를 직접 가진다.
- 기록이 없는 작업 객체도 항상 포함하며 count는 0, 날짜·평균·비율은 null, 목록은 빈 배열로 반환한다.
- 수확량 모름은 null로 유지하고 횟수에는 포함하되 수량 합계·평균에서는 제외한다.
- 관수 횟수·간격으로 수분 부족·과다·적정을 판단하지 않는다.
- 약제 ML과 G를 합산하지 않는다. 수확량은 kg, 총 살포량은 L, 생육 기간 비교는 개월로 정규화한다.
- 작업 통계 내부 값은 목록 검색·정렬 조건으로 사용하지 않는다.
- 별도 작업별 통계 테이블, 작업별 상세 API, fingerprint, 범용 backfill runner를 추가하지 않는다.
- 새 라이브러리를 추가하지 않는다.
- 각 Task는 실패 테스트 작성 → 실패 확인 → 최소 구현 → 통과 확인 → 커밋 순서로 실행한다.
- 커밋은 Conventional Commits와 저장소 Lore trailer 규칙을 따른다.

## Execution Gate: 선행 영농일지 계약

Task 0 완료 후 Task 1 전에 다음 명령을 실행한다.

```bash
rg -n "isFinalHarvest|materialCategory|pesticideCategory|PropagationMethod|SOIL|FOLIAR|amountUnknown|medicinalPart" \
  backend/domain/src/main backend/application/src/main backend/api/src/main
```

Expected:

- 이미 병합된 `PropagationMethod`, `SOIL/FOLIAR`, 수확량 모름, 수확 약용 부위가 검색된다.
- Task 0의 안정적인 비료 카테고리 code/label, 약제 카테고리 code/label, 마지막 수확 Boolean이 검색된다.
- 세 의미가 하나라도 없거나 `materialName`·`pesticideName`이 제품 코드에 남아 있으면 Task 1로 진행하지 않는다.

## File Structure

### Domain

- Create `backend/domain/src/main/kotlin/com/chamchamcham/domain/farming/FertilizerMaterialCategory.kt`: 비료·자재 안정 코드와 표시명
- Create `backend/domain/src/main/kotlin/com/chamchamcham/domain/farming/PesticideCategory.kt`: 약제 안정 코드와 표시명
- Modify 영농기록 domain/application/API 계약: 자유 입력명을 카테고리로 교체하고 마지막 수확 Boolean 추가
- Create `backend/domain/src/main/kotlin/com/chamchamcham/domain/report/CycleReportStatistics.kt`: JSONB로 저장하는 불변 작업별 통계 타입 전체
- Create `backend/domain/src/main/kotlin/com/chamchamcham/domain/report/FarmingCycleReportStatus.kt`: ACTIVE, COMPLETED, SUPERSEDED
- Create `backend/domain/src/main/kotlin/com/chamchamcham/domain/report/FarmingCycleStartBasis.kt`: FIRST_RECORD, AFTER_PREVIOUS_FINAL_HARVEST
- Create `backend/domain/src/main/kotlin/com/chamchamcham/domain/report/FarmingCycleReport.kt`: 저장형 리포트와 revision 전이
- Create `backend/domain/src/main/kotlin/com/chamchamcham/domain/report/FarmingCycleReportRepository.kt`: 소유권·범위 조회
- Create `backend/domain/src/main/kotlin/com/chamchamcham/domain/report/FarmingCycleReportQueryRepository.kt`: 완료 목록·현재·직전 조회 계약
- Create `backend/domain/src/main/kotlin/com/chamchamcham/domain/report/FarmingCycleReportQueryRepositoryImpl.kt`: JPQL 커서와 직전 완료 조회
- Create `backend/domain/src/main/kotlin/com/chamchamcham/domain/farming/FarmingCycleReportSourceRepository.kt`: 원본 상세 스냅샷 계약
- Create `backend/domain/src/main/kotlin/com/chamchamcham/domain/farming/FarmingCycleReportSourceRepositoryImpl.kt`: base·detail·media 일괄 조회
- Modify `backend/domain/src/main/kotlin/com/chamchamcham/domain/farming/FarmingRecordRepository.kt`: 범위별 원본 조회
- Modify 작업별 상세 repository 6개와 `FarmingRecordMediaRepository.kt`: record ID 일괄 조회
- Modify `backend/domain/src/main/kotlin/com/chamchamcham/domain/farm/FarmRepository.kt`: 소유권 포함 비관적 잠금 조회
- Create `backend/domain/src/test/resources/application-test.yml`: JSONB를 지원하는 H2 PostgreSQL mode

### Application

- Create `backend/application/src/main/kotlin/com/chamchamcham/application/report/CycleReportSource.kt`: 정규화된 계산 입력과 주기 slice
- Create `backend/application/src/main/kotlin/com/chamchamcham/application/report/FarmingCycleReportSourceLoader.kt`: 상세 엔티티를 정규화 입력으로 변환
- Create `backend/application/src/main/kotlin/com/chamchamcham/application/report/FarmingCyclePartitioner.kt`: 마지막 수확 기반 순수 분할
- Create `backend/application/src/main/kotlin/com/chamchamcham/application/report/StatisticsMath.kt`: 비율·평균·간격 계산
- Create `backend/application/src/main/kotlin/com/chamchamcham/application/report/CycleReportStatisticsCalculator.kt`: 모든 작업 통계 순수 계산
- Create `backend/application/src/main/kotlin/com/chamchamcham/application/report/FarmingCycleReportProjectionService.kt`: 전체 재계산·matching·SUPERSEDED 처리
- Create `backend/application/src/main/kotlin/com/chamchamcham/application/report/FarmingCycleReportCursorPayload.kt`: 완료 목록 cursor payload
- Create `backend/application/src/main/kotlin/com/chamchamcham/application/report/FarmingCycleReportSearchCondition.kt`: 완료 목록 조건
- Create `backend/application/src/main/kotlin/com/chamchamcham/application/report/FarmingCycleReportResult.kt`: API 독립 조회 결과
- Create `backend/application/src/main/kotlin/com/chamchamcham/application/report/FarmingCycleReportQueryService.kt`: 현재·완료 목록·상세 조회
- Modify `backend/application/src/main/kotlin/com/chamchamcham/application/farming/FarmingRecordService.kt`: 기록 CRUD 후 전후 범위 재계산
- Modify `backend/application/src/main/kotlin/com/chamchamcham/application/exception/ErrorCode.kt`: REPORT_NOT_FOUND

### API and Docs

- Create `backend/api/src/main/kotlin/com/chamchamcham/api/report/controller/FarmingCycleReportController.kt`: 세 조회 API
- Create `backend/api/src/main/kotlin/com/chamchamcham/api/report/dto/FarmingCycleReportResponses.kt`: 목록·현재·상세 응답
- Create `backend/docs/db/farming-cycle-report-schema.sql`: PostgreSQL DDL
- Modify `frontend/docs/Business Rule.md`: 마지막 수확 주기 규칙
- Modify `frontend/docs/API 명세서/API 명세서/영농 리포트 조회 3909e2d9440581ef91dbf34bba44c407.md`: 세 조회 API
- Modify `frontend/docs/API 명세서/DTO(데이터 전달 객체)/FarmingReportResponse 3909e2d9440581bbbf69c0e35cd17b8b.md`: 타입 통계 응답

---

### Task 0: 카테고리형 영농기록과 마지막 수확 계약

**Files:**
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/farming/FertilizerMaterialCategory.kt`
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/farming/PesticideCategory.kt`
- Modify: fertilizing, pest-control, harvest entities and their repository search contract
- Modify: farming record command, result, service, field catalog, request, response, controller
- Modify: focused domain/application/API tests for those contracts
- Create: `backend/docs/db/farming-record-report-contract-schema.sql`

**Interfaces:**
- Produces: `FertilizerMaterialCategory`, `PesticideCategory`, `HarvestRecord.isFinalHarvest`
- Replaces: `materialName` with `materialCategory`, `pesticideName` with `pesticideCategory`
- Preserves: amount/unit behavior, nullable methods, amount-unknown behavior, category-label keyword search

- [ ] **Step 1: enum·catalog·request 실패 테스트를 작성하고 실행한다**

Required assertions:

```kotlin
assertThat(FertilizerMaterialCategory.COMPOUND_FERTILIZER.label)
    .isEqualTo("복합비료")
assertThat(PesticideCategory.FUNGICIDE.label).isEqualTo("살균제")
assertThat(fields(WorkType.FERTILIZING).first().name)
    .isEqualTo("materialCategory")
assertThat(fields(WorkType.PEST_CONTROL).first().name)
    .isEqualTo("pesticideCategory")
assertThat(fields(WorkType.HARVEST).last().name)
    .isEqualTo("isFinalHarvest")
assertThat(fields(WorkType.HARVEST).last().type)
    .isEqualTo(FieldValueType.BOOLEAN)
```

A HARVEST HTTP request without `isFinalHarvest` must return 400; explicit `false` must be accepted.

Run:

```bash
cd backend && ./gradlew \
  :application:test --tests '*WorkTypeCatalogServiceTest' \
  :api:test --tests '*FarmingRecordControllerTest'
```

Expected: FAIL because the three contracts do not exist.

- [ ] **Step 2: exact category enums and Boolean field type를 구현한다**

```kotlin
enum class FertilizerMaterialCategory(val label: String) {
    COMPOUND_FERTILIZER("복합비료"),
    NITROGEN_FERTILIZER("질소질비료"),
    PHOSPHATE_FERTILIZER("인산질비료"),
    POTASSIUM_FERTILIZER("칼리질비료"),
    ORGANIC_FERTILIZER("유기질비료"),
    LIME_FERTILIZER("석회질비료"),
    OTHER("기타"),
}

enum class PesticideCategory(val label: String) {
    FUNGICIDE("살균제"),
    INSECTICIDE("살충제"),
    HERBICIDE("제초제"),
    ACARICIDE("살비제"),
    BIOPESTICIDE("생물농약"),
    OTHER("기타"),
}
```

Add `BOOLEAN` to `FieldValueType`.

- [ ] **Step 3: entity부터 HTTP까지 자유 입력명을 category로 교체한다**

Entity mappings:

```kotlin
@Enumerated(EnumType.STRING)
@Column(name = "material_category", nullable = false, length = 48)
val materialCategory: FertilizerMaterialCategory

@Enumerated(EnumType.STRING)
@Column(name = "pesticide_category", nullable = false, length = 32)
val pesticideCategory: PesticideCategory

@Column(name = "is_final_harvest", nullable = false)
val isFinalHarvest: Boolean
```

Use those exact names/types in command, result, request, response, controller mapping, and service detail mapping. Request category and final flag fields are nullable with `@NotNull`; controller maps them with `requireNotNull`. Remove `materialName` and `pesticideName` from product code.

Field catalog uses required ENUM options from each enum and required BOOLEAN `isFinalHarvest`.

- [ ] **Step 4: category-label keyword search를 구현한다**

Add to `FarmingRecordQueryRepository.SearchCondition`:

```kotlin
val matchedFertilizerCategories: List<FertilizerMaterialCategory> = emptyList(),
val matchedPesticideCategories: List<PesticideCategory> = emptyList(),
```

The query adds enum `in` predicates against `FertilizingRecord.materialCategory` and `PestControlRecord.pesticideCategory`. `FarmingRecordService.search` derives those lists by matching the trimmed keyword against enum labels. Delete the old free-text LIKE predicates.

- [ ] **Step 5: fixtures·검색·round-trip tests를 새 계약으로 갱신한다**

Required coverage:

| fixture/input | assertion |
| --- | --- |
| ORGANIC_FERTILIZER record + `유기질비료` keyword | only that record is returned |
| FUNGICIDE record + `살균제` keyword | only that record is returned |
| harvest create/detail with true and false | each `isFinalHarvest` value round-trips unchanged |
| category field catalog | every enum name and label appears exactly once |
| missing category or final flag HTTP request | 400 and service is not called |

Replace all old name fixtures with enums; every test must assert returned rows, mapped command/result, or HTTP JSON.

- [ ] **Step 6: 수동 PostgreSQL DDL을 작성한다**

Add category columns, add `is_final_harvest boolean not null default false`, then set category columns NOT NULL and drop old name columns. The SQL header must state that existing rows require manual category mapping before NOT NULL/drop; do not guess categories from old text.

- [ ] **Step 7: 집중 검증과 gate 검사를 통과시킨다**

```bash
cd backend && ./gradlew \
  :domain:test --tests '*FarmingRecordQueryRepositoryTest' \
  :application:test --tests '*WorkTypeCatalogServiceTest' \
  :application:test --tests '*FarmingRecordServiceTest' \
  :application:test --tests '*FarmingRecordDetailValidatorTest' \
  :api:test --tests '*FarmingRecordControllerTest' \
  :api:test --tests '*WorkTypeControllerTest'
```

Expected: PASS.

```bash
rg -n "materialName|pesticideName" \
  backend/domain/src/main backend/application/src/main backend/api/src/main
```

Expected: no matches.

```bash
rg -n "isFinalHarvest|materialCategory|pesticideCategory" \
  backend/domain/src/main backend/application/src/main backend/api/src/main
```

Expected: all three contracts appear across domain, application, and API.

- [ ] **Step 8: Task 0을 커밋한다**

```bash
git add backend/domain backend/application backend/api \
  backend/docs/db/farming-record-report-contract-schema.sql
git commit -m "feat(farming): 리포트용 기록 분류 계약을 확정" \
  -m "Constraint: 자유 입력 비료명과 약제명을 안정적인 enum 코드로 교체" \
  -m "Rejected: 기존 문자열을 집계 키로 유지 | 동일 카테고리 보장 불가" \
  -m "Confidence: high" \
  -m "Scope-risk: broad" \
  -m "Directive: 기존 운영 행은 수동 매핑 후 DDL 적용" \
  -m "Tested: farming query service catalog and controller tests"
```

---


### Task 1: 타입 통계 값 객체와 저장형 리포트 도메인

**Files:**
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/report/CycleReportStatistics.kt`
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/report/FarmingCycleReportStatus.kt`
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/report/FarmingCycleStartBasis.kt`
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/report/FarmingCycleReport.kt`
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/report/FarmingCycleReportRepository.kt`
- Create: `backend/domain/src/test/resources/application-test.yml`
- Test: `backend/domain/src/test/kotlin/com/chamchamcham/domain/report/CycleReportStatisticsTest.kt`
- Test: `backend/domain/src/test/kotlin/com/chamchamcham/domain/report/FarmingCycleReportTest.kt`
- Test: `backend/domain/src/test/kotlin/com/chamchamcham/domain/report/FarmingCycleReportRepositoryTest.kt`

**Interfaces:**
- Consumes: `Member`, `Farm`, `Crop`, nullable 종료 근거 `FarmingRecord`
- Produces: `CycleReportStatistics.empty()`, `FarmingCycleReport.create(...)`, `applyProjection(next): Boolean`, `supersede(): Boolean`

- [ ] **Step 1: 빈 통계 객체 계약 테스트를 작성한다**

```kotlin
@Test
fun `empty statistics contain every work type with direct common fields`() {
    val result = CycleReportStatistics.empty()

    assertThat(result.planting.recordCount).isZero()
    assertThat(result.watering.firstWorkedOn).isNull()
    assertThat(result.fertilizing.photoAttachmentRatePct).isNull()
    assertThat(result.pestControl.weatherDistribution).isEmpty()
    assertThat(result.weeding.methodDistribution).isEmpty()
    assertThat(result.pruning.recordCount).isZero()
    assertThat(result.harvest.totalAmountKg).isNull()
    assertThat(result.etc.recordCount).isZero()
    assertThat(CycleReportStatistics::class.memberProperties.map { it.name })
        .doesNotContain("common")
}
```

- [ ] **Step 2: 타입이 없어 실패하는지 확인한다**

Run:

```bash
cd backend && ./gradlew :domain:test --tests '*CycleReportStatisticsTest'
```

Expected: FAIL with unresolved `CycleReportStatistics`.

- [ ] **Step 3: 통계 보조 타입과 모든 작업 객체를 실제 필드로 구현한다**

`CycleReportStatistics.kt`에 아래 계약을 그대로 둔다. 각 작업 클래스에는 아래 9개 공통 property가 직접 존재하며 중첩 `common` 객체나 `Map<String, Any?>`은 없다.

```kotlin
data class Coverage(val recordedCount: Int, val targetCount: Int)

data class CountDistribution(
    val code: String,
    val label: String,
    val count: Int,
    val ratePct: BigDecimal,
)

data class AmountByUnit(
    val unit: String,
    val amount: BigDecimal,
    val coverage: Coverage,
)

data class PropagationStatistics(
    val code: String,
    val label: String,
    val recordCount: Int,
    val recordRatePct: BigDecimal,
    val totalQuantity: BigDecimal?,
    val quantityUnit: String?,
    val quantityCoverage: Coverage,
)

data class MaterialCategoryStatistics(
    val code: String,
    val label: String,
    val recordCount: Int,
    val recordRatePct: BigDecimal,
    val amountKg: BigDecimal,
    val amountRatePct: BigDecimal,
)

data class CategoryMethodStatistics(
    val categoryCode: String,
    val categoryLabel: String,
    val methodCode: String,
    val methodLabel: String,
    val recordCount: Int,
    val recordRatePct: BigDecimal,
)

data class CategoryAmountByUnit(
    val categoryCode: String,
    val categoryLabel: String,
    val unit: String,
    val recordCount: Int,
    val amount: BigDecimal,
    val coverage: Coverage,
)

data class TargetCount(val target: String, val count: Int)

data class HarvestPartStatistics(
    val code: String,
    val label: String,
    val recordCount: Int,
    val recordRatePct: BigDecimal,
    val knownAmountKg: BigDecimal?,
    val amountRatePct: BigDecimal?,
    val amountCoverage: Coverage,
)

data class GrowthPeriodRange(val minMonths: Int, val maxMonths: Int)
```

```kotlin
data class CommonOnlyStatistics(
    val recordCount: Int = 0,
    val firstWorkedOn: LocalDate? = null,
    val lastWorkedOn: LocalDate? = null,
    val workedDayCount: Int = 0,
    val averageIntervalDays: BigDecimal? = null,
    val photoAttachedRecordCount: Int = 0,
    val photoAttachmentRatePct: BigDecimal? = null,
    val weatherDistribution: List<CountDistribution> = emptyList(),
    val averageTemperatureC: BigDecimal? = null,
) {
    companion object { fun empty() = CommonOnlyStatistics() }
}

data class PlantingStatistics(
    val recordCount: Int = 0,
    val firstWorkedOn: LocalDate? = null,
    val lastWorkedOn: LocalDate? = null,
    val workedDayCount: Int = 0,
    val averageIntervalDays: BigDecimal? = null,
    val photoAttachedRecordCount: Int = 0,
    val photoAttachmentRatePct: BigDecimal? = null,
    val weatherDistribution: List<CountDistribution> = emptyList(),
    val averageTemperatureC: BigDecimal? = null,
    val propagationMethods: List<PropagationStatistics> = emptyList(),
) {
    companion object { fun empty() = PlantingStatistics() }
}

data class WateringStatistics(
    val recordCount: Int = 0,
    val firstWorkedOn: LocalDate? = null,
    val lastWorkedOn: LocalDate? = null,
    val workedDayCount: Int = 0,
    val averageIntervalDays: BigDecimal? = null,
    val photoAttachedRecordCount: Int = 0,
    val photoAttachmentRatePct: BigDecimal? = null,
    val weatherDistribution: List<CountDistribution> = emptyList(),
    val averageTemperatureC: BigDecimal? = null,
    val amountDistribution: List<CountDistribution> = emptyList(),
    val methodDistribution: List<CountDistribution> = emptyList(),
) {
    companion object { fun empty() = WateringStatistics() }
}

data class FertilizingStatistics(
    val recordCount: Int = 0,
    val firstWorkedOn: LocalDate? = null,
    val lastWorkedOn: LocalDate? = null,
    val workedDayCount: Int = 0,
    val averageIntervalDays: BigDecimal? = null,
    val photoAttachedRecordCount: Int = 0,
    val photoAttachmentRatePct: BigDecimal? = null,
    val weatherDistribution: List<CountDistribution> = emptyList(),
    val averageTemperatureC: BigDecimal? = null,
    val totalAmountKg: BigDecimal? = null,
    val averageAmountKg: BigDecimal? = null,
    val amountCoverage: Coverage = Coverage(0, 0),
    val materialCategories: List<MaterialCategoryStatistics> = emptyList(),
    val methodDistribution: List<CountDistribution> = emptyList(),
    val categoryMethods: List<CategoryMethodStatistics> = emptyList(),
) {
    companion object { fun empty() = FertilizingStatistics() }
}

data class PestControlStatistics(
    val recordCount: Int = 0,
    val firstWorkedOn: LocalDate? = null,
    val lastWorkedOn: LocalDate? = null,
    val workedDayCount: Int = 0,
    val averageIntervalDays: BigDecimal? = null,
    val photoAttachedRecordCount: Int = 0,
    val photoAttachmentRatePct: BigDecimal? = null,
    val weatherDistribution: List<CountDistribution> = emptyList(),
    val averageTemperatureC: BigDecimal? = null,
    val categoryDistribution: List<CountDistribution> = emptyList(),
    val pesticideAmounts: List<AmountByUnit> = emptyList(),
    val categoryAmounts: List<CategoryAmountByUnit> = emptyList(),
    val totalSprayAmountLiters: BigDecimal? = null,
    val sprayAmountCoverage: Coverage = Coverage(0, 0),
    val targets: List<TargetCount> = emptyList(),
) {
    companion object { fun empty() = PestControlStatistics() }
}

data class WeedingStatistics(
    val recordCount: Int = 0,
    val firstWorkedOn: LocalDate? = null,
    val lastWorkedOn: LocalDate? = null,
    val workedDayCount: Int = 0,
    val averageIntervalDays: BigDecimal? = null,
    val photoAttachedRecordCount: Int = 0,
    val photoAttachmentRatePct: BigDecimal? = null,
    val weatherDistribution: List<CountDistribution> = emptyList(),
    val averageTemperatureC: BigDecimal? = null,
    val methodDistribution: List<CountDistribution> = emptyList(),
) {
    companion object { fun empty() = WeedingStatistics() }
}

data class HarvestStatistics(
    val recordCount: Int = 0,
    val firstWorkedOn: LocalDate? = null,
    val lastWorkedOn: LocalDate? = null,
    val workedDayCount: Int = 0,
    val averageIntervalDays: BigDecimal? = null,
    val photoAttachedRecordCount: Int = 0,
    val photoAttachmentRatePct: BigDecimal? = null,
    val weatherDistribution: List<CountDistribution> = emptyList(),
    val averageTemperatureC: BigDecimal? = null,
    val totalAmountKg: BigDecimal? = null,
    val averageAmountKg: BigDecimal? = null,
    val amountCoverage: Coverage = Coverage(0, 0),
    val firstHarvestedOn: LocalDate? = null,
    val lastHarvestedOn: LocalDate? = null,
    val medicinalParts: List<HarvestPartStatistics> = emptyList(),
    val finalGrowthPeriodMonths: Int? = null,
    val growthPeriodRangeMonths: GrowthPeriodRange? = null,
) {
    companion object { fun empty() = HarvestStatistics() }
}

data class CycleReportStatistics(
    val planting: PlantingStatistics = PlantingStatistics.empty(),
    val watering: WateringStatistics = WateringStatistics.empty(),
    val fertilizing: FertilizingStatistics = FertilizingStatistics.empty(),
    val pestControl: PestControlStatistics = PestControlStatistics.empty(),
    val weeding: WeedingStatistics = WeedingStatistics.empty(),
    val pruning: CommonOnlyStatistics = CommonOnlyStatistics.empty(),
    val harvest: HarvestStatistics = HarvestStatistics.empty(),
    val etc: CommonOnlyStatistics = CommonOnlyStatistics.empty(),
) {
    companion object { fun empty() = CycleReportStatistics() }
}
```

- [ ] **Step 4: 빈 객체 테스트를 통과시킨다**

Run:

```bash
cd backend && ./gradlew :domain:test --tests '*CycleReportStatisticsTest'
```

Expected: PASS.

- [ ] **Step 5: revision과 상태 불변식 테스트를 작성한다**

```kotlin
@Test
fun `same projection keeps revision and changed typed statistics increment it`() {
    val initial = activeProjection(CycleReportStatistics.empty())
    val report = FarmingCycleReport.create(member, farm, crop, initial)
    val changed = initial.copy(
        statistics = CycleReportStatistics(
            watering = WateringStatistics(recordCount = 1),
        ),
    )

    assertThat(report.sourceRevision).isEqualTo(1)
    assertThat(report.applyProjection(initial)).isFalse()
    assertThat(report.sourceRevision).isEqualTo(1)
    assertThat(report.applyProjection(changed)).isTrue()
    assertThat(report.sourceRevision).isEqualTo(2)
}

@Test
fun `active rejects an end boundary and completed requires one`() {
    assertThatThrownBy {
        activeReport().applyProjection(
            activeProjection().copy(endsAt = workedAt, finalHarvestRecord = finalHarvest),
        )
    }.isInstanceOf(IllegalArgumentException::class.java)

    assertThatThrownBy {
        activeReport().applyProjection(
            completedProjection().copy(finalHarvestRecord = null),
        )
    }.isInstanceOf(IllegalArgumentException::class.java)
}

@Test
fun `supersede preserves statistics and increments once`() {
    val report = activeReport(
        CycleReportStatistics(watering = WateringStatistics(recordCount = 1)),
    )
    val revision = report.sourceRevision

    assertThat(report.supersede()).isTrue()
    assertThat(report.statistics.watering.recordCount).isEqualTo(1)
    assertThat(report.sourceRevision).isEqualTo(revision + 1)
    assertThat(report.supersede()).isFalse()
}
```

- [ ] **Step 6: 상태·projection·엔티티를 구현한다**

```kotlin
enum class FarmingCycleReportStatus { ACTIVE, COMPLETED, SUPERSEDED }
enum class FarmingCycleStartBasis { FIRST_RECORD, AFTER_PREVIOUS_FINAL_HARVEST }

data class FarmingCycleReportProjection(
    val status: FarmingCycleReportStatus,
    val startsAt: LocalDateTime,
    val endsAt: LocalDateTime?,
    val startBasis: FarmingCycleStartBasis,
    val finalHarvestRecord: FarmingRecord?,
    val statisticsSchemaVersion: Int,
    val statistics: CycleReportStatistics,
)
```

`FarmingCycleReport`는 다음 mapping을 사용한다.

```kotlin
@Entity
@Table(name = "farming_cycle_report")
class FarmingCycleReport private constructor(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    val member: Member,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "farm_id", nullable = false)
    val farm: Farm,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "crop_id", nullable = false)
    val crop: Crop,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: FarmingCycleReportStatus,

    @Column(name = "starts_at", nullable = false)
    var startsAt: LocalDateTime,

    @Column(name = "ends_at")
    var endsAt: LocalDateTime?,

    @Enumerated(EnumType.STRING)
    @Column(name = "start_basis", nullable = false, length = 48)
    var startBasis: FarmingCycleStartBasis,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "final_harvest_record_id")
    var finalHarvestRecord: FarmingRecord?,

    @Column(name = "statistics_schema_version", nullable = false)
    var statisticsSchemaVersion: Int,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    var statistics: CycleReportStatistics,

    @Column(name = "source_revision", nullable = false)
    var sourceRevision: Long,
) : BaseTimeEntity()
```

실제 class에서는 리포트 변경 필드의 setter를 `private set`으로 제한한다. factory는 revision 1로 만들고 `applyProjection`은 다음 검증을 먼저 적용한다.

```kotlin
private fun FarmingCycleReportProjection.requireValid() {
    require(statisticsSchemaVersion > 0)
    require(endsAt == null || !startsAt.isAfter(endsAt))
    when (status) {
        FarmingCycleReportStatus.ACTIVE -> {
            require(endsAt == null)
            require(finalHarvestRecord == null)
        }
        FarmingCycleReportStatus.COMPLETED -> {
            require(endsAt != null)
            require(finalHarvestRecord != null)
        }
        FarmingCycleReportStatus.SUPERSEDED ->
            throw IllegalArgumentException("SUPERSEDED is not a source projection")
    }
}
```

검증 후 status, startsAt, endsAt, startBasis, final record ID, schema version, typed statistics가 모두 같으면 false를 반환한다. 하나라도 다르면 전부 교체하고 revision을 1 증가시킨 뒤 true를 반환한다. `supersede()`는 이미 SUPERSEDED면 false, 아니면 상태와 revision만 변경하고 true를 반환한다.

- [ ] **Step 7: JSONB 테스트용 H2 프로필을 추가한다**

`backend/domain/src/test/resources/application-test.yml`:

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:domain-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH
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
  test:
    database:
      replace: none
```

- [ ] **Step 8: JSONB 왕복과 소유권 repository 테스트를 작성한다**

```kotlin
@Test
fun `typed statistics round trip without value changes`() {
    val original = completedReport(statisticsWithEveryWorkType())
    val id = requireNotNull(repository.saveAndFlush(original).id)
    entityManager.clear()

    val loaded = repository.findById(id).orElseThrow()

    assertThat(loaded.statistics).isEqualTo(original.statistics)
    assertThat(loaded.sourceRevision).isEqualTo(original.sourceRevision)
}

@Test
fun `member scoped lookup does not expose another members report`() {
    val report = repository.saveAndFlush(completedReport())

    assertThat(repository.findByIdAndMember_Id(requireNotNull(report.id), otherMemberId))
        .isNull()
}
```

Repository contract:

```kotlin
interface FarmingCycleReportRepository : JpaRepository<FarmingCycleReport, UUID> {
    fun findByIdAndMember_Id(id: UUID, memberId: UUID): FarmingCycleReport?

    fun findByMember_IdAndFarm_IdAndCrop_IdAndStatus(
        memberId: UUID,
        farmId: UUID,
        cropId: UUID,
        status: FarmingCycleReportStatus,
    ): FarmingCycleReport?

    @Query("""
        select r from FarmingCycleReport r
        where r.member.id = :memberId
          and r.farm.id = :farmId
          and r.crop.id = :cropId
          and r.status in (
            com.chamchamcham.domain.report.FarmingCycleReportStatus.ACTIVE,
            com.chamchamcham.domain.report.FarmingCycleReportStatus.COMPLETED
          )
        order by r.startsAt asc, r.id asc
    """)
    fun findAllCurrent(
        memberId: UUID,
        farmId: UUID,
        cropId: UUID,
    ): List<FarmingCycleReport>
}
```

- [ ] **Step 9: Task 1 전체 테스트를 통과시키고 커밋한다**

Run:

```bash
cd backend && ./gradlew :domain:test \
  --tests '*CycleReportStatisticsTest' \
  --tests '*FarmingCycleReportTest' \
  --tests '*FarmingCycleReportRepositoryTest'
```

Expected: PASS, H2 DDL에 `jsonb` 오류가 없다.

```bash
git add \
  backend/domain/src/main/kotlin/com/chamchamcham/domain/report \
  backend/domain/src/test/kotlin/com/chamchamcham/domain/report \
  backend/domain/src/test/resources/application-test.yml
git commit -m "feat(report): 작업별 타입 통계 저장 모델을 마련" \
  -m "Constraint: 통계 객체를 JSONB 한 컬럼에 직접 매핑" \
  -m "Rejected: 임의 Map과 작업별 통계 테이블 | 타입 안전성과 YAGNI 위반" \
  -m "Confidence: high" \
  -m "Scope-risk: moderate" \
  -m "Tested: report domain and JSON round-trip tests"
```
---

### Task 2: 원본 스냅샷 정규화와 마지막 수확 주기 분할

**Files:**
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/farming/FarmingCycleReportSourceRepository.kt`
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/farming/FarmingCycleReportSourceRepositoryImpl.kt`
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/farming/FarmingRecordRepository.kt`
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/farming/PlantingRecordRepository.kt`
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/farming/WateringRecordRepository.kt`
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/farming/FertilizingRecordRepository.kt`
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/farming/PestControlRecordRepository.kt`
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/farming/WeedingRecordRepository.kt`
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/farming/HarvestRecordRepository.kt`
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/farming/FarmingRecordMediaRepository.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/report/CycleReportSource.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/report/FarmingCycleReportSourceLoader.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/report/FarmingCyclePartitioner.kt`
- Test: `backend/domain/src/test/kotlin/com/chamchamcham/domain/farming/FarmingCycleReportSourceRepositoryTest.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/report/FarmingCycleReportSourceLoaderTest.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/report/FarmingCyclePartitionerTest.kt`

**Interfaces:**
- Consumes: 병합된 상세 엔티티의 카테고리 code/label과 마지막 수확 Boolean
- Produces: `ReportScope`, `CycleReportSourceRecord`, `CycleSlice`, `FarmingCycleReportSourceLoader.load(scope)`, `FarmingCyclePartitioner.partition(records)`

- [ ] **Step 1: 정규화 입력 타입을 구현한다**

`CycleReportSource.kt`:

```kotlin
data class ReportScope(
    val memberId: UUID,
    val farmId: UUID,
    val cropId: UUID,
)

data class CategoryRef(val code: String, val label: String)

data class PlantingReportSource(
    val propagationMethod: CategoryRef,
    val quantity: BigDecimal?,
    val quantityUnit: String?,
)

data class WateringReportSource(
    val amount: CategoryRef?,
    val method: CategoryRef?,
)

data class FertilizingReportSource(
    val materialCategory: CategoryRef,
    val amountKg: BigDecimal?,
    val applicationMethod: CategoryRef?,
)

data class PestControlReportSource(
    val pesticideCategory: CategoryRef,
    val pesticideAmount: BigDecimal?,
    val pesticideAmountUnit: String,
    val totalSprayAmountLiters: BigDecimal?,
    val pestTarget: String?,
)

data class WeedingReportSource(val method: CategoryRef?)

data class HarvestReportSource(
    val amountKg: BigDecimal?,
    val medicinalPart: CategoryRef,
    val growthPeriodMonths: Int,
    val isFinalHarvest: Boolean,
)

data class CycleReportSourceRecord(
    val id: UUID,
    val workedAt: LocalDateTime,
    val workType: WorkType,
    val weatherCondition: String,
    val weatherTemperature: Int,
    val hasPhoto: Boolean,
    val planting: PlantingReportSource? = null,
    val watering: WateringReportSource? = null,
    val fertilizing: FertilizingReportSource? = null,
    val pestControl: PestControlReportSource? = null,
    val weeding: WeedingReportSource? = null,
    val harvest: HarvestReportSource? = null,
)

data class CycleSlice(
    val status: FarmingCycleReportStatus,
    val startBasis: FarmingCycleStartBasis,
    val records: List<CycleReportSourceRecord>,
) {
    val finalHarvestRecordId: UUID? = records.lastOrNull()
        ?.takeIf { it.harvest?.isFinalHarvest == true }
        ?.id
}
```

- [ ] **Step 2: 원본 repository의 실패 테스트를 작성한다**

모든 작업 유형, 사진 2장이 달린 기록 1개, soft-delete 기록 1개를 저장한다.

```kotlin
@Test
fun `load returns ordered active records with detail maps and distinct media ids`() {
    val snapshot = repository.load(memberId, farmId, cropId)

    assertThat(snapshot.records.map { it.id })
        .containsExactlyElementsOf(expectedIdsSortedByWorkedAtAndId)
    assertThat(snapshot.records).allMatch { !it.isDeleted }
    assertThat(snapshot.plantingByRecordId).containsKey(plantingRecordId)
    assertThat(snapshot.harvestByRecordId[harvestRecordId]?.isFinalHarvest).isTrue()
    assertThat(snapshot.mediaRecordIds).containsExactly(recordWithTwoPhotosId)
}
```

- [ ] **Step 3: 실패를 확인한다**

Run:

```bash
cd backend && ./gradlew :domain:test --tests '*FarmingCycleReportSourceRepositoryTest'
```

Expected: FAIL because source repository does not exist.

- [ ] **Step 4: 범위 원본과 상세를 일괄 조회한다**

```kotlin
data class FarmingCycleReportSourceSnapshot(
    val records: List<FarmingRecord>,
    val plantingByRecordId: Map<UUID, PlantingRecord>,
    val wateringByRecordId: Map<UUID, WateringRecord>,
    val fertilizingByRecordId: Map<UUID, FertilizingRecord>,
    val pestControlByRecordId: Map<UUID, PestControlRecord>,
    val weedingByRecordId: Map<UUID, WeedingRecord>,
    val harvestByRecordId: Map<UUID, HarvestRecord>,
    val mediaRecordIds: Set<UUID>,
)

interface FarmingCycleReportSourceRepository {
    fun load(
        memberId: UUID,
        farmId: UUID,
        cropId: UUID,
    ): FarmingCycleReportSourceSnapshot
}
```

`FarmingRecordRepository`:

```kotlin
@Query("""
    select r from FarmingRecord r
    join fetch r.member
    join fetch r.farm
    join fetch r.crop
    where r.member.id = :memberId
      and r.farm.id = :farmId
      and r.crop.id = :cropId
      and r.isDeleted = false
    order by r.workedAt asc, r.id asc
""")
fun findReportSourceRecords(
    memberId: UUID,
    farmId: UUID,
    cropId: UUID,
): List<FarmingRecord>
```

각 상세 repository에는 해당 entity 반환 타입으로 아래 member method를 하나씩 넣는다.

```kotlin
fun findByRecord_IdIn(recordIds: Collection<UUID>): List<PlantingRecord>
fun findByRecord_IdIn(recordIds: Collection<UUID>): List<WateringRecord>
fun findByRecord_IdIn(recordIds: Collection<UUID>): List<FertilizingRecord>
fun findByRecord_IdIn(recordIds: Collection<UUID>): List<PestControlRecord>
fun findByRecord_IdIn(recordIds: Collection<UUID>): List<WeedingRecord>
fun findByRecord_IdIn(recordIds: Collection<UUID>): List<HarvestRecord>
```

`FarmingRecordMediaRepository`:

```kotlin
@Query("""
    select distinct m.record.id
    from FarmingRecordMedia m
    where m.record.id in :recordIds
""")
fun findDistinctRecordIdsByRecordIdIn(
    recordIds: Collection<UUID>,
): Set<UUID>
```

`FarmingCycleReportSourceRepositoryImpl.load`는 base 결과가 비면 빈 list/map/set snapshot을 즉시 반환한다. 결과가 있으면 ID 목록으로 상세 repository를 각각 한 번 호출하고 `associateBy { requireNotNull(it.record.id) }`로 map을 만든다.

- [ ] **Step 5: source repository 테스트를 통과시킨다**

Run:

```bash
cd backend && ./gradlew :domain:test --tests '*FarmingCycleReportSourceRepositoryTest'
```

Expected: PASS.

- [ ] **Step 6: loader 단위·카테고리 정규화 실패 테스트를 작성한다**

```kotlin
@Test
fun `loader normalizes merged fields without free text category fallback`() {
    whenever(repository.load(memberId, farmId, cropId)).thenReturn(fullSnapshot())

    val records = loader.load(ReportScope(memberId, farmId, cropId))

    assertThat(records.single { it.workType == WorkType.PLANTING }.planting!!.quantity)
        .isEqualByComparingTo("1250.0000")
    assertThat(records.single { it.workType == WorkType.PLANTING }.planting!!.quantityUnit)
        .isEqualTo("G")
    assertThat(records.single { it.workType == WorkType.FERTILIZING }
        .fertilizing!!.materialCategory.code)
        .isEqualTo("COMPOUND_FERTILIZER")
    assertThat(records.single { it.workType == WorkType.PEST_CONTROL }
        .pestControl!!.pesticideAmountUnit)
        .isEqualTo("ML")
    assertThat(records.single { it.workType == WorkType.HARVEST }.harvest!!.amountKg)
        .isNull()
    assertThat(records.single { it.workType == WorkType.HARVEST }.harvest!!.isFinalHarvest)
        .isTrue()
}
```

- [ ] **Step 7: source loader를 구현한다**

```kotlin
private fun normalizePlanting(detail: PlantingRecord): PlantingReportSource {
    val method = CategoryRef(detail.propagationMethod.name, detail.propagationMethod.label)
    return if (detail.propagationMethod == PropagationMethod.SEED) {
        val grams = when (detail.seedAmountUnit) {
            SeedAmountUnit.KG -> detail.seedAmount?.multiply(BigDecimal("1000"))
            SeedAmountUnit.G -> detail.seedAmount
            null -> null
        }
        PlantingReportSource(method, grams?.setScale(4), grams?.let { "G" })
    } else {
        PlantingReportSource(
            propagationMethod = method,
            quantity = detail.seedlingCount?.toBigDecimal()?.setScale(4),
            quantityUnit = detail.seedlingCount?.let { "JU" },
        )
    }
}

private fun normalizeGrowthMonths(detail: HarvestRecord): Int =
    when (detail.growthPeriodUnit) {
        GrowthPeriodUnit.MONTH -> detail.growthPeriod
        GrowthPeriodUnit.YEAR -> Math.multiplyExact(detail.growthPeriod, 12)
    }
```

비료·약제 category는 후속 PR의 stable code와 label만 `CategoryRef`로 매핑한다. `materialName`이나 `pesticideName`으로 `CategoryRef`를 만드는 코드는 금지한다.

나머지 exact 규칙:

- 시비량은 KG, 총 살포량은 L로 scale 4
- 약제량은 원래 ML/G code를 유지
- `amountUnknown=true`인 수확은 `amountKg=null`
- `isFinalHarvest`는 후속 PR Boolean을 그대로 복사
- optional 관수량·관수법·시비법·제초법은 null 유지
- detail이 필요한 work type인데 map에 없으면 `IllegalStateException("Missing detail for record $recordId")`
- `PRUNING`, `ETC`는 모든 detail property가 null
- `hasPhoto`는 `snapshot.mediaRecordIds.contains(recordId)`

- [ ] **Step 8: 마지막 수확 분할 실패 테스트를 작성한다**

```kotlin
@Test
fun `final harvest closes its cycle and partial harvest does not`() {
    val records = listOf(
        record("01", day = 1, WorkType.FERTILIZING),
        harvest("02", day = 10, final = false),
        harvest("03", day = 12, final = true),
        record("04", day = 20, WorkType.WATERING),
    )

    val slices = partitioner.partition(records)

    assertThat(slices).hasSize(2)
    assertThat(slices[0].status).isEqualTo(FarmingCycleReportStatus.COMPLETED)
    assertThat(slices[0].records.map { it.id }).containsExactly(
        id("01"), id("02"), id("03"),
    )
    assertThat(slices[1].status).isEqualTo(FarmingCycleReportStatus.ACTIVE)
    assertThat(slices[1].startBasis)
        .isEqualTo(FarmingCycleStartBasis.AFTER_PREVIOUS_FINAL_HARVEST)
}

@Test
fun `work before planting and ETC stay inside the cycle`() {
    val records = listOf(
        record("01", day = 1, WorkType.ETC),
        record("02", day = 3, WorkType.PLANTING),
        harvest("03", day = 90, final = true),
    )

    assertThat(partitioner.partition(records).single().records.map { it.workType })
        .containsExactly(WorkType.ETC, WorkType.PLANTING, WorkType.HARVEST)
}

@Test
fun `equal timestamps are deterministically ordered by id`() {
    val slices = partitioner.partition(
        listOf(
            harvest("02", day = 1, final = true),
            record("01", day = 1, WorkType.WATERING),
        ),
    )

    assertThat(slices.single().records.map { it.id }).containsExactly(id("01"), id("02"))
}
```

- [ ] **Step 9: 순수 partitioner를 구현한다**

```kotlin
@Component
class FarmingCyclePartitioner {
    fun partition(records: List<CycleReportSourceRecord>): List<CycleSlice> {
        val sorted = records.sortedWith(
            compareBy(CycleReportSourceRecord::workedAt, CycleReportSourceRecord::id),
        )
        if (sorted.isEmpty()) return emptyList()

        val result = mutableListOf<CycleSlice>()
        val current = mutableListOf<CycleReportSourceRecord>()
        var hasPreviousFinalHarvest = false

        sorted.forEach { record ->
            current += record
            if (record.harvest?.isFinalHarvest == true) {
                result += CycleSlice(
                    status = FarmingCycleReportStatus.COMPLETED,
                    startBasis = if (hasPreviousFinalHarvest) {
                        FarmingCycleStartBasis.AFTER_PREVIOUS_FINAL_HARVEST
                    } else {
                        FarmingCycleStartBasis.FIRST_RECORD
                    },
                    records = current.toList(),
                )
                current.clear()
                hasPreviousFinalHarvest = true
            }
        }

        if (current.isNotEmpty()) {
            result += CycleSlice(
                status = FarmingCycleReportStatus.ACTIVE,
                startBasis = if (hasPreviousFinalHarvest) {
                    FarmingCycleStartBasis.AFTER_PREVIOUS_FINAL_HARVEST
                } else {
                    FarmingCycleStartBasis.FIRST_RECORD
                },
                records = current.toList(),
            )
        }
        return result
    }
}
```

Run:

```bash
cd backend && ./gradlew :application:test \
  --tests '*FarmingCycleReportSourceLoaderTest' \
  --tests '*FarmingCyclePartitionerTest'
```

Expected: PASS.

- [ ] **Step 10: Task 2를 커밋한다**

```bash
git add \
  backend/domain/src/main/kotlin/com/chamchamcham/domain/farming \
  backend/domain/src/test/kotlin/com/chamchamcham/domain/farming/FarmingCycleReportSourceRepositoryTest.kt \
  backend/application/src/main/kotlin/com/chamchamcham/application/report/CycleReportSource.kt \
  backend/application/src/main/kotlin/com/chamchamcham/application/report/FarmingCycleReportSourceLoader.kt \
  backend/application/src/main/kotlin/com/chamchamcham/application/report/FarmingCyclePartitioner.kt \
  backend/application/src/test/kotlin/com/chamchamcham/application/report/FarmingCycleReportSourceLoaderTest.kt \
  backend/application/src/test/kotlin/com/chamchamcham/application/report/FarmingCyclePartitionerTest.kt
git commit -m "feat(report): 영농기록을 마지막 수확 주기로 정규화" \
  -m "Constraint: 병합된 카테고리와 마지막 수확 계약만 사용" \
  -m "Rejected: 자유 입력명을 임시 카테고리 키로 사용 | 집계 안정성 훼손" \
  -m "Confidence: high" \
  -m "Scope-risk: moderate" \
  -m "Tested: source repository loader and partition tests"
```
---

### Task 3: 모든 작업 유형의 타입 통계 계산

**Files:**
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/report/StatisticsMath.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/report/CycleReportStatisticsCalculator.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/report/StatisticsMathTest.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/report/CycleReportStatisticsCalculatorTest.kt`

**Interfaces:**
- Consumes: `List<CycleReportSourceRecord>`
- Produces: `CycleReportStatisticsCalculator.calculate(records): CycleReportStatistics`

- [ ] **Step 1: 반올림·간격 계산 실패 테스트를 작성한다**

```kotlin
@Test
fun `percentage and average use scale two half up`() {
    assertThat(StatisticsMath.percentage(1, 3)).isEqualByComparingTo("33.33")
    assertThat(StatisticsMath.average(listOf(BigDecimal("1"), BigDecimal("2"))))
        .isEqualByComparingTo("1.50")
}

@Test
fun `average interval uses distinct dates`() {
    assertThat(
        StatisticsMath.averageIntervalDays(
            listOf(date(1), date(1), date(4), date(10)),
        ),
    ).isEqualByComparingTo("4.50")
}

@Test
fun `empty values return null`() {
    assertThat(StatisticsMath.percentage(0, 0)).isNull()
    assertThat(StatisticsMath.average(emptyList())).isNull()
    assertThat(StatisticsMath.averageIntervalDays(emptyList())).isNull()
}
```

- [ ] **Step 2: 실패를 확인하고 수학 함수를 구현한다**

Run:

```bash
cd backend && ./gradlew :application:test --tests '*StatisticsMathTest'
```

Expected: FAIL because `StatisticsMath` does not exist.

```kotlin
object StatisticsMath {
    fun percentage(numerator: Int, denominator: Int): BigDecimal? =
        denominator.takeIf { it > 0 }?.let {
            BigDecimal(numerator).multiply(BigDecimal("100"))
                .divide(BigDecimal(it), 2, RoundingMode.HALF_UP)
        }

    fun percentage(
        numerator: BigDecimal,
        denominator: BigDecimal,
    ): BigDecimal? = denominator.takeIf { it.compareTo(BigDecimal.ZERO) != 0 }?.let {
        numerator.multiply(BigDecimal("100"))
            .divide(it, 2, RoundingMode.HALF_UP)
    }

    fun average(values: List<BigDecimal>): BigDecimal? =
        values.takeIf { it.isNotEmpty() }
            ?.sumOf { it }
            ?.divide(BigDecimal(values.size), 2, RoundingMode.HALF_UP)

    fun averageIntervalDays(dates: List<LocalDate>): BigDecimal? {
        val sorted = dates.distinct().sorted()
        if (sorted.size < 2) return null
        return average(
            sorted.zipWithNext { first, second ->
                BigDecimal(ChronoUnit.DAYS.between(first, second))
            },
        )
    }
}
```

Run the same test again. Expected: PASS.

- [ ] **Step 3: 모든 작업 객체와 빈 작업 계약 테스트를 작성한다**

```kotlin
@Test
fun `every work type receives direct common fields`() {
    val result = calculator.calculate(allWorkTypeFixture())

    assertThat(result.planting.recordCount).isEqualTo(1)
    assertThat(result.watering.recordCount).isEqualTo(1)
    assertThat(result.fertilizing.recordCount).isEqualTo(1)
    assertThat(result.pestControl.recordCount).isEqualTo(1)
    assertThat(result.weeding.recordCount).isEqualTo(1)
    assertThat(result.pruning.recordCount).isEqualTo(1)
    assertThat(result.harvest.recordCount).isEqualTo(1)
    assertThat(result.etc.recordCount).isEqualTo(1)
    assertThat(result.watering.firstWorkedOn).isEqualTo(date(2))
    assertThat(result.harvest.photoAttachedRecordCount).isEqualTo(1)
}

@Test
fun `missing work type remains an empty typed object`() {
    val result = calculator.calculate(listOf(watering(day = 1)))

    assertThat(result.planting).isEqualTo(PlantingStatistics.empty())
    assertThat(result.pruning).isEqualTo(CommonOnlyStatistics.empty())
    assertThat(result.etc).isEqualTo(CommonOnlyStatistics.empty())
}
```

- [ ] **Step 4: 작업별 수량·분포 테스트를 작성한다**

```kotlin
@Test
fun `planting separates seed grams and JU quantities`() {
    val result = calculator.calculate(
        listOf(seedPlanting("500"), seedPlanting("750"), cuttingPlanting(20)),
    ).planting

    assertThat(result.propagationMethods.first { it.code == "SEED" }.totalQuantity)
        .isEqualByComparingTo("1250.0000")
    assertThat(result.propagationMethods.first { it.code == "SEED" }.quantityUnit)
        .isEqualTo("G")
    assertThat(result.propagationMethods.first { it.code == "CUTTING" }.quantityUnit)
        .isEqualTo("JU")
}

@Test
fun `watering includes missing selections and has no adequacy fields`() {
    val result = calculator.calculate(
        listOf(watering("LOW", "DRIP"), watering(null, "MANUAL")),
    ).watering

    assertThat(result.amountDistribution.map { it.code to it.count })
        .containsExactlyInAnyOrder("LOW" to 1, "MISSING" to 1)
    assertThat(WateringStatistics::class.memberProperties.map { it.name })
        .doesNotContain("adequacy", "shortage", "excess")
}

@Test
fun `fertilizing groups stable categories and nullable methods`() {
    val result = calculator.calculate(
        listOf(fertilizing("NPK", "10", "SOIL"), fertilizing("NPK", "5", null)),
    ).fertilizing

    assertThat(result.totalAmountKg).isEqualByComparingTo("15.0000")
    assertThat(result.materialCategories.single().amountKg)
        .isEqualByComparingTo("15.0000")
    assertThat(result.methodDistribution.map { it.code to it.count })
        .containsExactlyInAnyOrder("SOIL" to 1, "MISSING" to 1)
}

@Test
fun `pesticide ML and G remain separate`() {
    val result = calculator.calculate(
        listOf(
            pest("FUNGICIDE", "10", "ML", "100"),
            pest("FUNGICIDE", "5", "G", "50"),
        ),
    ).pestControl

    assertThat(result.pesticideAmounts.map { it.unit to it.amount })
        .containsExactlyInAnyOrder(
            "ML" to BigDecimal("10.0000"),
            "G" to BigDecimal("5.0000"),
        )
    assertThat(result.totalSprayAmountLiters).isEqualByComparingTo("150.0000")
}

@Test
fun `unknown harvest amount counts the record but not quantity`() {
    val result = calculator.calculate(
        listOf(
            harvest(null, "ROOT_BARK", 10, final = false),
            harvest("30", "LEAF", 12, final = true),
        ),
    ).harvest

    assertThat(result.recordCount).isEqualTo(2)
    assertThat(result.totalAmountKg).isEqualByComparingTo("30.0000")
    assertThat(result.averageAmountKg).isEqualByComparingTo("30.00")
    assertThat(result.amountCoverage).isEqualTo(Coverage(1, 2))
    assertThat(result.medicinalParts.sumOf { it.recordCount }).isEqualTo(2)
    assertThat(result.finalGrowthPeriodMonths).isEqualTo(12)
    assertThat(result.growthPeriodRangeMonths).isEqualTo(GrowthPeriodRange(10, 12))
}
```

- [ ] **Step 5: 단일 순수 계산기를 구현한다**

```kotlin
@Component
class CycleReportStatisticsCalculator {
    fun calculate(records: List<CycleReportSourceRecord>): CycleReportStatistics {
        val grouped = records.groupBy { it.workType }
        return CycleReportStatistics(
            planting = calculatePlanting(grouped[WorkType.PLANTING].orEmpty()),
            watering = calculateWatering(grouped[WorkType.WATERING].orEmpty()),
            fertilizing = calculateFertilizing(grouped[WorkType.FERTILIZING].orEmpty()),
            pestControl = calculatePestControl(grouped[WorkType.PEST_CONTROL].orEmpty()),
            weeding = calculateWeeding(grouped[WorkType.WEEDING].orEmpty()),
            pruning = commonOnly(grouped[WorkType.PRUNING].orEmpty()),
            harvest = calculateHarvest(grouped[WorkType.HARVEST].orEmpty()),
            etc = commonOnly(grouped[WorkType.ETC].orEmpty()),
        )
    }
}
```

계산기 내부에서만 다음 값을 사용하고 저장 JSON에는 넣지 않는다.

```kotlin
private data class CommonValues(
    val recordCount: Int,
    val firstWorkedOn: LocalDate?,
    val lastWorkedOn: LocalDate?,
    val workedDayCount: Int,
    val averageIntervalDays: BigDecimal?,
    val photoAttachedRecordCount: Int,
    val photoAttachmentRatePct: BigDecimal?,
    val weatherDistribution: List<CountDistribution>,
    val averageTemperatureC: BigDecimal?,
)
```

공통 계산과 선택 분포는 다음 코드로 고정한다.

```kotlin
private fun common(records: List<CycleReportSourceRecord>): CommonValues {
    val dates = records.map { it.workedAt.toLocalDate() }
    val photoCount = records.count { it.hasPhoto }
    return CommonValues(
        recordCount = records.size,
        firstWorkedOn = dates.minOrNull(),
        lastWorkedOn = dates.maxOrNull(),
        workedDayCount = dates.distinct().size,
        averageIntervalDays = StatisticsMath.averageIntervalDays(dates),
        photoAttachedRecordCount = photoCount,
        photoAttachmentRatePct = StatisticsMath.percentage(photoCount, records.size),
        weatherDistribution = distribution(
            values = records.map {
                it.weatherCondition.trim().takeIf(String::isNotEmpty)
                    ?.let { value -> CategoryRef(value, value) }
            },
            denominator = records.size,
        ),
        averageTemperatureC = StatisticsMath.average(
            records.map { BigDecimal(it.weatherTemperature) },
        ),
    )
}

private fun distribution(
    values: List<CategoryRef?>,
    denominator: Int,
): List<CountDistribution> =
    values.map { it ?: CategoryRef("MISSING", "미입력") }
        .groupingBy { it }
        .eachCount()
        .map { (item, count) ->
            CountDistribution(
                code = item.code,
                label = item.label,
                count = count,
                ratePct = requireNotNull(StatisticsMath.percentage(count, denominator)),
            )
        }
        .sortedWith(compareByDescending<CountDistribution> { it.count }.thenBy { it.code })
```

작업별 함수는 아래 수식과 정렬을 그대로 적용한다.

| 함수 | 계산 |
| --- | --- |
| `calculatePlanting` | propagation code별 count/rate; SEED는 G, 나머지는 JU; non-null quantity만 합계; coverage는 값 입력 건수/그룹 기록 수 |
| `calculateWatering` | amount와 method 각각 `distribution`; null은 MISSING |
| `calculateFertilizing` | non-null kg 합계 scale 4·평균 scale 2·coverage; category count/rate와 amount/rate; method 분포; category+method count/rate |
| `calculatePestControl` | category count 분포; unit별 amount와 coverage; category+unit별 amount와 coverage; spray L 합계와 coverage; trim한 nonblank target exact count |
| `calculateWeeding` | method `distribution`; null은 MISSING |
| `calculateHarvest` | non-null kg 합계 scale 4·평균 scale 2·coverage; 부위별 count/rate·known amount·amount rate·coverage; 첫/마지막 날짜; final record growth months; 전체 growth min/max |
| `commonOnly` | `CommonValues`의 9개 값을 `CommonOnlyStatistics` 생성자에 복사 |

추가 규칙:

- 합계 수량은 scale 4, 평균·비율은 scale 2와 HALF_UP
- 분포는 count 내림차순 후 code 오름차순
- category+method는 category code 후 method code 오름차순
- category+method의 `recordRatePct` 분모는 전체 시비 기록이 아니라 해당 category의 기록 수
- 전체 금액 분모가 0이면 amount rate 항목을 만들지 않고 관련 목록을 빈 배열로 둔다.
- 병해충 target null/blank는 빈도 목록에서 제외한다.
- final harvest가 없으면 `finalGrowthPeriodMonths=null`
- harvest records가 없으면 range null, 하나 이상이면 min/max를 모두 채운다.
- 모든 concrete 생성자에 `CommonValues`의 9개 값을 직접 전달한다.

- [ ] **Step 6: 계산기 테스트를 통과시킨다**

Run:

```bash
cd backend && ./gradlew :application:test \
  --tests '*StatisticsMathTest' \
  --tests '*CycleReportStatisticsCalculatorTest'
```

Expected: PASS.

- [ ] **Step 7: Task 3을 커밋한다**

```bash
git add \
  backend/application/src/main/kotlin/com/chamchamcham/application/report/StatisticsMath.kt \
  backend/application/src/main/kotlin/com/chamchamcham/application/report/CycleReportStatisticsCalculator.kt \
  backend/application/src/test/kotlin/com/chamchamcham/application/report/StatisticsMathTest.kt \
  backend/application/src/test/kotlin/com/chamchamcham/application/report/CycleReportStatisticsCalculatorTest.kt
git commit -m "feat(report): 모든 작업의 타입 통계를 계산" \
  -m "Constraint: 공통 필드는 각 작업 객체에 직접 포함" \
  -m "Directive: 관수 적정성을 기록 횟수로 해석하지 말 것" \
  -m "Confidence: high" \
  -m "Scope-risk: moderate" \
  -m "Tested: statistics math and all-work calculator tests"
```
---

### Task 4: 저장 프로젝션 재계산과 영농기록 CRUD 연결

**Files:**
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/farm/FarmRepository.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/report/FarmingCycleReportProjectionService.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/farming/FarmingRecordService.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/report/FarmingCycleReportProjectionServiceTest.kt`
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/farming/FarmingRecordServiceTest.kt`

**Interfaces:**
- Consumes: owned farm lock, source loader, partitioner, calculator, report repository
- Produces: `FarmingCycleReportProjectionService.rebuild(scope)`, `rebuildAll(scopes)`

- [ ] **Step 1: farm lock 순서 실패 테스트를 작성한다**

```kotlin
@Test
fun `rebuild locks owned farm before reading source records`() {
    whenever(farmRepository.findOwnedByIdForReportUpdate(farmId, memberId))
        .thenReturn(farm)
    whenever(cropRepository.findById(cropId)).thenReturn(Optional.of(crop))
    whenever(reportRepository.findAllCurrent(memberId, farmId, cropId))
        .thenReturn(emptyList())
    whenever(sourceLoader.load(scope)).thenReturn(listOf(watering(day = 1)))
    whenever(partitioner.partition(any())).thenReturn(listOf(activeSlice()))

    service.rebuild(scope)

    inOrder(farmRepository, sourceLoader).apply {
        verify(farmRepository).findOwnedByIdForReportUpdate(farmId, memberId)
        verify(sourceLoader).load(scope)
    }
}
```

- [ ] **Step 2: 소유권을 포함한 짧은 비관적 잠금을 추가한다**

`FarmRepository`:

```kotlin
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("""
    select f from Farm f
    where f.id = :farmId
      and f.owner.id = :memberId
""")
fun findOwnedByIdForReportUpdate(
    farmId: UUID,
    memberId: UUID,
): Farm?
```

이 잠금은 같은 밭의 겹친 저장 요청이 리포트를 서로 덮어쓰지 않게 DB 작업 동안만 순서를 정한다. 사용자 상태에 잠금 필드를 저장하지 않는다.

- [ ] **Step 3: reconciliation 실패 테스트를 작성한다**

| 테스트 | fixture | 반드시 검증할 값 |
| --- | --- | --- |
| first record creates active | existing 없음, ACTIVE slice 1개 | 저장 report status ACTIVE, watering count 1 |
| latest final completes active | existing ACTIVE, newest COMPLETED slice | 같은 report ID, COMPLETED, final record ID, endsAt |
| records after final preserve active | existing ACTIVE, COMPLETED+ACTIVE slices | 기존 ACTIVE ID는 trailing slice, 새 COMPLETED 1건 |
| historical boundary matching | existing COMPLETED+ACTIVE, 같은 시각의 UUID가 다른 기록 | final record ID로 COMPLETED 매칭, ACTIVE ID 유지 |
| changed completed boundary | old final ID report, new final ID slice | 새 COMPLETED 생성, old report SUPERSEDED |
| removed final boundary | existing COMPLETED+ACTIVE, ACTIVE slice 1개 | ACTIVE로 병합, COMPLETED SUPERSEDED |
| empty source | existing COMPLETED+ACTIVE, slice 없음 | 둘 다 SUPERSEDED |
| idempotent rebuild | 동일 final ID·경계·통계 | sourceRevision 유지 |
| ordered rebuildAll | 중복·역순 scope 입력 | distinct 후 member/farm/crop 순으로 lock |

`historical boundary matching` fixture는 같은 `workedAt`의 서로 다른 UUID 기록을 포함해 timestamp만으로 매칭하지 않는지 검증한다. 각 테스트는 아래와 같이 최종 저장 상태를 직접 assert한다.

```kotlin
@Test
fun `latest final harvest converts active report to completed`() {
    val active = persistedActiveReport()
    whenever(reportRepository.findAllCurrent(memberId, farmId, cropId))
        .thenReturn(listOf(active))
    whenever(sourceLoader.load(scope)).thenReturn(recordsEndingWithFinalHarvest())
    whenever(partitioner.partition(any())).thenReturn(listOf(completedSlice()))

    service.rebuild(scope)

    assertThat(active.status).isEqualTo(FarmingCycleReportStatus.COMPLETED)
    assertThat(active.finalHarvestRecord?.id).isEqualTo(finalHarvestRecordId)
    assertThat(active.endsAt).isEqualTo(finalHarvestWorkedAt)
    verify(reportRepository).save(active)
}
```

- [ ] **Step 4: projection service를 구현한다**

```kotlin
@Service
@Transactional
class FarmingCycleReportProjectionService(
    private val farmRepository: FarmRepository,
    private val cropRepository: CropRepository,
    private val farmingRecordRepository: FarmingRecordRepository,
    private val sourceLoader: FarmingCycleReportSourceLoader,
    private val partitioner: FarmingCyclePartitioner,
    private val statisticsCalculator: CycleReportStatisticsCalculator,
    private val reportRepository: FarmingCycleReportRepository,
) {
    fun rebuild(scope: ReportScope) {
        val farm = farmRepository.findOwnedByIdForReportUpdate(
            scope.farmId,
            scope.memberId,
        ) ?: throw BusinessException(ErrorCode.FARM_NOT_FOUND)
        val crop = cropRepository.findById(scope.cropId).orElseThrow {
            BusinessException(ErrorCode.CROP_NOT_FOUND)
        }

        val existing = reportRepository.findAllCurrent(
            scope.memberId,
            scope.farmId,
            scope.cropId,
        )
        val slices = partitioner.partition(sourceLoader.load(scope))
        val unmatched = existing.associateBy { requireNotNull(it.id) }.toMutableMap()

        slices.forEachIndexed { index, slice ->
            val matched = takeMatchingReport(
                unmatched = unmatched,
                slice = slice,
                allowActiveCompletion = index == slices.lastIndex &&
                    slice.status == FarmingCycleReportStatus.COMPLETED,
            )
            val projection = toProjection(slice)
            val report = matched?.also { it.applyProjection(projection) }
                ?: FarmingCycleReport.create(farm.owner, farm, crop, projection)
            reportRepository.save(report)
        }

        val obsolete = unmatched.values.filter { it.supersede() }
        if (obsolete.isNotEmpty()) {
            reportRepository.saveAll(obsolete)
        }
    }

    fun rebuildAll(scopes: Collection<ReportScope>) {
        scopes.distinct()
            .sortedWith(
                compareBy(
                    ReportScope::memberId,
                    ReportScope::farmId,
                    ReportScope::cropId,
                ),
            )
            .forEach(::rebuild)
    }

    private fun takeMatchingReport(
        unmatched: MutableMap<UUID, FarmingCycleReport>,
        slice: CycleSlice,
        allowActiveCompletion: Boolean,
    ): FarmingCycleReport? {
        val exactCompleted = slice.finalHarvestRecordId?.let { finalId ->
            unmatched.values.firstOrNull {
                it.status == FarmingCycleReportStatus.COMPLETED &&
                    it.finalHarvestRecord?.id == finalId
            }
        }
        val active = unmatched.values.singleOrNull {
            it.status == FarmingCycleReportStatus.ACTIVE
        }
        val selected = when {
            exactCompleted != null -> exactCompleted
            slice.status == FarmingCycleReportStatus.ACTIVE -> active
            allowActiveCompletion -> active
            else -> null
        }
        selected?.let { unmatched.remove(requireNotNull(it.id)) }
        return selected
    }

    private fun toProjection(slice: CycleSlice): FarmingCycleReportProjection {
        val completed = slice.status == FarmingCycleReportStatus.COMPLETED
        return FarmingCycleReportProjection(
            status = slice.status,
            startsAt = slice.records.first().workedAt,
            endsAt = slice.records.last().workedAt.takeIf { completed },
            startBasis = slice.startBasis,
            finalHarvestRecord = slice.finalHarvestRecordId
                ?.let(farmingRecordRepository::getReferenceById),
            statisticsSchemaVersion = 1,
            statistics = statisticsCalculator.calculate(slice.records),
        )
    }
}
```

Matching 규칙:

1. 기존 COMPLETED는 같은 `finalHarvestRecord.id`로만 재사용한다.
2. ACTIVE slice는 기존 ACTIVE를 재사용한다.
3. ACTIVE를 COMPLETED로 바꾸는 것은 trailing ACTIVE가 없고 newest slice가 COMPLETED인 경우뿐이다.
4. 매칭되지 않은 기존 ACTIVE/COMPLETED는 삭제하지 않고 SUPERSEDED로 바꾼다.

- [ ] **Step 5: projection 테스트를 통과시킨다**

Run:

```bash
cd backend && ./gradlew :application:test \
  --tests '*FarmingCycleReportProjectionServiceTest'
```

Expected: PASS.

- [ ] **Step 6: FarmingRecordService 연결 실패 테스트를 작성한다**

기존 test fixture에 `FarmingCycleReportProjectionService` mock을 추가한다.

```kotlin
@Test
fun `create rebuilds scope after detail and media are attached`() {
    service.create(createCommand(withMedia = true))

    val order = inOrder(
        harvestRecordRepository,
        farmingRecordMediaRepository,
        projectionService,
    )
    order.verify(harvestRecordRepository).save(any())
    order.verify(farmingRecordMediaRepository).saveAll(anyList())
    order.verify(projectionService).rebuild(
        ReportScope(memberId, farmId, cropId),
    )
}

@Test
fun `update rebuilds old and new scopes when farm or crop changes`() {
    service.update(updateCommand(farmId = newFarmId, cropId = newCropId))

    verify(projectionService).rebuildAll(
        listOf(
            ReportScope(memberId, oldFarmId, oldCropId),
            ReportScope(memberId, newFarmId, newCropId),
        ),
    )
}

@Test
fun `delete soft deletes before rebuilding its scope`() {
    service.delete(FarmingRecordCommand.Delete(memberId, recordId))

    inOrder(record, projectionService).apply {
        verify(record).softDelete()
        verify(projectionService).rebuild(
            ReportScope(memberId, farmId, cropId),
        )
    }
}

@Test
fun `projection failure is propagated from the record transaction`() {
    doThrow(IllegalStateException("projection failed"))
        .whenever(projectionService)
        .rebuild(ReportScope(memberId, farmId, cropId))

    assertThatThrownBy { service.create(createCommand()) }
        .isInstanceOf(IllegalStateException::class.java)
        .hasMessage("projection failed")
}
```

- [ ] **Step 7: 기록 CRUD의 같은 transaction에서 projection을 호출한다**

`FarmingRecordService`의 기존 `@Transactional` create/update/delete 흐름에서 상세와 media 반영 뒤 다음을 호출한다.

```kotlin
// create
projectionService.rebuild(
    ReportScope(command.memberId, command.farmId, command.cropId),
)

// update: record.update 전에 이전 범위를 캡처한다.
val previousScope = ReportScope(
    command.memberId,
    requireNotNull(record.farm.id),
    requireNotNull(record.crop.id),
)
// record/detail/media update 후
val currentScope = ReportScope(
    command.memberId,
    requireNotNull(record.farm.id),
    requireNotNull(record.crop.id),
)
projectionService.rebuildAll(listOf(previousScope, currentScope))

// delete: softDelete 전에 범위를 캡처하고 삭제 뒤 호출한다.
record.softDelete()
projectionService.rebuild(scope)
```

기존 validation, ownership, detail replacement, media ordering은 변경하지 않는다. 내부 projection 예외를 catch하지 않으므로 record/detail/media와 report 변경이 함께 rollback된다.

- [ ] **Step 8: CRUD 연결 테스트를 통과시킨다**

Run:

```bash
cd backend && ./gradlew :application:test \
  --tests '*FarmingRecordServiceTest' \
  --tests '*FarmingCycleReportProjectionServiceTest'
```

Expected: PASS.

- [ ] **Step 9: Task 4를 커밋한다**

```bash
git add \
  backend/domain/src/main/kotlin/com/chamchamcham/domain/farm/FarmRepository.kt \
  backend/application/src/main/kotlin/com/chamchamcham/application/report/FarmingCycleReportProjectionService.kt \
  backend/application/src/main/kotlin/com/chamchamcham/application/farming/FarmingRecordService.kt \
  backend/application/src/test/kotlin/com/chamchamcham/application/report/FarmingCycleReportProjectionServiceTest.kt \
  backend/application/src/test/kotlin/com/chamchamcham/application/farming/FarmingRecordServiceTest.kt
git commit -m "feat(report): 기록 변경에 주기 리포트를 동기화" \
  -m "Constraint: 동일 밭 재계산은 부모 행 잠금으로 순차 처리" \
  -m "Rejected: 필드별 증감 갱신 | 경계 변경 시 불일치 위험" \
  -m "Confidence: high" \
  -m "Scope-risk: broad" \
  -m "Directive: 기록 transaction 밖으로 projection 갱신을 분리하지 말 것" \
  -m "Tested: projection reconciliation and farming record service tests"
```
---

### Task 5: 현재·완료 목록·직전 주기 조회

**Files:**
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/report/FarmingCycleReportQueryRepository.kt`
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/report/FarmingCycleReportQueryRepositoryImpl.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/report/FarmingCycleReportCursorPayload.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/report/FarmingCycleReportSearchCondition.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/report/FarmingCycleReportResult.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/report/FarmingCycleReportQueryService.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/exception/ErrorCode.kt`
- Test: `backend/domain/src/test/kotlin/com/chamchamcham/domain/report/FarmingCycleReportQueryRepositoryTest.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/report/FarmingCycleReportQueryServiceTest.kt`

**Interfaces:**
- Consumes: 저장된 `FarmingCycleReport`, `OpaqueCursorCodec`, owned farm and crop validation
- Produces: `getCurrent`, `listCompleted`, `getDetail`

- [ ] **Step 1: 완료 목록과 직전 주기 repository 실패 테스트를 작성한다**

```kotlin
@Test
fun `completed page excludes active superseded and other member reports`() {
    persistCompleted(endedAt = day(30), finalId = uuid("30"))
    persistCompleted(endedAt = day(20), finalId = uuid("20"))
    persistActive()
    persistSuperseded()
    persistCompleted(owner = otherMember)

    val result = repository.searchCompleted(
        condition(size = 10),
    )

    assertThat(result.rows.map { it.endsAt })
        .containsExactly(day(30), day(20))
}

@Test
fun `completed cursor resolves equal end times with final harvest id`() {
    persistCompleted(endedAt = day(30), finalId = uuid("03"))
    persistCompleted(endedAt = day(30), finalId = uuid("02"))
    persistCompleted(endedAt = day(30), finalId = uuid("01"))

    val result = repository.searchCompleted(
        condition(
            cursor = FarmingCycleReportQueryRepository.Cursor(
                endsAt = day(30),
                finalHarvestRecordId = uuid("02"),
            ),
            size = 10,
        ),
    )

    assertThat(result.rows.map { it.finalHarvestRecord?.id })
        .containsExactly(uuid("01"))
}

@Test
fun `previous completed report uses the same boundary ordering`() {
    val previous = persistCompleted(endedAt = day(30), finalId = uuid("01"))
    val selected = persistCompleted(endedAt = day(30), finalId = uuid("02"))

    assertThat(
        repository.findPreviousCompleted(
            memberId = memberId,
            farmId = farmId,
            cropId = cropId,
            endsAt = requireNotNull(selected.endsAt),
            finalHarvestRecordId = requireNotNull(selected.finalHarvestRecord?.id),
        )?.id,
    ).isEqualTo(previous.id)
}
```

- [ ] **Step 2: 실패를 확인한다**

Run:

```bash
cd backend && ./gradlew :domain:test --tests '*FarmingCycleReportQueryRepositoryTest'
```

Expected: FAIL because query repository does not exist.

- [ ] **Step 3: query repository 계약과 JPQL 구현을 작성한다**

```kotlin
interface FarmingCycleReportQueryRepository {
    data class Cursor(
        val endsAt: LocalDateTime,
        val finalHarvestRecordId: UUID,
    )

    data class SearchCondition(
        val memberId: UUID,
        val farmId: UUID,
        val cropId: UUID,
        val cursor: Cursor?,
        val size: Int,
    )

    data class SearchResult(val rows: List<FarmingCycleReport>)

    fun searchCompleted(condition: SearchCondition): SearchResult

    fun findLatestCompleted(
        memberId: UUID,
        farmId: UUID,
        cropId: UUID,
    ): FarmingCycleReport?

    fun findPreviousCompleted(
        memberId: UUID,
        farmId: UUID,
        cropId: UUID,
        endsAt: LocalDateTime,
        finalHarvestRecordId: UUID,
    ): FarmingCycleReport?
}
```

`FarmingCycleReportQueryRepositoryImpl`은 `EntityManager`로 다음 predicate를 조립한다.

```kotlin
private fun completedScopeWhere(): MutableList<String> = mutableListOf(
    "r.member.id = :memberId",
    "r.farm.id = :farmId",
    "r.crop.id = :cropId",
    "r.status = :completed",
)

private fun cursorPredicate(): String =
    """
    (
      r.endsAt < :cursorEndsAt
      or (
        r.endsAt = :cursorEndsAt
        and r.finalHarvestRecord.id < :cursorFinalHarvestRecordId
      )
    )
    """.trimIndent()
```

검색·최신·직전 query 모두 아래 정렬을 공유한다.

```sql
order by r.endsAt desc, r.finalHarvestRecord.id desc
```

- `searchCompleted`: cursor가 있으면 cursor predicate를 추가하고 `maxResults=condition.size`
- `findLatestCompleted`: cursor 없이 `maxResults=1`
- `findPreviousCompleted`: 선택 리포트의 endsAt/final ID를 cursor로 사용하고 `maxResults=1`
- status parameter는 `FarmingCycleReportStatus.COMPLETED`
- ACTIVE와 SUPERSEDED는 어떤 query에도 포함하지 않는다.

- [ ] **Step 4: repository 테스트를 통과시킨다**

Run:

```bash
cd backend && ./gradlew :domain:test --tests '*FarmingCycleReportQueryRepositoryTest'
```

Expected: PASS.

- [ ] **Step 5: application result와 cursor 타입을 구현한다**

```kotlin
data class FarmingCycleReportCursorPayload(
    val endsAt: LocalDateTime,
    val finalHarvestRecordId: UUID,
)

data class FarmingCycleReportSearchCondition(
    val memberId: UUID,
    val farmId: UUID,
    val cropId: UUID,
    val cursor: String?,
    val size: Int,
)

object FarmingCycleReportResult {
    data class Metadata(
        val id: UUID,
        val farmId: UUID,
        val farmName: String,
        val cropId: UUID,
        val cropName: String,
        val status: FarmingCycleReportStatus,
        val startsAt: LocalDateTime,
        val endsAt: LocalDateTime?,
        val startBasis: FarmingCycleStartBasis,
        val sourceRevision: Long,
    )

    data class Snapshot(
        val id: UUID,
        val farmId: UUID,
        val farmName: String,
        val cropId: UUID,
        val cropName: String,
        val status: FarmingCycleReportStatus,
        val startsAt: LocalDateTime,
        val endsAt: LocalDateTime?,
        val startBasis: FarmingCycleStartBasis,
        val finalHarvestRecordId: UUID?,
        val statisticsSchemaVersion: Int,
        val sourceRevision: Long,
        val statistics: CycleReportStatistics,
    )

    data class Current(
        val current: Snapshot?,
        val previous: Snapshot?,
    )

    data class Detail(
        val selected: Snapshot,
        val previous: Snapshot?,
    )

    data class Page(
        val items: List<Metadata>,
        val nextCursor: String?,
    )
}
```

- [ ] **Step 6: query service 실패 테스트를 작성한다**

```kotlin
@Test
fun `current returns active and latest completed`() {
    whenever(reportRepository.findByMember_IdAndFarm_IdAndCrop_IdAndStatus(
        memberId, farmId, cropId, FarmingCycleReportStatus.ACTIVE,
    )).thenReturn(active)
    whenever(queryRepository.findLatestCompleted(memberId, farmId, cropId))
        .thenReturn(previous)

    val result = service.getCurrent(memberId, farmId, cropId)

    assertThat(result.current?.id).isEqualTo(active.id)
    assertThat(result.previous?.id).isEqualTo(previous.id)
}

@Test
fun `current without active returns null and latest completed as previous`() {
    whenever(reportRepository.findByMember_IdAndFarm_IdAndCrop_IdAndStatus(
        memberId, farmId, cropId, FarmingCycleReportStatus.ACTIVE,
    )).thenReturn(null)
    whenever(queryRepository.findLatestCompleted(memberId, farmId, cropId))
        .thenReturn(previous)

    val result = service.getCurrent(memberId, farmId, cropId)

    assertThat(result.current).isNull()
    assertThat(result.previous?.id).isEqualTo(previous.id)
}

@Test
fun `detail returns selected report and immediately previous completed`() {
    whenever(reportRepository.findByIdAndMember_Id(reportId, memberId))
        .thenReturn(selectedCompleted)
    whenever(queryRepository.findPreviousCompleted(
        memberId,
        farmId,
        cropId,
        requireNotNull(selectedCompleted.endsAt),
        requireNotNull(selectedCompleted.finalHarvestRecord?.id),
    )).thenReturn(previous)

    val result = service.getDetail(memberId, reportId)

    assertThat(result.selected.id).isEqualTo(reportId)
    assertThat(result.previous?.id).isEqualTo(previous.id)
}

@Test
fun `superseded detail is not exposed`() {
    whenever(reportRepository.findByIdAndMember_Id(reportId, memberId))
        .thenReturn(superseded)

    assertThatThrownBy { service.getDetail(memberId, reportId) }
        .isInstanceOf(BusinessException::class.java)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.REPORT_NOT_FOUND)
}
```

추가 케이스는 아래 입력과 결과를 그대로 검증한다.

| 테스트 | 입력 | assert/verify |
| --- | --- | --- |
| active detail previous | selected ACTIVE, latest COMPLETED 존재 | `findLatestCompleted` 호출, previous ID 일치 |
| page lookahead | size 2, repository rows 3개 | repository size 3, items 2개, 마지막 visible boundary cursor |
| blank cursor | `cursor="   "` | repository condition cursor null |
| invalid cursor | Base64가 아닌 문자열 | `BusinessException(INVALID_CURSOR)` |
| invalid size | 0, 101 | 각각 `BusinessException(INVALID_INPUT)`, repository 미호출 |
| unowned farm | `findByIdAndOwnerId` null | current/list 모두 `FARM_NOT_FOUND`, query repository 미호출 |
| member isolation | `findByIdAndMember_Id` null | `REPORT_NOT_FOUND`, previous query 미호출 |

- [ ] **Step 7: query service를 구현한다**

`ErrorCode`에 다음 항목을 추가한다.

```kotlin
REPORT_NOT_FOUND("REPORT_001", "error.report_not_found", 404),
```

서비스의 public 계약:

```kotlin
@Service
@Transactional(readOnly = true)
class FarmingCycleReportQueryService(
    private val farmRepository: FarmRepository,
    private val cropRepository: CropRepository,
    private val reportRepository: FarmingCycleReportRepository,
    private val queryRepository: FarmingCycleReportQueryRepository,
    private val cursorCodec: OpaqueCursorCodec,
) {
    fun getCurrent(
        memberId: UUID,
        farmId: UUID,
        cropId: UUID,
    ): FarmingCycleReportResult.Current

    fun listCompleted(
        condition: FarmingCycleReportSearchCondition,
    ): FarmingCycleReportResult.Page

    fun getDetail(
        memberId: UUID,
        reportId: UUID,
    ): FarmingCycleReportResult.Detail
}
```

각 함수의 exact 흐름:

```kotlin
private fun validateScope(memberId: UUID, farmId: UUID, cropId: UUID) {
    farmRepository.findByIdAndOwnerId(farmId, memberId)
        ?: throw BusinessException(ErrorCode.FARM_NOT_FOUND)
    if (!cropRepository.existsById(cropId)) {
        throw BusinessException(ErrorCode.CROP_NOT_FOUND)
    }
}

private fun decodeCursor(cursor: String?): FarmingCycleReportQueryRepository.Cursor? {
    if (cursor.isNullOrBlank()) return null
    val payload = cursorCodec.decode(
        cursor,
        FarmingCycleReportCursorPayload::class.java,
    )
    return FarmingCycleReportQueryRepository.Cursor(
        payload.endsAt,
        payload.finalHarvestRecordId,
    )
}
```

- `getCurrent`: scope 검증 → ACTIVE 조회 → latest COMPLETED 조회 → 둘 다 `Snapshot`으로 mapping
- `listCompleted`: size가 1..100이 아니면 INVALID_INPUT → scope 검증 → cursor decode → repository에 `size+1` → visible rows만 `Metadata` → 초과 행이 있으면 visible 마지막 행의 endsAt/final ID를 encode
- `getDetail`: `findByIdAndMember_Id`가 null 또는 SUPERSEDED면 REPORT_NOT_FOUND
- selected ACTIVE의 previous는 `findLatestCompleted`
- selected COMPLETED의 previous는 `findPreviousCompleted`이며 non-null endsAt/final ID를 require
- `toSnapshot`에서 통계 객체를 복사하거나 Map으로 바꾸지 않고 `report.statistics`를 그대로 넣는다.
- `toMetadata`는 statistics를 포함하지 않는다.

- [ ] **Step 8: application 조회 테스트를 통과시킨다**

Run:

```bash
cd backend && ./gradlew :application:test \
  --tests '*FarmingCycleReportQueryServiceTest'
```

Expected: PASS.

- [ ] **Step 9: Task 5를 커밋한다**

```bash
git add \
  backend/domain/src/main/kotlin/com/chamchamcham/domain/report/FarmingCycleReportQueryRepository.kt \
  backend/domain/src/main/kotlin/com/chamchamcham/domain/report/FarmingCycleReportQueryRepositoryImpl.kt \
  backend/domain/src/test/kotlin/com/chamchamcham/domain/report/FarmingCycleReportQueryRepositoryTest.kt \
  backend/application/src/main/kotlin/com/chamchamcham/application/report/FarmingCycleReportCursorPayload.kt \
  backend/application/src/main/kotlin/com/chamchamcham/application/report/FarmingCycleReportSearchCondition.kt \
  backend/application/src/main/kotlin/com/chamchamcham/application/report/FarmingCycleReportResult.kt \
  backend/application/src/main/kotlin/com/chamchamcham/application/report/FarmingCycleReportQueryService.kt \
  backend/application/src/main/kotlin/com/chamchamcham/application/exception/ErrorCode.kt \
  backend/application/src/test/kotlin/com/chamchamcham/application/report/FarmingCycleReportQueryServiceTest.kt
git commit -m "feat(report): 현재와 직전 완료 주기를 조회" \
  -m "Constraint: 목록 정렬과 직전 주기 판별은 관계형 경계 컬럼만 사용" \
  -m "Rejected: statistics JSON 내부 검색 | 조회 요구가 없음" \
  -m "Confidence: high" \
  -m "Scope-risk: moderate" \
  -m "Tested: report query repository and service tests"
```
---

### Task 6: 리포트 HTTP API

**Files:**
- Create: `backend/api/src/main/kotlin/com/chamchamcham/api/report/controller/FarmingCycleReportController.kt`
- Create: `backend/api/src/main/kotlin/com/chamchamcham/api/report/dto/FarmingCycleReportResponses.kt`
- Test: `backend/api/src/test/kotlin/com/chamchamcham/api/report/controller/FarmingCycleReportControllerTest.kt`

**Interfaces:**
- Consumes: `FarmingCycleReportQueryService`
- Produces:
  - `GET /api/v1/farming-reports/current?farmId=&cropId=`
  - `GET /api/v1/farming-reports?farmId=&cropId=&cursor=&size=`
  - `GET /api/v1/farming-reports/{reportId}`

- [ ] **Step 1: current·list·detail controller 실패 테스트를 작성한다**

```kotlin
@Test
fun `current returns nullable current and latest completed previous`() {
    whenever(service.getCurrent(memberId, farmId, cropId))
        .thenReturn(
            FarmingCycleReportResult.Current(
                current = null,
                previous = completedSnapshot(),
            ),
        )

    mockMvc.perform(
        get("/api/v1/farming-reports/current")
            .with(authenticatedMember(memberId.toString()))
            .param("farmId", farmId.toString())
            .param("cropId", cropId.toString()),
    )
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.data.current").doesNotExist())
        .andExpect(jsonPath("$.data.previous.id").value(previousReportId.toString()))
        .andExpect(jsonPath("$.data.previous.statistics.watering.recordCount").value(2))
}

@Test
fun `list returns completed metadata and cursor without statistics`() {
    whenever(service.listCompleted(searchCondition()))
        .thenReturn(completedPage())

    mockMvc.perform(
        get("/api/v1/farming-reports")
            .with(authenticatedMember(memberId.toString()))
            .param("farmId", farmId.toString())
            .param("cropId", cropId.toString())
            .param("size", "20"),
    )
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.data.items[0].status").value("COMPLETED"))
        .andExpect(jsonPath("$.data.items[0].statistics").doesNotExist())
        .andExpect(jsonPath("$.data.nextCursor").value("next-cursor"))
}

@Test
fun `detail returns selected and previous full typed statistics`() {
    whenever(service.getDetail(memberId, selectedReportId))
        .thenReturn(
            FarmingCycleReportResult.Detail(
                selected = selectedSnapshot(),
                previous = completedSnapshot(),
            ),
        )

    mockMvc.perform(
        get("/api/v1/farming-reports/{reportId}", selectedReportId)
            .with(authenticatedMember(memberId.toString())),
    )
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.data.selected.id").value(selectedReportId.toString()))
        .andExpect(jsonPath("$.data.selected.statistics.planting.recordCount").value(1))
        .andExpect(jsonPath("$.data.selected.statistics.watering.recordCount").value(2))
        .andExpect(jsonPath("$.data.selected.statistics.harvest.totalAmountKg").value(30.0))
        .andExpect(jsonPath("$.data.previous.id").value(previousReportId.toString()))
}
```

추가 controller 케이스:

| 요청 | service stub/검증 | HTTP 결과 |
| --- | --- | --- |
| principal 없음 | service 미호출 | 401, `AUTH_001` |
| principal `not-a-uuid` | service 미호출 | 401, `AUTH_001` |
| current에서 farmId 또는 cropId 누락 | service 미호출 | 400 |
| list service가 `INVALID_INPUT` throw | 전달 condition의 size 확인 | 400, `COMMON_001` |
| detail service가 `REPORT_NOT_FOUND` throw | memberId/reportId 전달 확인 | 404, `REPORT_001` |
| current 정상 | memberId/farmId/cropId exact verify | 200 |
| list에 cursor와 size=5 | condition cursor/size exact verify | 200 |

- [ ] **Step 2: 실패를 확인한다**

Run:

```bash
cd backend && ./gradlew :api:test --tests '*FarmingCycleReportControllerTest'
```

Expected: FAIL because controller and response DTOs do not exist.

- [ ] **Step 3: response DTO를 구현한다**

```kotlin
object FarmingCycleReportResponses {
    data class MetadataResponse(
        val id: UUID,
        val farmId: UUID,
        val farmName: String,
        val cropId: UUID,
        val cropName: String,
        val status: FarmingCycleReportStatus,
        val startsAt: LocalDateTime,
        val endsAt: LocalDateTime?,
        val startBasis: FarmingCycleStartBasis,
        val sourceRevision: Long,
    ) {
        companion object {
            fun from(source: FarmingCycleReportResult.Metadata) =
                MetadataResponse(
                    source.id,
                    source.farmId,
                    source.farmName,
                    source.cropId,
                    source.cropName,
                    source.status,
                    source.startsAt,
                    source.endsAt,
                    source.startBasis,
                    source.sourceRevision,
                )
        }
    }

    data class SnapshotResponse(
        val id: UUID,
        val farmId: UUID,
        val farmName: String,
        val cropId: UUID,
        val cropName: String,
        val status: FarmingCycleReportStatus,
        val startsAt: LocalDateTime,
        val endsAt: LocalDateTime?,
        val startBasis: FarmingCycleStartBasis,
        val finalHarvestRecordId: UUID?,
        val statisticsSchemaVersion: Int,
        val sourceRevision: Long,
        val statistics: CycleReportStatistics,
    ) {
        companion object {
            fun from(source: FarmingCycleReportResult.Snapshot) =
                SnapshotResponse(
                    source.id,
                    source.farmId,
                    source.farmName,
                    source.cropId,
                    source.cropName,
                    source.status,
                    source.startsAt,
                    source.endsAt,
                    source.startBasis,
                    source.finalHarvestRecordId,
                    source.statisticsSchemaVersion,
                    source.sourceRevision,
                    source.statistics,
                )
        }
    }

    data class CurrentResponse(
        val current: SnapshotResponse?,
        val previous: SnapshotResponse?,
    ) {
        companion object {
            fun from(source: FarmingCycleReportResult.Current) =
                CurrentResponse(
                    source.current?.let(SnapshotResponse::from),
                    source.previous?.let(SnapshotResponse::from),
                )
        }
    }

    data class DetailResponse(
        val selected: SnapshotResponse,
        val previous: SnapshotResponse?,
    ) {
        companion object {
            fun from(source: FarmingCycleReportResult.Detail) =
                DetailResponse(
                    SnapshotResponse.from(source.selected),
                    source.previous?.let(SnapshotResponse::from),
                )
        }
    }

    data class PageResponse(
        val items: List<MetadataResponse>,
        val nextCursor: String?,
    ) {
        companion object {
            fun from(source: FarmingCycleReportResult.Page) =
                PageResponse(source.items.map(MetadataResponse::from), source.nextCursor)
        }
    }
}
```

`CycleReportStatistics`는 JPA entity가 아니라 versioned immutable value object이므로 Map이나 API별 중복 DTO로 변환하지 않고 그대로 직렬화한다.

- [ ] **Step 4: controller를 구현한다**

```kotlin
@RestController
@RequestMapping("/api/v1/farming-reports")
class FarmingCycleReportController(
    private val queryService: FarmingCycleReportQueryService,
) {
    @GetMapping("/current")
    fun getCurrent(
        @AuthenticationPrincipal memberId: String?,
        @RequestParam farmId: UUID,
        @RequestParam cropId: UUID,
    ): ResponseEntity<ApiResponse<FarmingCycleReportResponses.CurrentResponse>> {
        val result = queryService.getCurrent(parseMemberId(memberId), farmId, cropId)
        return ResponseEntity.ok(
            ApiResponse.ok(FarmingCycleReportResponses.CurrentResponse.from(result)),
        )
    }

    @GetMapping
    fun listCompleted(
        @AuthenticationPrincipal memberId: String?,
        @RequestParam farmId: UUID,
        @RequestParam cropId: UUID,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ApiResponse<FarmingCycleReportResponses.PageResponse>> {
        val result = queryService.listCompleted(
            FarmingCycleReportSearchCondition(
                memberId = parseMemberId(memberId),
                farmId = farmId,
                cropId = cropId,
                cursor = cursor,
                size = size,
            ),
        )
        return ResponseEntity.ok(
            ApiResponse.ok(FarmingCycleReportResponses.PageResponse.from(result)),
        )
    }

    @GetMapping("/{reportId}")
    fun getDetail(
        @AuthenticationPrincipal memberId: String?,
        @PathVariable reportId: UUID,
    ): ResponseEntity<ApiResponse<FarmingCycleReportResponses.DetailResponse>> {
        val result = queryService.getDetail(parseMemberId(memberId), reportId)
        return ResponseEntity.ok(
            ApiResponse.ok(FarmingCycleReportResponses.DetailResponse.from(result)),
        )
    }

    private fun parseMemberId(memberId: String?): UUID {
        if (memberId.isNullOrBlank()) {
            throw BusinessException(ErrorCode.UNAUTHORIZED)
        }
        return try {
            UUID.fromString(memberId)
        } catch (exception: IllegalArgumentException) {
            throw BusinessException(ErrorCode.UNAUTHORIZED)
        }
    }
}
```

상태 filter와 `/work-types/{workType}` endpoint는 추가하지 않는다.

- [ ] **Step 5: controller 테스트를 통과시킨다**

Run:

```bash
cd backend && ./gradlew :api:test --tests '*FarmingCycleReportControllerTest'
```

Expected: PASS.

- [ ] **Step 6: Task 6을 커밋한다**

```bash
git add \
  backend/api/src/main/kotlin/com/chamchamcham/api/report/controller/FarmingCycleReportController.kt \
  backend/api/src/main/kotlin/com/chamchamcham/api/report/dto/FarmingCycleReportResponses.kt \
  backend/api/src/test/kotlin/com/chamchamcham/api/report/controller/FarmingCycleReportControllerTest.kt
git commit -m "feat(report): 주기 리포트 조회 API를 제공" \
  -m "Constraint: 상세 응답 하나를 전체와 작업별 화면이 재사용" \
  -m "Rejected: 작업 유형별 상세 endpoint | 현재 payload 요구에 불필요" \
  -m "Confidence: high" \
  -m "Scope-risk: moderate" \
  -m "Tested: farming cycle report controller tests"
```
---

### Task 7: PostgreSQL schema, 통합 검증, 문서 계약

**Files:**
- Create: `backend/docs/db/farming-cycle-report-schema.sql`
- Test: `backend/api/src/test/kotlin/com/chamchamcham/api/report/FarmingCycleReportIntegrationTest.kt`
- Test: `backend/api/src/test/kotlin/com/chamchamcham/api/report/FarmingRecordProjectionRollbackIntegrationTest.kt`
- Modify: `frontend/docs/Business Rule.md`
- Modify: `frontend/docs/API 명세서/API 명세서/영농 리포트 조회 3909e2d9440581ef91dbf34bba44c407.md`
- Modify: `frontend/docs/API 명세서/DTO(데이터 전달 객체)/FarmingReportResponse 3909e2d9440581bbbf69c0e35cd17b8b.md`

**Interfaces:**
- Consumes: Tasks 1-6 전체
- Produces: 배포용 수동 DDL, DB transaction 회귀 증거, 확정 API 문서

- [ ] **Step 1: 실제 CRUD→리포트 통합 실패 테스트를 작성한다**

`FarmingCycleReportIntegrationTest`는 `@SpringBootTest(classes = [ApiApplication::class])`, `@ActiveProfiles("test")`를 사용하고 실제 `FarmingRecordService`, projection, query service, H2 repositories를 주입한다.

```kotlin
@Test
fun `record CRUD rebuilds active and completed report end to end`() {
    val wateringId = farmingRecordService.create(wateringCommand()).id

    val active = reportRepository.findByMember_IdAndFarm_IdAndCrop_IdAndStatus(
        memberId,
        farmId,
        cropId,
        FarmingCycleReportStatus.ACTIVE,
    )
    assertThat(active).isNotNull
    assertThat(active!!.statistics.watering.recordCount).isEqualTo(1)

    val finalHarvestId = farmingRecordService.create(
        harvestCommand(isFinalHarvest = true, amountKg = "30"),
    ).id

    assertThat(
        reportRepository.findByMember_IdAndFarm_IdAndCrop_IdAndStatus(
            memberId,
            farmId,
            cropId,
            FarmingCycleReportStatus.ACTIVE,
        ),
    ).isNull()
    val current = queryService.getCurrent(memberId, farmId, cropId)
    assertThat(current.current).isNull()
    assertThat(current.previous?.status).isEqualTo(FarmingCycleReportStatus.COMPLETED)
    assertThat(current.previous?.finalHarvestRecordId).isEqualTo(finalHarvestId)
    assertThat(current.previous?.statistics?.watering?.recordCount).isEqualTo(1)
    assertThat(current.previous?.statistics?.harvest?.totalAmountKg)
        .isEqualByComparingTo("30.0000")

    farmingRecordService.delete(FarmingRecordCommand.Delete(memberId, wateringId))

    val rebuilt = queryService.getDetail(
        memberId,
        requireNotNull(current.previous?.id),
    )
    assertThat(rebuilt.selected.statistics.watering.recordCount).isZero()
    assertThat(rebuilt.selected.sourceRevision)
        .isGreaterThan(requireNotNull(current.previous?.sourceRevision))
}
```

각 test의 `@BeforeEach`는 repository의 FK 역순으로 데이터를 삭제한 뒤 member/farm/crop을 persist한다. 다른 test의 상태를 공유하지 않는다.

- [ ] **Step 2: projection 예외의 DB rollback 실패 테스트를 작성한다**

`FarmingRecordProjectionRollbackIntegrationTest`는 실제 repository와 `FarmingRecordService`를 사용하고 projection bean만 `@MockBean`으로 교체한다.

```kotlin
@Test
fun `projection failure rolls back record detail and media rows`() {
    doThrow(IllegalStateException("projection failed"))
        .whenever(projectionService)
        .rebuild(ReportScope(memberId, farmId, cropId))

    assertThatThrownBy {
        farmingRecordService.create(wateringCommand(withMedia = true))
    }.isInstanceOf(IllegalStateException::class.java)

    assertThat(farmingRecordRepository.findAll()).isEmpty()
    assertThat(wateringRecordRepository.findAll()).isEmpty()
    assertThat(farmingRecordMediaRepository.findAll()).isEmpty()
}
```

- [ ] **Step 3: 두 통합 테스트를 실행한다**

Run:

```bash
cd backend && ./gradlew :api:test \
  --tests '*FarmingCycleReportIntegrationTest' \
  --tests '*FarmingRecordProjectionRollbackIntegrationTest'
```

Expected: PASS. 제품 코드를 우회하는 repository 직접 리포트 생성으로 테스트를 바꾸지 않는다.

- [ ] **Step 4: PostgreSQL DDL을 작성한다**

`backend/docs/db/farming-cycle-report-schema.sql`:

```sql
-- Flyway is not installed. Apply this reviewed schema manually to dev/prod.
create table if not exists farming_cycle_report (
    id uuid primary key,
    member_id uuid not null references member (id),
    farm_id uuid not null references farm (id),
    crop_id uuid not null references crop (id),
    status varchar(16) not null,
    starts_at timestamp not null,
    ends_at timestamp null,
    start_basis varchar(48) not null,
    final_harvest_record_id uuid null references farming_record (id),
    statistics_schema_version integer not null,
    statistics jsonb not null,
    source_revision bigint not null,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint ck_farming_cycle_report_status
        check (status in ('ACTIVE', 'COMPLETED', 'SUPERSEDED')),
    constraint ck_farming_cycle_report_start_basis
        check (
            start_basis in (
                'FIRST_RECORD',
                'AFTER_PREVIOUS_FINAL_HARVEST'
            )
        ),
    constraint ck_farming_cycle_report_boundary
        check (
            status = 'SUPERSEDED'
            or (
                status = 'ACTIVE'
                and ends_at is null
                and final_harvest_record_id is null
            )
            or (
                status = 'COMPLETED'
                and ends_at is not null
                and final_harvest_record_id is not null
            )
        ),
    constraint ck_farming_cycle_report_dates
        check (ends_at is null or starts_at <= ends_at),
    constraint ck_farming_cycle_report_schema_version
        check (statistics_schema_version > 0),
    constraint ck_farming_cycle_report_source_revision
        check (source_revision > 0)
);

create unique index if not exists uq_farming_cycle_report_active_scope
    on farming_cycle_report (member_id, farm_id, crop_id)
    where status = 'ACTIVE';

create unique index if not exists uq_farming_cycle_report_completed_final
    on farming_cycle_report (final_harvest_record_id)
    where status = 'COMPLETED';

create index if not exists idx_farming_cycle_report_completed_list
    on farming_cycle_report (
        member_id,
        farm_id,
        crop_id,
        ends_at desc,
        final_harvest_record_id desc
    )
    where status = 'COMPLETED';

create index if not exists idx_farming_cycle_report_scope_start
    on farming_cycle_report (
        member_id,
        farm_id,
        crop_id,
        starts_at,
        id
    );
```

`completed_at`, `previous_report_id`, fingerprint, 작업별 통계 table은 추가하지 않는다.

- [ ] **Step 5: 프론트 계약 문서를 확정한다**

`frontend/docs/Business Rule.md`에 다음 규칙을 추가한다.

```markdown
### 영농 리포트 주기

- 주기 범위는 회원·밭·작물 조합이다.
- 앞선 마지막 수확이 없으면 최초 기록부터 시작한다.
- 앞선 마지막 수확이 있으면 그 다음 기록부터 시작한다.
- 일부 수확은 주기를 닫지 않고 마지막 수확만 해당 기록을 포함해 주기를 닫는다.
- 심기는 강제 시작점이 아니며 심기 전 준비 작업도 주기에 포함한다.
- 현재 리포트의 비교 대상은 지난해가 아니라 직전 완료 주기다.
- 관수 횟수와 간격만으로 수분 부족·과다·적정을 판단하지 않는다.
```

영농 리포트 API 문서는 다음 세 endpoint만 1단계로 표기한다.

```http
GET /api/v1/farming-reports/current?farmId={uuid}&cropId={uuid}
GET /api/v1/farming-reports?farmId={uuid}&cropId={uuid}&cursor={opaque}&size=20
GET /api/v1/farming-reports/{reportId}
```

DTO 문서의 핵심 JSON shape:

```json
{
  "selected": {
    "id": "uuid",
    "status": "COMPLETED",
    "startsAt": "2026-03-01T09:00:00",
    "endsAt": "2026-06-30T09:00:00",
    "statisticsSchemaVersion": 1,
    "sourceRevision": 5,
    "statistics": {
      "planting": { "recordCount": 1, "propagationMethods": [] },
      "watering": {
        "recordCount": 8,
        "averageIntervalDays": 12.0,
        "amountDistribution": [],
        "methodDistribution": []
      },
      "fertilizing": { "recordCount": 4, "materialCategories": [] },
      "pestControl": { "recordCount": 2, "pesticideAmounts": [] },
      "weeding": { "recordCount": 1, "methodDistribution": [] },
      "pruning": { "recordCount": 0 },
      "harvest": {
        "recordCount": 3,
        "totalAmountKg": 30.0,
        "amountCoverage": { "recordedCount": 2, "targetCount": 3 }
      },
      "etc": { "recordCount": 0 }
    }
  },
  "previous": null
}
```

문서에는 실제 응답에서 각 작업 객체가 9개 공통 필드를 모두 가진다는 표를 별도로 넣고, 축약 JSON은 예시를 줄인 것임을 명시한다. `current` 응답의 ACTIVE 부재는 404가 아니라 `current: null`임을 명시한다. 목록은 COMPLETED metadata만 반환하고 statistics는 포함하지 않는다.

- [ ] **Step 6: 통합 테스트와 정적 계약 검사를 통과시킨다**

Run:

```bash
cd backend && ./gradlew :api:test \
  --tests '*FarmingCycleReportIntegrationTest' \
  --tests '*FarmingRecordProjectionRollbackIntegrationTest'
```

Expected: PASS.

Run:

```bash
rg -n "materialName|pesticideName" \
  backend/application/src/main/kotlin/com/chamchamcham/application/report
```

Expected: no matches.

Run:

```bash
rg -n "adequacy|shortage|excess" \
  backend/domain/src/main/kotlin/com/chamchamcham/domain/report \
  backend/application/src/main/kotlin/com/chamchamcham/application/report
```

Expected: no matches.

Run:

```bash
rg -n "work-types|previous_report_id|fingerprint|completed_at" \
  backend/api/src/main/kotlin/com/chamchamcham/api/report \
  backend/domain/src/main/kotlin/com/chamchamcham/domain/report \
  backend/docs/db/farming-cycle-report-schema.sql
```

Expected: no matches.

- [ ] **Step 7: 전체 backend 회귀 검증을 실행한다**

Run:

```bash
cd backend && ./gradlew test
```

Expected: BUILD SUCCESSFUL.

Run:

```bash
git diff --check
```

Expected: no output and exit code 0.

- [ ] **Step 8: Task 7을 커밋한다**

```bash
git add \
  backend/docs/db/farming-cycle-report-schema.sql \
  backend/api/src/test/kotlin/com/chamchamcham/api/report/FarmingCycleReportIntegrationTest.kt \
  backend/api/src/test/kotlin/com/chamchamcham/api/report/FarmingRecordProjectionRollbackIntegrationTest.kt \
  frontend/docs/Business\ Rule.md \
  "frontend/docs/API 명세서/API 명세서/영농 리포트 조회 3909e2d9440581ef91dbf34bba44c407.md" \
  "frontend/docs/API 명세서/DTO(데이터 전달 객체)/FarmingReportResponse 3909e2d9440581bbbf69c0e35cd17b8b.md"
git commit -m "docs(report): 주기 리포트 배포 계약을 확정" \
  -m "Constraint: Flyway가 없어 dev prod DDL은 수동 적용" \
  -m "Rejected: 범용 backfill runner | 보존할 운영 데이터 요구가 아직 없음" \
  -m "Confidence: high" \
  -m "Scope-risk: moderate" \
  -m "Directive: 배포 전 farming-cycle-report-schema.sql을 대상 DB에 적용" \
  -m "Tested: integration rollback and full backend test suite"
```

## Final Acceptance Checklist

- [ ] 후속 영농일지 PR의 stable 비료·약제 카테고리와 마지막 수확 Boolean만 사용한다.
- [ ] 리포트 한 행은 회원·밭·작물의 한 주기를 나타낸다.
- [ ] 마지막 수확 기록 ID가 COMPLETED 경계 근거로 저장된다.
- [ ] `statistics`는 typed `CycleReportStatistics` JSONB이고 Map이 아니다.
- [ ] 최상위 common 없이 8개 작업 객체 모두 9개 공통 필드를 직접 가진다.
- [ ] 기록 없는 작업 객체도 `0/null/[]` 계약으로 존재한다.
- [ ] 기록 CRUD 직후 같은 transaction에서 report가 재계산된다.
- [ ] 마지막 수확 직후 ACTIVE가 사라지고 COMPLETED가 조회된다.
- [ ] 현재와 상세 응답은 직전 완료 주기의 전체 통계를 함께 반환한다.
- [ ] 완료 목록은 ACTIVE·SUPERSEDED·statistics를 노출하지 않는다.
- [ ] 한 주기 상세 응답을 전체 화면과 작업별 화면이 함께 사용한다.
- [ ] 강수 근거 없는 관수 적정성 판단이 없다.
- [ ] ML/G 분리, 수확 kg, 수확량 모름 coverage, 생육 개월 정규화가 검증된다.
- [ ] 범용 backfill, work-type endpoint, fingerprint, 통계 관계형 table이 없다.
- [ ] `./gradlew test`와 `git diff --check`가 통과한다.
