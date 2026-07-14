# 텍스트로 기록하기 / 심기 작업 선택시 - 1 (씨앗 심기)

- Captured: 2026-07-13
- Source MCP calls: `get_selection` → `read_my_design` → `export_node_as_image`
- Figma node ID: `1247:23381`
- Frame name: `텍스트로 기록하기 / 심기 작업 선택시 - 1`
- Frame size: 390 × **1206** (default 990 대비 커짐 — 심기 상세 입력란 추가분)
- State: [텍스트 작성 폼 default](2026-07-13-record-text-compose-default.md)에서 **작업 내용 = "심기"**를
  선택하면 "작업 내용" 아래에 **심기 전용 상세 입력란**이 동적으로 추가된 상태. 이 프레임은
  심은 방법 = **씨앗 심기** 선택 변형(variant 1). (BR-RECORD-004 작업별 입력)

## 핵심: 작업유형 선택 → 동적 상세 폼

- default 대비 **작업 내용 드롭다운 값이 "심기"로 채워짐** (`1247:23395`, 텍스트 `#1a1a1a`).
- 그 아래 **content-2**(`1247:23393`) 높이가 296→**512**로 늘고, 아래 2개 필드가 신규 추가됨:
  1. **심은 방법** (select, 필수)
  2. **심은 씨앗량 (g)** (text-input, 필수)
- 나머지 영역(기본 정보/진행 작물/텍스트 메모/사진/완료)은 default와 동일.
- → workType별로 이 상세 블록만 교체되는 구조. 심기는 다시 씨앗/모종 하위 분기를 가짐.

## 신규 필드 1 — 심은 방법 (`1247:23397`)

라벨 "심은 방법" + 필수 `*`(`#ef4444`). **2택 세그먼트형 select** (`Frame 1428752800` 안 select-area 2개, 각 169×56):

| select-area | 라벨 | 상태 | fill | border | text |
|---|---|---|---|---|---|
| `1247:23403` | **씨앗 심기** | **선택** | `#e4f8e3`(primarySubtle) | `#38c284`(primary) | `#27865c`(primary) |
| `1247:23405` | 모종 심기 | 비선택 | `#fafafa`(subtle) | `#f3f3f3`(subtle) | `#878787`(muted) |

- cornerRadius 8, 각 169 wide(사이 12 gap → 350). 텍스트 Pretendard Medium 18.
- 선택 스타일이 필터 시트 칩과 동일 계열(초록 pastel) but 사각(radius 8) 세그먼트.
- 헬퍼 슬롯 "메시지를 전달합니다." 존재.

## 신규 필드 2 — 심은 씨앗량 (g) (`1247:23407`, text-input)

- 라벨 "심은 씨앗량 (g)" + 필수 `*`. → **씨앗 심기 선택 시** 노출되는 필드(단위 g).
- field cornerRadius 8, border `#e0e0e0`. placeholder "뿌린 씨앗의 양을 작성해주세요."
  (`#878787`). trailing 아이콘 슬롯 존재하나 이 프레임엔 비어있음(단위 표시용 추정).
- 헬퍼 슬롯 존재.

## API 매핑 (`SaveRecordRequest.planting` = `PlantingDetailRequest`)

`PlantingDetailRequest` (required=`propagationMethod`):
- `propagationMethod`: enum `[SEED, CUTTING, GRAFTING, LAYERING, DIVISION, TISSUE_CULTURE]`
- `seedAmount`: number / `seedAmountUnit`: enum `[KG, G]`
- `seedlingCount`: integer / `seedlingUnit`: enum `[JU]`

| UI | 요청 필드 | 비고 |
|---|---|---|
| 심은 방법 = 씨앗 심기 | `propagationMethod = SEED` | UI는 2택(씨앗/모종)인데 API enum은 6종 → 아래 충돌 |
| 심은 방법 = 모종 심기 | `propagationMethod = ?` (SEEDLING 없음 — 매핑 확인 필요) | variant 2에서 확인 |
| 심은 씨앗량 (g) | `seedAmount` + `seedAmountUnit = G` | 씨앗 심기 분기 필드 |
| (모종 심기 분기) | `seedlingCount` + `seedlingUnit = JU` | variant 2(모종)에서 캡처 예정 |

## Figma ↔ API 충돌 / 확인 필요

1. **propagationMethod 2택 vs 6 enum** → **[모종 variant 문서](2026-07-13-record-text-compose-planting-2-seedling.md)에서
   해소**: "심은 방법"(씨앗/모종)은 UI 그룹핑이고, 모종 심기 선택 시 나타나는 **"모종 번식법"
   드롭다운**이 실제 `propagationMethod`(꺾꽂이=CUTTING/접붙이기=GRAFTING/휘묻이=LAYERING/
   포기나누기=DIVISION/조직 배양=TISSUE_CULTURE/시판 구매=미정)에 매핑됨. 씨앗 심기 → `SEED`.
   ("시판 구매"는 API enum에 없어 백엔드 추가 필요.)
2. **단위 고정**: 씨앗량 라벨이 "(g)"로 고정 → `seedAmountUnit`은 항상 `G`? (KG 선택지 없음)
3. **모종 분기(variant 2)**: 모종 심기 선택 시 "심은 씨앗량 (g)" 대신 모종 수(주/JU) 입력으로
   바뀔 것으로 추정 — **별도 캡처 필요** (`심기 작업 선택시 - 2` 프레임 존재 추정).

## 디자인 시스템 매핑 후보

- 심은 방법 2택 세그먼트 → `AppSegmentedControl` 후보(초록 pastel 선택 스타일 확인 필요) 또는
  2개 select-area 화면 로컬 구성. select-area 스타일(radius 8, 선택 시 primarySubtle+primary
  border)이 `AppSegmentedControl`/`AppChip`과 정확히 맞는지 대조 필요.
- 심은 씨앗량 → `AppTextField`(숫자 키보드, 단위 suffix). trailing 아이콘 슬롯 용도 확인.
- 나머지(date/dropdown/textarea/image/button)는 default 문서와 동일 매핑.

## 미결

- 상세 블록이 "작업 내용"(메모 textarea)과 "사진 첨부" **사이**에 삽입됨 → 폼 순서:
  작업유형 드롭다운 → 메모 → **[작업별 상세]** → 사진. 다른 workType도 동일 위치 삽입 추정.
- 심은 방법 미선택 시 씨앗량/모종수 필드 노출 여부(선택 후 노출 vs 항상 노출).
- 완료 활성 조건에 상세 필수 필드(심은 방법, 씨앗량) 포함.
