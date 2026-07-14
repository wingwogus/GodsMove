# 영농기록 상세(결과 상세) 구현 계획서

- 작성일: 2026-07-14
- 범위: 영농기록 리스트 → 한 건 탭 → **결과 상세(읽기)** 화면. 수정/삭제 진입점(⋮) 포함.
- 선행 문서: [상세 캡처 + workType 작업정보](2026-07-14-record-detail-planting-seed.md),
  [백엔드 충돌 트래킹](2026-07-13-record-backend-conflicts.md), [작성 구현 계획](2026-07-13-record-implementation-plan.md).
- Swagger 근거: `docs/swagger/openapi.json`(스냅샷 2026-07-12). 이 세션에서 `sync_swagger_spec.py` 재동기화는
  배포 서버 이슈로 미실행 — 스냅샷 기준. 배포 재동기화 후 enum 재확인 필요.

---

## 구현 완료 (2026-07-14, 빌드·테스트 통과)

**상세 읽기(RecordDetailView) 구현 완료** — 리스트 row 탭 → push. 스코프 1번(읽기 전용) 착수분.

- 신규: `Domain/RecordDetailModels`, `Data/DTOs/RecordDetailDTOs`(배포 shape + **관용 매핑** `RecordDetailLabels`),
  `Presentation/ViewModels/RecordDetailViewModel`(loading/loaded/failed), `Presentation/Views/RecordDetailView`.
- 수정: `RecordEndpoint`에 `.recordDetail(id:)`, `RecordRepository`에 `fetchDetail(id:)`,
  `RecordListView`에 `NavigationStack` + row `NavigationLink` + `navigationDestination`(상세 push, 탭바 숨김).
- 신규 DS 컴포넌트 0(전부 기존 토큰/컴포넌트 재사용). 색·타이포는 `Color.Text/Object/Border` + `AppTypography` 매핑, raw 0.
- **AI 코칭 = placeholder 카드**(`Color.Object.secondarySubtle`), 실데이터 미연결(C-18).
- 사진 0장 시 섹션 숨김, 로딩/에러/재시도 상태 구현.
- 테스트: `RecordDetailLabelsTests`(12) — workType 8종 매핑 + 배포/dev enum 관용(SPRINKLER/PURCHASED/KG) +
  미입력 숨김 + 미상 fallback. 전부 통과. clean build 성공(iPhone 17).

**삭제 구현 완료(2026-07-14, iOS 네이티브 — 별도 UI 없음)**: ⋮ → `confirmationDialog`(삭제) → 삭제 확인
`confirmationDialog` → `DELETE /farming-records/{id}` → pop + 리스트 `reload()`. 실패 시 `alert`, 진행 중 오버레이.
`RecordEndpoint.deleteRecord` + `RecordRepository.deleteRecord`(EmptyDTO) 추가.

**작성 완료 → 상세 이동 + 토스트(2026-07-14)**: `RecordComposeView` 성공 시 생성된 recordId를
`RecordListView`의 `NavigationStack(path:)`에 push → 방금 만든 기록 상세로 이동. 동시에 하단 토스트
"영농 기록 작성이 완료되었습니다."(Figma `toast-bar` node `1520:22391`: `Object.bold` 바 + `check_circle`
흰 아이콘 + 흰 텍스트, r12, 2초 자동 소멸) 표시 + 리스트 `reload()`. 토스트는 feature-local
`RecordToastBar`/`.recordToast(message:)` — 2번째 피처가 쓰면 DS 승격.

**수정(edit) 보류(2026-07-14, 사용자 확정)**: 상세 응답에 media id가 없어 편집 시 기존 사진 보존 불가
(→ [C-19](2026-07-13-record-backend-conflicts.md), 데이터 손실). 백엔드가 상세 응답에 media id를 반환하거나
PATCH 부분 업데이트를 지원한 뒤 구현. 그때까지 ⋮ 메뉴는 **삭제만** 노출.

- **미구현(후속)**: 수정(C-19 해소 후), AI 코칭 실데이터(C-18), 오프라인 캐시. 런타임 시각 QA는 인증 게이트 뒤 → 로그인 세션 필요.

---

## 1. API 준비도 (배포 Swagger 기준)

| 동작 | 엔드포인트 | 준비 | 비고 |
|---|---|---|---|
| 상세 조회 | `GET /api/v1/farming-records/{recordId}` → `RecordDetailResponse` | ✅ 배포됨 | 공통 + workType 상세 객체 + `imageUrls` |
| 수정 | `PATCH /api/v1/farming-records/{recordId}` (`SaveRecordRequest`) | ✅ 배포됨 | **작성 폼(RecordComposeView) 재사용** |
| 삭제 | `DELETE /api/v1/farming-records/{recordId}` | ✅ 배포됨 | ⋮ 메뉴 → 확인 다이얼로그 |
| **AI 코칭("참참참의 코칭")** | **없음** | ❌ **API 부재** | 상세 응답에 필드 없음. 별도 코칭/AI 엔드포인트도 없음 |

### `RecordDetailResponse` 필드 (배포)

- 공통: `id, farmId, farmName, cropId, cropName, workType(8종 enum), workedAt, memo,
  weatherCondition(string), weatherTemperature(int), imageUrls(string[]), createdAt, updatedAt`.
- workType 상세(레코드당 1개만 채워짐):
  - `planting`: `propagationMethod[SEED,CUTTING,GRAFTING,LAYERING,DIVISION,TISSUE_CULTURE]`,
    `seedAmount`+`seedAmountUnit[KG,G]`, `seedlingCount`+`seedlingUnit[JU]`
  - `watering`: `irrigationAmount[LOW,NORMAL,SUFFICIENT]`, `irrigationMethod[DRIP,SPRINKLER,SPRAYING,MANUAL]`
  - `fertilizing`: `materialName`, `amount`+`amountUnit[KG]`, `applicationMethod[SOIL,FOLIAR]`
  - `pestControl`: `pesticideName(string)`, `pestTarget(string)`, `pesticideAmount`+`pesticideAmountUnit[ML,G]`,
    `totalSprayAmount`+`totalSprayAmountUnit[L]`
  - `weeding`: `weedingMethod[HAND,MACHINE,MULCHING,HERBICIDE]`
  - `harvest`: `harvestAmount`+`amountUnknown`, `growthPeriod`+`growthPeriodUnit[YEAR,MONTH]`,
    `harvestSource[CULTIVATED,FORAGED]`, `medicinalPart[9종]`
  - `pruning`/`etc`: 상세 객체 없음

## 2. ⚠️ 계약 불일치 — 상세 응답(읽기, 배포) ↔ compose(dev 계약)

상세 **읽기** 응답은 **구 배포 계약**이라, 이미 dev 계약으로 선구현한 `RecordComposeModels`와 enum이 다르다:

| 필드 | 상세 응답(배포) | compose(dev, 이미 구현) |
|---|---|---|
| 심기 방법 표현 | `propagationMethod`에 **SEED 포함**(별도 방법 필드 없음) | `plantingMethod[SEED,SEEDLING]` + `propagationMethod`(SEED 제거, **PURCHASED 추가**) |
| 물주기 진행방식 | `[DRIP,SPRINKLER,SPRAYING,MANUAL]` | `[DRIP,SPRAYING,ETC]` |
| 비료 단위 | `[KG]`만 | `[G,ML]` |

**대응 원칙(권장): 디코딩 관용(tolerant) 매핑.**
- 상세 응답 enum은 **raw string으로 받고**, 표시 라벨은 **배포·dev 값을 모두 커버하는 룩업**으로 변환.
  (예: `propagationMethod == SEED` → "심기 방법: 씨앗 심기"; 그 외 → "심기 방법: 모종 심기" + 번식법 라벨.
  `PURCHASED`도 미리 라벨 매핑에 포함해 dev 배포 후 자동 대응.)
- 알 수 없는 enum 값이 와도 크래시하지 않게 `default`/`unknown` 라벨 처리.
- 이렇게 하면 **읽기 경로는 오늘(배포 계약)도 동작**하고, dev 배포 후에도 깨지지 않는다.
- compose의 dev-계약 enum을 상세 디코딩에 직접 재사용하지 말 것(배포 값 SPRINKLER/MANUAL/KG 디코딩 실패 위험).

## 3. AI 코칭("참참참의 코칭") — API 부재 처리

Figma엔 성공 카드만 있으나 **배포 백엔드에 데이터 소스가 없다**(→ 충돌 [C-18](2026-07-13-record-backend-conflicts.md)).
AGENTS "API unavailable → 지어내지 말고 placeholder/omit" 규칙 적용. 옵션:

- **(권장) v1에서 섹션 생략** + 코드에 백엔드 의존성 주석. 응답에 코칭 필드가 생기면 그때 섹션 추가.
- 대안: "코칭 준비 중" 비활성 placeholder 카드(디자인 확정 필요). 빈/실패 상태가 스펙에 없어 근거 부족.

→ **결정(2026-07-14, 사용자 확정)**: **(b) placeholder 카드**로 구현. Figma 코칭 카드 프레임(`#f9fcf3` r12)을
재사용하되 내용은 "코칭 준비 중" 안내로 렌더(실데이터 미연결). 백엔드 코칭 제공 방식 확정 시 실데이터로 교체.
빈/실패 상태 디자인이 스펙에 없어 placeholder 문구/톤은 디자인 시스템 기준으로 최소 구성.

## 4. 화면 / 네비게이션

```
RecordListView
 └─ row 탭 → RecordDetailView(recordId)   [push]
     ├ top-app-bar: 뒤로 + ⋮(more_vert)
     │   └ ⋮ → 액션시트: [수정] → RecordComposeView(편집 모드, PATCH)
     │                   [삭제] → 확인 다이얼로그 → DELETE → pop + 리스트 갱신
     ├ 제목(workType 라벨) + 날짜·날씨 칩
     ├ 메모 본문
     ├ 작업 정보 카드 (농지-작물 + workType별 항목, 미입력 행 숨김)
     ├ 작업 사진 (imageUrls 가로 스크롤; 0장이면 섹션 숨김 — 확인)
     └ [AI 코칭: v1 생략]
```

- **수정/삭제 플로우는 Figma 미캡처** — ⋮ 탭 이후(액션시트/편집 화면/삭제 확인) 디자인 미확보.
  본 계획은 **상세 읽기(RecordDetailView)를 먼저 확정**하고, 수정/삭제 UI는 캡처 후 확정.
  삭제 확인 다이얼로그는 마이페이지 삭제 패턴 재사용.
- 편집: `PATCH`가 `SaveRecordRequest`(작성과 동일)라 `RecordComposeView`에 편집 모드(초기값 주입 + PATCH 제출)
  추가로 재사용. 단 compose의 dev-계약 쓰기 필드는 배포 전 제출 실패 가능(작성 계획의 caveat 동일 적용).

## 5. 상태 모델 (RecordDetailViewModel, @Observable)

```
enum LoadState { idle, loading, loaded(RecordDetail), failed(RecordErrorMessage) }
recordId: UUID
state: LoadState
// 삭제
isDeleting: Bool / deleteConfirm 표시
```

- 진입 시 `GET {recordId}` → `RecordDetail`(도메인 모델, 배포 응답을 관용 매핑) 로드.
- 로딩/에러/재시도 상태 필수(Figma는 성공만). 에러는 기존 `RecordErrorMessage` 재사용.
- 삭제 성공 시 리스트로 pop + 리스트 재조회(또는 로컬 제거).

## 6. 데이터 계층

- 신규 도메인 모델 `RecordDetail`(공통 + `WorkDetail` enum: planting/watering/.../none) — 표시 라벨 포함.
- 신규 DTO `RecordDetailResponse`(배포 shape, raw enum string) + `toDomain()` 관용 매핑.
- `RecordEndpoint`에 `.detail(id)`, `.delete(id)`(기존 존재 확인), `.update(id, body)` 추가.
- `RecordRepository`에 `fetchDetail(id)`, `delete(id)`, `update(id, request)` 추가.
- 오프라인: 상세는 읽기 → 현 리스트와 동일하게 네트워크 조회(캐시는 후속). 삭제/수정은 네트워크.

## 7. 디자인 시스템 매핑 (기존 재사용 우선)

| UI | 컴포넌트 |
|---|---|
| top-app-bar(뒤로+⋮) | `AppTopAppBar`(detail, trailing more_vert) |
| 날짜·날씨 칩 | 리스트 caption과 동일 소스 → 공통 표시 요소(로컬 or 기존 재사용 확인) |
| 작업 정보 카드(라벨-값 반복) | 피처 로컬 카드(반복 확인되면 DS 승격 후보) |
| 작업 사진 가로 스크롤 | `ScrollView(.horizontal)` + 144² 썸네일(r8) |
| 삭제 확인 | 기존 다이얼로그 패턴 재사용 |

- 색 `#4f4f4f`(헤딩/값), `#242428`(top-app-bar) → `Color.Text` 토큰 매핑 확인, raw 추가 금지.
- 코칭 카드(`#f9fcf3`/badge `#e6f7bf`·`#27865c`)는 v1 생략이면 미구현.

## 8. SE 2·3 레이아웃

- 상세는 긴 스크롤(1135pt+). `ScrollView` 세로. 작업 사진 가로 스크롤 유지.
- top-app-bar 고정, 본문만 스크롤. ⋮ 액션시트는 시스템 표준.

## 9. 권장 구현 순서 (사용자 확정 스코프 = 1번부터, 상세 읽기 먼저)

1. **[이번 구현] 상세 읽기(RecordDetailView + VM + Detail DTO 관용 매핑)** — 리스트 row 탭 연결.
   로딩/에러/재시도. 작업 정보 workType 8종 라벨 매핑. AI 코칭은 **placeholder 카드**.
   ⋮ 메뉴는 렌더하되 inert(수정/삭제 캡처 전까지). (배포 API로 오늘 동작 가능)
2. **[완료] 삭제**: ⋮ → 네이티브 확인 다이얼로그 → `DELETE` → pop + 리스트 갱신. (별도 UI 없음)
3. **수정 (보류)**: `RecordComposeView` 편집 모드(초기값 + `PATCH`) 예정이나, **C-19(사진 보존 불가)로 보류**.
   백엔드가 상세 응답에 media id 반환 or PATCH 부분 업데이트 지원 후 착수. dev-계약 쓰기 caveat도 동일 적용.
4. **AI 코칭**: 백엔드 코칭 필드/생성 확정 후 placeholder → 실데이터 교체.

## 10. 착수 전 확정 필요

- **AI 코칭**: v1 생략 vs placeholder + 백엔드 코칭 API 유무/형태.
- **수정/삭제 UI**: ⋮ 액션시트·삭제 확인·편집 진입 디자인 캡처.
- **작업 사진 0장**: 섹션 숨김 여부.
- 상세 응답 enum 관용 매핑 범위(배포 SPRINKLER/MANUAL/KG ↔ dev 값 동시 지원) 확인.
- dev 배포 + `sync_swagger_spec.py` 재동기화 후 상세 응답 enum 재검증.
