# 텍스트로 기록하기 / 가지·순 정리 작업 선택 시

- Captured: 2026-07-13
- Source MCP calls: `get_selection` → `read_my_design` → `export_node_as_image`
- Figma node ID: `1247:23186`
- Frame name: `텍스트로 기록하기 / 가지·순 정리 작업 선택 시`
- Frame size: 390 × **990** (= default와 동일)
- State: 작업 내용 = "가지·순 정리" 선택 시. **상세 입력란 없음** — default 폼과 구조 동일.

## 핵심: 상세 필드 없음

작업 내용 드롭다운만 "가지·순 정리"로 채워지고, **작업별 상세 블록이 추가되지 않음**.
content-2 높이 296(= default), 프레임 990(= default). 폼 구성:

- 기본 정보(날짜/날씨) / 진행 작물(농지·작물) / 작업 내용(드롭다운="가지·순 정리" + 메모) /
  사진 첨부 / 완료.

## API 매핑

- `SaveRecordRequest`에 **`pruning` 상세 객체가 없음** (planting/watering/fertilizing/
  pestControl/weeding/harvest만 존재). → 가지·순 정리(`workType = PRUNING`)는 **상세 없이**
  공통 필드(workType/메모/작물/날짜/사진 등)만 전송.
- Figma에 상세 필드가 없는 것과 **API가 일치** ✅ (충돌 없음).

## 디자인 시스템 매핑

- default 문서([텍스트 작성 default](2026-07-13-record-text-compose-default.md))와 동일.
  작업 내용 드롭다운 선택값만 다름.

## 참고

- workType 8종 중 **상세 폼이 없는 유형**: 가지·순 정리(PRUNING), 기타(ETC, API에 상세 객체 없음
  — 확인 예정). 나머지 6종(심기/물주기/비료/병해충/잡초/수확)은 각자 상세 객체 보유.
