# 텍스트로 기록하기 / 기타 작업 선택 시

- 기록일: 2026-07-13 (사용자 구두 확정 — 별도 Figma 캡처 없음)
- State: 작업 내용 = "기타" 선택 시. **상세 입력란 없음** — [가지·순 정리](2026-07-13-record-text-compose-pruning.md)와
  동일하게 default 폼과 완전히 같음.

## 핵심: 상세 필드 없음

작업 내용 드롭다운만 "기타"로 채워지고, 작업별 상세 블록이 추가되지 않음. 폼 구성은
default와 동일:

- 기본 정보(날짜/날씨) / 진행 작물(농지·작물) / 작업 내용(드롭다운="기타" + 메모) /
  사진 첨부 / 완료.

## API 매핑

- `SaveRecordRequest`에 **`etc` 상세 객체 없음** (planting/watering/fertilizing/pestControl/
  weeding/harvest만 존재). → 기타(`workType = ETC`)는 상세 없이 공통 필드만 전송.
- Figma에 상세 필드가 없는 것과 **API 일치** ✅ (충돌 없음).

## 참고 — workType 8종 상세 폼 유무 (최종)

| workType | 상세 폼 | 상세 API 객체 |
|---|---|---|
| 심기 PLANTING | 있음 (씨앗/모종 분기) | `planting` |
| 물주기 WATERING | 있음 | `watering` |
| 비료 주기 FERTILIZING | 있음 | `fertilizing` |
| 병해충 관리 PEST_CONTROL | 있음 | `pestControl` |
| 잡초 관리 WEEDING | 있음 | `weeding` |
| 수확 HARVEST | 있음 (최다) | `harvest` |
| 가지·순 정리 PRUNING | **없음** | (없음) |
| 기타 ETC | **없음** | (없음) |

→ 텍스트 기록 작성 폼의 workType별 분기 캡처 **전부 완료**.
