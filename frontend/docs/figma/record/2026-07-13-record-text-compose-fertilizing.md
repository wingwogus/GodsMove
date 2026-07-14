# 텍스트로 기록하기 / 비료 주기 작업 선택 시

- Captured: 2026-07-13
- Source MCP calls: `get_selection` → `read_my_design` → `export_node_as_image`
- Figma node ID: `1247:23481`
- Frame name: `텍스트로 기록하기 / 비료주기 작업 선택 시`
- Frame size: 390 × **1314**
- State: 작업 내용 = "비료 주기" 선택 시 상세 폼. content-2 620.

## 상세 필드 (비료 주기 = `FertilizingDetailRequest`)

작업 내용 "비료 주기" 선택 후, 메모 아래 상세 3영역 추가:

### 사용 비료 (`1247:23497`, drop-down, **필수 `*`**)
- 라벨 "사용 비료", placeholder "사용한 비료를 선택해주세요."
- → API `FertilizingDetailRequest.materialName` (string, required). 단, API는 자유
  문자열인데 UI는 **드롭다운**(선택형) → 옵션 소스 확인 필요(비료 카탈로그?).

### 비료 사용량 + 단위 (`1247:23498` `usage` 행, 좌우 분할)
- **비료 사용량** (`1247:23499`, text-input, 171 wide, **필수 `*`**): placeholder "비료 사용량 작성".
  → `FertilizingDetailRequest.amount` (number, required).
- **단위 선택** (`1247:23500`, drop-down, 171 wide, 라벨 없음): placeholder "단위 선택".
  → `FertilizingDetailRequest.amountUnit` enum `[KG]` (required). ⚠️ API는 `KG` 단일값인데
  UI는 단위 **선택 드롭다운** → 선택지가 KG뿐인지/추가 단위 있는지 확인 필요.

### 진행 방식 (`1247:23501`, drop-down, **필수 `*`**)
- 라벨 "진행 방식", placeholder "비료 주기 진행 방식을 선택해주세요."
- → API `FertilizingDetailRequest.applicationMethod` enum `[SOIL, FOLIAR]` (토양/엽면).
  ⚠️ API에서 `applicationMethod`는 **optional**인데 UI는 **필수(`*`)** → 불일치.

나머지(기본 정보/진행 작물/메모/사진/완료)는 default와 동일.

---

## 드롭다운 옵션 워딩 스펙 (사용자 제공 스크린샷 전사)

> 2026-07-13 사용자가 채팅에 붙여넣은 스펙 이미지. 파일 저장 불가로 **텍스트 전사**.
> 비료 주기 "진행 방식"과 "비료 사용량 단위" 옵션 워딩(기존 → 수정) 확정 근거.

### 진행 방식 (`applicationMethod`)

| 기존 워딩 | 확정 워딩(수정) | API enum |
|---|---|---|
| 토양시비 | **토양에 주기** | `SOIL` |
| 엽면시비 | **엽면에 뿌리기** | `FOLIAR` |

2옵션 1:1 매핑, enum 충돌 없음. (스크린샷에서 "토양에 주기"에 선택 마커 — 예시 선택 상태.)

### 비료 사용량 단위 (`amountUnit`)

| 기존 워딩 | 확정 워딩(수정) | API enum |
|---|---|---|
| kg | **g** | `KG`?? |
| (없음) `-` | **ml** | (대응 enum 없음) |

- 확정 단위 = **g, ml** 2종. (스크린샷에서 "g"에 선택 마커.)
- ⚠️ API `FertilizingDetailRequest.amountUnit` enum은 **`[KG]` 단일뿐** → 확정 UI(g/ml)와
  전혀 불일치. `G`, `ML` enum 신규 추가 필요 → [충돌 C-9](2026-07-13-record-backend-conflicts.md#c-9-비료-주기--사용량-단위-enum).

## API 매핑 (`SaveRecordRequest.fertilizing` = `FertilizingDetailRequest`)

`FertilizingDetailRequest` (required=`amount`, `amountUnit`, `materialName`):
- `materialName`: string (required)
- `amount`: number (required)
- `amountUnit`: enum `[KG]` (required)
- `applicationMethod`: enum `[SOIL, FOLIAR]` (optional)

| UI | 요청 필드 | 비고 |
|---|---|---|
| 사용 비료 | `materialName` | UI 드롭다운 ↔ API string(자유입력) — 옵션 소스 확인 |
| 비료 사용량 | `amount` | number |
| 단위 선택 | `amountUnit` | API enum `[KG]` 단일 ↔ UI 선택 드롭다운 |
| 진행 방식 | `applicationMethod` | UI 필수 ↔ API optional |

## Figma ↔ API 충돌 / 확인 필요 → [백엔드 충돌 문서](2026-07-13-record-backend-conflicts.md)

- **C-8** 사용 비료: UI 드롭다운(선택) ↔ API `materialName` 자유 문자열. 드롭다운 옵션 소스
  (비료 카탈로그 API?) 확인 필요.
- **C-9** 비료 단위: API `amountUnit` enum이 `[KG]` 단일뿐인데 UI는 단위 선택 드롭다운.
  실제 선택지(KG 외 g/L 등) 확정 필요.
- **C-10** 진행 방식 필수 여부: UI 필수(`*`) ↔ API `applicationMethod` optional.

## 디자인 시스템 매핑

- 사용 비료 / 진행 방식 → `AppDropdown`.
- 비료 사용량 → `AppTextField` (숫자) + 단위 `AppDropdown` 좌우 분할(171+171, gap 8).
- 나머지 default 문서와 동일.

## 미결

- 사용 비료 드롭다운 옵션 워딩/소스(비료 카탈로그 존재 여부) — C-8.
- 진행 방식/단위 워딩은 위 [드롭다운 옵션 워딩 스펙](#드롭다운-옵션-워딩-스펙-사용자-제공-스크린샷-전사)으로 확정
  (진행 방식: 토양에 주기/엽면에 뿌리기 = SOIL/FOLIAR / 단위: g, ml).
