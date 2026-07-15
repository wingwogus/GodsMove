# 텍스트로 기록하기 / 병해충 관리 작업 선택 시

- Captured: 2026-07-13
- Source MCP calls: `get_selection` → `read_my_design` → `export_node_as_image`
- Figma node ID: `1247:23511`
- Frame name: `텍스트로 기록하기 / 병해충 관리 작업 선택 시`
- Frame size: 390 × **1422** (상세 필드 최다 — 4영역)
- State: 작업 내용 = "병해충 관리" 선택 시 상세 폼. content-2 728.

## 상세 필드 (병해충 관리 = `PestControlDetailRequest`)

작업 내용 "병해충 관리" 선택 후, 메모 아래 상세 4영역:

### 사용 농약 (`1247:23527`, drop-down, **필수 `*`**)
- placeholder "사용한 농약을 선택해주세요." → `pesticideName` (string, required).
  UI 드롭다운 ↔ API 자유 문자열 (C-8과 동일 패턴 — 옵션 소스 확인).

### 농약 사용량 + 단위 (`1247:23528` `usage` 행, 좌우 분할)
- **농약 사용량** (`1247:23529`, text-input, 171, **필수 `*`**): placeholder "농약 사용량 작성".
  → `pesticideAmount` (number, required).
- **단위 선택** (`1247:23530`, drop-down, 171): placeholder "단위 선택".
  → `pesticideAmountUnit` enum `[ML, G]` (required). **단위 = g, ml (사용자 확정)** ↔ API
  `[ML, G]` **정확히 일치** ✅ (비료 C-9와 달리 충돌 없음).

### 총 살포량 (ml) (`1247:23531`, text-input, 350, **필수 `*`**)
- 라벨 "총 살포량 (ml)", placeholder "총 농약 살포량을 작성해주세요. ---"
  (placeholder 끝 " ---"는 디자인 임시 텍스트로 보임 — 확인).
- → `totalSprayAmount` (number, required) + `totalSprayAmountUnit` enum `[L]` (required).
- ⚠️ 라벨 단위 **(ml)** ↔ API `totalSprayAmountUnit` enum **`[L]`** 불일치.

### 대상 병해충 (`1247:23532`, drop-down, **필수 `*` 없음**)
- 라벨 "대상 병해충"(선택), placeholder "대상 병해충을 선택해주세요."
- → `pestTarget` (string, optional). UI 드롭다운 ↔ API 자유 문자열.

나머지(기본 정보/진행 작물/메모/사진/완료)는 default와 동일.

## API 매핑 (`SaveRecordRequest.pestControl` = `PestControlDetailRequest`)

required = `pesticideAmount`, `pesticideAmountUnit`, `pesticideName`, `totalSprayAmount`,
`totalSprayAmountUnit`.

| UI | 요청 필드 | 비고 |
|---|---|---|
| 사용 농약 | `pesticideName` (string) | UI 드롭다운 ↔ API string |
| 농약 사용량 | `pesticideAmount` (number) | |
| 단위 선택 | `pesticideAmountUnit` `[ML, G]` | **g/ml 일치** ✅ |
| 총 살포량 (ml) | `totalSprayAmount` (number) | |
| (총 살포량 단위) | `totalSprayAmountUnit` `[L]` | ⚠️ 라벨 ml ↔ enum L |
| 대상 병해충 | `pestTarget` (string, optional) | UI 드롭다운 ↔ API string |

## Figma ↔ API 충돌 → [백엔드 충돌 문서](2026-07-13-record-backend-conflicts.md)

- **C-11** 사용 농약 / 대상 병해충: UI 드롭다운(선택) ↔ API `pesticideName`/`pestTarget`
  자유 문자열. 옵션 소스(농약/병해충 카탈로그 API?) 확인 필요. (비료 C-8과 동류.)
- **C-12** 총 살포량 단위: UI 라벨 **(ml)** ↔ API `totalSprayAmountUnit` enum **`[L]`**.
  단위 불일치 — 총 살포량은 L 단위인지 ml인지 확정 필요. (총 살포량에 단위 드롭다운은
  없고 라벨에 (ml) 고정 → API는 L 고정.)

## 디자인 시스템 매핑

- 사용 농약 / 대상 병해충 → `AppDropdown`.
- 농약 사용량 + 단위 → `AppTextField`(숫자) + `AppDropdown`(g/ml) 좌우 분할.
- 총 살포량 → `AppTextField`(숫자, 단위 ml 라벨 고정).
- 나머지 default 문서와 동일.

## 미결

- 사용 농약/대상 병해충 드롭다운 옵션 소스(카탈로그).
- 총 살포량 placeholder 끝 " ---" 의미(디자인 임시).
- 총 살포량 단위 L vs ml 확정(C-12).
