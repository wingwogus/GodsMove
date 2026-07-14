# Figma Capture: Onboarding Step 2 — Primary Farm Location

- Captured at: 2026-07-11
- Source: TalkToFigma MCP `get_selection`, `read_my_design`
- Figma nodes: `631:12049` (default), `631:12222` (required information filled)
- Additional evidence: user-provided composite screenshot,
  `Screenshot 2026-07-11 at 11.21.03 PM.png` (required data missing states)
- Frame size: `390 x 844`
- Purpose: 대표 재배지의 주소, 농지명, 지도 선택을 수집하는 Step 2 화면.

## Product and Flow Position

Figma는 이 화면을 Step 2로 표시한다. title은 `대표 재배지 설정하기`이며, 이는
온보딩 완료 요청으로 최초 농장 하나를 생성하는 `BR-USER-002`와 맞는다.

현재 SwiftUI는 작물 선택을 farm location보다 먼저 표시한다. Step 3 이후 캡처까지
확인한 뒤, Figma 기준으로 단계 순서를 확정한다. 이 문서는 재배지 화면 자체의
시각 구조와 동작 경계를 기록하며, 작물 선택 위치를 독단적으로 확정하지 않는다.

## Screen Structure

1. Status bar template: `54pt` — 앱에서 별도 구현하지 않는다.
2. Top app bar: `390 x 60`, leading back slot `48 x 48`, arrow icon visual `32 x 32`.
3. Progress: 좌우 inset `20`, track `350 x 4`, active fill `176 x 4` (`#38c284`) — 4단계 중 Step 2의 50%.
4. Header: 좌우 inset `20`.
5. Map area: `390 x 590`, 지도 위에 주소 검색·농지명 입력을 overlay한다.
6. Bottom CTA: `390 x 100`, inset `20`의 `350 x 56` button.

### Header

- Title: `대표 재배지 설정하기`, Pretendard SemiBold `28`, `#1a1a1a`, line height `36.4`.
- Subtitle: `재배지의 주소명과 농지명을 입력해주세요.`, Pretendard Medium `18`, `#878787`, line height `27`.

### Map presentation area

Figma의 map area는 `390 x 590` image fill placeholder다. 사용자가 명시한 대로
실제 앱에서는 이 영역을 지도 렌더링으로 대체한다.

- 지도는 화면 폭 전체를 사용한다.
- 주소 검색과 농지명 입력은 지도 상단에서 각각 `350 x 56`, 좌우 inset `20`으로 겹친다.
- 지도 오른쪽에는 흰 배경·border `#e0e0e0`·radius `8`의 세로 control `48 x 104`가 있다.
  - 확대: `48 x 48`, plus icon visual `40 x 40`.
  - 축소: `48 x 48`, minus icon visual `40 x 40`.
- 지도 gesture는 유지하고, 이 controls는 카메라 zoom in/out을 명시적으로 제공한다.

## Input and CTA States

| 상태 | 주소 검색 overlay | 농지명 overlay | CTA |
| --- | --- | --- | --- |
| Default (`631:12049`) | search icon + `주소지를 입력해주세요.`, secondary text | `농지명을 입력해주세요.`, secondary text | `다음` 비활성, `#e0e0e0` |
| Required filled (`631:12222`) | `도로명 주소 입력 완료`, primary text | `농지명 입력 완료`, primary text | `다음` 활성, `#343434` |

완료 프레임의 농지명 input 아래에 `이름은 필수로 입력해주세요.`가 남아 있지만,
field 자체는 입력 완료 상태이고 CTA도 활성이다. 이는 Step 1과 마찬가지로 입력
컴포넌트의 validation message slot이 mock text로 남은 것으로 취급한다. 실제 오류
문구는 해당 field가 비었거나 유효하지 않을 때만 표시한다.

### Required-data missing states

사용자가 추가로 제공한 composite screenshot은 전체 프레임 캡처가 아니라 map overlay
영역의 필수값 누락 상태만 보여준다. 세 상태는 Step 2의 시각·문구 규칙으로 반영한다.

| 상태 | 주소 검색 overlay | 농지명 overlay | 오류 문구 |
| --- | --- | --- | --- |
| 주소 입력 완료, 농지명 누락 | 정상 border, `도로명 주소 입력 완료` | red border, `농지명을 입력해주세요.` | `농지명은 필수로 입력해주세요.` |
| 주소와 농지명 모두 누락 | red border, `주소지를 입력해주세요.` | red border, `농지명을 입력해주세요.` | `주소지와 농지명은 필수로 입력해주세요.` |
| 주소 누락, 농지명 입력 완료 | red border, `주소지를 입력해주세요.` | 정상 border, `농지명 입력 완료` | `주소지는 필수로 입력해주세요.` |

오류 색상은 screenshot 기준 red `#ff3333` 계열이며, border와 field 아래 문구가 함께
사용된다. 실제 구현에서는 red border만으로 오류를 전달하지 않고, 위 문구를 함께
노출한다. CTA는 주소·농지명 중 하나라도 누락되면 비활성 상태로 유지한다.

## Runtime Behavior Behind the Figma Map

Figma placeholder를 다음 기존 흐름으로 대체한다.

1. 주소 overlay 탭 → JUSO 주소 검색 sheet를 연다.
2. 사용자가 도로명 주소를 선택하면 V-World로 좌표를 해석하고 map camera를 이동한다.
3. 해당 좌표의 지적도 필지를 조회해 polygon, PNU, 지목, 면적을 표시한다.
4. 사용자는 지도 탭으로 필지를 다시 선택할 수 있다.
5. 필지를 찾지 못하면 면적 직접 입력 fallback을 보인다.
6. `다음`은 농지명, 선택 주소, 유효한 좌표가 확보되고, 필지 또는 유효한 수동 면적이 있을 때만 활성화한다.

최신 deployed Swagger의 `FarmRequest.required`에는 `name`, `roadAddress`,
`latitude`, `longitude`, `dataSource`, `areaIsManualEntry`, `boundaryCoordinates`,
`cropIds`가 포함된다. 따라서 Figma가 주소/농지명만 보여도 좌표·데이터 출처를
수집하지 않은 채 다음 단계로 보내면 안 된다.

### Offline and failure states

이 단계는 네트워크가 필요한 JUSO/V-World 조회를 사용한다. 농가 네트워크 환경에서
다음을 Figma 외 runtime state로 제공한다.

- 주소 검색 loading, no-result, network error, retry
- 좌표 resolving 및 parcel loading
- 지도/지적도 조회 실패 후 주소 검색 재시도
- parcel not found 후 수동 면적 입력·양수 검증
- 기존 초안과 선택된 주소/농지명 보존

오프라인에서 임시로 완료된 것처럼 다음 단계나 Home으로 보내지 않는다. 입력 초안을
보존하고 연결 복구 후 같은 조회를 재시도한다.

## Data Mapping and Open Decision

| Figma field | Current draft/API field | Status |
| --- | --- | --- |
| 도로명 주소 | `farmRoadAddress` → `FarmRequest.roadAddress` | 직접 매핑 |
| 농지명 | `farmName` → `FarmRequest.name` | 제품 의미 확인 필요 |
| 지도 선택 좌표 | `farmLatitude`, `farmLongitude` | backend required |
| 선택 필지 | PNU, 지목, 면적, boundary data source | 기존 위치 해석 흐름 유지 |

`농지명`이 농장/재배지의 표시 이름을 뜻한다면 현재 `farmName`에 매핑한다. 만약
필지의 공식 명칭을 뜻한다면 `FarmRequest.name`과 다른 데이터이므로 backend 변경
요청 후보로 분리해야 한다.

## Current Implementation Differences

- 현재 `FarmLocationView`는 주소 버튼을 누르면 sheet를 열고, 좌표가 해석된 뒤에만
  `300pt` 높이 map을 보인다. Figma는 full-width `590pt` map과 overlay controls를 사용한다.
- 현재 화면 문구는 `농장 이름과 위치를 입력해주세요`; Figma는 `대표 재배지`와 `농지명`을 사용한다.
- 현재 flow order는 `basicProfile → cropSelection → farmLocation`; Figma는 재배지 화면을 Step 2로 표기한다.
- 현재 화면은 필수값 누락 시 CTA 비활성 중심이고, Figma처럼 주소/농지명별 red border와
  조합별 오류 문구를 분리해서 보여주지는 않는다.

UI 구현에서는 map/overlay/zoom/back/progress를 Figma에 맞추고, 주소·필지·수동
면적의 실제 domain flow와 Swagger 필수값은 유지한다.

## Missing Figma States

- 주소 검색 결과 sheet
- 주소 선택 후 좌표 resolving
- 필지 polygon 선택 및 정보 card
- parcel not found + 수동 면적 입력
- 지도/주소 API 오류·retry
- zoom interaction state
- 키보드가 농지명 input을 가리는 상태
