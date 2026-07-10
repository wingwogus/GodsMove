# 실제 영농기록 기반 코칭 Context 2단계 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 영농기록 생성·수정 시 코칭 `PENDING`을 같은 트랜잭션에 만들고, 커밋 후 실제 회원·밭·작물·작업 상세·날씨에서 조립한 입력 스냅샷을 저장해 3단계 LLM 코칭이 더미 Context 없이 실행될 기반을 만든다.

**Architecture:** `FarmingRecord.sourceRevision`을 기록 피드백 원본 버전으로 사용한다. 기록 트랜잭션은 revision별 `CoachingFeedback(PENDING)`과 준비 이벤트만 저장하고, `AFTER_COMMIT` 리스너가 새 트랜잭션에서 `RecordFeedbackContextAssembler`를 호출해 JSONB 입력 스냅샷을 붙인다. 실제 LLM·RAG 생성과 `READY` 전이는 3단계로 남긴다.

**Tech Stack:** Kotlin 1.9, Spring Boot 3.5.4, Spring Data JPA, Hibernate JSONB, PostgreSQL, H2 PostgreSQL mode, Spring transactional events, KMA 단기예보 REST API, JUnit 5, Mockito, MockMvc, Gradle

## Global Constraints

- 이 계획은 2단계 실제 기록 Context 연동만 구현한다. 공식문서 검색, LLM 호출, 짧은 피드백 생성·검증, 완료 리포트 코칭은 포함하지 않는다.
- 프로덕션 기록 피드백 입력은 인증 `memberId`와 `recordId`다. 외부에서 전체 Context JSON을 받지 않는다.
- 같은 주기의 이전 기록, 주기별 작업 횟수, 최근 30일 통계, 직전 주기 통계, 파종 후 경과일을 Context에 넣지 않는다.
- 현재 `WorkType`과 영농기록 상세 enum을 직접 사용한다. `TodayRecordWorkType` 같은 중복 enum과 자유 문자열 작업 필드 Map을 만들지 않는다.
- 사진은 첨부 수만 전달하며 이미지 내용은 분석하지 않는다.
- 기록 당시 `weatherCondition`·`weatherTemperature`는 항상 RECORD 근거로 보존한다.
- 현재 날씨·예보 실패나 밭 좌표 부재는 기록 저장을 실패시키지 않는다. 날씨를 제외한 축소 Context와 경고를 저장한다.
- 예보 Context는 오늘 이후 최대 7개 날짜를 표현하지만 제공자가 반환한 날짜만 사용한다. 현재 KMA 단기예보의 최대 약 5일을 넘어가는 날짜를 추정·복제하지 않는다.
- `PENDING`은 영농기록 생성·수정 트랜잭션 안에서 저장한다. 외부 날씨 호출과 입력 스냅샷 조립은 커밋 후 `REQUIRES_NEW` 트랜잭션에서 수행한다.
- 2단계 정상 종료 상태는 `inputSnapshot != null`인 `PENDING`이다. `READY`·LLM 결과·인용 감사는 3단계 책임이다.
- 멱등성 키는 `feedbackType + targetId + sourceRevision`이다.
- 원본 revision이 바뀌면 이전 `PENDING`과 `READY`를 `STALE`로 만든다. 실패한 같은 revision 재시도는 기존 `FAILED` 행을 `PENDING`으로 되돌린다.
- `CoachingRagService`의 범용 `/query`는 CHAT 실험만 담당하고 제품 기록·리포트 코칭을 저장하지 않는다.
- 범용 backfill runner, 스케줄러, 메시지 브로커, 비동기 실행기, 중기예보 API, 새 라이브러리를 추가하지 않는다.
- 각 Task는 실패 테스트 작성 → 실패 확인 → 최소 구현 → 통과 확인 → 커밋 순서로 실행한다.
- 커밋은 Conventional Commits와 저장소 Lore trailer 규칙을 따른다.

## File Structure

### Domain

- `FarmingRecord.kt`: 기록 피드백 원본 revision 소유
- `CoachingFeedback.kt`: RECORD/CYCLE_REPORT 생성 상태와 JSONB 입력·출력 엔벨로프
- `CoachingFeedbackStatus.kt`: `PENDING | READY | FAILED | STALE`
- `CoachingFeedbackRepository.kt`: revision별 멱등 조회와 현재/STALE 조회

### Application

- `RecordFeedbackContext.kt`: 실제 기록 기반 불변 Context와 작업별 타입 상세
- `RecordFeedbackContextAssembler.kt`: 소유권 확인, 대상 상세 1건, 사진 수, 날씨를 조립
- `RecordFeedbackWeatherPort.kt`: 현재·예보를 제공하는 application port
- `RecordFeedbackLifecycleService.kt`: stale 전이, PENDING 멱등 생성, 재시도
- `RecordFeedbackPreparationService.kt`: Context를 JSONB 입력 스냅샷으로 저장
- `RecordFeedbackPreparationListener.kt`: 커밋 후 준비 서비스 호출
- `RecordFeedbackQueryService.kt`: 현재 revision 우선·STALE fallback 조회와 재생성

### API

- `KmaWeatherProvider.kt`: 기존 현재 날씨와 KMA 단기예보 일별 집계 제공
- `FarmingRecordFeedbackController.kt`: 기록 피드백 GET·regenerate API
- `FarmingRecordFeedbackResponses.kt`: 사용자용 상태 응답

---

### Task 1: 기록 revision과 코칭 상태 도메인

**Files:**
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/farming/FarmingRecord.kt`
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/coaching/FeedbackType.kt`
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/coaching/CoachingFeedbackStatus.kt`
- Rewrite: `backend/domain/src/main/kotlin/com/chamchamcham/domain/coaching/CoachingFeedback.kt`
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/coaching/CoachingFeedbackRepository.kt`
- Create: `backend/domain/src/test/kotlin/com/chamchamcham/domain/coaching/CoachingFeedbackTest.kt`
- Modify: `backend/domain/src/test/kotlin/com/chamchamcham/domain/farming/FarmingRecordTest.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/chat/CoachingRagService.kt`
- Delete: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/chat/CoachingFeedbackMapper.kt`
- Delete: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/chat/CoachingFeedbackPersistencePolicy.kt`
- Modify/Delete matching tests under `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/chat/`

**Interfaces:**
- Produces: `FarmingRecord.sourceRevision: Long`
- Produces: `FeedbackType.RECORD`, `FeedbackType.CYCLE_REPORT`
- Produces: `CoachingFeedback.pendingRecord(member, record, sourceRevision)`
- Produces: `markStale()`, `attachInputSnapshot(snapshot)`, `markFailed(code)`, `retry()`

- [ ] **Step 1: Write failing revision and lifecycle tests**

```kotlin
@Test
fun `record update and delete advance feedback source revision`() {
    val record = farmingRecord(sourceRevision = 1)

    record.update(record.farm, record.crop, WorkType.WATERING, record.workedAt, "맑음", 24, "수정된 기록 메모 30자 이상")
    assertThat(record.sourceRevision).isEqualTo(2)

    record.softDelete()
    assertThat(record.sourceRevision).isEqualTo(3)
}

@Test
fun `pending record feedback validates target and transitions`() {
    val feedback = CoachingFeedback.pendingRecord(member, record, sourceRevision = 1)
    assertThat(feedback.feedbackType).isEqualTo(FeedbackType.RECORD)
    assertThat(feedback.status).isEqualTo(CoachingFeedbackStatus.PENDING)
    assertThat(feedback.inputSnapshot).isNull()

    feedback.attachInputSnapshot(mapOf("schemaVersion" to "record-feedback-context.v2"))
    assertThat(feedback.inputSnapshot).isNotNull()

    feedback.markStale()
    assertThat(feedback.status).isEqualTo(CoachingFeedbackStatus.STALE)
}

@Test
fun `failed feedback can retry same revision`() {
    val feedback = CoachingFeedback.pendingRecord(member, record, 1)
    feedback.markFailed("CONTEXT_ASSEMBLY_FAILED")
    feedback.retry()

    assertThat(feedback.status).isEqualTo(CoachingFeedbackStatus.PENDING)
    assertThat(feedback.failureCode).isNull()
    assertThat(feedback.inputSnapshot).isNull()
}
```

- [ ] **Step 2: Run tests and confirm failure**

Run:

```bash
cd backend
./gradlew :domain:test --tests '*CoachingFeedbackTest' --tests '*FarmingRecordTest'
```

Expected: FAIL because `sourceRevision`, `CoachingFeedbackStatus`, and lifecycle methods do not exist.

- [ ] **Step 3: Add the exact domain model**

Add to `FarmingRecord`:

```kotlin
@Column(name = "source_revision", nullable = false)
var sourceRevision: Long = 1
    private set
```

Increment it once at the end of `update()` and `softDelete()`. Replace `FeedbackType.STATISTICS` with `CYCLE_REPORT` and create:

```kotlin
enum class CoachingFeedbackStatus { PENDING, READY, FAILED, STALE }
```

The rewritten entity must have these persisted fields and nullable rules:

```kotlin
member: Member                         // non-null
feedbackType: FeedbackType             // non-null
status: CoachingFeedbackStatus         // non-null
record: FarmingRecord?                 // RECORD target
cycleReport: FarmingCycleReport?       // CYCLE_REPORT target
sourceRevision: Long                   // non-null
inputSnapshot: Map<String, Any?>?       // nullable until prepared
structuredResult: Map<String, Any?>?    // nullable until phase 3 READY
citations: List<Map<String, Any?>>      // default []
auditStatus: String?                    // phase 3
auditWarnings: List<String>             // default []
failureCode: String?
modelName: String?
embeddingModel: String?
```

Use `@JdbcTypeCode(SqlTypes.JSON)` and `columnDefinition = "jsonb"` for JSON fields. The constructor/factory must enforce exactly one target. `pendingRecord` sets only `record`, `PENDING`, empty lists, and null generation fields. `attachInputSnapshot` accepts only `PENDING`; `markFailed` accepts only `PENDING`; `retry` accepts only `FAILED`; `markStale` is idempotent and may invalidate `PENDING` or `READY`.

- [ ] **Step 4: Isolate generic CHAT from product persistence**

Change `CoachingRagService.validateModeRequirements` to reject every mode except `CoachingMode.CHAT`, remove `CoachingFeedbackRepository` and persistence dependencies, remove `saveFeedback`, and always return `savedFeedbackId = null`. Delete the now-unused mapper and persistence policy with their tests. Keep the generic query endpoint behavior for CHAT unchanged.

- [ ] **Step 5: Add repository signatures**

```kotlin
fun findByFeedbackTypeAndRecord_IdAndSourceRevision(
    feedbackType: FeedbackType,
    recordId: UUID,
    sourceRevision: Long,
): CoachingFeedback?

fun findAllByFeedbackTypeAndRecord_IdAndStatusIn(
    feedbackType: FeedbackType,
    recordId: UUID,
    statuses: Collection<CoachingFeedbackStatus>,
): List<CoachingFeedback>

fun findTopByFeedbackTypeAndRecord_IdAndStatusOrderByUpdatedAtDesc(
    feedbackType: FeedbackType,
    recordId: UUID,
    status: CoachingFeedbackStatus,
): CoachingFeedback?

fun findByIdAndMember_Id(id: UUID, memberId: UUID): CoachingFeedback?
```

Remove old history methods that are no longer consumed.

- [ ] **Step 6: Run focused and chat regression tests**

```bash
cd backend
./gradlew :domain:test --tests '*CoachingFeedbackTest' --tests '*FarmingRecordTest' \
  :application:test --tests '*CoachingRagServiceTest'
```

Expected: PASS; a non-CHAT generic query is rejected with `RAG_INVALID_REQUEST`, and CHAT is not persisted.

- [ ] **Step 7: Commit Task 1**

```bash
git add backend/domain backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/chat backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/chat
git commit -m "feat(coaching): 기록 피드백 생성 상태를 분리" \
  -m "Constraint: 2단계에서는 LLM 결과 없이 revision별 PENDING 입력만 준비" \
  -m "Rejected: 범용 CHAT 결과를 제품 코칭 테이블에 계속 저장 | 타입별 상태와 타깃 제약을 깨뜨림" \
  -m "Confidence: high" -m "Scope-risk: moderate" \
  -m "Tested: domain coaching/farming tests and CoachingRagServiceTest"
```

---

### Task 2: 실제 엔티티 기반 타입 Context와 조립기

**Files:**
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/record/RecordFeedbackContext.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/record/RecordFeedbackContextAssembler.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/record/RecordFeedbackWeatherPort.kt`
- Delete: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/record/TodayRecordFeedbackContext.kt`
- Rename/Modify: validator, query planner, prompt builder, and `TodayRecordFeedbackService` inputs in the same package
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/farming/FarmingRecordRepository.kt`
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/farming/FarmingRecordMediaRepository.kt`
- Modify: five fixture JSON files under `backend/application/src/test/resources/coaching/rag/`
- Create: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/record/RecordFeedbackContextAssemblerTest.kt`
- Modify: existing record RAG tests to consume Context v2

**Interfaces:**
- Consumes: `FarmingRecord.sourceRevision`
- Produces: `RecordFeedbackContextAssembler.assemble(memberId: UUID, recordId: UUID): RecordFeedbackContext`
- Produces: `RecordFeedbackWeatherPort.fetch(latitude: Double, longitude: Double, limitDays: Int = 7): RecordFeedbackLiveWeather`

- [ ] **Step 1: Write failing tests for all eight work types**

Use a parameterized test or eight explicit cases. Every case must assert the concrete detail subtype and exact enum/value mapping:

```kotlin
@Test
fun `fertilizing maps actual category amount unit and method without statistics`() {
    stubOwnedRecord(WorkType.FERTILIZING)
    whenever(fertilizingRecordRepository.findByRecord_Id(recordId)).thenReturn(
        FertilizingRecord(
            record = record,
            materialCategory = FertilizerMaterialCategory.ORGANIC_FERTILIZER,
            amount = BigDecimal("2.5000"),
            amountUnit = FertilizerAmountUnit.KG,
            applicationMethod = FertilizingMethod.SOIL,
        )
    )
    whenever(mediaRepository.countByRecord_Id(recordId)).thenReturn(2)
    whenever(weatherPort.fetch(37.1, 128.2, 7)).thenReturn(liveWeather())

    val context = assembler.assemble(memberId, recordId)

    assertThat(context.record.workType).isEqualTo(WorkType.FERTILIZING)
    assertThat(context.record.detail).isEqualTo(
        FertilizingFeedbackDetail(
            FertilizerMaterialCategory.ORGANIC_FERTILIZER,
            BigDecimal("2.5000"),
            FertilizerAmountUnit.KG,
            FertilizingMethod.SOIL,
        )
    )
    assertThat(context.record.photoCount).isEqualTo(2)
    assertThat(RecordFeedbackContext::class.java.declaredFields.map { it.name })
        .doesNotContain("recentRecords", "workTypeStats", "cropCycle")
}
```

Required mapping cases:

| WorkType | detail type | repository behavior |
| --- | --- | --- |
| `PLANTING` | `PlantingFeedbackDetail` | exact seed/seedling/propagation fields; missing row is data error |
| `WATERING` | `WateringFeedbackDetail` | nullable amount/method; missing row becomes empty detail |
| `FERTILIZING` | `FertilizingFeedbackDetail` | exact category/kg-unit/method; missing row is data error |
| `PEST_CONTROL` | `PestControlFeedbackDetail` | category, ML/G, spray L, target; missing row is data error |
| `WEEDING` | `WeedingFeedbackDetail` | nullable method; missing row becomes empty detail |
| `PRUNING` | `CommonFeedbackDetail` | no detail repository call |
| `HARVEST` | `HarvestFeedbackDetail` | kg/null, part, source, growth unit, final flag |
| `ETC` | `CommonFeedbackDetail` | memo only; no inferred category |

Also test another member's record and soft-deleted record return `FARMING_RECORD_NOT_FOUND`, photo content is never loaded, no-coordinate weather produces `weather = null` plus `weather_location_unavailable`, and provider failure produces `weather = null` plus `weather_provider_unavailable`.

- [ ] **Step 2: Run the assembler test and confirm failure**

```bash
cd backend
./gradlew :application:test --tests '*RecordFeedbackContextAssemblerTest'
```

Expected: FAIL because Context v2, the assembler, and weather port do not exist.

- [ ] **Step 3: Define Context v2 with typed details**

Use schema version `record-feedback-context.v2` and this top-level shape:

```kotlin
data class RecordFeedbackContext(
    val schemaVersion: String = RECORD_FEEDBACK_CONTEXT_SCHEMA_VERSION,
    val member: RecordFeedbackMemberContext,
    val farm: RecordFeedbackFarmContext,
    val crop: RecordFeedbackCropContext,
    val record: RecordFeedbackRecordContext,
    val weather: RecordFeedbackLiveWeather?,
    val warnings: List<String> = emptyList(),
)

data class RecordFeedbackMemberContext(
    val memberId: UUID,
    val experienceLevel: Int?,
    val managementType: ManagementType?,
)

data class RecordFeedbackFarmContext(
    val farmId: UUID,
    val name: String,
    val roadAddress: String,
    val latitude: Double?,
    val longitude: Double?,
)

data class RecordFeedbackCropContext(
    val cropId: UUID,
    val name: String,
    val usePartCategory: CropUsePartCategory,
)

data class RecordFeedbackRecordContext(
    val recordId: UUID,
    val sourceRevision: Long,
    val workedAt: LocalDateTime,
    val workType: WorkType,
    val detail: RecordFeedbackWorkDetail,
    val recordedWeatherCondition: String,
    val recordedTemperatureC: Int,
    val memo: String,
    val photoCount: Int,
)

sealed interface RecordFeedbackWorkDetail
data class PlantingFeedbackDetail(
    val seedAmount: BigDecimal?,
    val seedAmountUnit: SeedAmountUnit?,
    val seedlingCount: Int?,
    val seedlingUnit: SeedlingUnit?,
    val propagationMethod: PropagationMethod,
): RecordFeedbackWorkDetail
data class WateringFeedbackDetail(
    val irrigationAmount: IrrigationAmount?,
    val irrigationMethod: IrrigationMethod?,
): RecordFeedbackWorkDetail
data class FertilizingFeedbackDetail(
    val materialCategory: FertilizerMaterialCategory,
    val amount: BigDecimal,
    val amountUnit: FertilizerAmountUnit,
    val applicationMethod: FertilizingMethod?,
): RecordFeedbackWorkDetail
data class PestControlFeedbackDetail(
    val pesticideCategory: PesticideCategory,
    val pesticideAmount: BigDecimal,
    val pesticideAmountUnit: PesticideAmountUnit,
    val totalSprayAmount: BigDecimal,
    val totalSprayAmountUnit: SprayAmountUnit,
    val pestTarget: String?,
): RecordFeedbackWorkDetail
data class WeedingFeedbackDetail(
    val weedingMethod: WeedingMethod?,
): RecordFeedbackWorkDetail
data class HarvestFeedbackDetail(
    val harvestAmountKg: BigDecimal?,
    val amountUnknown: Boolean,
    val medicinalPart: CropUsePartCategory,
    val harvestSource: HarvestSource,
    val growthPeriod: Int,
    val growthPeriodUnit: GrowthPeriodUnit,
    val isFinalHarvest: Boolean,
): RecordFeedbackWorkDetail
data object CommonFeedbackDetail: RecordFeedbackWorkDetail
```

Every property in each subtype must use the domain enum and numeric type from its entity. Do not add `Map<String, Any?>`, `TodayRecordWorkType`, cycle fields, history, or statistics.

Define the weather port:

```kotlin
interface RecordFeedbackWeatherPort {
    fun fetch(latitude: Double, longitude: Double, limitDays: Int = 7): RecordFeedbackLiveWeather
}

data class RecordFeedbackLiveWeather(
    val current: RecordFeedbackCurrentWeather,
    val forecastDays: List<RecordFeedbackForecastDay>,
    val source: String,
)

data class RecordFeedbackCurrentWeather(
    val temperatureC: Int,
    val skyCondition: String,
    val observedAt: LocalDateTime,
)

data class RecordFeedbackForecastDay(
    val date: LocalDate,
    val rainfallMm: BigDecimal?,
    val rainProbabilityPct: Int?,
    val maxTemperatureC: BigDecimal?,
    val minTemperatureC: BigDecimal?,
    val humidityPct: BigDecimal?,
    val windSpeedMs: BigDecimal?,
    val riskFlags: List<String>,
)
```

- [ ] **Step 4: Add bounded repository reads and assembler**

Add a `join fetch` repository query constrained by `id`, `member.id`, and `isDeleted=false`. Add only:

```kotlin
fun countByRecord_Id(recordId: UUID): Long
```

to media repository. `assemble` loads one base record, switches on its `WorkType`, calls only that work type's detail repository, calls `countByRecord_Id`, and fetches weather only when both coordinates exist. Catch `BusinessException(WEATHER_*)` and provider runtime failures as Context warnings; do not catch ownership or missing-detail errors.

- [ ] **Step 5: Migrate old RAG input consumers to Context v2**

Rename `TodayRecordFeedbackContextValidator` to `RecordFeedbackContextValidator`. Update the query planner and prompt builder to use `context.record.workType`, typed `detail`, recorded weather, optional live weather, and `record:${recordId}` citation ID. Remove queries and prompt sections for days-after-planting, recent records, recent-7-day summaries, and work-type statistics. Keep the existing LLM output contract unchanged because output redesign belongs to phase 3.

Replace all five fixture files with v2 payloads that contain only member/farm/crop/record/weather/warnings. Ensure `materialName` and `pesticideName` do not appear; use category enums.

- [ ] **Step 6: Run all record input tests**

```bash
cd backend
./gradlew :application:test --tests 'com.chamchamcham.application.coaching.rag.record.*'
```

Expected: PASS; tests prove all eight detail mappings and absence of cycle/history/statistics.

- [ ] **Step 7: Commit Task 2**

```bash
git add backend/domain/src/main/kotlin/com/chamchamcham/domain/farming backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/record backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/record backend/application/src/test/resources/coaching/rag
git commit -m "feat(coaching): 실제 영농기록 컨텍스트를 조립" \
  -m "Constraint: 기록 피드백은 대상 기록 한 건과 현재 날씨만 사용" \
  -m "Rejected: 주기 통계와 최근 기록 재사용 | 기록 피드백 역할을 다시 확장함" \
  -m "Confidence: high" -m "Scope-risk: moderate" \
  -m "Tested: all record-context application tests"
```

---

### Task 3: KMA 현재 날씨·단기 일별 예보 어댑터

**Files:**
- Modify: `backend/api/src/main/kotlin/com/chamchamcham/api/weather/KmaWeatherProvider.kt`
- Modify: `backend/api/src/main/kotlin/com/chamchamcham/api/weather/KmaBaseTimeResolver.kt`
- Modify: `backend/api/src/test/kotlin/com/chamchamcham/api/weather/KmaWeatherProviderTest.kt`
- Modify: `backend/api/src/test/kotlin/com/chamchamcham/api/weather/KmaBaseTimeResolverTest.kt`

**Interfaces:**
- Consumes: `RecordFeedbackWeatherPort`
- Preserves: `WeatherProvider.fetchCurrentWeather(latitude, longitude)`
- Produces: `RecordFeedbackWeatherPort.fetch(latitude: Double, longitude: Double, limitDays: Int = 7): RecordFeedbackLiveWeather`

- [ ] **Step 1: Write failing base-time, aggregation, and cap tests**

Tests must cover:

```kotlin
@Test
fun `village forecast chooses latest published base time with ten minute delay`() {
    assertThat(resolveVilageFcst(LocalDateTime.of(2026, 7, 11, 5, 9)))
        .isEqualTo(KmaBaseDateTime("20260711", "0200"))
    assertThat(resolveVilageFcst(LocalDateTime.of(2026, 7, 11, 5, 10)))
        .isEqualTo(KmaBaseDateTime("20260711", "0500"))
}

@Test
fun `daily forecast aggregates categories and never fabricates missing dates`() {
    // Stub getUltraSrtNcst/getUltraSrtFcst/getVilageFcst responses for three dates.
    val result = provider.fetch(37.1, 128.2, limitDays = 7)
    assertThat(result.forecastDays).hasSize(3)
    assertThat(result.forecastDays.map { it.date }).containsExactly(day1, day2, day3)
    assertThat(result.forecastDays.first().rainProbabilityPct).isEqualTo(80)
    assertThat(result.forecastDays.first().riskFlags)
        .contains("RAIN", "HIGH_HUMIDITY")
}
```

Also assert `limitDays` outside `1..7` throws `IllegalArgumentException`, KMA error maps to `WEATHER_PROVIDER_UNAVAILABLE`, current weather API behavior remains unchanged, and qualitative PCP values do not become invented numeric rainfall.

- [ ] **Step 2: Run tests and confirm failure**

```bash
cd backend
./gradlew :api:test --tests '*KmaBaseTimeResolverTest' --tests '*KmaWeatherProviderTest'
```

Expected: FAIL because village forecast support is absent.

- [ ] **Step 3: Implement village forecast base time and daily aggregation**

Use KMA village issue hours `02, 05, 08, 11, 14, 17, 20, 23` and a 10-minute availability delay. Request `getVilageFcst` with `numOfRows=2000`, group items by `fcstDate`, filter from current local date, sort ascending, and `take(limitDays)`.

Aggregate exact categories without converting qualitative precipitation into a guessed amount:

```text
TMP/TMN/TMX -> min/max temperature
POP         -> maximum rain probability
REH         -> maximum humidity
WSD         -> maximum wind speed
PTY         -> any non-zero value means precipitation
PCP         -> numeric only when the API returns a parseable exact mm value
SKY         -> representative worst sky code
```

Build flags only from observed values:

```kotlin
RAIN          when PTY != 0 or POP >= 60
HEAVY_RAIN    when exact rainfallMm >= 20
HIGH_HUMIDITY when humidityPct >= 85
HOT           when maxTemperatureC >= 30
STRONG_WIND   when windSpeedMs >= 8
```

Do not add `DRY` from absence of rain alone. Return source `KMA_SHORT_TERM`. Preserve the existing current weather method and reuse request/parsing helpers rather than duplicate HTTP code.

- [ ] **Step 4: Run focused tests**

```bash
cd backend
./gradlew :api:test --tests '*KmaBaseTimeResolverTest' --tests '*KmaWeatherProviderTest' \
  :application:test --tests '*FarmWeatherServiceTest'
```

Expected: PASS.

- [ ] **Step 5: Commit Task 3**

```bash
git add backend/api/src/main/kotlin/com/chamchamcham/api/weather backend/api/src/test/kotlin/com/chamchamcham/api/weather
git commit -m "feat(weather): 기록 코칭용 단기 예보를 제공" \
  -m "Constraint: KMA가 반환한 최대 5일 범위만 사용하고 6~7일차를 만들지 않음" \
  -m "Rejected: 중기예보 결합 | 별도 API 신청과 광역구역 매핑이 필요함" \
  -m "Confidence: medium" -m "Scope-risk: moderate" \
  -m "Tested: KMA provider/base-time and FarmWeatherService tests"
```

---

### Task 4: revision별 PENDING 생성과 커밋 후 입력 준비

**Files:**
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/feedback/RecordFeedbackPreparationRequested.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/feedback/RecordFeedbackLifecycleService.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/feedback/RecordFeedbackPreparationService.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/feedback/RecordFeedbackPreparationListener.kt`
- Create tests for all four under `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/feedback/`

**Interfaces:**
- Consumes: `RecordFeedbackContextAssembler.assemble(memberId, recordId)`
- Produces: `enqueue(record): CoachingFeedback`
- Produces: `staleFor(recordId)`
- Produces: `retry(feedback): CoachingFeedback`
- Produces event only after a feedback row is persisted

- [ ] **Step 1: Write failing lifecycle tests**

```kotlin
@Test
fun `enqueue stales old active attempts and is idempotent for current revision`() {
    whenever(repository.findAllByFeedbackTypeAndRecord_IdAndStatusIn(
        FeedbackType.RECORD, recordId,
        listOf(CoachingFeedbackStatus.PENDING, CoachingFeedbackStatus.READY),
    )).thenReturn(listOf(oldPending, oldReady))
    whenever(repository.findByFeedbackTypeAndRecord_IdAndSourceRevision(
        FeedbackType.RECORD, recordId, 3,
    )).thenReturn(null)

    val created = service.enqueue(recordWithRevision(3))

    assertThat(oldPending.status).isEqualTo(CoachingFeedbackStatus.STALE)
    assertThat(oldReady.status).isEqualTo(CoachingFeedbackStatus.STALE)
    verify(repository).save(created)
    verify(publisher).publishEvent(check<RecordFeedbackPreparationRequested> {
        assertThat(it.sourceRevision).isEqualTo(3)
    })
}

@Test
fun `preparation stores typed context map and leaves status pending`() {
    whenever(repository.findByIdAndMember_Id(feedbackId, memberId)).thenReturn(pending)
    whenever(assembler.assemble(memberId, recordId)).thenReturn(context)

    service.prepare(feedbackId, memberId, recordId, sourceRevision = 3)

    assertThat(pending.status).isEqualTo(CoachingFeedbackStatus.PENDING)
    assertThat(pending.inputSnapshot?.get("schemaVersion"))
        .isEqualTo("record-feedback-context.v2")
}
```

Test stale events are ignored, assembler data errors mark only the feedback `FAILED` with `CONTEXT_ASSEMBLY_FAILED`, and listener does not rethrow after commit.

- [ ] **Step 2: Run tests and confirm failure**

```bash
cd backend
./gradlew :application:test --tests 'com.chamchamcham.application.coaching.feedback.*'
```

Expected: FAIL because lifecycle classes do not exist.

- [ ] **Step 3: Implement lifecycle and event rules**

`enqueue(record)` must require persisted member/record IDs and first look up the exact current revision. If it already exists, return it without changing status or publishing a duplicate event. Otherwise stale only older `PENDING`/`READY` rows, save `pendingRecord`, and publish:

```kotlin
data class RecordFeedbackPreparationRequested(
    val feedbackId: UUID,
    val memberId: UUID,
    val recordId: UUID,
    val sourceRevision: Long,
)
```

`retry(feedback)` transitions `FAILED -> PENDING`, saves it, and publishes the same event. `staleFor(recordId)` invalidates active attempts but creates no replacement.

- [ ] **Step 4: Implement after-commit preparation**

The listener must be:

```kotlin
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
fun on(event: RecordFeedbackPreparationRequested) {
    runCatching { preparationService.prepare(event) }
        .onFailure { logger.error(it) { "record feedback context preparation failed" } }
}
```

`RecordFeedbackPreparationService.prepare` uses `@Transactional(propagation = Propagation.REQUIRES_NEW)`, re-reads feedback, verifies `PENDING`, target ID, and revision, assembles the Context, converts it with `objectMapper.convertValue(context, object : TypeReference<Map<String, Any?>>() {})`, and calls `attachInputSnapshot`. If the assembler fails for non-weather data, catch it inside this new transaction and mark the row `FAILED`; weather failures must already be represented as reduced Context warnings by the assembler.

- [ ] **Step 5: Run lifecycle tests**

```bash
cd backend
./gradlew :application:test --tests 'com.chamchamcham.application.coaching.feedback.*'
```

Expected: PASS.

- [ ] **Step 6: Commit Task 4**

```bash
git add backend/application/src/main/kotlin/com/chamchamcham/application/coaching/feedback backend/application/src/test/kotlin/com/chamchamcham/application/coaching/feedback
git commit -m "feat(coaching): 기록 피드백 입력 준비 흐름을 추가" \
  -m "Constraint: PENDING은 원본 트랜잭션, 날씨 Context는 커밋 후 새 트랜잭션" \
  -m "Rejected: 기록 저장 중 외부 날씨 호출 | 외부 실패가 영농기록을 롤백함" \
  -m "Confidence: high" -m "Scope-risk: moderate" \
  -m "Tested: record feedback lifecycle and preparation tests"
```

---

### Task 5: 영농기록 CRUD와 코칭 lifecycle 연결

**Files:**
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/farming/FarmingRecordService.kt`
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/farming/FarmingRecordServiceTest.kt`
- Modify: `backend/api/src/test/kotlin/com/chamchamcham/api/report/FarmingRecordProjectionRollbackIntegrationTest.kt`
- Create: `backend/api/src/test/kotlin/com/chamchamcham/api/coaching/RecordFeedbackLifecycleIntegrationTest.kt`

**Interfaces:**
- Consumes: `RecordFeedbackLifecycleService.enqueue(record)` and `staleFor(recordId)`
- Preserves: report projection rebuild behavior and rollback guarantees

- [ ] **Step 1: Add failing service tests**

Assert exact order:

```kotlin
@Test
fun `create queues feedback after details media and report rebuild`() {
    service.create(command)

    inOrder(detailRepository, mediaRepository, projectionService, lifecycleService).apply {
        verify(detailRepository).save(any())
        verify(mediaRepository).saveAll(any<List<FarmingRecordMedia>>())
        verify(projectionService).rebuild(ReportScope(memberId, farmId, cropId))
        verify(lifecycleService).enqueue(record)
    }
}

@Test
fun `update increments revision and queues replacement feedback`() {
    service.update(command)
    assertThat(record.sourceRevision).isEqualTo(2)
    verify(lifecycleService).enqueue(record)
}

@Test
fun `delete stales feedback without creating another`() {
    service.delete(deleteCommand)
    verify(lifecycleService).staleFor(recordId)
    verify(lifecycleService, never()).enqueue(any())
}
```

- [ ] **Step 2: Run focused tests and confirm failure**

```bash
cd backend
./gradlew :application:test --tests '*FarmingRecordServiceTest'
```

Expected: FAIL because lifecycle service is not injected/called.

- [ ] **Step 3: Wire lifecycle calls in the existing transaction**

Inject `RecordFeedbackLifecycleService`. Call `enqueue(record)` only after detail/media changes and report projection rebuild succeed. On update, `record.update` has already advanced revision. On delete, call `staleFor(recordId)` after projection rebuild. Do not catch lifecycle persistence errors; failure to create the same-transaction PENDING must roll back the record mutation.

- [ ] **Step 4: Add real transaction integration tests**

`RecordFeedbackLifecycleIntegrationTest` must commit a real record create transaction with a mocked `RecordFeedbackWeatherPort`, then assert:

```text
farming_record exists
coaching_feedback feedback_type=RECORD
status=PENDING
source_revision=1
input_snapshot schemaVersion=record-feedback-context.v2
snapshot has no recentRecords/workTypeStats/cropCycle keys
```

Commit an update and assert revision 1 is STALE, revision 2 is PENDING with a refreshed snapshot. In the existing rollback test, force report rebuild failure and assert both farming record and coaching feedback are absent.

- [ ] **Step 5: Run focused integration tests**

```bash
cd backend
./gradlew :application:test --tests '*FarmingRecordServiceTest' \
  :api:test --tests '*RecordFeedbackLifecycleIntegrationTest' \
  --tests '*FarmingRecordProjectionRollbackIntegrationTest'
```

Expected: PASS.

- [ ] **Step 6: Commit Task 5**

```bash
git add backend/application/src/main/kotlin/com/chamchamcham/application/farming/FarmingRecordService.kt backend/application/src/test/kotlin/com/chamchamcham/application/farming/FarmingRecordServiceTest.kt backend/api/src/test/kotlin/com/chamchamcham/api
git commit -m "feat(coaching): 영농기록 변경에 피드백 준비를 연결" \
  -m "Constraint: 기록·리포트·PENDING 저장은 하나의 DB 트랜잭션" \
  -m "Directive: 외부 날씨와 LLM 호출을 FarmingRecordService 트랜잭션에 넣지 말 것" \
  -m "Confidence: high" -m "Scope-risk: broad" \
  -m "Tested: farming service and feedback lifecycle integration/rollback tests"
```

---

### Task 6: 현재 피드백 상태 조회와 재생성 API

**Files:**
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/feedback/RecordFeedbackResult.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/feedback/RecordFeedbackQueryService.kt`
- Create: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/feedback/RecordFeedbackQueryServiceTest.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/exception/ErrorCode.kt`
- Modify: `backend/api/src/main/resources/messages.properties`
- Create: `backend/api/src/main/kotlin/com/chamchamcham/api/coaching/controller/FarmingRecordFeedbackController.kt`
- Create: `backend/api/src/main/kotlin/com/chamchamcham/api/coaching/dto/FarmingRecordFeedbackResponses.kt`
- Create: `backend/api/src/test/kotlin/com/chamchamcham/api/coaching/controller/FarmingRecordFeedbackControllerTest.kt`

**Interfaces:**
- Produces:
  - `GET /api/v1/farming-records/{recordId}/coaching-feedback`
  - `POST /api/v1/farming-records/{recordId}/coaching-feedback/regenerate`

- [ ] **Step 1: Write failing query selection and regeneration tests**

Required cases:

```text
owned current revision PENDING -> return it
owned current revision FAILED -> return it
no current row + latest STALE -> return latest STALE
other member or deleted record -> FARMING_RECORD_NOT_FOUND
no feedback at all -> RECORD_FEEDBACK_NOT_FOUND (404)
FAILED current row regenerate -> same row retry and event publish
STALE fallback regenerate -> new current-revision PENDING
PENDING or READY regenerate -> RECORD_FEEDBACK_REGENERATION_NOT_ALLOWED (409)
```

The application result contains only:

```kotlin
data class RecordFeedbackResult(
    val feedbackId: UUID,
    val recordId: UUID,
    val status: CoachingFeedbackStatus,
    val sourceRevision: Long,
    val inputPrepared: Boolean,
    val failureCode: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)
```

Do not expose input snapshot, structured result Map, citations, model, or audit fields in phase 2.

- [ ] **Step 2: Run service tests and confirm failure**

```bash
cd backend
./gradlew :application:test --tests '*RecordFeedbackQueryServiceTest'
```

Expected: FAIL because query service and feedback errors do not exist.

- [ ] **Step 3: Implement query and regeneration rules**

Add stable errors:

```kotlin
RECORD_FEEDBACK_NOT_FOUND("COACHING_001", "error.record_feedback_not_found", 404)
RECORD_FEEDBACK_REGENERATION_NOT_ALLOWED("COACHING_002", "error.record_feedback_regeneration_not_allowed", 409)
```

Always load the record using member ownership and `isDeleted=false` first. Query exact current revision before STALE fallback. Delegate retries/new enqueue to `RecordFeedbackLifecycleService`; do not mutate status in the controller.

- [ ] **Step 4: Add thin controller and HTTP tests**

Use `@AuthenticationPrincipal memberId: String?` and the same UUID parsing convention as `FarmingRecordController`. Both endpoints return `ApiResponse.ok(FarmingRecordFeedbackResponses.StatusResponse.from(result))` with HTTP 200. Controller tests cover authenticated success, missing principal 401, invalid UUID 401, not found 404, regeneration conflict 409, and exact response fields.

- [ ] **Step 5: Run focused API tests**

```bash
cd backend
./gradlew :application:test --tests '*RecordFeedbackQueryServiceTest' \
  :api:test --tests '*FarmingRecordFeedbackControllerTest'
```

Expected: PASS.

- [ ] **Step 6: Commit Task 6**

```bash
git add backend/application/src/main/kotlin/com/chamchamcham/application/coaching/feedback backend/application/src/test/kotlin/com/chamchamcham/application/coaching/feedback backend/application/src/main/kotlin/com/chamchamcham/application/exception/ErrorCode.kt backend/api/src/main/kotlin/com/chamchamcham/api/coaching backend/api/src/test/kotlin/com/chamchamcham/api/coaching backend/api/src/main/resources/messages.properties
git commit -m "feat(coaching): 기록 피드백 상태 조회 API를 제공" \
  -m "Constraint: 2단계 응답은 생성 상태만 노출하고 LLM 결과 Map을 노출하지 않음" \
  -m "Confidence: high" -m "Scope-risk: narrow" \
  -m "Tested: record feedback query service and controller tests"
```

---

### Task 7: PostgreSQL 계약, 문서, 전체 회귀 검증

**Files:**
- Create: `backend/docs/db/record-feedback-lifecycle-schema.sql`
- Modify: `frontend/docs/API 명세서/API 명세서/영농일지 생성 3909e2d944058133808ae56337e0f386.md`
- Modify: `frontend/docs/API 명세서/API 명세서/영농일지 수정 3909e2d94405819488a5d0a77f68c7a2.md`
- Modify: `frontend/docs/API 명세서/API 명세서/영농일지 코칭 피드백 조회 3909e2d9440581c1917ce4dcf0be3aa3.md`
- Modify: `frontend/docs/API 명세서/API 명세서/영농일지 코칭 피드백 재생성 3909e2d94405819e8218e40531d38349.md`
- Modify: `frontend/docs/API 명세서/DTO(데이터 전달 객체)/CoachingFeedbackResponse 3909e2d9440581699f25f89de41df770.md`
- Modify: `docs/superpowers/specs/2026-07-10-cycle-report-record-coaching-rag-redesign.md` only if implementation exposed a contradiction
- Test: full backend suite

**Interfaces:**
- Produces: reviewed manual DDL for dev/prod (`ddl-auto: none`)
- Produces: phase 2 client contract and phase 3 handoff notes

- [ ] **Step 1: Write executable manual DDL**

The SQL must:

1. add `farming_record.source_revision bigint not null default 1`;
2. reshape `coaching_feedback` with `feedback_type`, `status`, `cycle_report_id`, `source_revision`, nullable `input_snapshot jsonb`, nullable `structured_result jsonb`, citations/audit defaults, nullable failure/model fields;
3. map existing `RECORD_AUTO` rows to `RECORD` only when `record_id` exists;
4. require operators to map or archive legacy `REPORT_MANUAL` rows before target constraints because they have no `cycle_report_id`;
5. stop and explain rather than delete or guess unmappable rows;
6. add target CHECK constraint: RECORD has only record ID, CYCLE_REPORT has only cycle report ID;
7. add status CHECK with exactly `PENDING, READY, FAILED, STALE`;
8. add partial unique indexes for `(record_id, source_revision)` where type is RECORD and `(cycle_report_id, source_revision)` where type is CYCLE_REPORT;
9. make legacy question/period/summary/risk/confidence/farm/crop/coaching_mode columns nullable or drop them only after operator-confirmed migration;
10. be idempotent where PostgreSQL permits, with stable constraint/index names.

- [ ] **Step 2: Update API and phase handoff docs**

Document automatic behavior:

```text
record create/update response does not contain AI feedback
GET status endpoint returns PENDING with inputPrepared true/false
regenerate is only allowed for FAILED or returned STALE
phase 2 never returns generated coaching text
phase 3 will fill structured result and transition READY/FAILED
```

Document that forecast contains only provider-returned dates and currently may be fewer than seven.

- [ ] **Step 3: Run static contract gates**

```bash
rg -n "TodayRecordWorkType|recentRecords|workTypeStats|cropCycle|daysAfterPlanting|materialName|pesticideName" \
  backend/application/src/main/kotlin/com/chamchamcham/application/coaching \
  backend/api/src/main/kotlin/com/chamchamcham/api/coaching
```

Expected: no product Context matches. References in superseded docs/tests are not part of this gate.

```bash
rg -n "record-feedback-context.v2|sourceRevision|RecordFeedbackContextAssembler|RecordFeedbackPreparationRequested|REQUIRES_NEW" \
  backend/domain/src/main backend/application/src/main backend/api/src/main
```

Expected: all five contracts are present.

- [ ] **Step 4: Run full backend verification**

```bash
cd backend
./gradlew test --rerun-tasks
```

Expected: `BUILD SUCCESSFUL`; all domain/application/api/batch tests execute.

```bash
cd ..
git diff --check
git status --short --branch
```

Expected: no whitespace errors and no uncommitted tracked changes after commit.

- [ ] **Step 5: Commit Task 7**

```bash
git add backend/docs/db frontend/docs docs/superpowers/specs/2026-07-10-cycle-report-record-coaching-rag-redesign.md
git commit -m "docs(coaching): 실제 기록 피드백 배포 계약을 확정" \
  -m "Constraint: 기존 REPORT_MANUAL 행은 cycle report ID를 추정하지 않음" \
  -m "Rejected: 범용 자동 backfill | 운영 데이터 보존 규칙이 아직 없음" \
  -m "Confidence: high" -m "Scope-risk: moderate" \
  -m "Tested: full backend test suite and git diff check" \
  -m "Not-tested: manual DDL against production-like PostgreSQL"
```

## Final Acceptance Checklist

- [ ] 기록 생성·수정과 동일 트랜잭션에서 revision별 RECORD/PENDING이 저장된다.
- [ ] 기록 삭제·수정으로 이전 활성 코칭이 STALE이 된다.
- [ ] 커밋 후 실제 엔티티 Context가 JSONB input snapshot으로 저장된다.
- [ ] 다른 회원·삭제 기록 Context를 조립하거나 조회할 수 없다.
- [ ] PLANTING부터 ETC까지 여덟 WorkType이 실제 상세 엔티티에서 타입 안전하게 매핑된다.
- [ ] Context에 최근 기록·주기 통계·직전 통계·파종 후 경과일이 없다.
- [ ] 기록 당시 날씨와 메모·사진 수가 들어가며 사진 내용은 분석하지 않는다.
- [ ] 날씨 실패·좌표 부재에도 축소 Context가 준비되고 기록은 유지된다.
- [ ] KMA가 반환하지 않은 예보 날짜를 추정하지 않는다.
- [ ] 상태 조회는 현재 revision을 우선하고 없을 때 최신 STALE만 반환한다.
- [ ] 재생성은 FAILED 또는 STALE에만 허용된다.
- [ ] 2단계 API는 LLM 텍스트·내부 snapshot Map·감사·모델 정보를 노출하지 않는다.
- [ ] 범용 CHAT API가 제품 CoachingFeedback을 저장하지 않는다.
- [ ] LLM·RAG 생성, READY 전이, 완료 리포트 코칭, 범용 backfill/worker가 없다.
- [ ] PostgreSQL 수동 DDL과 프론트 API 문서가 코드 계약과 일치한다.
- [ ] `./gradlew test --rerun-tasks`와 `git diff --check`가 통과한다.
