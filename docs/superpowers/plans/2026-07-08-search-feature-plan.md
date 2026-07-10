# Search Feature Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add keyword search to the record main page (`GET /api/v1/farming-records?keyword=`) and a new unified search endpoint (`GET /api/v1/search`) across 기록(RECORD)/게시물(POST) with an `ALL` preview-sections mode, per the approved spec at `docs/superpowers/specs/2026-07-08-search-feature-design.md`.

**Architecture:** Keyword matching for records is added once inside `FarmingRecordQueryRepositoryImpl`/`FarmingRecordService` (EXISTS-subquery JPQL + enum-label resolution) so both the record main page and the unified search's `RECORD` category share it automatically. A new `application.search` package adds a polymorphic `CategorySearcher` (`FarmingRecordSearcher`, `CommunityPostSearcher`) dispatched by `SearchService`, wrapping the existing `FarmingRecordService.search` / `CommunityPostService.search` — no new query logic in the searchers themselves.

**Tech Stack:** Spring Boot 3.x + Kotlin, hand-written JPQL via `EntityManager` (no QueryDSL), JUnit 5 + AssertJ + Mockito (`org.mockito.Mockito.\`when\``, not MockK), `@DataJpaTest`/`@WebMvcTest` per existing patterns.

## Global Constraints

- Base package `com.chamchamcham`; module dependency direction `api -> application -> domain` (never the reverse).
- Do not reintroduce project-owned `user` naming (`userId`, `users`) — this codebase uses `member`.
- Application services accept `*Command`/`*SearchCondition` objects and return `*Result` objects only — never API DTOs or `ResponseEntity`.
- Expected failures are `BusinessException(ErrorCode.X)`; add new codes to `ErrorCode` rather than hardcoding response bodies.
- Test frameworks already in use: JUnit 5, AssertJ (`org.assertj.core.api.Assertions.assertThat`), Mockito (`@Mock`, `` `when` ``, `@MockBean` for `@WebMvcTest`), `@DataJpaTest` + `TestEntityManager` for query-repository tests. Do not introduce MockK or Kotest.
- Commits use Conventional Commits in Korean per `AGENTS.md` (`type(scope): title`), one commit per task below.
- **Shared working directory warning:** this repository is being actively edited by the user in parallel (another terminal/IDE) during this session. Before every task, run `git branch --show-current` and confirm it is `feat/search` before making any change. If `origin/dev` has moved ahead, rebase `feat/search` onto it (Task 0) before continuing — do not assume the branch state from a previous task still holds.

## Context

앱에 두 개의 검색 표면을 추가한다.
- **검색 메인 페이지**: 카테고리(기록/게시물/전체) 통합 검색. 복합 선택 불가.
- **기록 메인 페이지**: 기록(나의 일지) 한정 검색 + 필터(작물/작업활동/기간) + 커서 페이징.

향후 리포트·전용 커뮤니티 검색 확장을 대비해 **카테고리별 다형성(Searcher 인터페이스)** 으로 설계한다.

브레인스토밍 확정 사항:
1. **전체 검색** = 카테고리별 미리보기 섹션(각 상위 N개, cross-category 페이징 없음). '더보기' 탭 시 해당 카테고리 페이징 목록으로 진입.
2. 이번 범위 카테고리 = **기록(FarmingRecord), 게시물(CommunityPost)**. **정책(PolicyProgram)은 제외** — 다형성 인터페이스에 슬롯만 예약(추후 구현).
3. 키워드는 자유 텍스트(작물명, 메모, 자재명, 약제명)뿐 아니라 **enum 필드(작업활동 WorkType, 수확부위 CropUsePartCategory)도 한글 라벨 매핑으로 매칭**.

## Scope

**In**
1. 기록 메인 페이지: 기존 FarmingRecord 목록조회에 `keyword` 추가(자유텍스트 + enum 라벨 매칭). 기존 crop/workType/period 필터 및 커서 페이징과 공존.
2. 검색 메인 페이지: 신규 `GET /api/v1/search` 통합 엔드포인트. category ∈ {ALL, RECORD, POST}. ALL → 미리보기 섹션, RECORD/POST → 페이징 목록. 다형성 dispatch.
3. 게시물 검색: 기존 `CommunityPostQueryRepository` 재사용.

**Out**: 정책 검색(슬롯만), 리포트 검색, 전용 커뮤니티 검색 페이지, 복합 카테고리 선택.

## 아키텍처 — 다형성 Searcher

application 계층에 배치.

- `SearchCategory` enum: `ALL, RECORD, POST` (+ 추후 `POLICY, REPORT`)
- `CategorySearcher` 인터페이스:
  - `category(): SearchCategory`
  - `search(query: SearchQuery): SearchResult.Page` — keyword + cursor + size → 정규화 아이템 + nextCursor
- 구현체:
  - `FarmingRecordSearcher` → `FarmingRecordService.search`(keyword만, 필터 없이) 호출 후 결과를 정규화
  - `CommunityPostSearcher` → `CommunityPostService.search`(keyword, sort=LATEST) 호출 후 정규화
- `SearchService`:
  - 특정 카테고리 → 매칭 Searcher에 dispatch → `SearchResult.Page`
  - ALL → 각 구현체를 preview size로 호출 → `SearchResult.Sections`(카테고리 + 상위 N + hasMore)
  - Searcher는 Spring이 `List<CategorySearcher>` 주입 → `Map<SearchCategory, CategorySearcher>`. **신규 카테고리 = 구현체 1개 추가, 오케스트레이터 변경 불필요.**

**정규화 아이템** `SearchResult.Item`: `category, id, title, snippet, thumbnailUrl?, createdAt`. 각 Searcher가 도메인 행을 이 형태로 매핑.

## 데이터 흐름

**검색 메인**: `GET /api/v1/search?keyword=&category=&cursor=&size=`
→ `SearchService.search(SearchQuery)`
→ ALL: 카테고리별 preview → `SearchResult.Sections`
→ RECORD/POST: `searcher.search` → `SearchResult.Page(items, nextCursor)`
→ `SearchResponses` DTO → `ApiResponse.ok`

**기록 메인(확장)**: `GET /api/v1/farming-records` 에 `keyword: String?` 추가
→ `FarmingRecordSearchCondition`에 keyword 추가
→ `FarmingRecordQueryRepository.SearchCondition`에 keyword + 해석된 enum 매칭 추가
→ 기존 `RecordPageResponse` 그대로

## 기록 키워드 검색 (핵심 난이도)

`FarmingRecordQueryRepositoryImpl.findRecords` JPQL 확장. 활동별 상세는 별도 6개 테이블이고 FarmingRecord에 매핑이 없으므로 **EXISTS 서브쿼리**로 조인(엔티티 매핑 추가 불필요, JPQL 유지, row 증식 없음).

keyword 술어 = 다음의 OR:
- `lower(r.crop.name) like :kw`
- `lower(r.memo) like :kw`
- `exists (select 1 from FertilizingRecord f where f.record.id = r.id and lower(f.materialName) like :kw)`
- `exists (select 1 from PestControlRecord p where p.record.id = r.id and lower(p.pesticideName) like :kw)`
- enum 매칭(서비스에서 keyword→한글 라벨로 해석해 전달):
  - WorkType 매칭 시: `r.workType in :matchedWorkTypes`
  - CropUsePartCategory(수확부위) 매칭 시: `exists (select 1 from HarvestRecord h where h.record.id = r.id and h.medicinalPart in :matchedParts)`

구조적 필터(cropId, workType, period)는 기존처럼 AND 결합. keyword 블록은 별도 AND 그룹(내부 OR).

**enum 라벨 매핑**:
- `WorkType`에 한글 `label` 추가(신규), `CropUsePartCategory`는 기존 한글명을 `label` 프로퍼티로 노출.
- 서비스에서 해석: `WorkType.entries.filter { keyword contains/matches it.label }` 등. 작은 헬퍼로 분리(예: application에 enum 라벨 해석 로직).

## 커서

`OpaqueCursorCodec` 재사용.
- 기록: 기존 (workedAt, id) keyset 유지 — keyword가 정렬을 바꾸지 않음.
- 게시물: 기존 커서.
- 통합 RECORD/POST 페이징: 한 번에 한 카테고리만 페이징하므로 **각 카테고리 커서를 opaque 문자열로 통과**. 복합 커서 불필요(ALL은 페이징 없음).

## 최대 재사용 포인트

- 기록 keyword + enum 해석 로직은 **`FarmingRecordService.search`에 넣어** 두 표면(기록 메인, 통합 RECORD)이 모두 자동 적용받게 한다. `FarmingRecordSearcher`는 필터 없이 keyword만 넘기고 결과를 `Item`으로 재매핑.
- `CommunityPostSearcher`는 `CommunityPostService.search`(sort=LATEST) 재사용.

## Note: WorkType/CropUsePartCategory labels already exist

Discovered during planning (2026-07-08): `WorkType`(`domain/src/main/kotlin/com/chamchamcham/domain/farming/WorkType.kt`) already has `label: String` and `detailRequired: Boolean` (added on `dev` via commits `d5d4c91`/`ad18058`/`619c2bb`, a parallel in-progress feature). `CropUsePartCategory` already had `label` from the start. **No task below adds these — both are reused as-is.** Actual values:

```kotlin
enum class WorkType(val label: String, val detailRequired: Boolean) {
    PLANTING("파종/정식", detailRequired = false),
    WATERING("관수", detailRequired = false),
    FERTILIZING("시비", detailRequired = true),
    PEST_CONTROL("병해충 방제", detailRequired = true),
    WEEDING("제초", detailRequired = false),
    PRUNING("전정", detailRequired = false),
    HARVEST("수확", detailRequired = true),
}
```

```kotlin
enum class CropUsePartCategory(val label: String) {
    WHOLE_HERB("전초"), ROOT_BARK("뿌리·껍질"), RHIZOME("뿌리줄기"), LEAF("잎"),
    FLOWER("꽃"), FRUIT("열매/과실"), SEED("종자"), STEM_BRANCH("줄기/가지"), UNKNOWN("기타")
}
```

If a fresh `git status`/`cat` at execution time shows different values, trust the file on disk over this plan and adjust label-matching test assertions accordingly — do not re-add the properties.

---

# Tasks

## Task 0: Sync `feat/search` with latest `dev`

**Files:** none (git only)

- [ ] **Step 1: Confirm current branch and clean state**

Run: `git branch --show-current`
Expected: `feat/search` (if not, `git checkout feat/search`; if uncommitted changes exist on the current branch and it is not `feat/search`, STOP and ask the user before switching — do not discard work)

- [ ] **Step 2: Rebase onto latest origin/dev**

```bash
git fetch origin dev
git rebase origin/dev
```

Expected: `Successfully rebased and updated refs/heads/feat/search.` (only the spec-doc commit should be replayed; if conflicts appear, STOP and ask the user — do not force-resolve)

- [ ] **Step 3: Verify the design spec commit is still present**

Run: `git log --oneline -5`
Expected: top commit is `docs(search): 검색 기능 설계 스펙 문서 추가`, directly on top of the latest `origin/dev` tip.

## Task 1: Record keyword matching — domain query layer

**Files:**
- Modify: `domain/src/main/kotlin/com/chamchamcham/domain/farming/FarmingRecordQueryRepository.kt`
- Modify: `domain/src/main/kotlin/com/chamchamcham/domain/farming/FarmingRecordQueryRepositoryImpl.kt`
- Modify (test): `domain/src/test/kotlin/com/chamchamcham/domain/farming/FarmingRecordQueryRepositoryTest.kt`

**Interfaces:**
- Produces: `FarmingRecordQueryRepository.SearchCondition` gains `keyword: String? = null`, `matchedWorkTypes: List<WorkType> = emptyList()`, `matchedParts: List<CropUsePartCategory> = emptyList()` (inserted after `workedAtTo`, before `cursor`). All existing call sites use named args with no default gaps, so this is source-compatible.

- [ ] **Step 1: Write failing tests**

Add to `FarmingRecordQueryRepositoryTest.kt` (same file, same class). First widen the existing `persistRecord` helper to accept `memo`:

```kotlin
    private fun persistRecord(
        owner: Member = member,
        crop: Crop = hwanggiCrop,
        workType: WorkType = WorkType.PRUNING,
        workedAt: LocalDateTime = LocalDateTime.of(2026, 6, 12, 9, 0),
        memo: String = "memo",
        isDeleted: Boolean = false,
    ): FarmingRecord {
        val record = FarmingRecord(
            member = owner,
            farm = farm,
            crop = crop,
            workType = workType,
            workedAt = workedAt,
            weatherCondition = "맑음",
            weatherTemperature = 20,
            memo = memo,
            entryMode = "MANUAL",
            isDeleted = isDeleted,
        )
        return persist(record, now)
    }
```

Add new detail-persist helpers (place near `persistMedia`):

```kotlin
    private fun persistFertilizingDetail(record: FarmingRecord, materialName: String) {
        persist(
            FertilizingRecord(
                record = record,
                materialName = materialName,
                amount = BigDecimal.TEN,
                amountUnit = FertilizerAmountUnit.KG,
            ),
            now
        )
    }

    private fun persistPestControlDetail(record: FarmingRecord, pesticideName: String) {
        persist(
            PestControlRecord(
                record = record,
                pesticideName = pesticideName,
                pesticideAmount = BigDecimal.ONE,
                pesticideAmountUnit = PesticideAmountUnit.ML,
                totalSprayAmount = BigDecimal.TEN,
                totalSprayAmountUnit = SprayAmountUnit.L,
            ),
            now
        )
    }

    private fun persistHarvestDetail(record: FarmingRecord, medicinalPart: CropUsePartCategory) {
        persist(
            HarvestRecord(
                record = record,
                harvestAmount = BigDecimal.TEN,
                harvestAmountUnit = HarvestAmountUnit.KG,
                medicinalPart = medicinalPart,
                harvestSource = HarvestSource.CULTIVATED,
                growthPeriod = 1,
                growthPeriodUnit = GrowthPeriodUnit.YEAR,
            ),
            now
        )
    }
```

Add `import java.math.BigDecimal` (the other detail types are in the same `com.chamchamcham.domain.farming` package as the test, so no import needed for them).

Widen the `condition` helper:

```kotlin
    private fun condition(
        cropId: UUID? = null,
        workType: WorkType? = null,
        workedAtFrom: LocalDateTime? = null,
        workedAtTo: LocalDateTime? = null,
        keyword: String? = null,
        matchedWorkTypes: List<WorkType> = emptyList(),
        matchedParts: List<CropUsePartCategory> = emptyList(),
        cursor: FarmingRecordQueryRepository.Cursor? = null,
        size: Int = 20,
    ): FarmingRecordQueryRepository.SearchCondition =
        FarmingRecordQueryRepository.SearchCondition(
            memberId = memberId,
            cropId = cropId,
            workType = workType,
            workedAtFrom = workedAtFrom,
            workedAtTo = workedAtTo,
            keyword = keyword,
            matchedWorkTypes = matchedWorkTypes,
            matchedParts = matchedParts,
            cursor = cursor,
            size = size,
        )
```

Add these test methods (anywhere among the existing `@Test` methods):

```kotlin
    @Test
    fun `search matches keyword against crop name`() {
        persistRecord(crop = hwanggiCrop)
        persistRecord(crop = ginsengCrop)
        entityManager.flush()
        entityManager.clear()

        val result = queryRepository.search(condition(keyword = "황기"))

        assertThat(result.rows).hasSize(1)
        assertThat(result.rows.single().record.crop.id).isEqualTo(hwanggiCropId)
    }

    @Test
    fun `search matches keyword against memo`() {
        persistRecord(memo = "진딧물이 많이 보였다")
        persistRecord(memo = "특이사항 없음")
        entityManager.flush()
        entityManager.clear()

        val result = queryRepository.search(condition(keyword = "진딧물"))

        assertThat(result.rows).hasSize(1)
        assertThat(result.rows.single().record.memo).isEqualTo("진딧물이 많이 보였다")
    }

    @Test
    fun `search matches keyword against fertilizing material name`() {
        val record = persistRecord(workType = WorkType.FERTILIZING)
        persistFertilizingDetail(record, materialName = "유박비료")
        val other = persistRecord(workType = WorkType.FERTILIZING)
        persistFertilizingDetail(other, materialName = "복합비료")
        entityManager.flush()
        entityManager.clear()

        val result = queryRepository.search(condition(keyword = "유박"))

        assertThat(result.rows).hasSize(1)
        assertThat(result.rows.single().record.id).isEqualTo(record.id)
    }

    @Test
    fun `search matches keyword against pest control pesticide name`() {
        val record = persistRecord(workType = WorkType.PEST_CONTROL)
        persistPestControlDetail(record, pesticideName = "친환경약제")
        val other = persistRecord(workType = WorkType.PEST_CONTROL)
        persistPestControlDetail(other, pesticideName = "일반약제")
        entityManager.flush()
        entityManager.clear()

        val result = queryRepository.search(condition(keyword = "친환경"))

        assertThat(result.rows).hasSize(1)
        assertThat(result.rows.single().record.id).isEqualTo(record.id)
    }

    @Test
    fun `search matches matched work types`() {
        persistRecord(workType = WorkType.HARVEST)
        persistRecord(workType = WorkType.WATERING)
        entityManager.flush()
        entityManager.clear()

        val result = queryRepository.search(condition(matchedWorkTypes = listOf(WorkType.HARVEST)))

        assertThat(result.rows).hasSize(1)
        assertThat(result.rows.single().record.workType).isEqualTo(WorkType.HARVEST)
    }

    @Test
    fun `search matches matched harvest parts`() {
        val leafCrop = persist(crop(name = "깻잎", externalNo = 900), now)
        val harvestRecord = persistRecord(crop = leafCrop, workType = WorkType.HARVEST)
        persistHarvestDetail(harvestRecord, medicinalPart = CropUsePartCategory.LEAF)
        val otherHarvest = persistRecord(crop = hwanggiCrop, workType = WorkType.HARVEST)
        persistHarvestDetail(otherHarvest, medicinalPart = CropUsePartCategory.ROOT_BARK)
        entityManager.flush()
        entityManager.clear()

        val result = queryRepository.search(condition(matchedParts = listOf(CropUsePartCategory.LEAF)))

        assertThat(result.rows).hasSize(1)
        assertThat(result.rows.single().record.id).isEqualTo(harvestRecord.id)
    }

    @Test
    fun `search combines keyword with structural filters`() {
        persistRecord(crop = hwanggiCrop, memo = "황기 관수 완료")
        persistRecord(crop = ginsengCrop, memo = "관수 완료")
        entityManager.flush()
        entityManager.clear()

        val result = queryRepository.search(condition(cropId = hwanggiCropId, keyword = "관수"))

        assertThat(result.rows).hasSize(1)
        assertThat(result.rows.single().record.crop.id).isEqualTo(hwanggiCropId)
    }

    @Test
    fun `search returns empty when keyword matches nothing`() {
        persistRecord(memo = "특이사항 없음")
        entityManager.flush()
        entityManager.clear()

        val result = queryRepository.search(condition(keyword = "존재하지않는키워드"))

        assertThat(result.rows).isEmpty()
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :domain:test --tests "com.chamchamcham.domain.farming.FarmingRecordQueryRepositoryTest"`
Expected: compile failure — `SearchCondition` has no `keyword`/`matchedWorkTypes`/`matchedParts` parameters yet.

- [ ] **Step 3: Implement**

In `FarmingRecordQueryRepository.kt`, add the import and fields:

```kotlin
package com.chamchamcham.domain.farming

import com.chamchamcham.domain.crop.CropUsePartCategory
import java.time.LocalDateTime
import java.util.UUID

interface FarmingRecordQueryRepository {
    fun search(condition: SearchCondition): SearchResult

    data class SearchCondition(
        val memberId: UUID,
        val cropId: UUID?,
        val workType: WorkType?,
        val workedAtFrom: LocalDateTime?,
        val workedAtTo: LocalDateTime?,
        val keyword: String? = null,
        val matchedWorkTypes: List<WorkType> = emptyList(),
        val matchedParts: List<CropUsePartCategory> = emptyList(),
        val cursor: Cursor?,
        val size: Int
    )

    data class Cursor(
        val workedAt: LocalDateTime,
        val id: UUID
    )

    data class Row(
        val record: FarmingRecord,
        val thumbnailUrl: String?
    )

    data class SearchResult(
        val rows: List<Row>
    )
}
```

In `FarmingRecordQueryRepositoryImpl.kt`, replace `findRecords` with:

```kotlin
    private fun findRecords(condition: FarmingRecordQueryRepository.SearchCondition): List<FarmingRecord> {
        val where = mutableListOf("r.isDeleted = false", "r.member.id = :memberId")
        val params = mutableMapOf<String, Any>("memberId" to condition.memberId)

        condition.cropId?.let {
            where += "r.crop.id = :cropId"
            params["cropId"] = it
        }
        condition.workType?.let {
            where += "r.workType = :workType"
            params["workType"] = it
        }
        condition.workedAtFrom?.let {
            where += "r.workedAt >= :workedAtFrom"
            params["workedAtFrom"] = it
        }
        condition.workedAtTo?.let {
            where += "r.workedAt < :workedAtTo"
            params["workedAtTo"] = it
        }

        val keywordPredicates = mutableListOf<String>()
        condition.keyword?.trim()?.lowercase()?.takeIf(String::isNotEmpty)?.let { kw ->
            params["keyword"] = "%$kw%"
            keywordPredicates += "lower(r.crop.name) like :keyword"
            keywordPredicates += "lower(r.memo) like :keyword"
            keywordPredicates += "exists (select 1 from FertilizingRecord f where f.record.id = r.id and lower(f.materialName) like :keyword)"
            keywordPredicates += "exists (select 1 from PestControlRecord p where p.record.id = r.id and lower(p.pesticideName) like :keyword)"
        }
        if (condition.matchedWorkTypes.isNotEmpty()) {
            keywordPredicates += "r.workType in :matchedWorkTypes"
            params["matchedWorkTypes"] = condition.matchedWorkTypes
        }
        if (condition.matchedParts.isNotEmpty()) {
            keywordPredicates += "exists (select 1 from HarvestRecord h where h.record.id = r.id and h.medicinalPart in :matchedParts)"
            params["matchedParts"] = condition.matchedParts
        }
        if (keywordPredicates.isNotEmpty()) {
            where += "(${keywordPredicates.joinToString(" or ")})"
        }

        condition.cursor?.let { cursor ->
            where += "(r.workedAt < :cursorWorkedAt or (r.workedAt = :cursorWorkedAt and r.id < :cursorId))"
            params["cursorWorkedAt"] = cursor.workedAt
            params["cursorId"] = cursor.id
        }

        val query = entityManager.createQuery(
            """
            select r
            from FarmingRecord r
            where ${where.joinToString(" and ")}
            order by r.workedAt desc, r.id desc
            """.trimIndent(),
            FarmingRecord::class.java
        )
        params.forEach(query::setParameter)
        query.maxResults = condition.size
        return query.resultList
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :domain:test --tests "com.chamchamcham.domain.farming.FarmingRecordQueryRepositoryTest"`
Expected: PASS (all existing tests plus the new keyword tests)

- [ ] **Step 5: Commit**

```bash
git add domain/src/main/kotlin/com/chamchamcham/domain/farming/FarmingRecordQueryRepository.kt \
        domain/src/main/kotlin/com/chamchamcham/domain/farming/FarmingRecordQueryRepositoryImpl.kt \
        domain/src/test/kotlin/com/chamchamcham/domain/farming/FarmingRecordQueryRepositoryTest.kt
git commit -m "feat(farming): 기록 목록조회에 키워드/작업활동·수확부위 라벨 매칭 추가"
```

## Task 2: Record keyword matching — application service layer

**Files:**
- Modify: `application/src/main/kotlin/com/chamchamcham/application/farming/FarmingRecordSearchCondition.kt`
- Modify: `application/src/main/kotlin/com/chamchamcham/application/farming/FarmingRecordService.kt`
- Modify (test): `application/src/test/kotlin/com/chamchamcham/application/farming/FarmingRecordServiceTest.kt`

**Interfaces:**
- Consumes: `FarmingRecordQueryRepository.SearchCondition` (Task 1) — `keyword`, `matchedWorkTypes`, `matchedParts` fields.
- Produces: `FarmingRecordSearchCondition` gains `keyword: String? = null` (inserted after `endDate`, before `cursor`). Existing callers unaffected.

- [ ] **Step 1: Write failing tests**

Add to `FarmingRecordServiceTest.kt`, and widen the existing `searchCondition` helper:

```kotlin
    private fun searchCondition(
        cropId: UUID? = null,
        workType: WorkType? = null,
        startDate: LocalDate? = null,
        endDate: LocalDate? = null,
        keyword: String? = null,
        cursor: String? = null,
        size: Int = 20,
    ) = FarmingRecordSearchCondition(
        memberId = memberId,
        cropId = cropId,
        workType = workType,
        startDate = startDate,
        endDate = endDate,
        keyword = keyword,
        cursor = cursor,
        size = size,
    )
```

Add these tests (near the other `search` tests):

```kotlin
    @Test
    fun `search resolves keyword into matched work type label`() {
        `when`(
            farmingRecordQueryRepository.search(
                FarmingRecordQueryRepository.SearchCondition(
                    memberId = memberId,
                    cropId = null,
                    workType = null,
                    workedAtFrom = null,
                    workedAtTo = null,
                    keyword = "수확",
                    matchedWorkTypes = listOf(WorkType.HARVEST),
                    matchedParts = emptyList(),
                    cursor = null,
                    size = 21
                )
            )
        ).thenReturn(FarmingRecordQueryRepository.SearchResult(emptyList()))

        val page = service.search(searchCondition(keyword = "수확"))

        assertThat(page.items).isEmpty()
    }

    @Test
    fun `search resolves keyword into matched harvest part label`() {
        `when`(
            farmingRecordQueryRepository.search(
                FarmingRecordQueryRepository.SearchCondition(
                    memberId = memberId,
                    cropId = null,
                    workType = null,
                    workedAtFrom = null,
                    workedAtTo = null,
                    keyword = "잎",
                    matchedWorkTypes = emptyList(),
                    matchedParts = listOf(CropUsePartCategory.LEAF),
                    cursor = null,
                    size = 21
                )
            )
        ).thenReturn(FarmingRecordQueryRepository.SearchResult(emptyList()))

        val page = service.search(searchCondition(keyword = "잎"))

        assertThat(page.items).isEmpty()
    }

    @Test
    fun `search passes blank keyword as null with no matched labels`() {
        `when`(
            farmingRecordQueryRepository.search(
                FarmingRecordQueryRepository.SearchCondition(
                    memberId = memberId,
                    cropId = null,
                    workType = null,
                    workedAtFrom = null,
                    workedAtTo = null,
                    keyword = null,
                    matchedWorkTypes = emptyList(),
                    matchedParts = emptyList(),
                    cursor = null,
                    size = 21
                )
            )
        ).thenReturn(FarmingRecordQueryRepository.SearchResult(emptyList()))

        val page = service.search(searchCondition(keyword = "   "))

        assertThat(page.items).isEmpty()
    }
```

`CropUsePartCategory` is already imported in this test file.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :application:test --tests "com.chamchamcham.application.farming.FarmingRecordServiceTest"`
Expected: compile failure — `FarmingRecordSearchCondition` has no `keyword` parameter yet.

- [ ] **Step 3: Implement**

In `FarmingRecordSearchCondition.kt`:

```kotlin
package com.chamchamcham.application.farming

import com.chamchamcham.domain.farming.WorkType
import java.time.LocalDate
import java.util.UUID

data class FarmingRecordSearchCondition(
    val memberId: UUID,
    val cropId: UUID?,
    val workType: WorkType?,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val keyword: String? = null,
    val cursor: String?,
    val size: Int
)
```

In `FarmingRecordService.kt`, add the import `com.chamchamcham.domain.crop.CropUsePartCategory`, then replace the `search` function and add two private helpers:

```kotlin
    @Transactional(readOnly = true)
    fun search(condition: FarmingRecordSearchCondition): FarmingRecordResult.Page {
        validatePageSize(condition.size)
        val cursor = decodeCursor(condition.cursor)
        val trimmedKeyword = condition.keyword?.trim()?.takeIf(String::isNotEmpty)
        val result = farmingRecordQueryRepository.search(
            FarmingRecordQueryRepository.SearchCondition(
                memberId = condition.memberId,
                cropId = condition.cropId,
                workType = condition.workType,
                workedAtFrom = condition.startDate?.atStartOfDay(),
                workedAtTo = condition.endDate?.plusDays(1)?.atStartOfDay(),
                keyword = trimmedKeyword,
                matchedWorkTypes = matchedWorkTypes(trimmedKeyword),
                matchedParts = matchedParts(trimmedKeyword),
                cursor = cursor,
                size = condition.size + 1
            )
        )
        val visibleRows = result.rows.take(condition.size)
        val nextCursor = if (result.rows.size > condition.size) {
            visibleRows.lastOrNull()?.let(::encodeCursor)
        } else {
            null
        }
        return FarmingRecordResult.Page(
            items = visibleRows.map(::toSummary),
            nextCursor = nextCursor
        )
    }

    private fun matchedWorkTypes(keyword: String?): List<WorkType> =
        keyword?.let { kw -> WorkType.entries.filter { it.label.contains(kw) } } ?: emptyList()

    private fun matchedParts(keyword: String?): List<CropUsePartCategory> =
        keyword?.let { kw -> CropUsePartCategory.entries.filter { it.label.contains(kw) } } ?: emptyList()
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :application:test --tests "com.chamchamcham.application.farming.FarmingRecordServiceTest"`
Expected: PASS (all existing tests plus the new keyword-resolution tests)

- [ ] **Step 5: Commit**

```bash
git add application/src/main/kotlin/com/chamchamcham/application/farming/FarmingRecordSearchCondition.kt \
        application/src/main/kotlin/com/chamchamcham/application/farming/FarmingRecordService.kt \
        application/src/test/kotlin/com/chamchamcham/application/farming/FarmingRecordServiceTest.kt
git commit -m "feat(farming): 서비스에서 키워드를 작업활동/수확부위 라벨로 해석해 쿼리에 전달"
```

## Task 3: Record main page API — keyword parameter

**Files:**
- Modify: `api/src/main/kotlin/com/chamchamcham/api/farming/controller/FarmingRecordController.kt`
- Modify (test): `api/src/test/kotlin/com/chamchamcham/api/farming/controller/FarmingRecordControllerTest.kt`

**Interfaces:**
- Consumes: `FarmingRecordSearchCondition` (Task 2) — `keyword` field.

- [ ] **Step 1: Write failing test**

Add to `FarmingRecordControllerTest.kt`:

```kotlin
    @Test
    fun `list records maps keyword parameter`() {
        `when`(
            farmingRecordService.search(
                FarmingRecordSearchCondition(
                    memberId = memberId,
                    cropId = null,
                    workType = null,
                    startDate = null,
                    endDate = null,
                    keyword = "수확",
                    cursor = null,
                    size = 20
                )
            )
        ).thenReturn(pageResult())

        mockMvc.perform(
            get("/api/v1/farming-records")
                .with(authenticatedMember(memberId.toString()))
                .param("keyword", "수확")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items[0].id", equalTo(recordId.toString())))
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :api:test --tests "com.chamchamcham.api.farming.controller.FarmingRecordControllerTest"`
Expected: compile failure — `FarmingRecordSearchCondition(...)` call is missing the `keyword` argument used by other already-updated tests, or (if compiling) a stub-mismatch failure because the controller never sends `keyword` to the service.

- [ ] **Step 3: Implement**

In `FarmingRecordController.kt`, update `listRecords`:

```kotlin
    @GetMapping
    fun listRecords(
        @AuthenticationPrincipal memberId: String?,
        @RequestParam(required = false) cropId: UUID?,
        @RequestParam(required = false) workType: WorkType?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate?,
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<ApiResponse<FarmingRecordResponses.RecordPageResponse>> {
        val page = farmingRecordService.search(
            FarmingRecordSearchCondition(
                memberId = parseMemberId(memberId),
                cropId = cropId,
                workType = workType,
                startDate = startDate,
                endDate = endDate,
                keyword = keyword,
                cursor = cursor,
                size = size
            )
        )
        return ResponseEntity.ok(ApiResponse.ok(FarmingRecordResponses.RecordPageResponse.from(page)))
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :api:test --tests "com.chamchamcham.api.farming.controller.FarmingRecordControllerTest"`
Expected: PASS (all existing tests plus the new keyword-parameter test)

- [ ] **Step 5: Commit**

```bash
git add api/src/main/kotlin/com/chamchamcham/api/farming/controller/FarmingRecordController.kt \
        api/src/test/kotlin/com/chamchamcham/api/farming/controller/FarmingRecordControllerTest.kt
git commit -m "feat(farming): 기록 목록 API에 keyword 쿼리 파라미터 추가"
```

## Task 4: Unified search types + FarmingRecordSearcher

**Files:**
- Create: `application/src/main/kotlin/com/chamchamcham/application/search/SearchCategory.kt`
- Create: `application/src/main/kotlin/com/chamchamcham/application/search/SearchQuery.kt`
- Create: `application/src/main/kotlin/com/chamchamcham/application/search/SearchResult.kt`
- Create: `application/src/main/kotlin/com/chamchamcham/application/search/CategorySearcher.kt`
- Create: `application/src/main/kotlin/com/chamchamcham/application/search/FarmingRecordSearcher.kt`
- Create (test): `application/src/test/kotlin/com/chamchamcham/application/search/FarmingRecordSearcherTest.kt`

**Interfaces:**
- Consumes: `FarmingRecordService.search(FarmingRecordSearchCondition): FarmingRecordResult.Page` (Task 2), `FarmingRecordResult.Summary` fields (`id`, `cropId`, `cropName`, `workType`, `workedAt`, `weatherCondition`, `weatherTemperature`, `memoPreview`, `thumbnailUrl`).
- Produces: `SearchCategory` (`ALL`, `RECORD`, `POST`), `SearchQuery(memberId, keyword, cursor, size)`, `SearchResult.Item/Page/SectionPreview/Sections`, `CategorySearcher` interface (`category(): SearchCategory`, `search(query: SearchQuery): SearchResult.Page`) — all consumed by Task 5/6/7.

- [ ] **Step 1: Write failing test**

Create `application/src/test/kotlin/com/chamchamcham/application/search/FarmingRecordSearcherTest.kt`:

```kotlin
package com.chamchamcham.application.search

import com.chamchamcham.application.farming.FarmingRecordResult
import com.chamchamcham.application.farming.FarmingRecordSearchCondition
import com.chamchamcham.application.farming.FarmingRecordService
import com.chamchamcham.domain.farming.WorkType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class FarmingRecordSearcherTest {
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val recordId = UUID.fromString("00000000-0000-0000-0000-000000000101")
    private val cropId = UUID.fromString("00000000-0000-0000-0000-000000000201")
    private val workedAt = LocalDateTime.of(2026, 6, 1, 9, 0)

    @Mock private lateinit var farmingRecordService: FarmingRecordService

    private lateinit var searcher: FarmingRecordSearcher

    @BeforeEach
    fun setUp() {
        searcher = FarmingRecordSearcher(farmingRecordService)
    }

    @Test
    fun `category returns RECORD`() {
        assertThat(searcher.category()).isEqualTo(SearchCategory.RECORD)
    }

    @Test
    fun `search maps query to unfiltered keyword condition and normalizes items`() {
        `when`(
            farmingRecordService.search(
                FarmingRecordSearchCondition(
                    memberId = memberId,
                    cropId = null,
                    workType = null,
                    startDate = null,
                    endDate = null,
                    keyword = "황기",
                    cursor = "cursor-1",
                    size = 10
                )
            )
        ).thenReturn(
            FarmingRecordResult.Page(
                items = listOf(
                    FarmingRecordResult.Summary(
                        id = recordId,
                        cropId = cropId,
                        cropName = "황기",
                        workType = WorkType.HARVEST,
                        workedAt = workedAt,
                        weatherCondition = "맑음",
                        weatherTemperature = 20,
                        memoPreview = "수확 완료",
                        thumbnailUrl = "https://example.test/1.jpg",
                    )
                ),
                nextCursor = "cursor-2"
            )
        )

        val page = searcher.search(
            SearchQuery(memberId = memberId, keyword = "황기", cursor = "cursor-1", size = 10)
        )

        assertThat(page.nextCursor).isEqualTo("cursor-2")
        val item = page.items.single()
        assertThat(item.category).isEqualTo(SearchCategory.RECORD)
        assertThat(item.id).isEqualTo(recordId)
        assertThat(item.title).isEqualTo("황기 · 수확")
        assertThat(item.snippet).isEqualTo("수확 완료")
        assertThat(item.thumbnailUrl).isEqualTo("https://example.test/1.jpg")
        assertThat(item.createdAt).isEqualTo(workedAt)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :application:test --tests "com.chamchamcham.application.search.FarmingRecordSearcherTest"`
Expected: compile failure — none of the `application.search` types exist yet.

- [ ] **Step 3: Implement**

Create `SearchCategory.kt`:

```kotlin
package com.chamchamcham.application.search

enum class SearchCategory {
    ALL,
    RECORD,
    POST,
}
```

Create `SearchQuery.kt`:

```kotlin
package com.chamchamcham.application.search

import java.util.UUID

data class SearchQuery(
    val memberId: UUID,
    val keyword: String?,
    val cursor: String?,
    val size: Int,
)
```

Create `SearchResult.kt`:

```kotlin
package com.chamchamcham.application.search

import java.time.LocalDateTime
import java.util.UUID

object SearchResult {
    data class Item(
        val category: SearchCategory,
        val id: UUID,
        val title: String,
        val snippet: String,
        val thumbnailUrl: String?,
        val createdAt: LocalDateTime,
    )

    data class Page(
        val items: List<Item>,
        val nextCursor: String?,
    )

    data class SectionPreview(
        val category: SearchCategory,
        val items: List<Item>,
        val hasMore: Boolean,
    )

    data class Sections(
        val sections: List<SectionPreview>,
    )
}
```

Create `CategorySearcher.kt`:

```kotlin
package com.chamchamcham.application.search

interface CategorySearcher {
    fun category(): SearchCategory
    fun search(query: SearchQuery): SearchResult.Page
}
```

Create `FarmingRecordSearcher.kt`:

```kotlin
package com.chamchamcham.application.search

import com.chamchamcham.application.farming.FarmingRecordSearchCondition
import com.chamchamcham.application.farming.FarmingRecordService
import org.springframework.stereotype.Component

@Component
class FarmingRecordSearcher(
    private val farmingRecordService: FarmingRecordService,
) : CategorySearcher {
    override fun category(): SearchCategory = SearchCategory.RECORD

    override fun search(query: SearchQuery): SearchResult.Page {
        val page = farmingRecordService.search(
            FarmingRecordSearchCondition(
                memberId = query.memberId,
                cropId = null,
                workType = null,
                startDate = null,
                endDate = null,
                keyword = query.keyword,
                cursor = query.cursor,
                size = query.size,
            )
        )
        return SearchResult.Page(
            items = page.items.map { summary ->
                SearchResult.Item(
                    category = SearchCategory.RECORD,
                    id = summary.id,
                    title = "${summary.cropName} · ${summary.workType.label}",
                    snippet = summary.memoPreview,
                    thumbnailUrl = summary.thumbnailUrl,
                    createdAt = summary.workedAt,
                )
            },
            nextCursor = page.nextCursor,
        )
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :application:test --tests "com.chamchamcham.application.search.FarmingRecordSearcherTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add application/src/main/kotlin/com/chamchamcham/application/search/SearchCategory.kt \
        application/src/main/kotlin/com/chamchamcham/application/search/SearchQuery.kt \
        application/src/main/kotlin/com/chamchamcham/application/search/SearchResult.kt \
        application/src/main/kotlin/com/chamchamcham/application/search/CategorySearcher.kt \
        application/src/main/kotlin/com/chamchamcham/application/search/FarmingRecordSearcher.kt \
        application/src/test/kotlin/com/chamchamcham/application/search/FarmingRecordSearcherTest.kt
git commit -m "feat(search): 검색 공통 타입과 기록 카테고리 Searcher 추가"
```

## Task 5: CommunityPostSearcher

**Files:**
- Create: `application/src/main/kotlin/com/chamchamcham/application/search/CommunityPostSearcher.kt`
- Create (test): `application/src/test/kotlin/com/chamchamcham/application/search/CommunityPostSearcherTest.kt`

**Interfaces:**
- Consumes: `SearchCategory`, `SearchQuery`, `SearchResult` (Task 4); `CommunityPostService.search(CommunityPostSearchCondition): CommunityPostResult.Page`, `CommunityPostResult.PostSummary` fields (`id`, `cropId`, `cropName`, `postType`, `title`, `bodyPreview`, `thumbnailUrl`, `author`, `commentCount`, `likeCount`, `likedByMe`, `createdAt`).
- Produces: `CommunityPostSearcher : CategorySearcher` with `category() == SearchCategory.POST`, consumed by Task 6.

- [ ] **Step 1: Write failing test**

Create `application/src/test/kotlin/com/chamchamcham/application/search/CommunityPostSearcherTest.kt`:

```kotlin
package com.chamchamcham.application.search

import com.chamchamcham.application.community.CommunityPostResult
import com.chamchamcham.application.community.CommunityPostSearchCondition
import com.chamchamcham.application.community.CommunityPostService
import com.chamchamcham.domain.community.CommunityPostSort
import com.chamchamcham.domain.community.CommunityPostType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class CommunityPostSearcherTest {
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val postId = UUID.fromString("00000000-0000-0000-0000-000000000101")
    private val cropId = UUID.fromString("00000000-0000-0000-0000-000000000201")
    private val createdAt = LocalDateTime.of(2026, 6, 1, 9, 0)

    @Mock private lateinit var communityPostService: CommunityPostService

    private lateinit var searcher: CommunityPostSearcher

    @BeforeEach
    fun setUp() {
        searcher = CommunityPostSearcher(communityPostService)
    }

    @Test
    fun `category returns POST`() {
        assertThat(searcher.category()).isEqualTo(SearchCategory.POST)
    }

    @Test
    fun `search maps query to latest sorted unfiltered condition and normalizes items`() {
        `when`(
            communityPostService.search(
                CommunityPostSearchCondition(
                    memberId = memberId,
                    cropId = null,
                    postType = null,
                    keyword = "발아",
                    likedOnly = false,
                    mineOnly = false,
                    sort = CommunityPostSort.LATEST,
                    cursor = "cursor-1",
                    size = 10
                )
            )
        ).thenReturn(
            CommunityPostResult.Page(
                items = listOf(
                    CommunityPostResult.PostSummary(
                        id = postId,
                        cropId = cropId,
                        cropName = "황기",
                        postType = CommunityPostType.QUESTION,
                        title = "발아율 질문",
                        bodyPreview = "발아가 잘 안 됩니다",
                        thumbnailUrl = "https://example.test/1.jpg",
                        author = CommunityPostResult.AuthorSummary(memberId = memberId, nickname = "농부", profileImageUrl = null),
                        commentCount = 3,
                        likeCount = 1,
                        likedByMe = false,
                        createdAt = createdAt
                    )
                ),
                nextCursor = "cursor-2"
            )
        )

        val page = searcher.search(
            SearchQuery(memberId = memberId, keyword = "발아", cursor = "cursor-1", size = 10)
        )

        assertThat(page.nextCursor).isEqualTo("cursor-2")
        val item = page.items.single()
        assertThat(item.category).isEqualTo(SearchCategory.POST)
        assertThat(item.id).isEqualTo(postId)
        assertThat(item.title).isEqualTo("발아율 질문")
        assertThat(item.snippet).isEqualTo("발아가 잘 안 됩니다")
        assertThat(item.createdAt).isEqualTo(createdAt)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :application:test --tests "com.chamchamcham.application.search.CommunityPostSearcherTest"`
Expected: compile failure — `CommunityPostSearcher` does not exist yet.

- [ ] **Step 3: Implement**

Create `CommunityPostSearcher.kt`:

```kotlin
package com.chamchamcham.application.search

import com.chamchamcham.application.community.CommunityPostSearchCondition
import com.chamchamcham.application.community.CommunityPostService
import com.chamchamcham.domain.community.CommunityPostSort
import org.springframework.stereotype.Component

@Component
class CommunityPostSearcher(
    private val communityPostService: CommunityPostService,
) : CategorySearcher {
    override fun category(): SearchCategory = SearchCategory.POST

    override fun search(query: SearchQuery): SearchResult.Page {
        val page = communityPostService.search(
            CommunityPostSearchCondition(
                memberId = query.memberId,
                cropId = null,
                postType = null,
                keyword = query.keyword,
                likedOnly = false,
                mineOnly = false,
                sort = CommunityPostSort.LATEST,
                cursor = query.cursor,
                size = query.size,
            )
        )
        return SearchResult.Page(
            items = page.items.map { summary ->
                SearchResult.Item(
                    category = SearchCategory.POST,
                    id = summary.id,
                    title = summary.title,
                    snippet = summary.bodyPreview,
                    thumbnailUrl = summary.thumbnailUrl,
                    createdAt = summary.createdAt,
                )
            },
            nextCursor = page.nextCursor,
        )
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :application:test --tests "com.chamchamcham.application.search.CommunityPostSearcherTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add application/src/main/kotlin/com/chamchamcham/application/search/CommunityPostSearcher.kt \
        application/src/test/kotlin/com/chamchamcham/application/search/CommunityPostSearcherTest.kt
git commit -m "feat(search): 게시물 카테고리 Searcher 추가"
```

## Task 6: SearchService dispatch + ALL preview sections

**Files:**
- Create: `application/src/main/kotlin/com/chamchamcham/application/search/SearchService.kt`
- Create (test): `application/src/test/kotlin/com/chamchamcham/application/search/SearchServiceTest.kt`
- Modify: `application/src/main/kotlin/com/chamchamcham/application/exception/ErrorCode.kt`

**Interfaces:**
- Consumes: `CategorySearcher`, `SearchCategory`, `SearchQuery`, `SearchResult` (Task 4/5).
- Produces: `SearchService(searchers: List<CategorySearcher>)` with `search(category: SearchCategory, query: SearchQuery): SearchResult.Page` and `searchAll(query: SearchQuery): SearchResult.Sections`, consumed by Task 7. Throws `BusinessException(ErrorCode.SEARCH_CATEGORY_NOT_SUPPORTED)` when `search()` is called with a category that has no registered searcher.

- [ ] **Step 1: Write failing test**

Create `application/src/test/kotlin/com/chamchamcham/application/search/SearchServiceTest.kt`:

```kotlin
package com.chamchamcham.application.search

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class SearchServiceTest {
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val createdAt = LocalDateTime.of(2026, 6, 1, 9, 0)

    private class StubSearcher(
        private val stubCategory: SearchCategory,
        private val page: SearchResult.Page,
    ) : CategorySearcher {
        var lastQuery: SearchQuery? = null
        override fun category(): SearchCategory = stubCategory
        override fun search(query: SearchQuery): SearchResult.Page {
            lastQuery = query
            return page
        }
    }

    private fun item(category: SearchCategory): SearchResult.Item =
        SearchResult.Item(
            category = category,
            id = UUID.randomUUID(),
            title = "title",
            snippet = "snippet",
            thumbnailUrl = null,
            createdAt = createdAt,
        )

    @Test
    fun `search dispatches to the matching category searcher`() {
        val recordSearcher = StubSearcher(
            SearchCategory.RECORD,
            SearchResult.Page(items = listOf(item(SearchCategory.RECORD)), nextCursor = "next")
        )
        val postSearcher = StubSearcher(SearchCategory.POST, SearchResult.Page(items = emptyList(), nextCursor = null))
        val service = SearchService(listOf(recordSearcher, postSearcher))

        val query = SearchQuery(memberId = memberId, keyword = "황기", cursor = null, size = 10)
        val page = service.search(SearchCategory.RECORD, query)

        assertThat(page.items).hasSize(1)
        assertThat(page.items.single().category).isEqualTo(SearchCategory.RECORD)
        assertThat(page.nextCursor).isEqualTo("next")
        assertThat(recordSearcher.lastQuery).isEqualTo(query)
    }

    @Test
    fun `search throws for a category with no registered searcher`() {
        val service = SearchService(emptyList())

        val exception = assertThrows(BusinessException::class.java) {
            service.search(SearchCategory.RECORD, SearchQuery(memberId = memberId, keyword = null, cursor = null, size = 10))
        }

        assertEquals(ErrorCode.SEARCH_CATEGORY_NOT_SUPPORTED, exception.errorCode)
    }

    @Test
    fun `searchAll returns one preview section per registered category ordered record then post`() {
        val recordSearcher = StubSearcher(
            SearchCategory.RECORD,
            SearchResult.Page(items = listOf(item(SearchCategory.RECORD)), nextCursor = "more")
        )
        val postSearcher = StubSearcher(
            SearchCategory.POST,
            SearchResult.Page(items = listOf(item(SearchCategory.POST)), nextCursor = null)
        )
        val service = SearchService(listOf(postSearcher, recordSearcher))

        val sections = service.searchAll(SearchQuery(memberId = memberId, keyword = "황기", cursor = "ignored", size = 20)).sections

        assertThat(sections.map { it.category }).containsExactly(SearchCategory.RECORD, SearchCategory.POST)
        assertThat(sections[0].hasMore).isTrue()
        assertThat(sections[1].hasMore).isFalse()
        assertThat(recordSearcher.lastQuery?.size).isEqualTo(5)
        assertThat(recordSearcher.lastQuery?.cursor).isNull()
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :application:test --tests "com.chamchamcham.application.search.SearchServiceTest"`
Expected: compile failure — `SearchService` and `ErrorCode.SEARCH_CATEGORY_NOT_SUPPORTED` do not exist yet.

- [ ] **Step 3: Implement**

In `ErrorCode.kt`, add a new line directly after `WEATHER_PROVIDER_UNAVAILABLE`:

```kotlin
    SEARCH_CATEGORY_NOT_SUPPORTED("SEARCH_001", "error.search_category_not_supported", 400),
```

Create `SearchService.kt`:

```kotlin
package com.chamchamcham.application.search

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import org.springframework.stereotype.Service

@Service
class SearchService(
    searchers: List<CategorySearcher>,
) {
    private val searchersByCategory: Map<SearchCategory, CategorySearcher> =
        searchers.associateBy { it.category() }

    fun search(category: SearchCategory, query: SearchQuery): SearchResult.Page {
        val searcher = searchersByCategory[category]
            ?: throw BusinessException(ErrorCode.SEARCH_CATEGORY_NOT_SUPPORTED)
        return searcher.search(query)
    }

    fun searchAll(query: SearchQuery): SearchResult.Sections {
        val sections = searchersByCategory.values
            .sortedBy { it.category().ordinal }
            .map { searcher ->
                val page = searcher.search(query.copy(size = PREVIEW_SIZE, cursor = null))
                SearchResult.SectionPreview(
                    category = searcher.category(),
                    items = page.items,
                    hasMore = page.nextCursor != null,
                )
            }
        return SearchResult.Sections(sections = sections)
    }

    private companion object {
        const val PREVIEW_SIZE = 5
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :application:test --tests "com.chamchamcham.application.search.SearchServiceTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add application/src/main/kotlin/com/chamchamcham/application/search/SearchService.kt \
        application/src/test/kotlin/com/chamchamcham/application/search/SearchServiceTest.kt \
        application/src/main/kotlin/com/chamchamcham/application/exception/ErrorCode.kt
git commit -m "feat(search): 카테고리 dispatch와 전체 미리보기 섹션을 처리하는 SearchService 추가"
```

## Task 7: Unified search API

**Files:**
- Create: `api/src/main/kotlin/com/chamchamcham/api/search/controller/SearchController.kt`
- Create: `api/src/main/kotlin/com/chamchamcham/api/search/dto/SearchResponses.kt`
- Create (test): `api/src/test/kotlin/com/chamchamcham/api/search/controller/SearchControllerTest.kt`

**Interfaces:**
- Consumes: `SearchService.search(SearchCategory, SearchQuery): SearchResult.Page`, `SearchService.searchAll(SearchQuery): SearchResult.Sections` (Task 6).
- Produces: `GET /api/v1/search?keyword=&category=&cursor=&size=` wrapped in `ApiResponse`.

- [ ] **Step 1: Write failing tests**

Create `api/src/test/kotlin/com/chamchamcham/api/search/controller/SearchControllerTest.kt`:

```kotlin
package com.chamchamcham.api.search.controller

import com.chamchamcham.api.exception.GlobalExceptionHandler
import com.chamchamcham.application.search.SearchCategory
import com.chamchamcham.application.search.SearchQuery
import com.chamchamcham.application.search.SearchResult
import com.chamchamcham.application.search.SearchService
import com.chamchamcham.application.security.TokenProvider
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime
import java.util.UUID

@WebMvcTest(SearchController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler::class)
class SearchControllerTest(
    @Autowired private val mockMvc: MockMvc
) {
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val recordId = UUID.fromString("00000000-0000-0000-0000-000000000101")
    private val createdAt = LocalDateTime.of(2026, 6, 1, 9, 0)

    @MockBean private lateinit var searchService: SearchService
    @MockBean private lateinit var tokenProvider: TokenProvider

    @Test
    fun `search with category ALL returns sections`() {
        `when`(
            searchService.searchAll(
                SearchQuery(memberId = memberId, keyword = "황기", cursor = null, size = 20)
            )
        ).thenReturn(
            SearchResult.Sections(
                sections = listOf(
                    SearchResult.SectionPreview(
                        category = SearchCategory.RECORD,
                        items = listOf(
                            SearchResult.Item(
                                category = SearchCategory.RECORD,
                                id = recordId,
                                title = "황기 · 수확",
                                snippet = "수확 완료",
                                thumbnailUrl = null,
                                createdAt = createdAt,
                            )
                        ),
                        hasMore = true,
                    ),
                    SearchResult.SectionPreview(
                        category = SearchCategory.POST,
                        items = emptyList(),
                        hasMore = false,
                    ),
                )
            )
        )

        mockMvc.perform(
            get("/api/v1/search")
                .with(authenticatedMember(memberId.toString()))
                .param("keyword", "황기")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.sections[0].category", equalTo("RECORD")))
            .andExpect(jsonPath("$.data.sections[0].items[0].id", equalTo(recordId.toString())))
            .andExpect(jsonPath("$.data.sections[0].hasMore", equalTo(true)))
    }

    @Test
    fun `search with category RECORD returns cursor page`() {
        `when`(
            searchService.search(
                SearchCategory.RECORD,
                SearchQuery(memberId = memberId, keyword = "황기", cursor = "cursor-1", size = 10)
            )
        ).thenReturn(
            SearchResult.Page(
                items = listOf(
                    SearchResult.Item(
                        category = SearchCategory.RECORD,
                        id = recordId,
                        title = "황기 · 수확",
                        snippet = "수확 완료",
                        thumbnailUrl = null,
                        createdAt = createdAt,
                    )
                ),
                nextCursor = "cursor-2"
            )
        )

        mockMvc.perform(
            get("/api/v1/search")
                .with(authenticatedMember(memberId.toString()))
                .param("keyword", "황기")
                .param("category", "RECORD")
                .param("cursor", "cursor-1")
                .param("size", "10")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items[0].id", equalTo(recordId.toString())))
            .andExpect(jsonPath("$.data.nextCursor", equalTo("cursor-2")))
    }

    @Test
    fun `search without auth returns unauthorized`() {
        mockMvc.perform(get("/api/v1/search"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code", equalTo("AUTH_001")))
    }

    private fun authenticatedMember(memberId: String): RequestPostProcessor {
        return RequestPostProcessor { request ->
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(
                    memberId,
                    null,
                    listOf(SimpleGrantedAuthority("ROLE_USER"))
                )
            request
        }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :api:test --tests "com.chamchamcham.api.search.controller.SearchControllerTest"`
Expected: compile failure — `SearchController`/`SearchResponses` do not exist yet.

- [ ] **Step 3: Implement**

Create `api/src/main/kotlin/com/chamchamcham/api/search/dto/SearchResponses.kt`:

```kotlin
package com.chamchamcham.api.search.dto

import com.chamchamcham.application.search.SearchCategory
import com.chamchamcham.application.search.SearchResult
import java.time.LocalDateTime
import java.util.UUID

object SearchResponses {
    data class ItemResponse(
        val category: SearchCategory,
        val id: UUID,
        val title: String,
        val snippet: String,
        val thumbnailUrl: String?,
        val createdAt: LocalDateTime,
    ) {
        companion object {
            fun from(item: SearchResult.Item): ItemResponse = ItemResponse(
                category = item.category,
                id = item.id,
                title = item.title,
                snippet = item.snippet,
                thumbnailUrl = item.thumbnailUrl,
                createdAt = item.createdAt,
            )
        }
    }

    data class PageResponse(
        val items: List<ItemResponse>,
        val nextCursor: String?,
    ) {
        companion object {
            fun from(page: SearchResult.Page): PageResponse = PageResponse(
                items = page.items.map(ItemResponse::from),
                nextCursor = page.nextCursor,
            )
        }
    }

    data class SectionResponse(
        val category: SearchCategory,
        val items: List<ItemResponse>,
        val hasMore: Boolean,
    ) {
        companion object {
            fun from(section: SearchResult.SectionPreview): SectionResponse = SectionResponse(
                category = section.category,
                items = section.items.map(ItemResponse::from),
                hasMore = section.hasMore,
            )
        }
    }

    data class SectionsResponse(
        val sections: List<SectionResponse>,
    ) {
        companion object {
            fun from(result: SearchResult.Sections): SectionsResponse = SectionsResponse(
                sections = result.sections.map(SectionResponse::from),
            )
        }
    }
}
```

Create `api/src/main/kotlin/com/chamchamcham/api/search/controller/SearchController.kt`:

```kotlin
package com.chamchamcham.api.search.controller

import com.chamchamcham.api.common.ApiResponse
import com.chamchamcham.api.search.dto.SearchResponses
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.application.search.SearchCategory
import com.chamchamcham.application.search.SearchQuery
import com.chamchamcham.application.search.SearchService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/search")
class SearchController(
    private val searchService: SearchService,
) {
    @GetMapping
    fun search(
        @AuthenticationPrincipal memberId: String?,
        @RequestParam(required = false) keyword: String?,
        @RequestParam(defaultValue = "ALL") category: SearchCategory,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ApiResponse<*>> {
        val query = SearchQuery(
            memberId = parseMemberId(memberId),
            keyword = keyword,
            cursor = cursor,
            size = size,
        )
        return if (category == SearchCategory.ALL) {
            ResponseEntity.ok(ApiResponse.ok(SearchResponses.SectionsResponse.from(searchService.searchAll(query))))
        } else {
            ResponseEntity.ok(ApiResponse.ok(SearchResponses.PageResponse.from(searchService.search(category, query))))
        }
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

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :api:test --tests "com.chamchamcham.api.search.controller.SearchControllerTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add api/src/main/kotlin/com/chamchamcham/api/search/controller/SearchController.kt \
        api/src/main/kotlin/com/chamchamcham/api/search/dto/SearchResponses.kt \
        api/src/test/kotlin/com/chamchamcham/api/search/controller/SearchControllerTest.kt
git commit -m "feat(search): 통합 검색 API 컨트롤러 추가"
```

## Task 8: Full build verification

**Files:** none (verification only)

- [ ] **Step 1: Run the full backend test suite**

Run (from `backend`): `./gradlew test`
Expected: BUILD SUCCESSFUL, all modules green (`domain`, `application`, `api`, `batch`).

- [ ] **Step 2: Manual smoke check (optional but recommended)**

```bash
./gradlew :api:bootRun
```

Then call, with a valid auth token/header the local profile accepts:
- `GET /api/v1/farming-records?keyword=수확`
- `GET /api/v1/search?keyword=황기&category=ALL`
- `GET /api/v1/search?keyword=황기&category=RECORD`
- `GET /api/v1/search?keyword=황기&category=POST`

Confirm each returns `200` with the expected `ApiResponse` shape (`items`/`nextCursor` for RECORD/POST, `sections[]` for ALL).

- [ ] **Step 3: Push the branch (only if the user asks)**

Do not run `git push` unless the user explicitly requests it — per this session's working agreement, pushing is a visible/shared action that needs separate confirmation.

## Execution notes

- Re-run Task 0's branch check before Task 1, and again before any task if this session was interrupted — the shared working directory has drifted underneath this session twice already today.
- After the plan is approved, save this plan document to `docs/superpowers/plans/2026-07-08-search-feature-plan.md` and commit it (`docs(search): 검색 기능 구현 계획 문서 추가`) as the first action post-approval, mirroring how the spec doc was committed.
- Use the `superpowers:subagent-driven-development` or `superpowers:executing-plans` skill to run Tasks 1–8 task-by-task, per the required sub-skill note at the top of this document.
