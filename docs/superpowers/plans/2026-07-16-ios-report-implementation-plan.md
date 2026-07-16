# iOS 리포트 화면 구현 계획

> **실행 규칙:** 이 계획은 `superpowers:test-driven-development`로 각 동작의 실패 테스트를
> 먼저 만든 뒤 구현하고, 완료 전 `superpowers:verification-before-completion`으로 전체 검증한다.

**목표:** `영농 기록 > 리포트` placeholder를 배포 API 기반의 목록·단일 선택 필터·작업별
상세·그래프·리포트 코칭 화면으로 교체한다. 기존 기록 목록·작성·상세·기록 코칭은 변경하지
않는다.

**구조:** 기존 단일 앱 target 안에 `Features/Report/{Data,Domain,Presentation}`를 추가한다.
`RecordListView`는 앱 바와 기록/리포트 탭 및 단일 `NavigationStack`만 계속 소유하고,
리포트는 자체 repository/view model/view로 분리한다. 네트워크 성공 DTO만 SwiftData V2에
저장하고 transport network 실패에서만 캐시로 fallback한다.

**기술:** Swift 6, SwiftUI, Observation (`@Observable`), SwiftData, Swift Charts,
URLSession/async-await, Swift Testing, iOS 17+

**기준 문서:**

- `AGENTS.md`
- `frontend/AGENTS.md`
- `docs/superpowers/specs/2026-07-16-ios-report-design.md`
- Figma `awacdwTczdvkObfsG0eK3X`의 report 노드 `1:930`~`1:1862`
- 라이브 Swagger `https://chamchamcham.jaehyuns.com/v3/api-docs`

## 공통 구현 원칙

- 모든 신규 Swift 파일에 저장소의 Xcode 헤더를 넣는다.
- 신규 파일은 synchronized root group으로 자동 포함되므로 `project.pbxproj`를 수정하지 않는다.
- 기록 기능에 이미 정의된 `WorkType`을 그대로 사용한다. 이 작업에서 enum을 복제하거나
  `RecordModels.swift`를 이동·수정하지 않는다.
- 화면은 먼저 `AppTopAppBar`, `AppTabBar`, `AppChip`, `AppBadge`, `AppCard`, `AppButton`,
  `AppIconView`, `Color.*`, `AppTypography`, `Spacing`을 사용한다.
- 리포트 전용 metric/chart/coaching 조합은 feature-local view로 둔다. 반복 사용이 확인되지
  않은 디자인 시스템 component를 새로 만들지 않는다.
- Figma 그래프에 필요한 색만 승인된 `Color.Chart` namespace로 추가한다. 기존 token 값과
  component public API는 바꾸지 않는다.
- 목록 identity는 `reportId` 단독이 아니라 `(reportId, workType)` 조합으로 만든다.
- cursor는 opaque string으로 저장·전달하며 클라이언트에서 해석하거나 재생성하지 않는다.
- 서버 `BigDecimal` 필드는 `Decimal`, LocalDate/LocalDateTime은 wire에서 `String`으로 받은 뒤
  리포트 전용 parser로 변환한다.
- API의 알려지지 않은 work type은 잘못 `.etc`로 표시하지 않는다. 목록에서는 지원하지 않는
  항목을 제외하고, 상세에서는 명시적 unsupported 오류로 전환한다.

---

## Task 1: 배포 Swagger 계약 동기화

**수정 파일:**

- `frontend/docs/swagger/openapi.json`
- `frontend/docs/swagger/openapi.sha256`
- `frontend/docs/swagger/summary.md`

**1. 라이브 스냅샷 쓰기**

```bash
cd frontend
python3 scripts/sync_swagger_spec.py --write
```

**2. 리포트 operation 확인**

```bash
rg -n 'farming-reports|work-items|regenerate' docs/swagger/summary.md docs/swagger/openapi.json
```

다음 네 operation이 없으면 프런트 DTO 구현을 시작하지 않고 배포 계약 불일치를 보고한다.

- `GET /api/v1/farming-reports/work-items`
- `GET /api/v1/farming-reports/{reportId}/work-types/{workType}`
- `GET /api/v1/farming-reports/{reportId}/feedback`
- `POST /api/v1/farming-reports/{reportId}/feedback/{workType}/regenerate`

**3. 이름 충돌 확인**

OpenAPI의 `StatusResponse`/`DetailResponse`가 서로 다른 중첩 DTO를 병합하는지 확인하고,
충돌한 schema ref를 client type 이름으로 사용하지 않는다.

**4. 커밋**

`chore(swagger): 리포트 배포 계약 동기화`와 실제 검증 명령을 Lore trailer에 기록한다.

---

## Task 2: endpoint, wire DTO, domain mapping을 계약 테스트로 고정

**신규 파일:**

- `frontend/ChamChamCham/ChamChamCham/Features/Report/Data/ReportEndpoint.swift`
- `frontend/ChamChamCham/ChamChamCham/Features/Report/Data/DTOs/FarmingWorkReportDTOs.swift`
- `frontend/ChamChamCham/ChamChamCham/Features/Report/Data/DTOs/FarmingWorkReportStatisticsDTOs.swift`
- `frontend/ChamChamCham/ChamChamCham/Features/Report/Domain/FarmingWorkReportModels.swift`
- `frontend/ChamChamCham/ChamChamCham/Features/Report/Domain/FarmingWorkReportStatistics.swift`
- `frontend/ChamChamCham/ChamChamCham/Features/Report/Domain/ReportDateParser.swift`
- `frontend/ChamChamCham/ChamChamChamTests/ReportEndpointTests.swift`
- `frontend/ChamChamCham/ChamChamChamTests/ReportResponseDecodingTests.swift`

**1. endpoint 실패 테스트 작성**

`ReportEndpointTests`에서 다음을 먼저 기대한다.

- work-items path, GET, auth
- nil filter와 nil/blank cursor 생략
- `farmId`, `cropId`, `workType`, opaque cursor, `size` 직렬화
- detail/feedback/regenerate path와 regenerate의 POST/body 없음

**2. endpoint 최소 구현**

```swift
struct ReportQuery: Equatable, Sendable {
    var farmId: UUID?
    var cropId: UUID?
    var workType: WorkType?
    var cursor: String?
    var size = 20
}

enum ReportEndpoint: Endpoint {
    case workItems(ReportQuery)
    case workDetail(reportId: UUID, workType: WorkType)
    case feedback(reportId: UUID)
    case regenerate(reportId: UUID, workType: WorkType)
}
```

**3. fixture decode 실패 테스트 작성**

실제 endpoint별 envelope fixture를 별도로 둔다.

- work-items page: ACTIVE/COMPLETED, nullable dates/thumbnail, composite identity, cursor
- detail: common + 선택된 typed branch 하나, 나머지 branch key 생략
- PLANTING/WATERING/FERTILIZING/PEST_CONTROL/WEEDING/HARVEST
- PRUNING/ETC common-only
- embedded feedback의 `content`
- bulk feedback의 `feedback`, timestamps, failureCode
- ACTIVE detail의 `feedback == nil`
- COMPLETED detail의 synthetic PENDING과 bulk의 빈 `feedbacks` 배열
- PENDING/FAILED/STALE/READY
- 미래 enum 문자열을 받아도 JSON decode 자체는 실패하지 않는지

**4. wire DTO 구현**

Wire enum은 `String`, amount/rate는 `Decimal`, timestamps는 `String`으로 둔다.
`statistics`의 typed branch는 모두 optional로 두고 backend의 `NON_NULL` key omission을 허용한다.
DTO는 cache 저장을 위해 `Codable & Sendable`을 채택한다.

**5. domain mapping 구현**

- `WorkReportKey(reportId:workType:)`를 `Identifiable` key로 사용
- `FarmingWorkReportSummary/Page/Detail`, `ReportCycleStatus`, `ReportFeedbackState/Content`
- typed statistics value models
- `ReportFilter`와 farm/crop filter option. 옵션 로딩은 새 wire DTO를 만들지 않고
  `RecordRepository.fetchFarmCrops()`를 closure로 주입해 기존 catalog 경계를 재사용
- stable LocalDate/LocalDateTime parsing과 표시 formatter
- unknown cycle/feedback status는 `.unsupported(rawValue)`로 유지
- unknown work type은 목록 `compactMap`, 상세 `ReportMappingError.unsupportedWorkType`

**6. targeted test**

```bash
xcodebuild -project ChamChamCham.xcodeproj -scheme ChamChamCham \
  -destination 'platform=iOS Simulator,name=iPhone 17,OS=26.0' \
  -derivedDataPath /private/tmp/ChamChamCham-derived \
  -only-testing:ChamChamChamTests/ReportEndpointTests \
  -only-testing:ChamChamChamTests/ReportResponseDecodingTests test
```

---

## Task 3: SwiftData V2 리포트 읽기 캐시

**신규 파일:**

- `frontend/ChamChamCham/ChamChamCham/Core/Persistence/CachedReportPayload.swift`
- `frontend/ChamChamCham/ChamChamCham/Core/Persistence/SchemaV2.swift`
- `frontend/ChamChamCham/ChamChamCham/Core/Persistence/AppSchemaMigrationPlan.swift`
- `frontend/ChamChamCham/ChamChamCham/Features/Report/Data/ReportCache.swift`
- `frontend/ChamChamCham/ChamChamChamTests/ReportCacheTests.swift`

**수정 파일:**

- `frontend/ChamChamCham/ChamChamCham/Core/Persistence/ModelContainer+App.swift`

**1. cache 실패 테스트 작성**

일반 cache 동작은 in-memory container로, 실제 migration은 `/private/tmp`의 임시 on-disk store로
다음을 먼저 검증한다.

- filter key별 첫/누적 page payload 분리
- `(reportId, workType)` detail key 분리
- reportId feedback payload 저장/교체
- updatedAt 보존
- corrupt payload는 miss로 처리하고 해당 row 삭제
- 임시 URL에 V1 container 생성→member 저장→container 해제→같은 URL을 V2 migration plan으로
  재개방한 뒤 기존 member cache가 남는지

**2. V2 schema 구현**

```swift
@Model
final class CachedReportPayload {
    @Attribute(.unique) var key: String
    var kindRaw: String
    var payload: Data
    var updatedAt: Date
}
```

`SchemaV2`는 `CachedMemberProfile`과 `CachedReportPayload`를 포함한다.
`AppSchemaMigrationPlan`은 V1→V2 lightweight migration을 선언하고 `makeApp()`이 V2와 migration
plan을 사용하게 한다. 기존 `SchemaV1`은 수정하지 않는다.

**3. cache boundary 구현**

`@MainActor ReportCache`가 `Data`와 timestamp만 반환하도록 하여 presentation에서 SwiftData를
모르게 한다. key namespace는 `list:`, `detail:`, `feedback:`으로 분리한다.

**4. targeted test 및 커밋**

`ReportCacheTests`를 실행한 뒤 Task 2~3을 함께
`feat(report): 리포트 API와 오프라인 캐시 추가`로 커밋한다.

---

## Task 4: remote data source와 cache fallback repository

**신규 파일:**

- `frontend/ChamChamCham/ChamChamCham/Features/Report/Data/ReportRemoteDataSource.swift`
- `frontend/ChamChamCham/ChamChamCham/Features/Report/Data/ReportRepository.swift`
- `frontend/ChamChamCham/ChamChamChamTests/ReportRepositoryTests.swift`
- `frontend/ChamChamCham/ChamChamChamTests/Support/ReportTestSupport.swift`

**수정 파일:**

- `frontend/ChamChamCham/ChamChamCham/App/DIContainer.swift`

**1. repository 실패 테스트 작성**

Stub remote/cache로 다음을 고정한다.

- 성공한 첫 page/detail/feedback만 cache 갱신
- cursor page를 기존 page와 id deduplicate해 누적 저장
- `APIError.network` + cache 있음 → `.cache(updatedAt)` source로 반환
- `APIError.network` + cache 없음 → 원래 오류 throw
- unauthorized, validation, server, business, decoding 오류에서는 cache를 성공처럼 반환하지 않음
- regenerate는 cache fallback/queue 없이 POST 결과만 반환

**2. data source 구현**

`ReportRemoteDataSource: Sendable`를 APIClient adapter와 test stub의 seam으로 사용한다. 각 API는
충돌한 OpenAPI schema가 아니라 Task 2의 endpoint-specific DTO를 반환한다.

**3. repository 구현**

```swift
struct ReportResource<Value: Sendable>: Sendable {
    enum Source: Sendable { case network; case cache(updatedAt: Date) }
    let value: Value
    let source: Source
}
```

첫 page/detail/feedback은 성공 시 encode→cache하고, `APIError.network`에만 cache decode로
fallback한다. 현재 `APIClient`가 실패 envelope의 HTTP status를 `apiError`에서 보존하지 않으므로
5xx를 추정해 fallback하지 않는다. page append와 feedback work type 선택은 repository에서 수행한다.

**4. DI 등록**

`DIContainer`에 `reportCache`를 한 번 만들고 `makeReportRepository()`가 API data source와 같은
cache instance를 주입하도록 한다.

**5. targeted test**

`ReportRepositoryTests`와 `ReportCacheTests`를 함께 실행한다.

---

## Task 5: 리포트 목록 상태·필터·pagination ViewModel

**신규 파일:**

- `frontend/ChamChamCham/ChamChamCham/Features/Report/Presentation/ViewModels/ReportListViewModel.swift`
- `frontend/ChamChamCham/ChamChamCham/Features/Report/Presentation/ViewModels/ReportErrorMessage.swift`
- `frontend/ChamChamCham/ChamChamChamTests/ReportListViewModelTests.swift`

**1. ViewModel 실패 테스트 작성**

- 최초 load가 주입된 기존 farm-crop loader와 첫 page를 가져옴
- cached source면 items를 유지하고 offline/stale banner 상태 설정
- refresh 성공 시 network source로 교체
- cache가 보이는 refresh 실패는 items를 지우지 않음
- no-cache 실패만 전체 error/retry
- empty, loading, loading-more 상태
- cursor append, composite id deduplicate, next cursor 보존
- farm/crop/workType 변경이 기존 task를 취소하고 첫 page로 reset
- farm 변경 시 그 farm에 없는 crop filter 자동 해제
- stale response가 최신 filter 결과를 덮지 않음

**2. ViewModel 구현**

`@MainActor @Observable`을 사용한다. init에는 report repository와
`@Sendable () async throws -> [FarmWithCrops]` loader를 받는다. production은 기존
`RecordRepository.fetchFarmCrops()`를 전달하고 test는 closure fixture를 전달한다. filter 변경마다
진행 중 load task를 취소하고 generation token을 확인한다. 목록 상태와 refresh/loading-more
상태는 독립적으로 관리한다.

**3. error copy 구현**

기존 `RecordErrorMessage`의 한국어 business message 판별 규칙을 재사용하되, report feature에서
기록 타입을 참조하지 않는 `ReportErrorMessage`로 둔다. raw `failureCode`는 노출하지 않는다.

**4. targeted test**

`ReportListViewModelTests`를 실행한다.

---

## Task 6: 리포트 목록·필터 UI와 기존 navigation 통합

**신규 파일:**

- `frontend/ChamChamCham/ChamChamCham/Features/Report/Presentation/Views/ReportListView.swift`
- `frontend/ChamChamCham/ChamChamCham/Features/Report/Presentation/Views/ReportFilterSheets.swift`
- `frontend/ChamChamCham/ChamChamCham/Features/Report/Presentation/Views/ReportListCard.swift`
- `frontend/ChamChamCham/ChamChamCham/Features/Report/Presentation/ReportRoute.swift`
- `frontend/ChamChamCham/ChamChamChamTests/ReportListPresentationTests.swift`

**수정 파일:**

- `frontend/ChamChamCham/ChamChamCham/App/MainTabView.swift`
- `frontend/ChamChamCham/ChamChamCham/Features/Record/Presentation/Views/RecordListView.swift`

**1. pure presentation 실패 테스트 작성**

- filter chip title/selected state
- card title, crop/farm badge, period caption, thumbnail fallback
- active/completed period formatting
- Figma 주요 layout constants와 SE용 adaptive rule

Route는 기존 record UUID와 섞여도 충돌하지 않는 별도 value type으로 둔다.

```swift
enum ReportRoute: Hashable {
    case detail(WorkReportKey)
    case recordHistory(WorkReportKey)
}
```

**2. 목록 content 구현**

`ReportListView`는 top app bar/tab/navigation stack을 만들지 않는다.

- filter row: `AppChip` 세 개
- sheet 선택: `AppSelectItem`을 사용한 단일 선택과 완료 `AppButton`
- card: `AppCard(.large)` + feature-local AsyncImage thumbnail
- states: skeleton/progress, empty, full error/retry, cached banner, refresh, loading more
- report 탭에는 기록 작성 FAB를 표시하지 않음

**3. navigation 최소 통합**

- `RecordListView.path`를 `[UUID]`에서 `NavigationPath`로 바꾼다.
- 기존 record row와 compose 완료는 계속 UUID를 append하고 기존 UUID destination을 유지한다.
- report card만 `ReportRoute.detail(WorkReportKey)`를 append한다.
- `reportPlaceholder`를 `ReportListView`로 교체한다.
- `RecordListView`가 `ReportListViewModel`을 `@State`로 소유해 기록↔리포트 탭 전환에도 filter,
  page, load state가 유지되게 한다.
- 이때 이미 주입된 `RecordRepository.fetchFarmCrops()`를 filter option loader로 넘겨 기존
  `/members/me/farm-crops` endpoint와 DTO를 재사용한다.
- `MainTabView`는 `container.makeReportRepository()`를 추가 주입한다.
- 기록 탭 ViewModel, filter sheet, detail, compose 코드는 수정하지 않는다.

**4. targeted test 및 회귀 test**

```bash
cd frontend/ChamChamCham
xcodebuild -project ChamChamCham.xcodeproj -scheme ChamChamCham \
  -destination 'platform=iOS Simulator,name=iPhone 17,OS=26.0' \
  -derivedDataPath /private/tmp/ChamChamCham-derived \
  -only-testing:ChamChamChamTests/ReportListPresentationTests \
  -only-testing:ChamChamChamTests/ReportListViewModelTests \
  -only-testing:ChamChamChamTests/RecordDetailLabelsTests test
```

**5. 커밋**

`feat(report): 리포트 목록과 필터 연결`

---

## Task 7: 작업별 표시 모델과 그래프

**신규 파일:**

- `frontend/ChamChamCham/ChamChamCham/Features/Report/Presentation/Models/ReportPresentationModels.swift`
- `frontend/ChamChamCham/ChamChamCham/Features/Report/Presentation/Models/ReportChartModel.swift`
- `frontend/ChamChamCham/ChamChamCham/Features/Report/Presentation/Views/ReportChartCard.swift`
- `frontend/ChamChamCham/ChamChamChamTests/ReportPresentationModelTests.swift`
- `frontend/ChamChamCham/ChamChamChamTests/ReportChartModelTests.swift`

**수정 파일:**

- `frontend/ChamChamCham/ChamChamCham/Core/DesignSystem/Foundation/Color+App.swift`
- `frontend/ChamChamCham/ChamChamChamTests/DesignColorTokenTests.swift`

**1. 작업별 표시 실패 테스트 작성**

여덟 work type에 대해 metric/section mapping을 고정한다.

- 심기: 횟수, 단위별 심은 양, propagation
- 물주기: 횟수, 최빈 물 양, amount/method distribution
- 비료: 횟수, 총 kg, method/material count/material amount
- 병해충: 횟수, 단위별 농약량, 살포 L, category/amount/target
- 잡초: 횟수, method
- 가지·순 정리: 횟수, graph 없음
- 수확: 횟수, 총 kg, medicinal part, growth month
- 기타: 횟수, graph 없음

서로 다른 단위를 합산하지 않고 값이 없는 metric/section은 숨긴다.

**2. chart normalization 실패 테스트 작성**

- 0 이하 제거
- 값 내림차순 + code/label tie-break
- 1~3개 stacked bar, 4~6개 semi donut
- 6개 초과는 상위 5개 + `기타`
- 실제 value 합으로 fraction 계산
- 접힘 primary label/value, 펼침 전체 legend
- 빈 입력은 chart card 미생성

**3. 승인된 chart 색 추가**

`Color.Chart`에 primary `#38C284`, green300 `#A5E9B1`, yellow `#F7DC11`, lime
`#C8F468`, turquoise `#81DAD8`, blue `#B1CBDF`만 추가하고 hex test를 먼저 만든다.

**4. graph view 구현**

- 1~3개: Swift Charts `BarMark`를 한 행에 stack하고 rounded clip
- 4~6개: SwiftUI `Shape`/`Canvas` arc로 반원 donut
- header `AppIconView(.asset("keyboard_arrow_down/up"))`
- expanded legend는 swatch + wrapping label + trailing value
- 동일 데이터를 accessibility summary/value로 제공
- 색만으로 의미를 전달하지 않음

**5. targeted test**

`ReportPresentationModelTests`, `ReportChartModelTests`, `DesignColorTokenTests`를 실행한다.

---

## Task 8: 상세·코칭 ViewModel과 cancellable polling

**신규 파일:**

- `frontend/ChamChamCham/ChamChamCham/Features/Report/Presentation/ViewModels/ReportDetailViewModel.swift`
- `frontend/ChamChamCham/ChamChamChamTests/ReportDetailViewModelTests.swift`

**1. 실패 테스트 작성**

- cached detail 즉시 표시 후 network refresh
- no-cache load failure
- ACTIVE는 feedback API/polling 미호출
- COMPLETED READY/FAILED/STALE/PENDING mapping
- embedded synthetic PENDING + bulk empty는 pending 유지
- READY/FAILED/STALE에서 polling 종료
- 5초 간격 최대 24회 후 pending UI를 남기고 자동 polling 종료
- 화면 이탈/scene background/filter key 변경에서 task 취소
- regenerate는 FAILED/STALE + network source에서만 허용
- regenerate 200 PENDING 즉시 반영 후 polling 재개
- offline cache에서 regenerate disabled

**2. ViewModel 구현**

`@MainActor @Observable`로 detail load와 feedback load를 분리한다. test에서는
`@Sendable (Duration) async throws -> Void` sleep closure를 주입하고 production은
`ContinuousClock.sleep`을 사용한다. status의 권위는 `status`이며 `inputPrepared`나
`failureCode`로 terminal 여부를 추론하지 않는다.

**3. targeted test**

`ReportDetailViewModelTests`를 실행한다.

---

## Task 9: 작업별 상세·코칭·기록 내역 준비 화면

**신규 파일:**

- `frontend/ChamChamCham/ChamChamCham/Features/Report/Presentation/Views/ReportDetailView.swift`
- `frontend/ChamChamCham/ChamChamCham/Features/Report/Presentation/Views/ReportMetricCard.swift`
- `frontend/ChamChamCham/ChamChamCham/Features/Report/Presentation/Views/ReportCoachingSection.swift`
- `frontend/ChamChamCham/ChamChamCham/Features/Report/Presentation/Views/ReportRecordHistoryView.swift`
- `frontend/ChamChamCham/ChamChamCham/Features/Report/Presentation/Views/ReportPreviewFixtures.swift`
- `frontend/ChamChamCham/ChamChamChamTests/ReportDetailPresentationTests.swift`

**1. presentation contract 실패 테스트 작성**

- ACTIVE 안내 문구
- PENDING/READY/FAILED/STALE/offline action state
- coaching 순서: summary → strengths → comparisons(있을 때) → improvements → actions
- 같은 역할의 여러 문장은 한 card 안에서 유지
- 기록 내역 production unavailable copy
- SE에서 고정 높이가 아닌 adaptive metric/coaching layout constants

`ReportPreviewFixtures.swift`에는 `#if DEBUG`의 deterministic stub repository와 preview state
factory를 둬서 live login/data 없이 목록 default/filtered, 여덟 work type, chart 펼침/접힘,
ACTIVE/PENDING/READY/FAILED/STALE/offline을 각각 렌더할 수 있게 한다. production path에서는 이
fixture를 참조하지 않는다.

**2. 상세 shell 구현**

- `AppTopAppBar(isDetail: true)`의 기존 asset back/more icon
- crop/farm `AppBadge`, period, workType title
- 공통 metric grid와 Task 7 graph cards
- loading/cached/error/retry 상태
- 전체 vertical `ScrollView`, Dynamic Type에서 고정 content height 금지

**3. coaching 구현**

- READY: summary + horizontal 역할 card
- ACTIVE: 재배 주기 종료 후 제공 안내
- PENDING: skeleton/progress와 수동 새로고침
- FAILED/STALE: `AppButton` regenerate
- offline: cache 표시와 regenerate disabled

**4. 기록 내역 준비 화면 구현**

상세 하단 navigation row와 독립 `ReportRecordHistoryView`를 제공하되 production에서는
`이 리포트에 포함된 기록을 불러오는 기능을 준비하고 있어요`만 표시한다. reportId가 없는
`GET /farming-records`로 기록을 추정하지 않는다. Preview fixture로만 row layout을 확인한다.

**5. route 연결 및 lifecycle**

`RecordListView`의 `.navigationDestination(for: ReportRoute.self)`에서 detail/history를 분기하고,
detail은 scene phase와 disappear를 ViewModel polling lifecycle에 전달한다.

**6. targeted test 및 커밋**

Task 7~9 tests를 실행한 뒤
`feat(report): 작업별 그래프와 리포트 코칭 구현`으로 커밋한다.

---

## Task 10: 전체 회귀·빌드·시각 검증

**로컬 전용 준비:**

빌드에 `Secrets.swift`가 없으면 `Secrets.example.swift`를 gitignored `Secrets.swift`로 복사해
placeholder 값으로 compile만 가능하게 한다. 이 파일은 절대 stage/commit하지 않는다.

**1. 정적 diff 검토**

```bash
git diff --check
git status --short
rg -n 'Color\(hex:|\.font\(\.system|TODO|FIXME|fatalError' \
  frontend/ChamChamCham/ChamChamCham/Features/Report
```

Raw foundation 값, 디자인 시스템 중복 component, 기록 코칭 변경, secrets staging이 없어야 한다.

**2. 전체 test**

```bash
cd frontend/ChamChamCham
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer \
xcodebuild -project ChamChamCham.xcodeproj -scheme ChamChamCham \
  -destination 'platform=iOS Simulator,name=iPhone 17,OS=26.0' \
  -derivedDataPath /private/tmp/ChamChamCham-derived test
```

**3. iPhone 17 build**

동일 destination에서 `build`를 실행한다.

**4. iPhone SE build**

```bash
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer \
xcodebuild -project ChamChamCham.xcodeproj -scheme ChamChamCham \
  -destination 'platform=iOS Simulator,name=iPhone SE (3rd generation),OS=18.3' \
  -derivedDataPath /private/tmp/ChamChamCham-se-derived build
```

**5. Simulator/Figma 시각 검증**

- Build iOS Apps의 SwiftUI preview/simulator browser로 deterministic preview matrix의 목록
  default/filtered, 여덟 상세, chart collapsed/expanded, coaching states를 캡처
- Figma reference와 `visual-verdict`로 비교하고 major mismatch를 수정 후 재검증
- iPhone SE 3에서 badge/date wrapping, metric grid, legend, coaching copy, retry/regenerate button,
  vertical scroll 확인
- VoiceOver label로 chart summary와 expand/collapse state 확인
- live app에서는 가능한 golden path와 online→offline cache fallback, 화면 이탈 polling 취소,
  기존 기록 목록→상세→작성 navigation을 별도로 확인

**6. 최종 상태 확인**

```bash
git status --short --branch
git log --oneline --decorate -8
```

사용자 소유 `.claude/`, `.omx/`와 gitignored `Secrets.swift`는 commit하지 않는다. 실패한 검증이
있으면 원인과 잔여 위험을 숨기지 않고 기록한다.

## 완료 기준

- 라이브 Swagger 네 operation과 endpoint-specific fixture가 일치한다.
- 리포트 목록·단일 선택 필터·cursor pagination·캐시 fallback이 동작한다.
- 여덟 작업 상세이 서버 통계만 표시한다.
- 1~3개 누적 막대, 4~6개 반원 donut, 펼침 legend가 Figma와 일치한다.
- ACTIVE/PENDING/READY/FAILED/STALE/offline coaching 상태와 재생성이 계약대로 동작한다.
- 기존 기록 목록·작성·상세 코드 동작에 회귀가 없다.
- 전체 test, iPhone 17 build, iPhone SE 3 build가 통과한다.
- Figma 시각 비교와 접근성 점검 결과가 남아 있다.
