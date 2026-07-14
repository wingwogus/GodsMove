# 텍스트로 기록하기 / 수확 작업 선택 시

- Captured: 2026-07-13
- Source MCP calls: `get_selection` → `read_my_design` → `export_node_as_image`
- Figma node ID: `1247:23264`
- Frame name: `텍스트로 기록하기 / 수확 작업 선택 시`
- Frame size: 390 × **1406**
- State: 작업 내용 = "수확" 선택 시 상세 폼. content-2 712 (workType 중 상세 필드 최다).

## 상세 필드 (수확 = `HarvestDetailRequest`)

작업 내용 "수확" 선택 후, 메모 아래 상세 5요소:

### 재배 기간 (개월) (`1270:18755`, text-input, **필수 `*`**)
- placeholder "작물의 총 재배 기간을 작성해주세요."
- → `growthPeriod` (int, required) + `growthPeriodUnit` enum `[YEAR, MONTH]`.
  라벨 단위 **(개월)** 고정 → `growthPeriodUnit = MONTH` 추정. (UI에 단위 선택 없음, YEAR 미노출.)

### 수확량 (kg) + 잘 모르겠음 (`1247:23281` `amount`)
- **수확량 (kg)** (`1247:23282`, text-input, **필수 `*`**): placeholder "작물의 총 수확량을 작성해주세요."
  → `harvestAmount` (number, optional). 라벨 단위 **(kg)** 고정 (API에 수확량 단위 필드 없음 — 표시용).
- **잘 모르겠음** 체크박스 (`1247:23283`): `check-box` + 라벨 "잘 모르겠음".
  → `harvestAmountUnknown` (boolean, required). 체크 시 수확량 미입력 허용 추정.

### 수확 부위 (`1247:23286`, drop-down, **필수 `*`**)
- placeholder "작물의 수확 부위를 선택해주세요."
- → `medicinalPart` enum `[WHOLE_HERB, ROOT_BARK, RHIZOME, LEAF, FLOWER, FRUIT, SEED,
  STEM_BRANCH, UNKNOWN]` (9종). 옵션 한글 워딩 미확정(드롭다운 열림 미캡처).

### 최종 수확 완료 (`1247:23287`, toggle)
- 라벨 "최종 수확 완료" + 토글(off 상태, fill `#e0e0e0`).
- → ⚠️ **대응 API 필드 없음** (`HarvestDetailRequest`에 최종수확 플래그 없음). BR-RECORD-008
  (다년생 작물, 수확이 여러 번) 관련 추정 — 백엔드 확인 필요.

나머지(기본 정보/진행 작물/메모/사진/완료)는 default와 동일.

## API 매핑 (`SaveRecordRequest.harvest` = `HarvestDetailRequest`)

required = `growthPeriod`, `growthPeriodUnit`, `harvestAmountUnknown`, `harvestSource`, `medicinalPart`.
optional = `harvestAmount`.

| UI | 요청 필드 | 비고 |
|---|---|---|
| 재배 기간 (개월) | `growthPeriod` + `growthPeriodUnit` | 단위 개월 고정 → MONTH? (enum YEAR 미노출) |
| 수확량 (kg) | `harvestAmount` (optional) | 단위 kg 표시용(API 단위 필드 없음) |
| 잘 모르겠음 | `harvestAmountUnknown` (bool) | 체크 시 수확량 unknown |
| 수확 부위 | `medicinalPart` (9종 enum) | 옵션 워딩 미확정 |
| 최종 수확 완료(토글) | (대응 필드 없음) | ⚠️ API 필드 부재 |
| (없음) | `harvestSource` `[CULTIVATED, FORAGED]` **required** | ⚠️ UI에 재배/채취 선택 없음 |

## Figma ↔ API 충돌 → [백엔드 충돌 문서](2026-07-13-record-backend-conflicts.md)

- **C-14** `harvestSource` 누락: API에서 `harvestSource`(재배 CULTIVATED / 채취 FORAGED)는
  **required**인데 UI에 해당 입력이 **없음**. 기본값(CULTIVATED?) or UI 추가 or optional 완화 필요.
- **C-15** "최종 수확 완료" 토글: UI에 있으나 `HarvestDetailRequest`에 **대응 필드 없음**.
  다년생(BR-RECORD-008) 관련 플래그로 백엔드 필드 추가 필요 여부 확인.
- **C-16** 재배 기간 단위: 라벨 (개월) 고정 ↔ API `growthPeriodUnit` enum `[YEAR, MONTH]`.
  단위 선택 UI 없음 → MONTH 고정 전송인지, YEAR 지원 필요한지 확정.
- 수확 부위 `medicinalPart` 9종 한글 워딩 미확정(드롭다운 열림 미캡처).

## 디자인 시스템 매핑

- 재배 기간 / 수확량 → `AppTextField` (숫자, 단위 라벨).
- 잘 모르겠음 → `AppCheckbox` 계열(DS에 체크박스 컴포넌트 확인 필요 — `check-box` 인스턴스).
- 수확 부위 → `AppDropdown` (9옵션).
- 최종 수확 완료 → `AppToggle`.
- 나머지 default 문서와 동일.

## 미결

- 수확 부위 9종 한글 워딩.
- 최종 수확 완료 토글의 저장 대상(C-15).
- harvestSource 입력 위치(C-14).
- 잘 모르겠음 체크 시 수확량 필드 비활성/필수 해제 동작.
