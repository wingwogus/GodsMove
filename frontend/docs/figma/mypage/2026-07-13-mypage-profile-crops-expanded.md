# Figma Capture: My Page / Profile Main / Crops Expanded (전체 내용 공개)

- Captured at: `2026-07-13 KST`
- Source: TalkToFigma MCP `join_channel(chamchamcham)`, `get_selection`,
  `read_my_design`
- Figma node: `1247:17757`
- Frame name: `프로필 메인/ 작물 뱃지 탭 - 전체 내용 공개`
- Frame size: `390 x 1321`
- Capture state: 프로필 카드의 작물 뱃지를 `외 n종` 요약 없이 전체(8개)를 두 줄로
  펼쳐 보여주는 상태. 나머지 영역(상단바/탭/필터/리스트/하단 내비)은
  `프로필 메인 / default`(`1247:17727`)와 동일.
- PNG export: `export_node_as_image(PNG, scale 2)` 호출은 성공했고 렌더 결과를
  육안 검증함. 단, 이 클라이언트(Claude Code)에서는 export 결과가 인라인
  이미지로만 반환되어 디스크 파일로 저장되지 않음. 필요 시 Figma에서 수동
  Export(2x)로 `assets/`에 저장.

## Difference From Default (`1247:17727`)

이 상태는 default 캡처와 **작물 뱃지 영역만** 다르다. 나머지는 동일하므로
default 문서를 기준으로 하고 아래 델타만 반영한다.

| Element | default (`1247:17727`) | crops-expanded (`1247:17757`) |
|---|---|---|
| Profile card height | `271` | `311` |
| Products block (`products`) | `310 x 32`, 한 줄 | `310 x 72`, 두 줄 |
| Crop badges | 3개 + `외 n종` 요약 라벨 | 8개 전부 노출, 요약 라벨 없음 |
| Badge row 1 | — | 5개 (`264` wide: 5×48 + 4×6 gap) |
| Badge row 2 | — | 3개 (`156` wide: 3×48 + 2×6 gap) |
| Row gap | — | `8` (32 + 8 + 32 = 72) |

카드 높이(`271` → `311`)는 뱃지 2행(72) 추가분(≈40)을 흡수한 결과다.
뱃지 블록은 카드 안에서 가로 중앙 정렬(row1 x=-840, row2 x=-786로 중앙 맞춤).

## Crop Badge Measurements

| Element | Value |
|---|---:|
| Crop badge | `48 x 32`, radius `8` |
| Crop badge fill | `#38c284` |
| Crop badge text | `작물`, Pretendard Medium 15 / lh 19.5 / tracking -0.3, `#ffffff` |
| Badge horizontal gap | `6` |
| Badge row gap | `8` |
| Products block | `310 x 72` |

## Crop Keyword Layout Rule (작물 키워드 배치)

출처: 디자이너 제공 배치 규칙 스펙(2026-07-13, 사용자 첨부 이미지 `작물 키워드
배치`). 이 규칙이 작물 뱃지 표시 동작의 기준(source of truth)이다.

세 가지 상태가 있다.

1. **3개 이하 (collapsed / all shown)**
   - 작물 뱃지를 전부 한 줄로 노출. `외 n종` 요약 없음.
   - 카드 높이 = 요약 모드와 동일(`271`).

2. **3개 초과 (collapsed / summarized)** — `프로필 메인 / default`(`1247:17727`)
   - 앞 3개만 뱃지로 노출 + `외 n종` 요약 라벨(`n` = 남은 작물 수).
   - 카드 높이 `271`.

3. **전체 공개 (expanded)** — 본 캡처(`1247:17757`)
   - `외 n종`을 **클릭(탭)** 하면 나머지 작물 전체를 여러 줄로 펼쳐 노출.
   - 카드 높이는 뱃지 줄 수에 따라 가변(8개 → `311`).
   - 요약 라벨은 사라진다.

전환 트리거가 명확해졌다: **`외 n종` 탭 → 전체 공개.** (프레임 이름의
`작물 뱃지 탭`은 이 탭 인터랙션을 가리킨다.) 되접기(collapse) UI는 스펙에
명시가 없으므로 구현 시 정의한다(예: 다시 탭 시 접기).

### 3개 경계 처리 주의

스펙 문구가 `3개 이하`와 `3개 이상`으로 경계(정확히 3개)에서 겹친다. 시각
예시상 의도는 "인라인 최대 3개"이므로 구현 규칙은 다음으로 확정한다:

- 작물 수 `<= 3` → 전부 노출, 요약/펼침 없음
- 작물 수 `> 3` → 앞 3개 + `외 n종`, 탭 시 전체 펼침

`n = 전체 작물 수 - 3`.

## Open Questions

- 되접기(collapse) 인터랙션 존재 여부와 UI (다시 탭? 접기 버튼?)
- 뱃지가 아주 많을 때(예: 12개 이상) 카드 높이 상한과 스크롤/줄바꿈 정책은?
- iPhone SE 2/3 폭(375)에서 한 줄에 들어가는 뱃지 개수가 이 프레임의 5개/줄과
  달라질 수 있음 — 구현 시 유동 배치(wrap)로 처리.
- 작물 데이터 소스(정렬 순서, 최대 개수)를 Swagger member/profile 필드와 대조 필요.

## Existing Component Candidates

default 문서와 동일. 작물 뱃지는 `AppBadge`(또는 crop 전용 뱃지) 후보이며,
다줄 유동 배치(wrap)는 화면 단위 레이아웃으로 둔다. 실제 컴포넌트 API는
`Core/DesignSystem/` 확인 후 확정.
