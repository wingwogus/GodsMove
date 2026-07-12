# Figma Capture: Onboarding Step 3 - Crop Selection

- Captured at: 2026-07-11
- Source: TalkToFigma MCP `read_my_design`, `scan_text_nodes`; user-provided screenshot
- Figma nodes: `631:12568` (default), `631:13342` (selected/active), `631:13561` (scrolled sticky)
- Asset: [2026-07-11-onboarding-step-3-crop-selection-notes.png](assets/2026-07-11-onboarding-step-3-crop-selection-notes.png)
- Screenshot size: `1094 x 1716`
- Product screen name: `대표 재배지의 작물 설정하기`
- Purpose: Step 2에서 설정한 대표 재배지에 재배 작물을 연결한다.

## Evidence Limit

사용자 제공 이미지는 실제 iPhone 전체 프레임이 아니라 Figma 안의 설계 메모와 UI
조각을 캡처한 것이다. 이후 TalkToFigma MCP로 실제 선택 프레임 `631:12568`,
`631:13342`, `631:13561`을 읽어 기본 화면 구조, 선택 완료 상태, sticky scroll
상태, 텍스트를 보강했다.

`631:12568`은 미선택 default 상태이고, `631:13342`는 selected row, 5개 chip,
활성 `완료` CTA가 보이는 선택 완료 상태다. `631:13561`은 scroll 후 sticky 상태로,
검색창과 카테고리 tab이 상단에 고정된 모습을 보여준다.

다음 규칙은 screenshot, 사용자 메모, MCP selection을 합쳐 기록한다.

- 검색창과 카테고리 레이블은 scroll 시 상단에 sticky로 고정한다.
- 작물 리스트는 가나다순으로 배치한다.
- 선택된 작물은 하단 tray의 chip으로 노출하고 `x`로 제거할 수 있다.
- 선택 완료 CTA 문구는 `완료`다.

선택 최대값은 5개로 확정한다. MCP로 읽은 default/selected 프레임은 `최대 5개`와
5개 chip 상태를 보여주며, 사용자가 2026-07-11에 `5개`로 확인했다. 이전 사용자 제공
설계 노트의 `3개 이하` 표기는 폐기된 노트로 취급한다.

## Flow Position

Figma는 이 화면을 Step 3으로 둔다. Step 2가 `대표 재배지 설정하기`였으므로,
사용자 입력 흐름은 Figma 기준으로 다음 순서가 된다.

1. 기본 정보 설정
2. 대표 재배지 설정
3. 대표 재배지의 작물 설정
4. 제출 또는 완료 상태

현재 SwiftUI는 `basicProfile -> cropSelection -> farmLocation` 순서다. Figma
적용 시 `basicProfile -> farmLocation -> cropSelection`로 바꾸는 것이 맞다.
단, `BR-USER-002`에 따라 서버 저장은 여전히 마지막 완료 요청 한 번으로 처리한다.

## Captured Design Rules

### MCP-selected default frame

TalkToFigma MCP가 읽은 선택 프레임:

- Node: `631:12568`
- Name: `onboarding / step 3 재배작물 선택`
- Size: `390 x 844`
- Status bar: `390 x 54`
- Top app bar: `390 x 60`, leading back `48 x 48`, title text empty
- Progress bar: track `350 x 4`, active fill `176 x 4`
- Header area: `350 x 94`, x inset `20`
- Search bar area: `390 x 88`, search bar `370 x 56`, x inset `10`
- Content area: `390 x 532`
- Category tab bar: `390 x 56`, tab item width `104`
- List area: `390 x 464`, row height `58`
- Bottom button area: `390 x 100`, CTA `350 x 56`

MCP-selected frame text:

- Title: `재배 중인 작물 설정하기`
- Subtitle: `대표 재배지의 작물을 입력해주세요.\n작물은 최대 5개까지 선택 가능합니다.`
- Search placeholder: `작물명을 입력해주세요.`
- CTA: `완료`
- Default row placeholder: `타이틀`

MCP-selected frame은 default/empty selection 상태다. CTA fill은 `#e0e0e0`,
CTA label은 `#878787`라 비활성 상태로 해석한다.

Progress active width가 `176/350`으로 Step 2와 같은 50%다. Step 3라면 4-step
progress 기준 `264/350` 내외가 자연스럽다. 이는 별도 보완 후보로 기록한다.

### MCP-selected scrolled sticky frame

TalkToFigma MCP가 읽은 scroll 후 sticky 프레임:

- Node: `631:13561`
- Name: `onboarding / step 3 재배작물 선택`
- Size: `390 x 844`
- State: 미선택 default + scroll 후 sticky 상태
- Header: `390 x 94`, y가 default frame보다 `126pt` 위로 올라간 상태다.
- Search bar area: `390 x 88`, search bar `370 x 56`, x inset `10`, y가 default frame보다 `126pt` 위로 올라간 상태다.
- Content area: `390 x 764`, y `-2187`, default content y보다 `126pt` 위에 있다.
- Category tab bar: `390 x 56`, content 최상단에 붙어 있고, search bar 바로 아래에 유지된다.
- List area: `390 x 696`, default list보다 길어져 scroll 후 viewport를 채운다.
- Bottom CTA: `390 x 100`, disabled state, default frame과 같은 하단 고정 위치다.

이 상태는 사용자가 요청한 `검색창과 카테고리 레이블 sticky` 규칙을 구현할 때의 시각
목표다. 실제 구현에서는 top app bar/progress 아래에 sticky search 영역이 고정되고,
리스트만 그 아래에서 계속 스크롤되어야 한다. header title/subtitle은 scroll과 함께
위로 사라질 수 있다.

구현 주의:

- sticky 영역은 흰 배경을 유지해 list row가 뒤에서 비치지 않게 한다.
- sticky 영역과 list row 사이 divider/border `#f3f3f3`를 유지한다.
- bottom CTA 또는 선택 chip tray와 겹치지 않도록 list bottom inset을 별도로 둔다.
- 키보드가 열린 검색 상태에서도 sticky search와 bottom CTA가 동시에 가려지지 않게 한다.

### MCP-selected active frame

TalkToFigma MCP가 읽은 선택 완료 프레임:

- Node: `631:13342`
- Name: `onboarding / step 3 재배작물 선택`
- Size: `390 x 844`
- Header, search, category tabs, list geometry는 default frame과 동일하다.
- Progress bar active fill도 `176 x 4`로 default frame과 같다.
- Selected row: 두 번째 list row, `390 x 58`, fill `#e4f8e3`, text `#27865c`, trailing `icon/check_circle` `24 x 24`.
- Bottom selected state container: `390 x 192`, 화면 하단에 고정된다.
- Selected chip area: `350 x 72`, x inset `20`, 2줄 구성.
- Row 1 chips: 3개, each `85 x 32`, x gap `8`.
- Row 2 chips: 2개, each `85 x 32`, x gap `8`.
- Chip style: fill `#e4f8e3`, stroke `#38c284`, radius `1000`, text `#27865c`, font `Pretendard Medium 15`, close icon `24 x 24`.
- Active CTA: `350 x 56`, fill `#343434`, radius `12`, label `완료`, text `#ffffff`.

이 frame은 5개 chip을 직접 보여준다. 구현 기준은 최대 5개 선택이다.

선택 완료 상태에서는 bottom container가 list 위에 overlay된다. 실제 SwiftUI 구현에서는
리스트 하단에 bottom inset을 충분히 줘서 마지막 row가 selected chip/CTA 뒤에 가려지지
않게 해야 한다.

### Category tabs

Screenshot의 메모는 `재배 작물 상위 카테고리 tab`이다. 보이는 label은 다음 9개다.

| 순서 | Figma label | Backend code | Current backend label |
| --- | --- | --- | --- |
| 1 | 전초 | `WHOLE_HERB` | 전초 |
| 2 | 뿌리·껍질 | `ROOT_BARK` | 뿌리·껍질 |
| 3 | 뿌리줄기 | `RHIZOME` | 뿌리줄기 |
| 4 | 잎 | `LEAF` | 잎 |
| 5 | 꽃 | `FLOWER` | 꽃 |
| 6 | 열매·과실 | `FRUIT` | 열매/과실 |
| 7 | 종자 | `SEED` | 종자 |
| 8 | 줄기·가지 | `STEM_BRANCH` | 줄기/가지 |
| 9 | 기타 | `UNKNOWN` | 기타 |

캡처 텍스트는 `총 10개`라고 쓰여 있지만, 실제 보이는 label과 최신 Swagger,
backend enum은 모두 9개다. 이 차이는
[Step 3 Figma-계약 후보](2026-07-11-onboarding-step-3-figma-contract-candidate.md)에
분리해 기록한다.

현재 frontend의 client-only `인기` 카테고리는 이 캡처에 없다. 후속 Figma에서
별도 전체/인기 탭이 나오지 않으면 제거하는 쪽이 자연스럽다.

### Sticky search and category area

사용자 메모: `검색창과 카테고리 레이블은 sticky로 진행`. MCP node `631:13561`로
scroll 후 sticky 상태를 캡처했다.

구현 기준:

- 화면 header는 일반 스크롤 영역이어도 된다.
- 검색창, 카테고리 tab row, 현재 카테고리 label/header는 리스트 스크롤 시 상단에 고정한다.
- sticky 영역은 top app bar/progress와 겹치지 않아야 한다.
- header title/subtitle은 sticky 대상이 아니며 scroll과 함께 위로 사라질 수 있다.
- 검색창에 포커스가 있을 때 키보드가 리스트와 bottom tray를 가리지 않아야 한다.
- 검색은 현재 선택 카테고리 안에서 우선 필터링한다. 전체 검색이 필요하면 후속 Figma에서 확정한다.

### Crop list ordering and selected row

작물 리스트 순서 메모는 `가나다 순으로 배치해주시면 됩니다!`다.

최신 backend의 `/api/v1/crops`와 `/api/v1/crops/categories/{category}/crops`는
`name ASC, externalNo ASC` 순서로 반환한다. UI도 같은 순서를 유지한다. 실제 DB
collation 때문에 한글 가나다순이 어긋나면 client에서 `localizedStandardCompare` 등으로
보정하는 후보를 둔다.

선택된 row 조각은 다음 형태다.

- row background: 연한 초록
- text: 진한 초록
- trailing: 초록 원형 check icon
- 선택 해제 시 row는 기본 흰 배경으로 돌아가야 한다.

선택 전 row, disabled row, 검색 결과 없음 상태는 캡처에 없다.

### Bottom tray and CTA

선택 후 UI는 화면 하단에 흰색 tray가 고정되고, 위쪽 모서리가 크게 둥글다.

- 선택 chip: 연한 초록 fill, 초록 border, 초록 text, `x` remove icon
- CTA: dark fill, label `완료`
- CTA는 선택 수가 1개 이상일 때 활성화한다.
- 선택 수가 0개인 상태의 tray와 disabled CTA는 후속 캡처 또는 기존 버튼 패턴으로 보완한다.

선택 순서는 사용자가 선택한 순서를 보존하는 것이 가장 자연스럽다. chip의 `x`는
해당 작물만 선택 해제하며, 리스트의 selected row도 즉시 갱신한다.

선택 수 상한은 최대 5개다. 5개까지 chip으로 표시하고, 6번째 선택은 추가하지 않는다.

## Runtime Behavior

1. 화면 진입 시 인증된 상태에서 crop categories와 crop list를 불러온다.
2. loading 중에는 리스트 영역에 skeleton 또는 loading state를 보인다.
3. 로드 실패 시 retry를 제공하고, 이미 선택한 crop draft는 유지한다.
4. 카테고리 tab을 누르면 해당 카테고리의 가나다순 리스트를 보여준다.
5. 검색어 입력 시 sticky search 아래 리스트만 갱신한다.
6. 선택은 최대 5개까지만 허용한다.
7. 6번째 선택 시에는 선택을 추가하지 않고, 가벼운 안내를 노출한다.
8. `완료`는 다음 온보딩 상태로 이동하되, 서버 저장은 마지막 완료 요청까지 미룬다.

## API and Data Mapping

최신 deployed Swagger snapshot SHA-256:
`3cc2a1870dbc6006a9dd3591e7e1c1aee5bb188c4ac836c15d58657babdf2541`

Crop API:

- `GET /api/v1/crops`
- `GET /api/v1/crops/categories`
- `GET /api/v1/crops/categories/{category}/crops`

`CropResponse` fields:

- `id`
- `externalNo`
- `name`
- `usePartCategory`
- `usePartCategoryLabel`

`CategoryResponse` fields:

- `code`
- `label`

Current frontend `Crop` model keeps only `name` and category label. Figma 적용 시
`code + label`을 함께 보존하는 모델이 필요하다. label만으로 필터링하면 표시 문구
변경, punctuation 변경, backend label 변경에 취약하다.

`CompleteOnboardingRequest.cropIds`는 필수 배열이지만 Swagger와 backend request에는
`maxItems: 5` 또는 `@Size(max = 5)`가 없다. 최대 5개가 제품 규칙이므로 backend
계약 변경 요청 후보로 기록한다.

## Current Implementation Differences

- 현재 flow order는 crop selection이 farm location보다 먼저다. Figma Step 3 기준으로 순서를 바꿔야 한다.
- 현재 UI는 4열 chip grid다. Figma는 vertical list row와 selected row check를 보여준다.
- 현재 `selectedCategory` 기본값은 `인기`다. Figma 캡처에는 `인기` 탭이 없다.
- 현재 선택 개수 제한이 없다. Figma-selected default/active frames는 최대 5개와 5개 chip 상태를 보여준다.
- 현재 CTA는 `다음`이고 선택 수가 0이면 `작물을 선택하세요`로 바뀐다. Figma CTA는 `완료`다.
- 현재 선택된 작물 chip이 하단 tray에 모이지 않는다.
- 현재 `Crop` 모델이 category code를 버리고 label만 들고 있어 category endpoint와 정확히 연결하기 어렵다.
- 검색 결과 없음, 최대 선택 초과, 오프라인 retry 상태가 별도 디자인으로 정리되어 있지 않다.
- MCP-selected Step 3 frame의 progress fill은 Step 2와 같은 50%라 단계 진행률 보정이 필요하다.

## Missing Figma States

- 빈 선택 상태의 bottom tray와 disabled CTA
- 1개, 2개, 3개, 4개 선택 상태
- 5개 선택 상태는 `631:13342`로 캡처됨
- 6번째 선택 시 안내
- 검색어 입력 중 keyboard 상태
- 검색 결과 없음
- crop list loading
- crop list error와 retry
- category별 빈 리스트
