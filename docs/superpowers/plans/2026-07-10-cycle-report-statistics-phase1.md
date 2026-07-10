# 주기별 저장형 영농 리포트 통계 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 영농기록 원본을 마지막 수확 기준 주기로 나누고, 진행 중·완료 리포트 통계를 저장·재계산하며, 현재 주기와 직전 완료 주기를 조회하는 API를 제공한다.

**Architecture:** 영농기록과 작업별 상세 엔티티를 정규화된 `CycleReportSourceRecord`로 읽고, 순수 함수인 주기 분할기와 작업별 통계 계산기로 프로젝션을 만든다. `FarmingCycleReport`는 JSONB 통계 스냅샷과 revision을 저장하지만 원본은 영농기록이며, 모든 생성·수정·삭제에서 숫자를 증감하지 않고 해당 밭·작물 범위를 전체 재계산한다.

**Tech Stack:** Kotlin, Spring Boot 3, Spring Data JPA, Hibernate 6 JSONB, PostgreSQL, JUnit 5, Mockito, MockMvc, Gradle

## Global Constraints

- 이 계획은 1단계 리포트 통계만 구현한다. LLM, RAG 검색, 코칭 PENDING/READY/FAILED/STALE 상태는 후속 계획 범위다.
- 사용자 기록은 관계형 DB에서 직접 읽는다. 벡터스토어에 영농기록이나 리포트를 색인하지 않는다.
- 주기는 회원·밭·작물별로 계산하며 같은 밭·작물의 중첩 주기는 지원하지 않는다.
- 마지막 수확은 주기를 닫고, 일부 수확은 현재 주기를 유지한다.
- 심기 전 작업도 같은 주기에 포함하며 심기는 기준점으로만 저장한다.
- 진행 중·완료 통계는 저장하지만 모든 갱신은 원본 전체 재계산으로 수행한다.
- 현재 리포트와 비교할 대상은 같은 밭·작물의 직전 완료 주기다.
- 리포트 한 건이 이미 하나의 밭·작물 범위이므로 내부에서 농장별로 다시 group by 하지 않는다.
- 강수 이력이 없으므로 관수 횟수·간격으로 부족·과다를 판단하거나 그런 필드를 응답하지 않는다.
- 수확량은 kg만 집계한다. ml과 g 약제량은 합산하지 않는다.
- 빈 값을 0이나 평균으로 대체하지 않는다.
- 파종 번식법 필드는 다른 개발자의 병합 결과를 소비하며 이 계획에서 중복 추가하지 않는다.
- 비료·약제 카테고리 값은 다른 개발자의 병합 결과를 소비한다. 리포트 계층은 안정적인 `code + label`만 사용한다.
- 새 라이브러리를 추가하지 않는다.
- 커밋은 Conventional Commits와 저장소 Lore trailer 규칙을 따른다.

## Execution Gate: 영농일지 스키마 계약

Task 1을 시작하기 전에 병행 개발 브랜치가 다음 의미를 제공해야 한다. 실제 엔티티 필드명이 달라도 `FarmingCycleReportSourceLoader` 한 곳에서 아래 정규화 타입으로 변환한다.

```kotlin
data class CategoryRef(val code: String, val label: String)

data class PlantingSource(
    val propagationMethod: CategoryRef,
    val quantity: BigDecimal?,
    val quantityUnit: String?,
    val seedAmountGrams: BigDecimal?,
)

data class FertilizingSource(
    val materialCategory: CategoryRef,
    val amountKg: BigDecimal,
    val applicationMethod: CategoryRef,
)

data class PestControlSource(
    val pesticideCategory: CategoryRef,
    val pesticideAmount: BigDecimal,
    val pesticideAmountUnit: String,
    val totalSprayAmountLiters: BigDecimal,
    val pestTarget: String?,
)

data class HarvestSource(
    val amountKg: BigDecimal,
    val medicinalPart: CategoryRef,
    val growthPeriodMonths: Int,
    val isFinalHarvest: Boolean,
)
```

다음 명령으로 선행 계약이 병합됐는지 확인한다.

```bash
rg -n "isFinalHarvest|medicinalPart|materialCategory|pesticideCategory|propagationMethod|SOIL|FOLIAR" backend/domain/src/main backend/application/src/main backend/api/src/main
```

Expected: 각 의미가 도메인·요청·응답에 존재한다. 누락된 경우 리포트에서 임시 자유문자열을 사용하지 말고 선행 브랜치를 먼저 병합한다.

## File Structure

### Domain

- Create `backend/domain/src/main/kotlin/com/chamchamcham/domain/report/FarmingCycleReport.kt`: 저장형 리포트 엔티티와 revision 전이
- Create `backend/domain/src/main/kotlin/com/chamchamcham/domain/report/FarmingCycleReportStatus.kt`: ACTIVE, COMPLETED, SUPERSEDED
- Create `backend/domain/src/main/kotlin/com/chamchamcham/domain/report/FarmingCycleStartBasis.kt`: FIRST_RECORD, AFTER_PREVIOUS_FINAL_HARVEST
- Create `backend/domain/src/main/kotlin/com/chamchamcham/domain/report/FarmingCycleReportRepository.kt`: 잠금·현재·직전·소유권 조회
- Create `backend/domain/src/main/kotlin/com/chamchamcham/domain/report/FarmingCycleReportQueryRepository.kt`: 커서 목록 조회 계약
- Create `backend/domain/src/main/kotlin/com/chamchamcham/domain/report/FarmingCycleReportQueryRepositoryImpl.kt`: JPQL 커서 구현
- Create `backend/domain/src/main/kotlin/com/chamchamcham/domain/farming/FarmingCycleReportSourceRepository.kt`: 리포트 원본 스냅샷 계약
- Create `backend/domain/src/main/kotlin/com/chamchamcham/domain/farming/FarmingCycleReportSourceRepositoryImpl.kt`: 기록·상세·미디어 일괄 조회
- Create `backend/domain/src/main/kotlin/com/chamchamcham/domain/farming/FarmingRecordReportScope.kt`: 기존 기록 backfill 범위
- Modify `backend/domain/src/main/kotlin/com/chamchamcham/domain/farming/FarmingRecordRepository.kt`: 삭제되지 않은 distinct 범위 조회
- Modify `backend/domain/src/main/kotlin/com/chamchamcham/domain/farm/FarmRepository.kt`: 소유권 검증을 포함한 비관적 잠금 조회
- Modify `backend/domain/src/main/kotlin/com/chamchamcham/domain/farming/*RecordRepository.kt`: 리포트 원본용 상세 일괄 조회
- Modify `backend/domain/src/main/kotlin/com/chamchamcham/domain/farming/FarmingRecordMediaRepository.kt`: 사진 존재 기록 ID 일괄 조회

### Application

- Create `backend/application/src/main/kotlin/com/chamchamcham/application/report/CycleReportSource.kt`: 엔티티와 분리된 정규화 입력
- Create `backend/application/src/main/kotlin/com/chamchamcham/application/report/FarmingCyclePartitioner.kt`: 마지막 수확 기반 순수 주기 분할
- Create `backend/application/src/main/kotlin/com/chamchamcham/application/report/statistics/CycleReportStatistics.kt`: 타입이 있는 통계 스키마
- Create `backend/application/src/main/kotlin/com/chamchamcham/application/report/statistics/StatisticsMath.kt`: 비율·평균·간격 공통 계산
- Create `backend/application/src/main/kotlin/com/chamchamcham/application/report/statistics/CommonStatisticsCalculator.kt`: 공통 통계
- Create `backend/application/src/main/kotlin/com/chamchamcham/application/report/statistics/PlantingStatisticsCalculator.kt`: 번식법·파종량 통계
- Create `backend/application/src/main/kotlin/com/chamchamcham/application/report/statistics/WateringStatisticsCalculator.kt`: 관수 분포 통계
- Create `backend/application/src/main/kotlin/com/chamchamcham/application/report/statistics/FertilizingStatisticsCalculator.kt`: 카테고리·방식·kg 통계
- Create `backend/application/src/main/kotlin/com/chamchamcham/application/report/statistics/PestControlStatisticsCalculator.kt`: 약제 단위·살포량·대상 통계
- Create `backend/application/src/main/kotlin/com/chamchamcham/application/report/statistics/WeedingStatisticsCalculator.kt`: 제초 방식 통계
- Create `backend/application/src/main/kotlin/com/chamchamcham/application/report/statistics/HarvestStatisticsCalculator.kt`: kg·약용 부위·생육 기간 통계
- Create `backend/application/src/main/kotlin/com/chamchamcham/application/report/statistics/CycleReportStatisticsCalculator.kt`: 작업별 계산기 조합
- Create `backend/application/src/main/kotlin/com/chamchamcham/application/report/CycleReportStatisticsCodec.kt`: 타입 모델과 JSONB Map 경계
- Create `backend/application/src/main/kotlin/com/chamchamcham/application/report/FarmingCycleReportSourceLoader.kt`: 엔티티 스냅샷 정규화
- Create `backend/application/src/main/kotlin/com/chamchamcham/application/report/FarmingCycleReportProjectionService.kt`: 범위 전체 재계산·upsert·supersede
- Create `backend/application/src/main/kotlin/com/chamchamcham/application/report/FarmingCycleReportBackfillService.kt`: 기존 기록 범위별 재계산
- Create `backend/application/src/main/kotlin/com/chamchamcham/application/report/FarmingCycleReportQueryService.kt`: 현재·목록·상세·직전 주기 조회
- Create `backend/application/src/main/kotlin/com/chamchamcham/application/report/FarmingCycleReportResult.kt`: API 독립 결과 모델
- Create `backend/application/src/main/kotlin/com/chamchamcham/application/report/FarmingCycleReportSearchCondition.kt`: 커서 조회 조건
- Create `backend/application/src/main/kotlin/com/chamchamcham/application/report/FarmingCycleReportCursorPayload.kt`: 커서 payload
- Modify `backend/application/src/main/kotlin/com/chamchamcham/application/farming/FarmingRecordService.kt`: 생성·수정·삭제 후 영향 범위 재계산
- Modify `backend/application/src/main/kotlin/com/chamchamcham/application/exception/ErrorCode.kt`: REPORT_NOT_FOUND, REPORT_INVALID_STATUS

### API

- Create `backend/api/src/main/kotlin/com/chamchamcham/api/report/controller/FarmingCycleReportController.kt`: 현재·목록·상세 API
- Create `backend/api/src/main/kotlin/com/chamchamcham/api/report/dto/FarmingCycleReportResponses.kt`: 타입이 있는 응답 DTO
- Create `backend/api/src/main/kotlin/com/chamchamcham/config/FarmingCycleReportBackfillRunner.kt`: 명시적으로 활성화하는 일회성 runner

### Schema and Docs

- Create `backend/docs/db/farming-cycle-report-schema.sql`: report table, JSONB, 인덱스, 유일성 제약
- Modify `frontend/docs/Business Rule.md`: 기간 리포트를 주기 리포트로 교체
- Modify `frontend/docs/API 명세서/API 명세서/영농 리포트 조회 3909e2d9440581ef91dbf34bba44c407.md`: 세 조회 API 계약
- Modify `frontend/docs/API 명세서/DTO(데이터 전달 객체)/FarmingReportResponse 3909e2d9440581bbbf69c0e35cd17b8b.md`: 주기 통계 응답 계약

---

### Task 1: 저장형 리포트 도메인과 repository

**Files:**
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/report/FarmingCycleReportStatus.kt`
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/report/FarmingCycleStartBasis.kt`
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/report/FarmingCycleReport.kt`
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/report/FarmingCycleReportRepository.kt`
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/farm/FarmRepository.kt`
- Test: `backend/domain/src/test/kotlin/com/chamchamcham/domain/report/FarmingCycleReportTest.kt`
- Test: `backend/domain/src/test/kotlin/com/chamchamcham/domain/report/FarmingCycleReportRepositoryTest.kt`
- Test: `backend/domain/src/test/kotlin/com/chamchamcham/domain/farm/FarmRepositoryLockTest.kt`

**Interfaces:**
- Consumes: `Member`, `Farm`, `Crop`, 마지막 수확 `FarmingRecord`
- Produces: `FarmingCycleReport.create(member: Member, farm: Farm, crop: Crop, projection: FarmingCycleReportProjection): FarmingCycleReport`, `FarmingCycleReport.applyProjection(FarmingCycleReportProjection): Boolean`, `FarmingCycleReport.supersede()`, 잠금 repository 메서드

- [ ] **Step 1: revision과 supersede 동작을 고정하는 실패 테스트 작성**

```kotlin
@Test
fun `same projection keeps revision and changed projection increments it`() {
    val first = projection(totalRecordCount = 3)
    val second = projection(totalRecordCount = 4)
    val report = FarmingCycleReport.create(member, farm, crop, first)

    assertThat(report.sourceRevision).isEqualTo(1)
    assertThat(report.applyProjection(first)).isFalse()
    assertThat(report.sourceRevision).isEqualTo(1)
    assertThat(report.applyProjection(second)).isTrue()
    assertThat(report.sourceRevision).isEqualTo(2)
}

@Test
fun `supersede preserves snapshot and excludes report from active state`() {
    val report = FarmingCycleReport.create(
        member,
        farm,
        crop,
        projection(totalRecordCount = 3),
    )

    report.supersede()

    assertThat(report.status).isEqualTo(FarmingCycleReportStatus.SUPERSEDED)
    assertThat(report.statistics["totalRecordCount"]).isEqualTo(3)
}

@Test
fun `active projection rejects completed boundary fields`() {
    assertThatThrownBy {
        activeReport().applyProjection(activeProjection(completedAt = fixedNow))
    }.isInstanceOf(IllegalArgumentException::class.java)
}
```

- [ ] **Step 2: 테스트가 컴파일 실패하는지 확인**

Run:

```bash
cd backend && ./gradlew :domain:test --tests '*FarmingCycleReportTest'
```

Expected: FAIL because `FarmingCycleReport` and enums do not exist.

- [ ] **Step 3: enum과 엔티티의 최소 동작 구현**

```kotlin
enum class FarmingCycleReportStatus { ACTIVE, COMPLETED, SUPERSEDED }

enum class FarmingCycleStartBasis { FIRST_RECORD, AFTER_PREVIOUS_FINAL_HARVEST }

data class FarmingCycleReportProjection(
    val status: FarmingCycleReportStatus,
    val startsAt: LocalDateTime,
    val endsAt: LocalDateTime?,
    val completedAt: LocalDateTime?,
    val startBasis: FarmingCycleStartBasis,
    val plantingMilestoneAt: LocalDateTime?,
    val finalHarvestRecord: FarmingRecord?,
    val statisticsSchemaVersion: String,
    val statistics: Map<String, Any?>,
    val statisticsFingerprint: String,
)

fun applyProjection(next: FarmingCycleReportProjection): Boolean {
    require(next.status != FarmingCycleReportStatus.SUPERSEDED)
    if (next.status == FarmingCycleReportStatus.ACTIVE) {
        require(next.endsAt == null && next.completedAt == null && next.finalHarvestRecord == null)
    } else {
        require(next.endsAt != null && next.completedAt != null && next.finalHarvestRecord != null)
        require(next.startsAt <= next.endsAt)
    }
    val changed = status != next.status || startsAt != next.startsAt ||
        endsAt != next.endsAt || completedAt != next.completedAt ||
        startBasis != next.startBasis ||
        plantingMilestoneAt != next.plantingMilestoneAt ||
        finalHarvestRecord?.id != next.finalHarvestRecord?.id ||
        statisticsSchemaVersion != next.statisticsSchemaVersion ||
        statisticsFingerprint != next.statisticsFingerprint
    if (!changed) return false
    status = next.status
    startsAt = next.startsAt
    endsAt = next.endsAt
    completedAt = next.completedAt
    startBasis = next.startBasis
    plantingMilestoneAt = next.plantingMilestoneAt
    finalHarvestRecord = next.finalHarvestRecord
    statisticsSchemaVersion = next.statisticsSchemaVersion
    statistics = next.statistics
    statisticsFingerprint = next.statisticsFingerprint
    sourceRevision += 1
    return true
}

fun supersede() {
    if (status != FarmingCycleReportStatus.SUPERSEDED) {
        status = FarmingCycleReportStatus.SUPERSEDED
        sourceRevision += 1
    }
}
```

Entity mapping requirements:

- `@Table(name = "farming_cycle_report")`
- UUID primary key
- LAZY `Member`, `Farm`, `Crop`, nullable LAZY final harvest `FarmingRecord`
- `@JdbcTypeCode(SqlTypes.JSON)` and `columnDefinition = "jsonb"` for statistics
- `statisticsFingerprint` is a 64-character lowercase SHA-256 of canonical typed statistics JSON
- `sourceRevision` starts at 0
- `completedAt` is null for ACTIVE and is set once when ACTIVE first becomes COMPLETED
- all mutable fields have private setters
- `FarmingCycleReport.create(member, farm, crop, projection)` constructs a valid row and applies the first projection so its initial `sourceRevision` is 1; do not create a partially initialized entity

- [ ] **Step 4: 엔티티 단위 테스트 통과 확인**

Run:

```bash
cd backend && ./gradlew :domain:test --tests '*FarmingCycleReportTest'
```

Expected: PASS.

- [ ] **Step 5: repository 잠금·소유권 조회 테스트 작성**

Test these exact cases in `FarmingCycleReportRepositoryTest`:

```kotlin
@Test fun `find active report scopes by member farm and crop`()
@Test fun `find reports for update excludes superseded rows`()
@Test fun `other member cannot resolve report by id`()
@Test fun `jsonb reload keeps fingerprint based idempotence`()
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select r from FarmingCycleReport r
        where r.member.id = :memberId and r.farm.id = :farmId and r.crop.id = :cropId
          and r.status <> com.chamchamcham.domain.report.FarmingCycleReportStatus.SUPERSEDED
        order by r.startsAt asc, r.id asc
    """)
    fun findAllCurrentForUpdate(memberId: UUID, farmId: UUID, cropId: UUID): List<FarmingCycleReport>
}
```

Add the scope-creation lock to the existing farm repository. Locking a stable parent row also serializes the first ACTIVE report creation when no report row exists yet.

```kotlin
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select f from Farm f where f.id = :farmId and f.owner.id = :memberId")
fun findOwnedByIdForUpdate(farmId: UUID, memberId: UUID): Farm?
```

`FarmRepositoryLockTest` verifies ownership filtering and reflects on the method to assert `@Lock(PESSIMISTIC_WRITE)` is present.

- [ ] **Step 6: repository 테스트 실행**

Run:

```bash
cd backend && ./gradlew :domain:test --tests '*FarmingCycleReportRepositoryTest' --tests '*FarmRepositoryLockTest'
```

Expected: PASS with H2 test profile.

- [ ] **Step 7: Task 1 커밋**

```bash
git add \
  backend/domain/src/main/kotlin/com/chamchamcham/domain/report/FarmingCycleReportStatus.kt \
  backend/domain/src/main/kotlin/com/chamchamcham/domain/report/FarmingCycleStartBasis.kt \
  backend/domain/src/main/kotlin/com/chamchamcham/domain/report/FarmingCycleReport.kt \
  backend/domain/src/main/kotlin/com/chamchamcham/domain/report/FarmingCycleReportRepository.kt \
  backend/domain/src/main/kotlin/com/chamchamcham/domain/farm/FarmRepository.kt \
  backend/domain/src/test/kotlin/com/chamchamcham/domain/report/FarmingCycleReportTest.kt \
  backend/domain/src/test/kotlin/com/chamchamcham/domain/report/FarmingCycleReportRepositoryTest.kt \
  backend/domain/src/test/kotlin/com/chamchamcham/domain/farm/FarmRepositoryLockTest.kt
git commit -m "feat(report): 주기 리포트 영속 모델을 마련" -m "Tested: ./gradlew :domain:test --tests '*FarmingCycleReport*'"
```

---

### Task 2: 원본 스냅샷과 마지막 수확 기반 주기 분할

**Files:**
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/farming/FarmingCycleReportSourceRepository.kt`
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/farming/FarmingCycleReportSourceRepositoryImpl.kt`
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/farming/FarmingRecordReportScope.kt`
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
- Test: `backend/domain/src/test/kotlin/com/chamchamcham/domain/farming/FarmingRecordReportScopeRepositoryTest.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/report/FarmingCyclePartitionerTest.kt`

**Interfaces:**
- Consumes: 병합된 영농 상세 엔티티와 `HarvestRecord.isFinalHarvest`
- Produces: `FarmingCycleReportSourceLoader.load(scope): List<CycleReportSourceRecord>`, `FarmingCyclePartitioner.partition(records): List<CycleSlice>`

- [ ] **Step 1: 정규화 입력 계약 작성**

```kotlin
data class ReportScope(val memberId: UUID, val farmId: UUID, val cropId: UUID)

data class CycleReportSourceRecord(
    val id: UUID,
    val workedAt: LocalDateTime,
    val workType: WorkType,
    val weatherCondition: String,
    val weatherTemperature: Int,
    val hasPhoto: Boolean,
    val planting: PlantingSource? = null,
    val watering: WateringSource? = null,
    val fertilizing: FertilizingSource? = null,
    val pestControl: PestControlSource? = null,
    val weeding: WeedingSource? = null,
    val harvest: HarvestSource? = null,
)

data class CategoryRef(val code: String, val label: String)

data class PlantingSource(
    val propagationMethod: CategoryRef,
    val quantity: BigDecimal?,
    val quantityUnit: String?,
    val seedAmountGrams: BigDecimal?,
)

data class WateringSource(
    val amount: CategoryRef?,
    val method: CategoryRef?,
)

data class FertilizingSource(
    val materialCategory: CategoryRef,
    val amountKg: BigDecimal,
    val applicationMethod: CategoryRef,
)

data class PestControlSource(
    val pesticideCategory: CategoryRef,
    val pesticideAmount: BigDecimal,
    val pesticideAmountUnit: String,
    val totalSprayAmountLiters: BigDecimal,
    val pestTarget: String?,
)

data class WeedingSource(val method: CategoryRef?)

data class HarvestSource(
    val amountKg: BigDecimal,
    val medicinalPart: CategoryRef,
    val growthPeriodMonths: Int,
    val isFinalHarvest: Boolean,
)

data class CycleSlice(
    val status: FarmingCycleReportStatus,
    val startBasis: FarmingCycleStartBasis,
    val records: List<CycleReportSourceRecord>,
) {
    val finalHarvestRecordId: UUID? = records.lastOrNull()?.takeIf {
        it.harvest?.isFinalHarvest == true
    }?.id
}
```

Keep all category fields as `CategoryRef`; never pass an entity or arbitrary display string into calculators.

- [ ] **Step 2: 주기 분할 실패 테스트 작성**

```kotlin
@Test
fun `partition closes at final harvest and keeps partial harvest in same cycle`() {
    val records = listOf(
        record("01", day = 1, workType = WorkType.FERTILIZING),
        harvest("02", day = 10, final = false),
        harvest("03", day = 12, final = true),
        record("04", day = 20, workType = WorkType.WATERING),
    )

    val slices = partitioner.partition(records)

    assertThat(slices).hasSize(2)
    assertThat(slices[0].status).isEqualTo(FarmingCycleReportStatus.COMPLETED)
    assertThat(slices[0].records.map { it.id.toString().takeLast(2) }).containsExactly("01", "02", "03")
    assertThat(slices[1].status).isEqualTo(FarmingCycleReportStatus.ACTIVE)
    assertThat(slices[1].startBasis).isEqualTo(FarmingCycleStartBasis.AFTER_PREVIOUS_FINAL_HARVEST)
}

@Test
fun `partition keeps work before planting in first cycle`() {
    val records = listOf(
        record("01", day = 1, workType = WorkType.FERTILIZING),
        record("02", day = 3, workType = WorkType.PLANTING),
        harvest("03", day = 90, final = true),
    )
    assertThat(partitioner.partition(records).single().records).hasSize(3)
}
```

- [ ] **Step 3: 테스트 실패 확인**

Run:

```bash
cd backend && ./gradlew :application:test --tests '*FarmingCyclePartitionerTest'
```

Expected: FAIL because the partitioner does not exist.

- [ ] **Step 4: 순수 분할기 구현**

```kotlin
class FarmingCyclePartitioner {
    fun partition(records: List<CycleReportSourceRecord>): List<CycleSlice> {
        val sorted = records.sortedWith(compareBy(CycleReportSourceRecord::workedAt, CycleReportSourceRecord::id))
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

- [ ] **Step 5: 원본 repository의 일괄 조회 테스트 작성**

Persist one record of each work type and two media rows, then assert:

```kotlin
val snapshot = repository.load(memberId, farmId, cropId)

assertThat(snapshot.records).isSortedAccordingTo(compareBy(FarmingRecord::workedAt, FarmingRecord::id))
assertThat(snapshot.harvestByRecordId[harvestRecordId]?.isFinalHarvest).isTrue()
assertThat(snapshot.mediaRecordIds).contains(recordWithMediaId)
assertThat(snapshot.records).allMatch { !it.isDeleted }
```

The implementation must execute one query for base records, one query per detail table with `record.id in :recordIds`, and one distinct media-record query. It must not query details once per record.

- [ ] **Step 6: repository와 source loader 구현**

Repository snapshot contract:

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
    fun load(memberId: UUID, farmId: UUID, cropId: UUID): FarmingCycleReportSourceSnapshot
}

data class FarmingRecordReportScope(
    val memberId: UUID,
    val farmId: UUID,
    val cropId: UUID,
)
```

Add these bulk methods to the existing repositories:

```kotlin
// PlantingRecordRepository
fun findByRecord_IdIn(recordIds: Collection<UUID>): List<PlantingRecord>

// WateringRecordRepository
fun findByRecord_IdIn(recordIds: Collection<UUID>): List<WateringRecord>

// FertilizingRecordRepository
fun findByRecord_IdIn(recordIds: Collection<UUID>): List<FertilizingRecord>

// PestControlRecordRepository
fun findByRecord_IdIn(recordIds: Collection<UUID>): List<PestControlRecord>

// WeedingRecordRepository
fun findByRecord_IdIn(recordIds: Collection<UUID>): List<WeedingRecord>

// HarvestRecordRepository
fun findByRecord_IdIn(recordIds: Collection<UUID>): List<HarvestRecord>

// FarmingRecordMediaRepository
@Query("select distinct m.record.id from FarmingRecordMedia m where m.record.id in :recordIds")
fun findDistinctRecordIdsByRecordIdIn(recordIds: Collection<UUID>): Set<UUID>
```

The base-record JPQL in `FarmingCycleReportSourceRepositoryImpl` must filter `member.id`, `farm.id`, `crop.id`, and `isDeleted = false`, fetch member/farm/crop in the same query, and order by `workedAt asc, id asc`. Return an empty snapshot before calling any `IN` query when no base IDs exist.

Add the backfill scope query to `FarmingRecordRepository` and test that it deduplicates a scope and excludes soft-deleted-only scopes:

```kotlin
@Query("""
    select new com.chamchamcham.domain.farming.FarmingRecordReportScope(
        r.member.id, r.farm.id, r.crop.id
    )
    from FarmingRecord r
    where r.isDeleted = false
    group by r.member.id, r.farm.id, r.crop.id
""")
fun findDistinctReportScopes(): List<FarmingRecordReportScope>
```

In `FarmingCycleReportSourceLoader`, map each detail entity to the normalized source types. Convert seed kg to grams, harvest amount to kg, growth years to months, and expose category `code + label`. Throw `IllegalStateException` when a required merged-schema category is absent; do not fall back to free text.

- [ ] **Step 7: Task 2 테스트 실행**

Run:

```bash
cd backend && ./gradlew :domain:test --tests '*FarmingCycleReportSourceRepositoryTest' --tests '*FarmingRecordReportScopeRepositoryTest' :application:test --tests '*FarmingCyclePartitionerTest'
```

Expected: PASS.

- [ ] **Step 8: Task 2 커밋**

```bash
git add \
  backend/domain/src/main/kotlin/com/chamchamcham/domain/farming/FarmingCycleReportSourceRepository.kt \
  backend/domain/src/main/kotlin/com/chamchamcham/domain/farming/FarmingCycleReportSourceRepositoryImpl.kt \
  backend/domain/src/main/kotlin/com/chamchamcham/domain/farming/FarmingRecordReportScope.kt \
  backend/domain/src/main/kotlin/com/chamchamcham/domain/farming/FarmingRecordRepository.kt \
  backend/domain/src/main/kotlin/com/chamchamcham/domain/farming/PlantingRecordRepository.kt \
  backend/domain/src/main/kotlin/com/chamchamcham/domain/farming/WateringRecordRepository.kt \
  backend/domain/src/main/kotlin/com/chamchamcham/domain/farming/FertilizingRecordRepository.kt \
  backend/domain/src/main/kotlin/com/chamchamcham/domain/farming/PestControlRecordRepository.kt \
  backend/domain/src/main/kotlin/com/chamchamcham/domain/farming/WeedingRecordRepository.kt \
  backend/domain/src/main/kotlin/com/chamchamcham/domain/farming/HarvestRecordRepository.kt \
  backend/domain/src/main/kotlin/com/chamchamcham/domain/farming/FarmingRecordMediaRepository.kt \
  backend/domain/src/test/kotlin/com/chamchamcham/domain/farming/FarmingCycleReportSourceRepositoryTest.kt \
  backend/domain/src/test/kotlin/com/chamchamcham/domain/farming/FarmingRecordReportScopeRepositoryTest.kt \
  backend/application/src/main/kotlin/com/chamchamcham/application/report/CycleReportSource.kt \
  backend/application/src/main/kotlin/com/chamchamcham/application/report/FarmingCycleReportSourceLoader.kt \
  backend/application/src/main/kotlin/com/chamchamcham/application/report/FarmingCyclePartitioner.kt \
  backend/application/src/test/kotlin/com/chamchamcham/application/report/FarmingCyclePartitionerTest.kt
git commit -m "feat(report): 영농기록을 수확 주기로 분할" -m "Tested: source repository and cycle partition tests"
```

---

### Task 3: 공통 통계와 JSONB codec

**Files:**
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/report/statistics/CycleReportStatistics.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/report/statistics/StatisticsMath.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/report/statistics/CommonStatisticsCalculator.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/report/CycleReportStatisticsCodec.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/report/statistics/CommonStatisticsCalculatorTest.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/report/CycleReportStatisticsCodecTest.kt`

**Interfaces:**
- Consumes: `List<CycleReportSourceRecord>`
- Produces: `CommonStatistics`, `CycleReportStatisticsCodec.encode/decode`

- [ ] **Step 1: 타입 통계 schema 작성**

```kotlin
data class Coverage(val recordedCount: Int, val targetCount: Int)
data class CountDistribution(val code: String, val label: String, val count: Int, val ratePct: BigDecimal)
data class AmountByUnit(val unit: String, val amount: BigDecimal, val coverage: Coverage)

data class PlantingMethodStatistics(
    val code: String,
    val label: String,
    val count: Int,
    val ratePct: BigDecimal,
    val quantities: List<AmountByUnit>,
)

data class PlantingStatistics(
    val recordCount: Int,
    val methods: List<PlantingMethodStatistics>,
    val seedAmountGrams: BigDecimal?,
    val seedAmountCoverage: Coverage,
)

data class WateringStatistics(
    val recordCount: Int,
    val workedDayCount: Int,
    val averageIntervalDays: BigDecimal?,
    val amountDistribution: List<CountDistribution>,
    val methodDistribution: List<CountDistribution>,
)

data class FertilizerCategoryStatistics(
    val code: String,
    val label: String,
    val count: Int,
    val countRatePct: BigDecimal,
    val amountKg: BigDecimal,
    val amountRatePct: BigDecimal,
)

data class CategoryMethodCount(
    val categoryCode: String,
    val categoryLabel: String,
    val methodCode: String,
    val methodLabel: String,
    val count: Int,
)

data class FertilizingStatistics(
    val recordCount: Int,
    val totalAmountKg: BigDecimal,
    val averageAmountKg: BigDecimal,
    val categories: List<FertilizerCategoryStatistics>,
    val methods: List<CountDistribution>,
    val categoryMethods: List<CategoryMethodCount>,
)

data class CategoryAmountByUnit(
    val categoryCode: String,
    val categoryLabel: String,
    val unit: String,
    val amount: BigDecimal,
)

data class TargetCount(val target: String, val count: Int)

data class PestControlStatistics(
    val recordCount: Int,
    val averageIntervalDays: BigDecimal?,
    val pesticideAmounts: List<AmountByUnit>,
    val categoryAmounts: List<CategoryAmountByUnit>,
    val totalSprayAmountLiters: BigDecimal,
    val targets: List<TargetCount>,
)

data class WeedingStatistics(
    val recordCount: Int,
    val averageIntervalDays: BigDecimal?,
    val methodDistribution: List<CountDistribution>,
)

data class HarvestPartStatistics(
    val code: String,
    val label: String,
    val count: Int,
    val countRatePct: BigDecimal,
    val amountKg: BigDecimal,
    val amountRatePct: BigDecimal,
)

data class GrowthPeriodRange(val minMonths: Int, val maxMonths: Int)

data class HarvestStatistics(
    val recordCount: Int,
    val totalAmountKg: BigDecimal,
    val averageAmountKg: BigDecimal,
    val firstHarvestedOn: LocalDate,
    val lastHarvestedOn: LocalDate,
    val medicinalParts: List<HarvestPartStatistics>,
    val finalGrowthPeriodMonths: Int?,
    val growthPeriodRangeMonths: GrowthPeriodRange,
)

data class CommonStatistics(
    val totalRecordCount: Int,
    val firstWorkedOn: LocalDate?,
    val lastWorkedOn: LocalDate?,
    val workedDayCount: Int,
    val averageIntervalDays: BigDecimal?,
    val photoAttachedRecordCount: Int,
    val photoAttachmentRatePct: BigDecimal?,
    val weatherDistribution: List<CountDistribution>,
    val averageTemperatureC: BigDecimal?,
)

data class CycleReportStatistics(
    val common: CommonStatistics,
    val byWorkType: Map<WorkType, CommonStatistics>,
    val planting: PlantingStatistics?,
    val watering: WateringStatistics?,
    val fertilizing: FertilizingStatistics?,
    val pestControl: PestControlStatistics?,
    val weeding: WeedingStatistics?,
    val harvest: HarvestStatistics?,
)
```

PRUNING uses only `byWorkType[PRUNING]` and has no dedicated block.

- [ ] **Step 2: 공통 계산 실패 테스트 작성**

```kotlin
@Test
fun `average interval uses distinct work dates`() {
    val records = listOf(record(day = 1), record(day = 1), record(day = 4), record(day = 10))
    val result = calculator.calculate(records)
    assertThat(result.averageIntervalDays).isEqualByComparingTo("4.50")
    assertThat(result.workedDayCount).isEqualTo(3)
}

@Test
fun `empty records return null averages instead of zero`() {
    val result = calculator.calculate(emptyList())
    assertThat(result.averageIntervalDays).isNull()
    assertThat(result.averageTemperatureC).isNull()
    assertThat(result.photoAttachmentRatePct).isNull()
}
```

- [ ] **Step 3: 공통 계산 구현**

`StatisticsMath` rules:

```kotlin
fun percentage(numerator: Int, denominator: Int): BigDecimal? =
    denominator.takeIf { it > 0 }?.let {
        BigDecimal(numerator).multiply(BigDecimal(100))
            .divide(BigDecimal(it), 2, RoundingMode.HALF_UP)
    }

fun average(values: List<BigDecimal>): BigDecimal? =
    values.takeIf { it.isNotEmpty() }?.sumOf { it }
        ?.divide(BigDecimal(values.size), 2, RoundingMode.HALF_UP)
```

Average interval uses distinct `LocalDate`s and `ChronoUnit.DAYS` between adjacent dates. Weather distribution includes blank values as code `MISSING`, label `미입력`.

- [ ] **Step 4: 공통 계산 테스트 통과 확인**

Run:

```bash
cd backend && ./gradlew :application:test --tests '*CommonStatisticsCalculatorTest'
```

Expected: PASS.

- [ ] **Step 5: codec round-trip 실패 테스트와 구현**

```kotlin
@Test
fun `codec round trips typed statistics through map`() {
    val original = fixtureStatistics()
    val encoded = codec.encode(original)
    assertThat(codec.decode(encoded.value)).isEqualTo(original)
}

@Test
fun `fingerprint is stable across map insertion order and repeated encoding`() {
    val original = fixtureStatistics()
    val reordered = original.copy(
        byWorkType = original.byWorkType.entries.reversed().associate { it.key to it.value },
    )
    assertThat(codec.encode(original).fingerprint)
        .isEqualTo(codec.encode(reordered).fingerprint)
}
```

Implementation:

```kotlin
@Component
class CycleReportStatisticsCodec(private val objectMapper: ObjectMapper) {
    data class Encoded(
        val value: Map<String, Any?>,
        val fingerprint: String,
    )

    fun encode(statistics: CycleReportStatistics): Encoded {
        val canonicalBytes = objectMapper.writerFor(CycleReportStatistics::class.java)
            .with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .writeValueAsBytes(statistics)
        return Encoded(
            value = objectMapper.convertValue(
                statistics,
                object : TypeReference<Map<String, Any?>>() {},
            ),
            fingerprint = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(canonicalBytes),
            ),
        )
    }

    fun decode(json: Map<String, Any?>): CycleReportStatistics =
        objectMapper.convertValue(json, CycleReportStatistics::class.java)
}
```

- [ ] **Step 6: Task 3 전체 테스트와 커밋**

```bash
cd backend && ./gradlew :application:test --tests '*CommonStatisticsCalculatorTest' --tests '*CycleReportStatisticsCodecTest'
git add \
  backend/application/src/main/kotlin/com/chamchamcham/application/report/statistics/CycleReportStatistics.kt \
  backend/application/src/main/kotlin/com/chamchamcham/application/report/statistics/StatisticsMath.kt \
  backend/application/src/main/kotlin/com/chamchamcham/application/report/statistics/CommonStatisticsCalculator.kt \
  backend/application/src/main/kotlin/com/chamchamcham/application/report/CycleReportStatisticsCodec.kt \
  backend/application/src/test/kotlin/com/chamchamcham/application/report/statistics/CommonStatisticsCalculatorTest.kt \
  backend/application/src/test/kotlin/com/chamchamcham/application/report/CycleReportStatisticsCodecTest.kt
git commit -m "feat(report): 공통 통계 스키마와 직렬화를 추가" -m "Tested: common statistics and JSON codec tests"
```

---

### Task 4: 파종·관수·제초 통계 계산기

**Files:**
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/report/statistics/PlantingStatisticsCalculator.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/report/statistics/WateringStatisticsCalculator.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/report/statistics/WeedingStatisticsCalculator.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/report/statistics/PlantingStatisticsCalculatorTest.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/report/statistics/WateringStatisticsCalculatorTest.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/report/statistics/WeedingStatisticsCalculatorTest.kt`

**Interfaces:**
- Consumes: normalized planting, watering, weeding source blocks
- Produces: `PlantingStatistics?`, `WateringStatistics?`, `WeedingStatistics?`

- [ ] **Step 1: 작업별 기대 통계 테스트 작성**

```kotlin
@Test
fun `planting groups stable method codes and sums compatible quantities`() {
    val result = calculator.calculate(listOf(seedPlanting(grams = "500"), seedPlanting(grams = "750"), cutting(count = "20")))
    assertThat(result!!.methods.map { it.code to it.count }).containsExactly("SEED" to 2, "CUTTING" to 1)
    assertThat(result.seedAmountGrams).isEqualByComparingTo("1250")
}

@Test
fun `watering reports distribution without adequacy judgement`() {
    val result = calculator.calculate(listOf(watering("LOW", "DRIP"), watering(null, "MANUAL")))
    assertThat(result!!.amountDistribution.map { it.code to it.count })
        .containsExactlyInAnyOrder("LOW" to 1, "MISSING" to 1)
    assertThat(result).hasNoNullFieldsOrPropertiesExcept("averageIntervalDays")
}

@Test
fun `weeding includes missing method bucket`() {
    val result = calculator.calculate(listOf(weeding("HAND"), weeding(null)))
    assertThat(result!!.methodDistribution.map { it.code to it.count })
        .containsExactlyInAnyOrder("HAND" to 1, "MISSING" to 1)
}
```

- [ ] **Step 2: 작업별 계산기 구현**

Each calculator:

- filters only its work type
- returns `null` when no records exist
- sorts distribution by count descending then code ascending
- uses `StatisticsMath.percentage`
- includes MISSING in the denominator
- never returns adequacy, shortage, excess, or recommendation fields

Planting amount sums only normalized grams and same quantity-unit codes. Watering returns amount and method distributions plus common count/interval values. Weeding returns method distribution plus common count/interval values.

- [ ] **Step 3: Task 4 테스트 실행**

Run:

```bash
cd backend && ./gradlew :application:test --tests '*PlantingStatisticsCalculatorTest' --tests '*WateringStatisticsCalculatorTest' --tests '*WeedingStatisticsCalculatorTest'
```

Expected: PASS.

- [ ] **Step 4: Task 4 커밋**

```bash
git add \
  backend/application/src/main/kotlin/com/chamchamcham/application/report/statistics/PlantingStatisticsCalculator.kt \
  backend/application/src/main/kotlin/com/chamchamcham/application/report/statistics/WateringStatisticsCalculator.kt \
  backend/application/src/main/kotlin/com/chamchamcham/application/report/statistics/WeedingStatisticsCalculator.kt \
  backend/application/src/test/kotlin/com/chamchamcham/application/report/statistics/PlantingStatisticsCalculatorTest.kt \
  backend/application/src/test/kotlin/com/chamchamcham/application/report/statistics/WateringStatisticsCalculatorTest.kt \
  backend/application/src/test/kotlin/com/chamchamcham/application/report/statistics/WeedingStatisticsCalculatorTest.kt
git commit -m "feat(report): 파종 관수 제초 통계를 계산" -m "Constraint: 관수 적정성 해석은 포함하지 않음" -m "Tested: planting watering weeding calculator tests"
```

---

### Task 5: 시비·방제·수확 통계와 전체 조합

**Files:**
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/report/statistics/FertilizingStatisticsCalculator.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/report/statistics/PestControlStatisticsCalculator.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/report/statistics/HarvestStatisticsCalculator.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/report/statistics/CycleReportStatisticsCalculator.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/report/statistics/FertilizingStatisticsCalculatorTest.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/report/statistics/PestControlStatisticsCalculatorTest.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/report/statistics/HarvestStatisticsCalculatorTest.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/report/statistics/CycleReportStatisticsCalculatorTest.kt`

**Interfaces:**
- Consumes: normalized category codes, kg/ml/g/L quantities, medicinal part and final growth period
- Produces: complete `CycleReportStatistics`

- [ ] **Step 1: 수량 통계 실패 테스트 작성**

```kotlin
@Test
fun `fertilizing groups category and soil foliar method`() {
    val result = calculator.calculate(listOf(fertilizing("NPK", "복합비료", "10", "SOIL"), fertilizing("NPK", "복합비료", "5", "FOLIAR")))
    assertThat(result!!.totalAmountKg).isEqualByComparingTo("15")
    assertThat(result.categories.single().amountKg).isEqualByComparingTo("15")
    assertThat(result.methods.map { it.code to it.count }).containsExactlyInAnyOrder("SOIL" to 1, "FOLIAR" to 1)
}

@Test
fun `pest control never combines grams and milliliters`() {
    val result = calculator.calculate(listOf(pest("A", "10", "ML", "100"), pest("A", "5", "G", "50")))
    assertThat(result!!.pesticideAmounts.map { it.unit to it.amount })
        .containsExactlyInAnyOrder("ML" to BigDecimal("10"), "G" to BigDecimal("5"))
    assertThat(result.totalSprayAmountLiters).isEqualByComparingTo("150")
}

@Test
fun `harvest uses kilograms and final harvest growth period`() {
    val result = calculator.calculate(listOf(harvest("20", months = 10, final = false), harvest("30", months = 12, final = true)))
    assertThat(result!!.totalAmountKg).isEqualByComparingTo("50")
    assertThat(result.finalGrowthPeriodMonths).isEqualTo(12)
    assertThat(result.growthPeriodRangeMonths).isEqualTo(GrowthPeriodRange(10, 12))
}
```

- [ ] **Step 2: 세 계산기 구현**

Implementation rules:

- Fertilizing: category count/rate, category kg/rate, SOIL/FOLIAR count/rate, category-method cross table, total and per-record average kg.
- Pest control: category count, category amounts grouped by unit, total spray L, trimmed nonblank target frequency, ml/g never combined.
- Harvest: total/average kg, harvest count, first/last date, medicinal-part count/kg/rate, final-harvest growth months, min/max growth months.
- All category comparisons use `CategoryRef.code`; labels are display metadata.

- [ ] **Step 3: 전체 계산기 조합 테스트 작성 및 구현**

```kotlin
@Test
fun `calculator returns common and every present work section`() {
    val result = calculator.calculate(allWorkTypeFixture())
    assertThat(result.common.totalRecordCount).isEqualTo(7)
    assertThat(result.byWorkType.keys).containsExactlyInAnyOrderElementsOf(WorkType.entries)
    assertThat(result.watering).isNotNull()
    assertThat(result.fertilizing).isNotNull()
    assertThat(result.pestControl).isNotNull()
    assertThat(result.harvest).isNotNull()
}
```

`CycleReportStatisticsCalculator` receives all calculators by constructor injection and builds `byWorkType` with common statistics. A missing work type has common count 0 and a null dedicated section.

- [ ] **Step 4: Task 5 테스트 실행**

Run:

```bash
cd backend && ./gradlew :application:test --tests '*StatisticsCalculatorTest'
```

Expected: all common and work-specific calculator tests PASS.

- [ ] **Step 5: Task 5 커밋**

```bash
git add \
  backend/application/src/main/kotlin/com/chamchamcham/application/report/statistics/FertilizingStatisticsCalculator.kt \
  backend/application/src/main/kotlin/com/chamchamcham/application/report/statistics/PestControlStatisticsCalculator.kt \
  backend/application/src/main/kotlin/com/chamchamcham/application/report/statistics/HarvestStatisticsCalculator.kt \
  backend/application/src/main/kotlin/com/chamchamcham/application/report/statistics/CycleReportStatisticsCalculator.kt \
  backend/application/src/test/kotlin/com/chamchamcham/application/report/statistics/FertilizingStatisticsCalculatorTest.kt \
  backend/application/src/test/kotlin/com/chamchamcham/application/report/statistics/PestControlStatisticsCalculatorTest.kt \
  backend/application/src/test/kotlin/com/chamchamcham/application/report/statistics/HarvestStatisticsCalculatorTest.kt \
  backend/application/src/test/kotlin/com/chamchamcham/application/report/statistics/CycleReportStatisticsCalculatorTest.kt
git commit -m "feat(report): 수량 기반 작업 통계를 완성" -m "Directive: ml과 g 약제량을 합산하지 말 것" -m "Tested: all report statistics calculator tests"
```

---

### Task 6: 프로젝션 재계산과 영농기록 CRUD 연결

**Files:**
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/report/FarmingCycleReportProjectionService.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/farming/FarmingRecordService.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/report/FarmingCycleReportProjectionServiceTest.kt`
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/farming/FarmingRecordServiceTest.kt`

**Interfaces:**
- Consumes: locked owned farm, crop and record repositories, source loader, partitioner, statistics calculator, codec, report repository
- Produces: `rebuild(scope)` and `rebuild(scopes)` called atomically from CRUD

- [ ] **Step 1: reconcile 동작 실패 테스트 작성**

Cover these exact cases:

```kotlin
@Test fun `first records create one active projection`()
@Test fun `final harvest converts matching active report to completed`()
@Test fun `record after final harvest creates a new active report`()
@Test fun `historical final boundary does not consume a later active report`()
@Test fun `equal timestamps still reserve active identity for the trailing active slice`()
@Test fun `changed completed boundary creates replacement and supersedes old snapshot`()
@Test fun `removing final boundary supersedes obsolete report and merges records`()
@Test fun `restored final boundary creates current replacement while superseded snapshot remains`()
@Test fun `same calculated payload does not increment revision`()
@Test fun `empty scope supersedes every current report`()
@Test fun `farm row is locked before source records are read`()
```

- [ ] **Step 2: projection service 구현**

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
    private val codec: CycleReportStatisticsCodec,
    private val reportRepository: FarmingCycleReportRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    fun rebuild(scope: ReportScope) {
        val farm = farmRepository.findOwnedByIdForUpdate(scope.farmId, scope.memberId)
            ?: throw BusinessException(ErrorCode.FARM_NOT_FOUND)
        val crop = cropRepository.findById(scope.cropId).orElseThrow {
            BusinessException(ErrorCode.CROP_NOT_FOUND)
        }
        val existing = reportRepository.findAllCurrentForUpdate(scope.memberId, scope.farmId, scope.cropId)
        val slices = partitioner.partition(sourceLoader.load(scope))
        val unmatched = existing.associateBy { requireNotNull(it.id) }.toMutableMap()

        slices.forEachIndexed { index, slice ->
            val matched = takeMatchingReport(
                unmatched = unmatched,
                slice = slice,
                allowActiveCompletion = index == slices.lastIndex &&
                    slice.status == FarmingCycleReportStatus.COMPLETED,
            )
            val projection = toProjection(matched, slice)
            val report = matched?.also { it.applyProjection(projection) }
                ?: FarmingCycleReport.create(farm.owner, farm, crop, projection)
            reportRepository.save(report)
        }

        val superseded = unmatched.values.onEach(FarmingCycleReport::supersede)
        reportRepository.saveAll(superseded)
    }

    fun rebuild(scopes: Collection<ReportScope>) {
        scopes.distinct().sortedWith(compareBy(ReportScope::memberId, ReportScope::farmId, ReportScope::cropId))
            .forEach(::rebuild)
    }

    private fun takeMatchingReport(
        unmatched: MutableMap<UUID, FarmingCycleReport>,
        slice: CycleSlice,
        allowActiveCompletion: Boolean,
    ): FarmingCycleReport? {
        val exactCompleted = slice.finalHarvestRecordId?.let { finalId ->
            unmatched.values.firstOrNull { it.finalHarvestRecord?.id == finalId }
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

    private fun toProjection(
        current: FarmingCycleReport?,
        slice: CycleSlice,
    ): FarmingCycleReportProjection {
        val completed = slice.status == FarmingCycleReportStatus.COMPLETED
        val finalId = slice.finalHarvestRecordId
        val encoded = codec.encode(statisticsCalculator.calculate(slice.records))
        return FarmingCycleReportProjection(
            status = slice.status,
            startsAt = slice.records.first().workedAt,
            endsAt = slice.records.last().workedAt.takeIf { completed },
            completedAt = when {
                !completed -> null
                current?.status == FarmingCycleReportStatus.COMPLETED ->
                    current.completedAt ?: LocalDateTime.now(clock)
                else -> LocalDateTime.now(clock)
            },
            startBasis = slice.startBasis,
            plantingMilestoneAt = slice.records.firstOrNull {
                it.workType == WorkType.PLANTING
            }?.workedAt,
            finalHarvestRecord = finalId?.let(farmingRecordRepository::getReferenceById),
            statisticsSchemaVersion = "cycle-report-statistics.v1",
            statistics = encoded.value,
            statisticsFingerprint = encoded.fingerprint,
        )
    }
}
```

`takeMatchingReport` preserves identity for an exact final-harvest boundary. It reuses the existing ACTIVE row for an ACTIVE slice, or converts it to COMPLETED only when the newest slice is completed and no trailing ACTIVE slice exists. This avoids consuming the current ACTIVE identity when a historical boundary is inserted, including equal-timestamp records. A changed boundary on an already completed report creates a replacement and leaves the former snapshot available as SUPERSEDED for later coaching audit. The parent farm lock is acquired before both report lookup and source lookup, so an absent ACTIVE row has no first-create race.

- [ ] **Step 3: projection 테스트 실행**

Run:

```bash
cd backend && ./gradlew :application:test --tests '*FarmingCycleReportProjectionServiceTest'
```

Expected: PASS.

- [ ] **Step 4: FarmingRecordService 연결 실패 테스트 작성**

Add a mocked `FarmingCycleReportProjectionService` and verify:

```kotlin
@Test fun `create rebuilds saved farm crop scope`()
@Test fun `update rebuilds both old and new scopes when farm or crop changes`()
@Test fun `delete rebuilds deleted record scope`()
```

On update, capture old IDs before `record.update`. On create, rebuild only after detail and media are saved. On delete, soft-delete before rebuild so the source query excludes the row.

- [ ] **Step 5: CRUD 연결 구현과 전체 farming 테스트 실행**

Run:

```bash
cd backend && ./gradlew :application:test --tests '*FarmingRecordServiceTest' --tests '*FarmingCycleReportProjectionServiceTest'
```

Expected: PASS. A projection exception rolls back the record mutation; LLM is not involved in this transaction.

- [ ] **Step 6: Task 6 커밋**

```bash
git add backend/application/src/main/kotlin/com/chamchamcham/application/report/FarmingCycleReportProjectionService.kt backend/application/src/main/kotlin/com/chamchamcham/application/farming/FarmingRecordService.kt backend/application/src/test/kotlin/com/chamchamcham/application/report/FarmingCycleReportProjectionServiceTest.kt backend/application/src/test/kotlin/com/chamchamcham/application/farming/FarmingRecordServiceTest.kt
git commit -m "feat(report): 영농기록 변경에 주기 리포트를 동기화" -m "Rejected: 필드별 증감 갱신 | 경계 변경 시 불일치 위험" -m "Tested: projection and farming record service tests"
```

---

### Task 7: 현재·완료·직전 주기 조회 서비스와 커서

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
- Consumes: stored reports and codec
- Produces: `getCurrent`, `search`, `getDetail` with previous completed snapshot

- [ ] **Step 1: cursor repository 테스트와 계약 작성**

```kotlin
interface FarmingCycleReportQueryRepository {
    fun search(condition: SearchCondition): List<FarmingCycleReport>
    fun findPreviousCompleted(current: FarmingCycleReport): FarmingCycleReport?

    data class SearchCondition(
        val memberId: UUID,
        val farmId: UUID?,
        val cropId: UUID?,
        val status: FarmingCycleReportStatus,
        val cursor: Cursor?,
        val size: Int,
    )

    data class Cursor(val sortAt: LocalDateTime, val id: UUID)
}
```

Add the application cursor payload exactly as follows:

```kotlin
data class FarmingCycleReportCursorPayload(
    val status: FarmingCycleReportStatus,
    val sortAt: LocalDateTime,
    val id: UUID,
)
```

Test member isolation, filters, cursor continuation, and exclusion of SUPERSEDED for both allowed statuses. COMPLETED uses agricultural cycle order `endsAt desc, id desc`; ACTIVE uses `updatedAt desc, id desc`. Build one of two fixed JPQL strings in the repository implementation rather than interpolating a request value as a property name. Reject a cursor whose embedded status differs from the requested status. Set cursor `sortAt` from `endsAt` for COMPLETED and from `updatedAt` for ACTIVE; a backdated replacement report must not jump to the top merely because its `completedAt` is recent.

The two cursor predicates are:

```text
COMPLETED: cursor is null or r.endsAt < cursor.sortAt or (r.endsAt = cursor.sortAt and r.id < cursor.id)
ACTIVE:    cursor is null or r.updatedAt < cursor.sortAt or (r.updatedAt = cursor.sortAt and r.id < cursor.id)
```

Both queries require `r.member.id = memberId`, the requested status, optional farm/crop equality filters, descending sort, and `setMaxResults(condition.size)`. SUPERSEDED can never be supplied because the application validates status before constructing the domain condition.

`findPreviousCompleted` uses cycle order, not creation time:

- for ACTIVE, select the latest COMPLETED row in the same member/farm/crop ordered by `endsAt desc, finalHarvestRecord.id desc`
- for COMPLETED, select the latest COMPLETED row whose `(endsAt, finalHarvestRecord.id)` tuple is less than the current tuple, excluding the current ID
- rows with equal timestamps are resolved by the same farming-record UUID tie-breaker used by the partitioner

Use these predicates in `FarmingCycleReportQueryRepositoryImpl`, always adding `r.status = COMPLETED` and the same member/farm/crop IDs:

```kotlin
val beforeCurrentPredicate = if (current.status == FarmingCycleReportStatus.ACTIVE) {
    ""
} else {
    """
      and (
        r.endsAt < :currentEndsAt
        or (r.endsAt = :currentEndsAt and r.finalHarvestRecord.id < :currentFinalHarvestId)
      )
      and r.id <> :currentId
    """
}
```

Order both variants by `r.endsAt desc, r.finalHarvestRecord.id desc` and apply `setMaxResults(1)`. COMPLETED reports are guaranteed to have both boundary values by the entity and database checks.

- [ ] **Step 2: JPQL query 구현과 domain 테스트 실행**

Run:

```bash
cd backend && ./gradlew :domain:test --tests '*FarmingCycleReportQueryRepositoryTest'
```

Expected: PASS.

- [ ] **Step 3: query service 실패 테스트 작성**

```kotlin
@Test fun `get current returns active report for owned farm crop`()
@Test fun `get current throws REPORT_NOT_FOUND when active report does not exist`()
@Test fun `detail includes immediately previous completed cycle`()
@Test fun `first completed cycle returns null previous cycle`()
@Test fun `previous completed cycle resolves equal end timestamps by final record id`()
@Test fun `superseded report is not exposed`()
@Test fun `search rejects superseded status and cursor status mismatch`()
@Test fun `search rejects page size outside one through one hundred`()
@Test fun `search encodes next cursor from last visible row`()
```

Result contract:

```kotlin
data class CycleSnapshot(
    val id: UUID,
    val status: FarmingCycleReportStatus,
    val farmId: UUID,
    val farmName: String,
    val cropId: UUID,
    val cropName: String,
    val startsAt: LocalDateTime,
    val endsAt: LocalDateTime?,
    val completedAt: LocalDateTime?,
    val updatedAt: LocalDateTime,
    val startBasis: FarmingCycleStartBasis,
    val plantingMilestoneAt: LocalDateTime?,
    val finalHarvestRecordId: UUID?,
    val statisticsSchemaVersion: String,
    val sourceRevision: Long,
    val statistics: CycleReportStatistics,
)

data class Detail(val current: CycleSnapshot, val previous: CycleSnapshot?)
data class Page(val items: List<CycleSnapshot>, val nextCursor: String?)
```

Application search condition:

```kotlin
data class FarmingCycleReportSearchCondition(
    val memberId: UUID,
    val farmId: UUID?,
    val cropId: UUID?,
    val status: FarmingCycleReportStatus = FarmingCycleReportStatus.COMPLETED,
    val cursor: String?,
    val size: Int,
)
```

Allow only ACTIVE and COMPLETED, validate `size in 1..100`, decode with the existing `OpaqueCursorCodec`, and request `size + 1` rows to produce `nextCursor`. Do not persist a previous-report FK.

- [ ] **Step 4: query service 구현과 테스트 실행**

Add errors:

```kotlin
REPORT_NOT_FOUND("REPORT_001", "error.report_not_found", 404),
REPORT_INVALID_STATUS("REPORT_002", "error.report_invalid_status", 400),
```

Run:

```bash
cd backend && ./gradlew :application:test --tests '*FarmingCycleReportQueryServiceTest'
```

Expected: PASS.

- [ ] **Step 5: Task 7 커밋**

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
git commit -m "feat(report): 현재와 직전 영농 주기를 조회" -m "Constraint: SUPERSEDED 리포트는 제품 조회에서 제외" -m "Tested: report query repository and service tests"
```

---

### Task 8: 리포트 HTTP API

**Files:**
- Create: `backend/api/src/main/kotlin/com/chamchamcham/api/report/controller/FarmingCycleReportController.kt`
- Create: `backend/api/src/main/kotlin/com/chamchamcham/api/report/dto/FarmingCycleReportResponses.kt`
- Test: `backend/api/src/test/kotlin/com/chamchamcham/api/report/controller/FarmingCycleReportControllerTest.kt`

**Interfaces:**
- Consumes: `FarmingCycleReportQueryService`
- Produces: current, list, detail endpoints under `/api/v1/farming-reports`

- [ ] **Step 1: MockMvc 계약 테스트 작성**

Cover:

```kotlin
@Test fun `GET current returns active report statistics`()
@Test fun `GET list returns completed reports and cursor`()
@Test fun `GET detail returns current and previous cycle snapshots`()
@Test fun `missing authentication returns unauthorized`()
@Test fun `invalid member principal returns unauthorized`()
@Test fun `SUPERSEDED status filter returns bad request`()
```

Expected paths:

```text
GET /api/v1/farming-reports/current?farmId={farmId}&cropId={cropId}
GET /api/v1/farming-reports?farmId={farmId}&cropId={cropId}&status=COMPLETED&cursor={cursor}&size=20
GET /api/v1/farming-reports/{reportId}
```

- [ ] **Step 2: 테스트 실패 확인**

Run:

```bash
cd backend && ./gradlew :api:test --tests '*FarmingCycleReportControllerTest'
```

Expected: FAIL because the controller does not exist.

- [ ] **Step 3: DTO와 controller 구현**

Controller signatures:

```kotlin
@GetMapping("/current")
fun current(
    @AuthenticationPrincipal memberId: String?,
    @RequestParam farmId: UUID,
    @RequestParam cropId: UUID,
): ResponseEntity<ApiResponse<FarmingCycleReportResponses.DetailResponse>>

@GetMapping
fun list(
    @AuthenticationPrincipal memberId: String?,
    @RequestParam(required = false) farmId: UUID?,
    @RequestParam(required = false) cropId: UUID?,
    @RequestParam(defaultValue = "COMPLETED") status: FarmingCycleReportStatus,
    @RequestParam(required = false) cursor: String?,
    @RequestParam(defaultValue = "20") size: Int,
): ResponseEntity<ApiResponse<FarmingCycleReportResponses.PageResponse>>

@GetMapping("/{reportId}")
fun detail(
    @AuthenticationPrincipal memberId: String?,
    @PathVariable reportId: UUID,
): ResponseEntity<ApiResponse<FarmingCycleReportResponses.DetailResponse>>
```

Define the response contract explicitly; `workTypes` is a typed list instead of exposing the persisted JSON map.

```kotlin
object FarmingCycleReportResponses {
    data class DetailResponse(val current: SnapshotResponse, val previous: SnapshotResponse?)
    data class PageResponse(val items: List<SnapshotResponse>, val nextCursor: String?)

    data class SnapshotResponse(
        val id: UUID,
        val status: String,
        val farmId: UUID,
        val farmName: String,
        val cropId: UUID,
        val cropName: String,
        val startsAt: LocalDateTime,
        val endsAt: LocalDateTime?,
        val completedAt: LocalDateTime?,
        val updatedAt: LocalDateTime,
        val startBasis: String,
        val plantingMilestoneAt: LocalDateTime?,
        val finalHarvestRecordId: UUID?,
        val statisticsSchemaVersion: String,
        val sourceRevision: Long,
        val statistics: StatisticsResponse,
    )

    data class StatisticsResponse(
        val common: CommonStatisticsResponse,
        val workTypes: List<WorkTypeStatisticsResponse>,
        val planting: PlantingStatisticsResponse?,
        val watering: WateringStatisticsResponse?,
        val fertilizing: FertilizingStatisticsResponse?,
        val pestControl: PestControlStatisticsResponse?,
        val weeding: WeedingStatisticsResponse?,
        val harvest: HarvestStatisticsResponse?,
    )

    data class CoverageResponse(val recordedCount: Int, val targetCount: Int)
    data class DistributionResponse(
        val code: String,
        val label: String,
        val count: Int,
        val ratePct: BigDecimal,
    )
    data class AmountByUnitResponse(
        val unit: String,
        val amount: BigDecimal,
        val coverage: CoverageResponse,
    )
    data class CommonStatisticsResponse(
        val totalRecordCount: Int,
        val firstWorkedOn: LocalDate?,
        val lastWorkedOn: LocalDate?,
        val workedDayCount: Int,
        val averageIntervalDays: BigDecimal?,
        val photoAttachedRecordCount: Int,
        val photoAttachmentRatePct: BigDecimal?,
        val weatherDistribution: List<DistributionResponse>,
        val averageTemperatureC: BigDecimal?,
    )
    data class WorkTypeStatisticsResponse(
        val workType: String,
        val common: CommonStatisticsResponse,
    )
    data class PlantingMethodStatisticsResponse(
        val code: String,
        val label: String,
        val count: Int,
        val ratePct: BigDecimal,
        val quantities: List<AmountByUnitResponse>,
    )
    data class PlantingStatisticsResponse(
        val recordCount: Int,
        val methods: List<PlantingMethodStatisticsResponse>,
        val seedAmountGrams: BigDecimal?,
        val seedAmountCoverage: CoverageResponse,
    )
    data class WateringStatisticsResponse(
        val recordCount: Int,
        val workedDayCount: Int,
        val averageIntervalDays: BigDecimal?,
        val amountDistribution: List<DistributionResponse>,
        val methodDistribution: List<DistributionResponse>,
    )
    data class FertilizerCategoryStatisticsResponse(
        val code: String,
        val label: String,
        val count: Int,
        val countRatePct: BigDecimal,
        val amountKg: BigDecimal,
        val amountRatePct: BigDecimal,
    )
    data class CategoryMethodCountResponse(
        val categoryCode: String,
        val categoryLabel: String,
        val methodCode: String,
        val methodLabel: String,
        val count: Int,
    )
    data class FertilizingStatisticsResponse(
        val recordCount: Int,
        val totalAmountKg: BigDecimal,
        val averageAmountKg: BigDecimal,
        val categories: List<FertilizerCategoryStatisticsResponse>,
        val methods: List<DistributionResponse>,
        val categoryMethods: List<CategoryMethodCountResponse>,
    )
    data class CategoryAmountByUnitResponse(
        val categoryCode: String,
        val categoryLabel: String,
        val unit: String,
        val amount: BigDecimal,
    )
    data class TargetCountResponse(val target: String, val count: Int)
    data class PestControlStatisticsResponse(
        val recordCount: Int,
        val averageIntervalDays: BigDecimal?,
        val pesticideAmounts: List<AmountByUnitResponse>,
        val categoryAmounts: List<CategoryAmountByUnitResponse>,
        val totalSprayAmountLiters: BigDecimal,
        val targets: List<TargetCountResponse>,
    )
    data class WeedingStatisticsResponse(
        val recordCount: Int,
        val averageIntervalDays: BigDecimal?,
        val methodDistribution: List<DistributionResponse>,
    )
    data class HarvestPartStatisticsResponse(
        val code: String,
        val label: String,
        val count: Int,
        val countRatePct: BigDecimal,
        val amountKg: BigDecimal,
        val amountRatePct: BigDecimal,
    )
    data class GrowthPeriodRangeResponse(val minMonths: Int, val maxMonths: Int)
    data class HarvestStatisticsResponse(
        val recordCount: Int,
        val totalAmountKg: BigDecimal,
        val averageAmountKg: BigDecimal,
        val firstHarvestedOn: LocalDate,
        val lastHarvestedOn: LocalDate,
        val medicinalParts: List<HarvestPartStatisticsResponse>,
        val finalGrowthPeriodMonths: Int?,
        val growthPeriodRangeMonths: GrowthPeriodRangeResponse,
    )
}
```

Add `from` mapping functions in the same file for `FarmingCycleReportResult.Detail`, `Page`, `CycleSnapshot`, and every statistics type. Serialize enums with `.name`; preserve nulls and `BigDecimal` values exactly. The controller never returns application result classes directly.

- [ ] **Step 4: controller 테스트 통과 확인**

Run:

```bash
cd backend && ./gradlew :api:test --tests '*FarmingCycleReportControllerTest'
```

Expected: PASS.

- [ ] **Step 5: Task 8 커밋**

```bash
git add \
  backend/api/src/main/kotlin/com/chamchamcham/api/report/controller/FarmingCycleReportController.kt \
  backend/api/src/main/kotlin/com/chamchamcham/api/report/dto/FarmingCycleReportResponses.kt \
  backend/api/src/test/kotlin/com/chamchamcham/api/report/controller/FarmingCycleReportControllerTest.kt
git commit -m "feat(report): 주기별 영농 리포트 조회 API를 제공" -m "Tested: FarmingCycleReportControllerTest"
```

---

### Task 9: 기존 영농기록 리포트 backfill

**Files:**
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/report/FarmingCycleReportBackfillService.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/report/FarmingCycleReportBackfillServiceTest.kt`
- Create: `backend/api/src/main/kotlin/com/chamchamcham/config/FarmingCycleReportBackfillRunner.kt`
- Modify: `backend/api/src/main/resources/application.yml`
- Test: `backend/api/src/test/kotlin/com/chamchamcham/config/FarmingCycleReportBackfillRunnerTest.kt`

**Interfaces:**
- Consumes: distinct non-deleted record scopes from Task 2
- Produces: disabled-by-default, rerunnable one-time backfill

- [ ] **Step 1: 범위 정렬과 runner opt-in 실패 테스트 작성**

```kotlin
@Test
fun `backfill rebuilds each distinct scope in lock order`() {
    whenever(recordRepository.findDistinctReportScopes()).thenReturn(
        listOf(scope(member = "02", farm = "02", crop = "01"), scope(member = "01", farm = "03", crop = "01")),
    )

    assertThat(service.rebuildAll()).isEqualTo(2)

    inOrder(projectionService).apply {
        verify(projectionService).rebuild(reportScope(member = "01", farm = "03", crop = "01"))
        verify(projectionService).rebuild(reportScope(member = "02", farm = "02", crop = "01"))
    }
}

@Test
fun `runner requires explicit enabled property`() {
    val annotation = FarmingCycleReportBackfillRunner::class.java
        .getAnnotation(ConditionalOnProperty::class.java)
    assertThat(annotation.prefix).isEqualTo("report.backfill")
    assertThat(annotation.name).containsExactly("enabled")
    assertThat(annotation.havingValue).isEqualTo("true")
    assertThat(annotation.matchIfMissing).isFalse()
}
```

- [ ] **Step 2: backfill service와 runner 구현**

```kotlin
@Service
class FarmingCycleReportBackfillService(
    private val recordRepository: FarmingRecordRepository,
    private val projectionService: FarmingCycleReportProjectionService,
) {
    fun rebuildAll(): Int {
        val scopes = recordRepository.findDistinctReportScopes()
            .map { ReportScope(it.memberId, it.farmId, it.cropId) }
            .distinct()
            .sortedWith(compareBy(ReportScope::memberId, ReportScope::farmId, ReportScope::cropId))
        scopes.forEach(projectionService::rebuild)
        return scopes.size
    }
}

@Component
@ConditionalOnProperty(
    prefix = "report.backfill",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false,
)
class FarmingCycleReportBackfillRunner(
    private val service: FarmingCycleReportBackfillService,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        val rebuiltCount = service.rebuildAll()
        logger.info { "Farming cycle report backfill completed: $rebuiltCount scopes" }
    }
}
```

Do not annotate `rebuildAll` with `@Transactional`; each call into the separate projection-service bean owns one transaction and releases its farm lock before the next scope. Add this disabled default to `application.yml`:

```yaml
report:
  backfill:
    enabled: ${REPORT_BACKFILL_ENABLED:false}
```

Enable `REPORT_BACKFILL_ENABLED=true` for one deployment after applying the table DDL, verify completion logs, then turn it off. Re-running is safe because projection fingerprints prevent false revision increments. This all-at-once scope query is acceptable for the current pre-release dataset; replace it with keyset batches before using it on an unbounded production dataset.

- [ ] **Step 3: backfill 테스트 실행**

Run:

```bash
cd backend && ./gradlew :application:test --tests '*FarmingCycleReportBackfillServiceTest' :api:test --tests '*FarmingCycleReportBackfillRunnerTest'
```

Expected: PASS.

- [ ] **Step 4: Task 9 커밋**

```bash
git add \
  backend/application/src/main/kotlin/com/chamchamcham/application/report/FarmingCycleReportBackfillService.kt \
  backend/application/src/test/kotlin/com/chamchamcham/application/report/FarmingCycleReportBackfillServiceTest.kt \
  backend/api/src/main/kotlin/com/chamchamcham/config/FarmingCycleReportBackfillRunner.kt \
  backend/api/src/main/resources/application.yml \
  backend/api/src/test/kotlin/com/chamchamcham/config/FarmingCycleReportBackfillRunnerTest.kt
git commit -m "feat(report): 기존 영농기록 리포트를 재구성" -m "Constraint: runner는 명시적 환경 변수로 한 번만 활성화" -m "Tested: backfill service and runner tests"
```

---

### Task 10: PostgreSQL schema, 문서 계약, 전체 검증

**Files:**
- Create: `backend/docs/db/farming-cycle-report-schema.sql`
- Modify: `frontend/docs/Business Rule.md`
- Modify: `frontend/docs/API 명세서/API 명세서/영농 리포트 조회 3909e2d9440581ef91dbf34bba44c407.md`
- Modify: `frontend/docs/API 명세서/DTO(데이터 전달 객체)/FarmingReportResponse 3909e2d9440581bbbf69c0e35cd17b8b.md`

**Interfaces:**
- Consumes: final entity and HTTP contracts
- Produces: deployable manual SQL and matching API documentation

- [ ] **Step 1: PostgreSQL DDL 작성**

```sql
create table if not exists farming_cycle_report (
    id uuid primary key,
    member_id uuid not null references member(id),
    farm_id uuid not null references farm(id),
    crop_id uuid not null references crop(id),
    status varchar(32) not null,
    starts_at timestamp not null,
    ends_at timestamp null,
    start_basis varchar(64) not null,
    planting_milestone_at timestamp null,
    final_harvest_record_id uuid null references farming_record(id),
    statistics_schema_version varchar(32) not null,
    statistics jsonb not null,
    statistics_fingerprint varchar(64) not null,
    source_revision bigint not null default 0,
    created_at timestamp not null,
    updated_at timestamp not null,
    completed_at timestamp null,
    constraint ck_cycle_report_status check (status in ('ACTIVE', 'COMPLETED', 'SUPERSEDED')),
    constraint ck_cycle_report_boundary check (
        status = 'SUPERSEDED'
        or (status = 'ACTIVE' and ends_at is null and final_harvest_record_id is null and completed_at is null)
        or (status = 'COMPLETED' and ends_at is not null and final_harvest_record_id is not null and completed_at is not null)
    )
);

create unique index if not exists uq_cycle_report_active_scope
    on farming_cycle_report(member_id, farm_id, crop_id)
    where status = 'ACTIVE';

create unique index if not exists uq_cycle_report_completed_final_harvest
    on farming_cycle_report(final_harvest_record_id)
    where status = 'COMPLETED';

create index if not exists idx_cycle_report_member_completed
    on farming_cycle_report(member_id, status, ends_at desc, id desc);

create index if not exists idx_cycle_report_member_active
    on farming_cycle_report(member_id, status, updated_at desc, id desc);

create index if not exists idx_cycle_report_scope_end
    on farming_cycle_report(member_id, farm_id, crop_id, status, ends_at desc);
```

Do not add upstream planting, category, medicinal-part, kg-normalization, or `is_final_harvest` columns in this SQL file. The Execution Gate requires those migrations to be merged before Task 1.

- [ ] **Step 2: 비즈니스·API 문서 갱신**

Document these rules verbatim:

- reports are ACTIVE/COMPLETED projections updated from source records
- SUPERSEDED is internal and never returned in normal list results
- current and previous snapshots are returned together in detail
- no date presets remain in the report contract
- no AI fields exist in phase 1 responses
- watering statistics are descriptive and do not claim adequacy
- apply `farming-cycle-report-schema.sql` before enabling the API deployment
- enable `REPORT_BACKFILL_ENABLED=true` for one deployment when existing records need projection, verify the scope count, then disable it

- [ ] **Step 3: focused module tests**

Run:

```bash
cd backend && ./gradlew :domain:test :application:test :api:test
```

Expected: BUILD SUCCESSFUL with all module tests passing.

- [ ] **Step 4: full backend regression suite**

Run:

```bash
cd backend && ./gradlew test
```

Expected: BUILD SUCCESSFUL. If a failure appears, fix it before committing; do not document a known failing suite as complete.

- [ ] **Step 5: schema and source sanity checks**

Run:

```bash
rg -n "materialName|pesticideName|HarvestAmountUnit" backend/application/src/main/kotlin/com/chamchamcham/application/report backend/api/src/main/kotlin/com/chamchamcham/api/report
rg -n "부족|과다|적정" backend/application/src/main/kotlin/com/chamchamcham/application/report backend/api/src/main/kotlin/com/chamchamcham/api/report
git diff --check
```

Expected:

- report packages do not group free-input material or pesticide names
- report response contains no watering adequacy judgement fields
- no whitespace errors

- [ ] **Step 6: Task 10 커밋**

```bash
git add \
  backend/docs/db/farming-cycle-report-schema.sql \
  frontend/docs/Business\ Rule.md \
  "frontend/docs/API 명세서/API 명세서/영농 리포트 조회 3909e2d9440581ef91dbf34bba44c407.md" \
  "frontend/docs/API 명세서/DTO(데이터 전달 객체)/FarmingReportResponse 3909e2d9440581bbbf69c0e35cd17b8b.md"
git commit -m "docs(report): 주기 리포트 배포와 API 계약을 정리" -m "Tested: ./gradlew test" -m "Not-tested: production PostgreSQL DDL application"
```

## Final Verification Checklist

- [ ] 첫 기록이 ACTIVE 리포트를 만든다.
- [ ] 일부 수확은 주기를 닫지 않는다.
- [ ] 마지막 수확은 현재 리포트를 COMPLETED로 만든다.
- [ ] 마지막 수확 다음 기록이 새 ACTIVE 리포트를 만든다.
- [ ] 심기 이전 작업이 같은 주기에 포함된다.
- [ ] 생성·수정·삭제에서 원본 전체를 다시 계산한다.
- [ ] 같은 payload 재계산은 revision을 올리지 않는다.
- [ ] 경계 변경으로 무효화된 리포트는 SUPERSEDED로 보존된다.
- [ ] 현재 상세에 직전 완료 주기 snapshot이 포함된다.
- [ ] 빈 값은 0으로 대체되지 않는다.
- [ ] 관수 부족·과다 판단 필드가 없다.
- [ ] 시비·약제는 안정적인 카테고리 코드로 집계된다.
- [ ] 수확량은 kg만 집계된다.
- [ ] SUPERSEDED 리포트는 제품 목록에서 제외된다.
- [ ] 다른 회원의 리포트에 접근할 수 없다.
- [ ] 기존 기록 backfill은 범위별로 재계산되며 재실행해도 revision이 불필요하게 증가하지 않는다.
- [ ] `./gradlew test`가 성공한다.
