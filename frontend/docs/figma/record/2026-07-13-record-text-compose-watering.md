# 텍스트로 기록하기 / 물주기 작업 선택

- Captured: 2026-07-13
- Source MCP calls: `get_selection` → `read_my_design` → `export_node_as_image`
- Figma node ID: `1247:23454`
- Frame name: `텍스트로 기록하기 / 물주기 작업 선택`
- Frame size: 390 × **1206**
- State: 작업 내용 = "물주기" 선택 시 상세 폼. content-2 512.

## 상세 필드 (물주기 = `WateringDetailRequest`)

작업 내용 드롭다운 "물주기" 선택 후, 메모 아래 상세 드롭다운 2개 추가:

### 진행 방식 (`1247:23471`, drop-down, **필수 `*`**)
- 라벨 "진행 방식", placeholder "물주기 진행 방식을 선택해주세요."
- → API `WateringDetailRequest.irrigationMethod` enum `[DRIP, SPRINKLER, SPRAYING, MANUAL]`.
- 옵션 워딩: 아래 [드롭다운 옵션 워딩 스펙](#드롭다운-옵션-워딩-스펙-사용자-제공-스크린샷-전사) 참조.

### 물의 양 (`1247:23470`, drop-down, **필수 `*`**)
- 라벨 "물의 양", placeholder "진행한 물의 양 정도를 선택해주세요."
- → API `WateringDetailRequest.irrigationAmount` enum `[LOW, NORMAL, SUFFICIENT]`.
- 옵션 워딩: 아래 스펙 참조.

---

## 드롭다운 옵션 워딩 스펙 (사용자 제공 스크린샷 전사)

> 2026-07-13 사용자가 채팅에 붙여넣은 스펙 이미지. 파일 저장 불가로 **텍스트 전사**.
> 물주기 상세 드롭다운 2종의 옵션 워딩(기존 → 수정) 확정 근거.

### 물의 양 (`irrigationAmount`)

| 기존 워딩 | 확정 워딩(수정) | API enum |
|---|---|---|
| 소 | **조금** | `LOW` |
| 보통 | **보통** | `NORMAL` |
| 충분 | **많이** | `SUFFICIENT` |

3옵션 1:1 매핑, 충돌 없음. (스크린샷에서 "보통"에 선택 마커 표시 — 예시 선택 상태.)

### 진행 방식 (`irrigationMethod`)

기존 워딩 4종(점적/스프링클러/살수/수동)에서 **수정 3종으로 통합**:

| 확정 워딩(수정) | 매핑 후보 enum | 비고 |
|---|---|---|
| 점적 | `DRIP` | 명확 |
| 살수 | `SPRINKLER` 또는 `SPRAYING` | ⚠️ 기존 스프링클러+살수 통합 추정 — enum 택1 확정 필요 |
| 기타 | (대응 enum 없음) | ⚠️ `MANUAL`로 흡수? 신규 값? — 확정 필요 |

- 수정본은 3옵션인데 API enum은 4종 → **N:1 통합 + "기타" 대응 불명** → 백엔드 협의 필요
  ([충돌 C-7](2026-07-13-record-backend-conflicts.md#c-7-물주기-진행-방식-옵션-통합--enum-매핑)).

나머지(기본 정보/진행 작물/메모/사진/완료)는 default와 동일.

## API 매핑 (`SaveRecordRequest.watering` = `WateringDetailRequest`)

`WateringDetailRequest` (required=**없음** — 둘 다 optional):
- `irrigationMethod`: enum `[DRIP, SPRINKLER, SPRAYING, MANUAL]`
- `irrigationAmount`: enum `[LOW, NORMAL, SUFFICIENT]`

| UI | 요청 필드 | 비고 |
|---|---|---|
| 진행 방식 | `irrigationMethod` | UI 필수(`*`) ↔ API optional |
| 물의 양 | `irrigationAmount` | UI 필수(`*`) ↔ API optional |

## Figma ↔ API 충돌 / 확인 필요

1. **필수 여부 불일치**: UI는 진행 방식/물의 양 둘 다 필수(`*`)인데 API
   `WateringDetailRequest`는 둘 다 optional(required 없음). → [충돌 C-5](2026-07-13-record-backend-conflicts.md#c-5-물주기--상세-필수-여부-불일치).
2. **진행 방식 옵션 통합/매핑**: 확정 UI 3종(점적/살수/기타) ↔ API enum 4종
   (DRIP/SPRINKLER/SPRAYING/MANUAL). "살수" enum 택1, "기타" 대응 불명 →
   [충돌 C-7](2026-07-13-record-backend-conflicts.md#c-7-물주기-진행-방식-옵션-통합--enum-매핑).

## 디자인 시스템 매핑

- 진행 방식 / 물의 양 → `AppDropdown` (enum 옵션). 나머지 default 문서와 동일.
