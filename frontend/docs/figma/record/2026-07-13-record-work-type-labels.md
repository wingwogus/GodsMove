# 작업 유형(workType) 확정 워딩

- 확정일: 2026-07-13 (제품 오너 지정)
- 적용 범위: 영농 활동 필터 칩, 텍스트/음성 기록 작성 폼의 "작업 내용" 드롭다운, 리스트 배지 등
  workType을 표시하는 모든 곳.

## 확정 라벨 ↔ API enum 매핑

배포 Swagger `SaveRecordRequest.workType` / `RecordSummaryResponse.workType` enum(8종)과
**1:1 일치**. (이 목록에 `가공`은 없음 — 앞서 논의된 Figma 9번째 칩 `가공`은 최종 미채택.)

| 순서 | UI 라벨 (확정) | API enum |
|---|---|---|
| 1 | 심기 | `PLANTING` |
| 2 | 물주기 | `WATERING` |
| 3 | 비료 주기 | `FERTILIZING` |
| 4 | 병해충 관리 | `PEST_CONTROL` |
| 5 | 잡초 관리 | `WEEDING` |
| 6 | 가지·순 정리 | `PRUNING` |
| 7 | 수확 | `HARVEST` |
| 8 | 기타 | `ETC` |

## 이전 워딩 대비 변경점

초기 Figma 필터 시트 캡처([영농 활동 바텀시트](2026-07-13-record-filter-bottom-sheets.md))의
워딩에서 아래가 **이 확정 워딩으로 대체됨**:

| 이전(Figma 캡처) | 확정 |
|---|---|
| 거름·비료 | **비료 주기** |
| 병해충 방제 | **병해충 관리** |
| 제초 | **잡초 관리** |
| (가공) | 삭제 — 채택 안 함 |

나머지(심기/물주기/가지·순 정리/수확/기타)는 동일.

## 코드 반영

`Features/Record/Domain/RecordModels.swift`의 `WorkType.label`이 이 표대로 반영됨(2026-07-13).
필터 시트/작성 폼 등은 모두 `WorkType.label`을 사용하므로 이 한 곳이 단일 출처(single source).
