# 텍스트로 기록하기 / 심기 작업 선택시 - 2 (모종 심기)

- Captured: 2026-07-13
- Source MCP calls: `get_selection` → `read_my_design` → `export_node_as_image`
- Figma node ID: `1247:23417`
- Frame name: `텍스트로 기록하기 / 심기 작업 선택시 - 2`
- Frame size: 390 × **1314** (씨앗 variant 1206보다 큼 — 모종 번식법 드롭다운 추가분)
- State: 작업 내용 = "심기", 심은 방법 = **모종 심기** 선택 variant. 씨앗 variant
  ([-1 씨앗 심기](2026-07-13-record-text-compose-planting-1-seed.md))와 짝.

## 씨앗 variant(-1) 대비 차이

심은 방법 select가 **모종 심기** 선택(초록 pastel)로 바뀌고, 아래 상세 입력이 달라짐:

| 필드 | 씨앗 심기(-1) | 모종 심기(-2, 이 문서) |
|---|---|---|
| 심은 방법 select | 씨앗 심기 선택 | **모종 심기 선택** |
| 수량 입력 | 심은 씨앗량 **(g)** — `seedAmount` | **심은 갯수 (주)** — `seedlingCount` (placeholder "심은 모종의 갯수를 작성해주세요.") |
| 추가 드롭다운 | 없음 | **모종 번식법** 드롭다운 신규 (필수는 아님 — `*` 없음) |

content-2 높이 512→**620**로 커짐.

## 필드 상세 (모종 심기)

### 심은 방법 (`1247:23433`, select 2택)
- 씨앗 심기(`1247:23439`): **비선택** — fill `#fafafa`, border `#f3f3f3`, text `#878787`
- 모종 심기(`1247:23441`): **선택** — fill `#e4f8e3`, border `#38c284`, text `#27865c`

### 심은 갯수 (주) (`1247:23443`, text-input, **필수 `*`**)
- placeholder "심은 모종의 갯수를 작성해주세요." 단위 (주) = JU.

### 모종 번식법 (`1247:23444`, drop-down, **필수 `*` 없음** — 라벨에 `*` 미표기)
- placeholder "진행한 모종 번식법을 선택해주세요."
- 옵션은 아래 [재배법-번식법 스펙](#재배법-번식법-스펙-사용자-제공-스크린샷-전사) 참조.

나머지(기본 정보/진행 작물/메모/사진/완료)는 default·씨앗 variant와 동일.

---

## 재배법-번식법 스펙 (사용자 제공 스크린샷 전사)

> 2026-07-13 사용자가 채팅에 붙여넣은 스펙 이미지. 파일 저장 불가로 **텍스트 전사**.
> 이 화면의 "심은 방법"(재배법)과 "모종 번식법"의 옵션 구조/워딩 확정 근거.

### 재배법 dropdown — 2단 구조

**1. 재배법 선택** (= "심은 방법"):
- 씨앗 심기
- 모종 심기

**2. 번식법 선택** (모종 심기인 경우에만 = "모종 번식법"):
- 씨앗 심기 → **`-`** (해당 없음, 번식법 미표시)
- 모종 심기 → 다음 6개:
  1. 꺾꽂이
  2. 접붙이기
  3. 휘묻이
  4. 포기나누기
  5. 조직 배양
  6. 시판 구매

### 기존 워딩 → 확정 워딩 매핑 (스크린샷의 "재배법 카테고리 기존 워딩" 대비)

| 기존 워딩 | 확정 UI 워딩 | API `propagationMethod` enum |
|---|---|---|
| 종자 | 씨앗 심기 (재배법 1단) | `SEED` |
| 삽목 | 꺾꽂이 | `CUTTING` |
| 접목 | 접붙이기 | `GRAFTING` |
| 취목 | 휘묻이 | `LAYERING` |
| 분주 | 포기나누기 | `DIVISION` |
| 조직배양 | 조직 배양 | `TISSUE_CULTURE` |
| (신규) | **시판 구매** | **⚠️ API enum에 없음 — 백엔드 추가 필요** |

### 수정 이유 (스크린샷 전사)

1. 기존 삽목/접목/취목/분주/조직배양은 **모두 "모종 심기"에 해당**하므로, 재배법은
   **씨앗 심기 / 모종 심기**로 먼저 구분하는 것이 필요.
2. **모종 심기 선택 시에만** 번식법(기존 삽목~조직배양)을 선택하도록 구분해 사용자
   피로도 최소화. (씨앗 심기는 번식법 선택 없음 → `-`)
3. 현재 **단순 시판 구매 모종**의 경우가 고려되지 않아 **"시판 구매" 추가**.

---

## API 매핑 (`PlantingDetailRequest`)

`PlantingDetailRequest`: `propagationMethod`(required, enum), `seedAmount`/`seedAmountUnit`,
`seedlingCount`/`seedlingUnit(JU)`.

- **씨앗 심기**: `propagationMethod = SEED`, `seedAmount` + `seedAmountUnit = G`.
- **모종 심기**: `seedlingCount` + `seedlingUnit = JU`, 그리고 "모종 번식법"이 실제
  `propagationMethod` 값(CUTTING/GRAFTING/LAYERING/DIVISION/TISSUE_CULTURE)에 매핑됨.

즉 **"심은 방법"(씨앗/모종)은 UI 그룹핑**이고, 실제 `propagationMethod` enum은:
- 씨앗 심기 → `SEED` (번식법 없음)
- 모종 심기 → 모종 번식법 드롭다운에서 고른 값이 곧 `propagationMethod`
  (꺾꽂이=CUTTING / 접붙이기=GRAFTING / 휘묻이=LAYERING / 포기나누기=DIVISION /
  조직 배양=TISSUE_CULTURE / **시판 구매=미정**)

## ⚠️ Figma ↔ API 충돌 / 확정 필요

1. **"시판 구매" enum 부재**: 모종 번식법 6번째 "시판 구매"에 대응하는
   `propagationMethod` enum 값이 없음 → **백엔드에 신규 enum(e.g. `PURCHASED`) 추가 요청 필요**.
   추가 전까지 프론트에서 이 옵션 선택 시 전송 값 미정.
2. **"모종 번식법" 필수 여부**: 라벨에 `*` 없음(선택). 그러나 API `propagationMethod`는
   required → 모종 심기인데 번식법 미선택 시 어떤 값을 보낼지 확정 필요
   (기본값? "시판 구매"가 기본? 검증에서 필수로 승격?).
3. **seedlingUnit 고정 (주/JU)**, **seedAmountUnit 고정 (g/G)** — KG 등 대체 단위 UI 없음.

## 디자인 시스템 매핑

- 심은 방법 2택 → `AppSegmentedControl` / select-area (씨앗 variant 문서와 동일).
- 심은 갯수(주) → `AppTextField` (숫자, 단위 suffix 주).
- 모종 번식법 → `AppDropdown` (6옵션). 씨앗 심기 시엔 이 드롭다운 **미노출**(조건부).
- 나머지 default 문서와 동일.

## 미결

- 조건부 렌더 규칙: 씨앗 심기 → [심은 씨앗량(g)]만. 모종 심기 → [심은 갯수(주)] +
  [모종 번식법]. 심은 방법 미선택 시 둘 다 숨김 여부.
- "시판 구매" 선택 시 모종 갯수(주) 입력이 여전히 필수인지(구매 모종도 심은 갯수 있음).
