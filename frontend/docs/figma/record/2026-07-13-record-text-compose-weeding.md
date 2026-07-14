# 텍스트로 기록하기 / 잡초 관리 작업 선택 시

- Captured: 2026-07-13
- Source MCP calls: `get_selection` → `read_my_design` → `export_node_as_image`
- Figma node ID: `1247:23542`
- Frame name: `텍스트로 기록하기 / 잡초 관리 작업 선택 시`
- Frame size: 390 × **1098** (상세 필드 1개뿐 — 가장 짧음)
- State: 작업 내용 = "잡초 관리" 선택 시 상세 폼. content-2 404.

## 상세 필드 (잡초 관리 = `WeedingDetailRequest`)

작업 내용 "잡초 관리" 선택 후, 메모 아래 상세 드롭다운 **1개**만 추가:

### 진행 방식 (`1247:23558`, drop-down, **필수 `*`**)
- 라벨 "진행 방식", placeholder "진행한 잡초 관리 방식을 선택해주세요."
- → `WeedingDetailRequest.weedingMethod` enum `[HAND, MACHINE, MULCHING, HERBICIDE]`.

나머지(기본 정보/진행 작물/메모/사진/완료)는 default와 동일.

## 진행 방식 옵션 워딩 (사용자 확정, 2026-07-13)

4종, API enum과 **1:1 매칭** ✅ (enum 충돌 없음):

| 확정 워딩 | API enum |
|---|---|
| 손으로 뽑기 | `HAND` |
| 예초기 사용 | `MACHINE` |
| 멀칭 (비닐 덮기) | `MULCHING` |
| 제초제 사용 | `HERBICIDE` |

## API 매핑 (`SaveRecordRequest.weeding` = `WeedingDetailRequest`)

`WeedingDetailRequest` (required=**없음**):
- `weedingMethod`: enum `[HAND, MACHINE, MULCHING, HERBICIDE]` (optional)

| UI | 요청 필드 | 비고 |
|---|---|---|
| 진행 방식 | `weedingMethod` | UI 필수(`*`) ↔ API optional |

## Figma ↔ API 충돌 → [백엔드 충돌 문서](2026-07-13-record-backend-conflicts.md)

- **C-13** 진행 방식 필수 여부: UI 필수(`*`) ↔ API `weedingMethod` optional.
  (C-5 물주기·C-10 비료와 동일한 "UI 필수 ↔ API optional" 반복 패턴 — 일괄 정책 결정 필요.)
- 옵션 워딩(손으로 뽑기/예초기 사용/멀칭/제초제 사용)은 enum과 1:1 → 워딩 충돌 없음.

## 디자인 시스템 매핑

- 진행 방식 → `AppDropdown` (4옵션). 나머지 default 문서와 동일.
