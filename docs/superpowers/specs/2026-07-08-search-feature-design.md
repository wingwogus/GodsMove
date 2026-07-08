# Search Feature Design

Date: 2026-07-08
Status: Approved

## Purpose

Add two search surfaces to the backend:

- **검색 메인 페이지**: a unified search endpoint across categories (기록/게시물/전체),
  single-category selection only.
- **기록 메인 페이지**: keyword search restricted to the member's own farming
  records (기록), layered on top of the existing crop/work-type/period filters
  and cursor pagination.

Report search and a dedicated community search page are known future needs but
are explicitly out of scope. The design uses a polymorphic `CategorySearcher`
interface so a new category (e.g. `POLICY`, `REPORT`) can be added later by
adding one implementation, without changing the orchestrator.

Both surfaces reuse the existing `FarmingRecordQueryRepository` /
`FarmingRecordService.search` and `CommunityPostQueryRepository` /
`CommunityPostService.search` infrastructure (already merged to `dev` via
`feat/farmrecord-api` and the community feature). No new database tables are
introduced.

## Product Decisions

- 검색 메인 카테고리: `ALL`, `RECORD`, `POST` (정책은 이번 범위 제외 — 인터페이스에 슬롯만 예약).
  Multi-category selection is not supported; no category param means `ALL`.
- `ALL` returns **카테고리별 미리보기 섹션**: each category returns its own top-N
  items with `hasMore`, no cross-category merged pagination. There is no
  cursor for `ALL` — pagination only applies once the client drills into a
  single category's own list ("더보기").
- `RECORD` and `POST` category searches return a normal cursor-paginated list
  scoped to that one category.
- 기록 키워드 매칭 대상: 작물명, 메모, 그리고 활동별 상세 필드 — 거름/비료의 자재명
  (`FertilizingRecord.materialName`), 병해충의 약제명 (`PestControlRecord.pesticideName`),
  수확의 수확 부위 (`HarvestRecord.medicinalPart`). 작업 활동(`WorkType`) 자체도
  한글 라벨로 매칭한다 (예: 키워드 "수확" → `WorkType.HARVEST`).
- Enum 라벨 매핑: `WorkType`에 한글 `label`을 새로 추가한다. `CropUsePartCategory`는
  이미 생성자에 한글 이름을 갖고 있으므로 그대로 재사용한다 (기존 `label` 프로퍼티 이미 존재).
- 게시물 검색은 기존 `CommunityPostQueryRepository`/`CommunityPostService.search`를
  그대로 재사용한다 (`sort = LATEST`, `likedOnly = false`, `mineOnly = false`).
- 기록 키워드 검색 로직은 `FarmingRecordService.search` 한 곳에만 추가한다. 기록
  메인 페이지(`GET /api/v1/farming-records`)와 통합 검색의 `RECORD` 카테고리가
  모두 이 로직을 공유한다.
- 인증: 기존 패턴과 동일하게 `@AuthenticationPrincipal memberId`로 인증된 사용자만
  검색할 수 있다. 기록은 본인 소유만, 게시물은 기존 커뮤니티 검색과 동일한 가시성
  규칙을 따른다.

## Application Structure

New package under `application`:

```text
application.search
- SearchCategory        (enum: ALL, RECORD, POST)
- CategorySearcher       (interface)
- SearchQuery            (input: memberId, keyword, cursor, size)
- SearchResult           (Item, Page, Sections, SectionPreview)
- SearchService
- FarmingRecordSearcher  (implements CategorySearcher, category() = RECORD)
- CommunityPostSearcher  (implements CategorySearcher, category() = POST)
```

New package under `api`:

```text
api.search.controller
- SearchController

api.search.dto
- SearchResponses
```

### CategorySearcher

```kotlin
interface CategorySearcher {
    fun category(): SearchCategory
    fun search(query: SearchQuery): SearchResult.Page
}
```

`SearchService` receives `List<CategorySearcher>` via Spring constructor
injection and indexes it into `Map<SearchCategory, CategorySearcher>`. Adding
a new category later means adding one new `CategorySearcher` bean; no changes
to `SearchService` are required. `ALL` and `POLICY`/`REPORT` are not resolvable
categories in the map — `ALL` is handled directly by `SearchService` by calling
every registered searcher with a preview size, and an unimplemented category
(e.g. a future `POLICY` value the API does not yet accept) is rejected with
`ErrorCode.SEARCH_CATEGORY_NOT_SUPPORTED` before dispatch.

### SearchResult shapes

```kotlin
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

Each `CategorySearcher` maps its domain rows into `Item`:

- `FarmingRecordSearcher`: `title` = `"${cropName} · ${workType 한글 라벨}"`,
  `snippet` = `memoPreview`, `createdAt` = `workedAt`.
- `CommunityPostSearcher`: `title` = post title, `snippet` = `bodyPreview`,
  `createdAt` = `createdAt`.

### FarmingRecordSearcher / CommunityPostSearcher

Both searchers call the existing service `search` methods with no structural
filters (cropId/workType/period/postType all `null`/default) and only
`keyword` + `cursor` + `size` populated, then map the returned `Page` into
`SearchResult.Page`. No new query logic lives in these classes — all matching
logic stays in `FarmingRecordService`/`FarmingRecordQueryRepositoryImpl` and
the existing `CommunityPostQueryRepositoryImpl`.

For the `ALL` preview call, each searcher is invoked with `size = previewSize`
and no cursor; `hasMore` is derived the same way page-based `hasMore` already
works elsewhere in this codebase — request `previewSize + 1` rows and check
if more than `previewSize` came back.

## Public API

### Unified Search

```http
GET /api/v1/search?keyword=&category=&cursor=&size=
```

Query parameters:

- `keyword` (optional; blank or absent applies no keyword filter, matching the
  existing `CommunityPostQueryRepositoryImpl` behavior — the endpoint then
  behaves as an unfiltered list scoped to the caller)
- `category`: `ALL` (default when absent) | `RECORD` | `POST`
- `cursor` (optional, only meaningful for `RECORD`/`POST`)
- `size` (default 20, only meaningful for `RECORD`/`POST`; `ALL` uses a fixed
  server-side preview size, see below)

Behavior:

- `category=ALL` → `SearchService` calls every registered `CategorySearcher`
  with a fixed preview size (`PREVIEW_SIZE = 5`) and returns
  `SearchResponses.SectionsResponse` (one section per category, each with its
  own `hasMore`). No `nextCursor` at this level.
- `category=RECORD` or `POST` → dispatch to the matching `CategorySearcher`
  with the requested `cursor`/`size`, return `SearchResponses.PageResponse`.

Response wrapped in the existing `ApiResponse`.

### Record Main Page (extended)

```http
GET /api/v1/farming-records?keyword=&cropId=&workType=&startDate=&endDate=&cursor=&size=
```

Adds an optional `keyword` query parameter to the existing endpoint. All
existing structural filters (`cropId`, `workType`, `startDate`, `endDate`) and
cursor pagination behavior are unchanged and combine with `keyword` using AND.
Response shape (`FarmingRecordResponses.RecordPageResponse`) is unchanged.

## Record Keyword Matching (core logic)

`FarmingRecordQueryRepositoryImpl.findRecords` JPQL is extended. The six
work-type detail tables (`PlantingRecord`, `WateringRecord`,
`FertilizingRecord`, `PestControlRecord`, `WeedingRecord`, `HarvestRecord`)
have a child-owned FK to `FarmingRecord` with no inverse mapping on
`FarmingRecord`, so keyword matching against detail fields uses `EXISTS`
subqueries rather than new entity mappings — this keeps JPQL only, adds no row
duplication, and touches no entity classes except the two enums below.

Keyword predicate = OR of:

- `lower(r.crop.name) like :kw`
- `lower(r.memo) like :kw`
- `exists (select 1 from FertilizingRecord f where f.record.id = r.id and lower(f.materialName) like :kw)`
- `exists (select 1 from PestControlRecord p where p.record.id = r.id and lower(p.pesticideName) like :kw)`
- `r.workType in :matchedWorkTypes` (only added when the keyword matches one or
  more `WorkType.label` values)
- `exists (select 1 from HarvestRecord h where h.record.id = r.id and h.medicinalPart in :matchedParts)`
  (only added when the keyword matches one or more `CropUsePartCategory.label`
  values)

Structural filters (`cropId`, `workType`, `workedAtFrom`/`workedAtTo`) stay
combined with AND exactly as today. The keyword block is a single additional
AND group containing the OR above.

`FarmingRecordQueryRepository.SearchCondition` gains three fields:

```kotlin
data class SearchCondition(
    val memberId: UUID,
    val cropId: UUID?,
    val workType: WorkType?,
    val workedAtFrom: LocalDateTime?,
    val workedAtTo: LocalDateTime?,
    val keyword: String?,
    val matchedWorkTypes: List<WorkType>,
    val matchedParts: List<CropUsePartCategory>,
    val cursor: Cursor?,
    val size: Int
)
```

### Enum label resolution

`WorkType` gets a new constructor-based `label: String` property (mirrors the
existing `CropUsePartCategory` pattern):

```kotlin
enum class WorkType(val label: String) {
    PLANTING("파종"),
    WATERING("관수"),
    FERTILIZING("시비"),
    PEST_CONTROL("병해충 방제"),
    WEEDING("제초"),
    PRUNING("정지·전정"),
    HARVEST("수확"),
}
```

`FarmingRecordService.search` resolves the raw keyword into matched enum
values before calling the query repository:

```kotlin
val trimmedKeyword = condition.keyword?.trim()?.takeIf(String::isNotEmpty)
val matchedWorkTypes = trimmedKeyword?.let { kw ->
    WorkType.entries.filter { it.label.contains(kw) }
} ?: emptyList()
val matchedParts = trimmedKeyword?.let { kw ->
    CropUsePartCategory.entries.filter { it.label.contains(kw) }
} ?: emptyList()
```

This keeps the free-text `like` search and the enum-label search both driven
by the same single keyword input, and both stay inside
`FarmingRecordService.search` so both call sites (record main page and the
`RECORD` category of unified search) get it automatically.

## Cursor

Reuses `OpaqueCursorCodec`, unchanged from the existing pattern:

- `RECORD`: existing `(workedAt, id)` keyset — `keyword` narrows the WHERE
  clause but does not change sort order, so the existing
  `FarmingRecordCursorPayload` is reused as-is.
- `POST`: existing `CommunityPostSort.LATEST` cursor, reused as-is.
- `ALL`: no cursor. Each section's "더보기" action is a client-side navigation
  to that category's own paginated list (`category=RECORD` or `category=POST`
  with the same keyword), not a cursor continuation of the preview call.

## Error Handling

New `ErrorCode` entries:

- `SEARCH_CATEGORY_NOT_SUPPORTED` (400) — category value is syntactically
  valid but not implemented yet (reserved for `POLICY`/`REPORT` before those
  searchers exist).

No other new error cases are needed: invalid `size`/`cursor` reuse
`ErrorCode.INVALID_INPUT` / `ErrorCode.INVALID_CURSOR` via the same validation
helpers already in `FarmingRecordService`/`CommunityPostService`.

## Testing

Domain/application (integration, existing patterns reused):

- `FarmingRecordQueryRepositoryTest`-style tests: keyword matches crop name,
  memo, `materialName`, `pesticideName`, work-type label ("수확" →
  `WorkType.HARVEST` records), harvest-part label ("잎" →
  `CropUsePartCategory.LEAF` records); keyword combined with existing
  structural filters (AND); keyword with no matches returns empty page.
- `FarmingRecordService` unit/integration: enum label resolution helper
  matches multiple `WorkType`/`CropUsePartCategory` values when the keyword is
  ambiguous, and none when it matches neither.
- `SearchService`: `ALL` returns one section per registered category with
  correct `hasMore`; `RECORD`/`POST` category returns a cursor page identical
  in shape to calling the underlying service directly; unsupported category
  throws `SEARCH_CATEGORY_NOT_SUPPORTED`.

API (controller contract tests, existing MockMvc pattern reused):

- `GET /api/v1/search?category=ALL` response shape (`sections[]` each with
  `items[]`/`hasMore`).
- `GET /api/v1/search?category=RECORD|POST` response shape
  (`items[]`/`nextCursor`).
- `GET /api/v1/farming-records?keyword=` still returns the existing
  `RecordPageResponse` shape with keyword applied.

Build verification: `./gradlew test` from `backend`.

Manual: `./gradlew :api:bootRun`, call both endpoints with sample keywords
covering crop name, memo text, material/pesticide name, and a work-type/harvest
-part label.

## Non-Goals

- Policy program (정책 정보) search — `SearchCategory`/`CategorySearcher` leave
  room for a future `PolicySearcher`, but no policy query/service/controller
  code is added now.
- Report search page.
- A dedicated community-only search page (community search already exists via
  `GET /api/v1/community/posts?keyword=`; this spec does not add a new one).
- Combined/multi-category search selection.
- A merged, cross-category cursor for `ALL`.
- QueryDSL migration for either `FarmingRecordQueryRepositoryImpl` or
  `CommunityPostQueryRepositoryImpl`.
