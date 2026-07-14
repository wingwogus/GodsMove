# 텍스트로 기록하기 / default

- Captured: 2026-07-13
- Source MCP calls: `mcp__TalkToFigma__get_selection` → `mcp__TalkToFigma__read_my_design` → `mcp__TalkToFigma__export_node_as_image`
- Figma node ID: `1247:23161`
- Frame name: `텍스트로 기록하기 / default`
- Frame size: 390 × **990** (스크롤 폼 — 뷰포트보다 큼)
- State: [FAB 스피드다이얼](2026-07-13-record-main-record-button-tapped.md)에서 "텍스트로
  기록하기" 선택 시 진입하는 영농일지 **텍스트 작성 폼**의 기본(빈) 상태. BR-RECORD-001/
  003/004, POST `/api/v1/farming-records` (`SaveRecordRequest`)에 대응.

## PNG

파일 저장 불가(인라인만). 구조·색상·타이포는 `read_my_design` 값으로 정밀 기록.

## 레이아웃 (top → bottom)

| 순서 | 노드 | 설명 |
|---|---|---|
| 1 | Status Bar (`1247:23185`) | 시스템 상태바 |
| 2 | top-app-bar (`1247:23184`) | leading `arrow_back_ios_new` + 중앙 타이틀 "기록하기" (detail 레이아웃) |
| 3 | content (`1257:26113`) | 폼 본문 (스크롤). content-1 / divider / content-2 / divider / image-uploader |
| 4 | bottom-button (`1247:23182`) | 하단 고정 `완료` 버튼 (비활성 상태) |

top-app-bar 타이틀 "기록하기": Pretendard **SemiBold 28**, letterSpacing -0.28, lineHeight 36.4,
color `#242428`. leading 아이콘 `icon/arrow_back_ios_new` 32×32 (48 tap target). trailing 비어있음.

## content-1 — 기본 정보 (`1257:26114`)

### 날짜 + 날씨 행 (`date` `1257:26115`, 350×88)
- **date-input** (`1257:26116`, 169 wide): 라벨 "기본 정보" + **필수 `*`**(`#ef4444`).
  - field: cornerRadius 8, border `#e0e0e0`, 값 "2026-12-12" (Pretendard Medium 18, `#1a1a1a`),
    trailing `icon/calendar_month` 24×24.
  - 헬퍼 슬롯 "메시지를 전달합니다." (Pretendard Medium 15, `#878787`).
- **weather** (`1257:26117`, 169 wide, 우측): **읽기 전용 표시 필드**. fill `#f9fcf3`
  (secondary-subtle 연한 초록), border `#e0e0e0`, cornerRadius 8. 텍스트 "맑음" + "12~16°"
  (Pretendard Medium 18, `#4f4f4f`). 라벨 없음(날짜 라벨에 묶임), 헬퍼 없음.
  - → 날씨는 **날짜 기준 자동 조회 값**으로 추정 (BR-RECORD-002 자동 입력). 입력 아닌 표시.

### 진행 작물 행 (`farm` `1257:26120`, 350×88)
- **drop-down** (`1257:26121`, 169 wide): 라벨 "진행 작물" + **필수 `*`**. placeholder
  "농지 선택" (`#878787`), trailing `keyboard_arrow_down`. 헬퍼 슬롯.
- **drop-down** (`1257:26122`, 169 wide, 우측): 라벨 없음. placeholder "작물 선택". 헬퍼 슬롯.
  - → **농지(farm) 선택 → 작물(crop) 선택** 2단계 드롭다운. `SaveRecordRequest.farmId` +
    `cropId`에 대응. 농지 먼저 고르면 그 농지의 작물로 좁혀지는 종속 관계로 추정.

## divider (`1257:26123`, 390×2, `#f3f3f3`)

## content-2 — 작업 내용 (`1257:26124`)

### work (`1257:26125`, 350×296)
- **drop-down** (`1257:26126`, 350 wide): 라벨 "작업 내용" + **필수 `*`**. placeholder
  "진행한 작업을 선택해주세요." trailing `keyboard_arrow_down`. 헬퍼 슬롯.
  - → **workType 선택** 드롭다운 (심기/물주기/… 8종, `SaveRecordRequest.workType`).
- **text-area** (`1257:26127`, 350×200): 라벨 없음. field cornerRadius **12**, border `#e0e0e0`.
  - placeholder "작업 내용을 작성해주세요." (Pretendard Medium 18, `#878787`).
  - 우하단 카운터 "**0/500**" (Pretendard Medium 15, `#878787`) → `memo` 최대 500자.
  - 헬퍼 슬롯.
  - → `SaveRecordRequest.memo` (required)에 대응.

## divider (`1257:26128`)

## image-uploader (`1257:26129`, 390×132)
- 제목 "사진 첨부하기" (Pretendard Medium 16, `#1a1a1a`).
- **image-uploader 슬롯** (`1257:26132`, 96×96): fill `#f3f3f3`, cornerRadius 8, 내부
  `icon/photo_camera` 32×32 + 카운터 "**0/5**" (`#4f4f4f`) → 사진 최대 5장.
  - → `SaveRecordRequest.mediaIds` (미디어 업로드 후 id 목록). BR-RECORD-009 (사진은 보조 정보).

## bottom-button (`1247:23182`, 390×100, 상단 border `#f3f3f3`)
- **완료** 버튼: 350×56, cornerRadius 12, fill `#e0e0e0`(disabled), 텍스트 "완료"
  Pretendard Medium 18 color `#878787`(disabled). → **필수값 미입력 시 비활성** 상태.
  활성 시 primary/bold로 전환 추정 (다른 화면 완료 버튼 대조 필요).

## 기존 디자인 시스템 컴포넌트 매핑 후보

- top-app-bar(detail, back+title) → `AppTopAppBar` detail 레이아웃 (leading `chevron.left`).
- date-input → `AppDateField` (라벨/필수/헬퍼/캘린더 아이콘 — 이미 일치).
- weather 표시 필드 → 읽기 전용 표시. `AppFieldContainer` 기반 커스텀 or 단순 표시 박스
  (입력 컴포넌트 아님, fill `#f9fcf3`). DS에 read-only 필드 없음 → 화면 로컬 처리 후보.
- drop-down(농지/작물/작업) → `AppDropdown` (라벨/필수/placeholder/chevron/헬퍼/에러 — 일치).
- text-area → `AppTextEditor` (cornerRadius 12, placeholder, n/n 카운터 — 일치, 카운터 500).
- image-uploader 슬롯 → `AppImageUploadSlot` (0/5 카운터, photo_camera). 다중 목록은 화면에서 구성.
- 완료 버튼 → `AppButton` (disabled/enabled).

## API 매핑 (`SaveRecordRequest`, POST `/farming-records`)

| 폼 필드 | 요청 필드 | 비고 |
|---|---|---|
| 날짜 | `workedAt` (date-time) | 필수 |
| 날씨(맑음/12~16°) | `weatherCondition` + `weatherTemperature` | **자동 조회 값 추정** — 표시 전용. temp는 단일 int인데 UI는 12~16° 범위 → 확인 필요 |
| 농지 | `farmId` (uuid) | 필수 |
| 작물 | `cropId` (uuid) | 필수 |
| 작업 내용(드롭다운) | `workType` (enum 8종) | 필수 |
| 작업 내용(텍스트) | `memo` (≤500자) | 필수 |
| 사진(0/5) | `mediaIds` ([uuid]) | 필수 필드지만 빈 배열 허용 추정 |

- **작업별 상세 입력(planting/watering/fertilizing/pestControl/weeding/harvest)**: `SaveRecordRequest`에
  workType별 상세 객체가 있으나 **이 default 프레임엔 없음**. workType 선택 후 나타나는
  동적 상세 폼(별도 상태/화면)으로 추정 — BR-RECORD-004(작업별 입력). **미캡처, 별도 필요**.

## 미결 질문 / 확인 필요

- **날씨 자동 조회**: 날짜+농지 위치 기반 자동 조회인지, 조회 실패/과거 날짜 시 처리.
  `weatherTemperature`는 단일 int인데 UI "12~16°"는 최저~최고 범위 → API 필드 불일치 확인.
- **farm→crop 종속**: 농지 선택 시 작물 목록이 그 농지 것으로 필터되는지.
- **workType별 상세 폼**: 작업 선택 후 상세 입력(예: 심기→번식방법, 수확→수확량)이 이 화면에
  펼쳐지는지 별도 화면인지 — default 프레임엔 없음.
- **완료 활성 조건**: 필수값(날짜/농지/작물/작업/메모) 충족 시 활성. 사진 필수 여부.
- **BR-RECORD-003 저장 조건**, **BR-STATE-001 / BR-VOICE-*** (음성 화면과 상태 공유 여부).
- top-app-bar trailing 비어있음(임시저장/삭제 없음) 확인.
