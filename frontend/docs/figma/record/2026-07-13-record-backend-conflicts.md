# 영농 기록 — Figma/스펙 ↔ 배포 백엔드 충돌 트래킹

디자인/제품 스펙과 배포 Swagger(`docs/swagger/openapi.json`) 사이에서 발견된 불일치를
한곳에 모은다. **프론트가 임의로 결정하지 않고 백엔드와 협의해야 하는 항목**. 캡처가
진행되며 계속 추가한다.

- 기준: 배포 Swagger (frontend 계약 소스). 로컬 backend 소스와 다르면 커밋 해시와 함께 별도 기록.
- 관련 규칙: 루트 `AGENTS.md` — "Swagger와 backend 소스가 다르면 Swagger를 배포 계약으로 보고
  불일치를 기록".

---

## ⭐️ 2026-07-13 origin dev 재대조 (중요)

사용자가 backend를 pull(`origin/dev`, `e4d1e31` — **PR #19 `feat/farming-record-refine`** 머지)한
뒤 재확인함. 결론:

- **C-1~C-16은 "배포 Swagger" 기준 충돌이 맞다.** (배포 서버는 현재 502이나 스냅샷
  `docs/swagger/openapi.json`은 유효 — sha `c92cfa6…`.)
- **그러나 origin dev 소스는 이 record 도메인을 리팩터해 대부분을 이미 해소**했다. 즉
  **dev는 아직 배포되지 않았고**(배포 Swagger엔 구 형태 잔존), dev가 배포되어 Swagger가
  재동기화되면 아래 표처럼 충돌 상당수가 사라진다.
- AGENTS 규칙대로 **프론트 계약 소스는 여전히 배포 Swagger**다. 따라서 쓰기(작성) 경로
  구현은 **dev 배포 + `sync_swagger_spec.py` 재동기화 이후** 착수하는 것이 안전.

### 충돌별 dev 재대조

| ID | 배포 Swagger (현재) | origin dev `e4d1e31` | dev에서 |
|---|---|---|---|
| C-2 심기 시판구매 | propagationMethod=[SEED,CUTTING,GRAFTING,LAYERING,DIVISION,TISSUE_CULTURE] | `plantingMethod`=SEED/SEEDLING **신설** + `propagationMethod`=CUTTING/GRAFTING/LAYERING/DIVISION/TISSUE_CULTURE/**PURCHASED**(SEED 제거) | ✅ 해소 (재배법=plantingMethod, 번식법=propagationMethod, 시판 구매 포함) |
| C-9 비료 단위 | amountUnit=[KG] | `FertilizerAmountUnit`=**G, ML** | ✅ 해소 (g/ml) |
| C-7 물주기 진행방식 | irrigationMethod=[DRIP,SPRINKLER,SPRAYING,MANUAL] | `IrrigationMethod`=DRIP(점적)/SPRAYING(살수)/**ETC(기타)** | ✅ 해소 (점적/살수/기타) |
| C-11 농약/병해충 | pesticideName·pestTarget=string | `pesticideId`:UUID(필수)·`pestId`:UUID | 🔁 변경: id 기반(드롭다운=카탈로그 선택). **농약/병해충 카탈로그 조회 API 필요**(신규 의존성) |
| C-14 harvestSource | required | `harvestSource` **기본값 CULTIVATED**(요청 필수 아님) | ✅ 해소 (기본값) |
| C-15 최종 수확 완료 | 필드 없음 | `isLastHarvest`:Boolean **@NotNull 신설** | ✅ 해소 (필드 추가, 단 **필수**) |
| C-12 총 살포량 단위 | totalSprayAmountUnit=[L] | `SprayAmountUnit`=[L] | ⚠️ 미해소 — UI 라벨 (ml) ↔ enum L 여전. |
| C-16 재배 기간 단위 | [YEAR,MONTH] | `GrowthPeriodUnit`=YEAR/MONTH | ⚠️ UI (개월) 고정 vs enum 2종 — 미해소(경미). |
| C-5/C-10/C-13 상세 필수여부 | optional | dev도 optional(단 UI는 필수) | ⚠️ UI가 더 엄격 — 프론트에서 처리 가능. dev의 `WorkType.detailRequired`/work-types API로 판단 가능. |
| C-1 필터 복수선택 | 단일 cropId/workType | (리스트 쿼리 미변경 추정) | ⚠️ 미해소(재확인 필요) |

### dev의 신규 사항 (배포 시 프론트가 반영해야 함)

- **`GET /api/v1/work-types`** (배포 Swagger에도 이미 존재): workType별 `fields`+`options`+
  `detailRequired`를 반환하는 **메타데이터 엔드포인트**. → 작성 폼의 workType 상세를
  **하드코딩 대신 서버 정의로 구성** 가능(강력 권장 검토).
- **memo `@Size(min=30, max=500)`** — 배포 Swagger에도 이미 `minLength:30`. **최소 30자** 규칙이
  디자인/에러 케이스 문서에 없음 → **C-17**로 신규 기록.
- `pesticideId`/`pestId` → 농약/병해충 **카탈로그 조회 API** 의존성(dev 기준).
- `EntryMode`(MANUAL/VOICE) — 텍스트/음성 기록 구분 필드.
- 라벨 참고: dev enum label은 백엔드용(예: WATERING="관수", PRUNING="전정")이며 프론트 확정
  워딩(물주기/가지·순 정리)과 다름. **프론트는 자체 라벨 유지**(enum 코드만 계약).

## C-17. 메모 최소 길이 30자 (배포/ dev 공통)

- **API**: `SaveRecordRequest.memo` `@Size(min=30, max=500)` — **최소 30자 필수**.
- **디자인**: 작성 폼/에러 케이스에 "필수", "최대 500자"만 있고 **최소 30자 규칙 없음**.
- **필요**: 최소 30자 미만 시 에러 문구/카운터 처리 정의. (배포 Swagger에도 이미 존재 →
  이건 dev-only 아님, 현행 계약.) 에러 케이스 문서 보완 필요.
- 출처: 배포 Swagger + [에러 케이스](2026-07-13-record-text-compose-error-cases.md)

---

## C-1. 필터 복수 선택 vs 단일 파라미터 (리스트)

- **디자인**: 기록 메인 필터에서 작물/영농 활동 **복수 선택** (활성 칩 "작물 4", "영농 활동 5"
  카운트 표기 — [기록 버튼 탭 문서](2026-07-13-record-main-record-button-tapped.md) 확인).
- **API**: `GET /api/v1/farming-records`의 `cropId`(단일 uuid), `workType`(단일 enum)만 받음.
- **영향**: 복수 선택 필터링 불가. 현재 구현은 **단일 선택**으로 우회.
- **필요**: 리스트 쿼리 파라미터를 배열(`cropIds`, `workTypes`) 또는 반복 파라미터로 확장.
- 출처: [필터 바텀시트](2026-07-13-record-filter-bottom-sheets.md), [기록 버튼 탭](2026-07-13-record-main-record-button-tapped.md)

## C-2. 심기 — "시판 구매" 번식법 enum 부재

- **스펙**: 모종 번식법 옵션 = 꺾꽂이/접붙이기/휘묻이/포기나누기/조직 배양/**시판 구매**.
- **API**: `PlantingDetailRequest.propagationMethod` enum =
  `[SEED, CUTTING, GRAFTING, LAYERING, DIVISION, TISSUE_CULTURE]` — **"시판 구매"에 대응하는
  값이 없음**.
- **필요**: 백엔드 enum에 신규 값(예: `PURCHASED`) 추가. 추가 전까지 프론트에서 "시판 구매"
  선택 시 전송 값 미정.
- 출처: [심기-2 모종 심기](2026-07-13-record-text-compose-planting-2-seedling.md)

## C-3. 심기 — "모종 번식법" 필수 여부

- **디자인**: "모종 번식법" 드롭다운 라벨에 `*` 없음(선택).
- **API**: `PlantingDetailRequest.propagationMethod`는 **required**.
- **영향**: 모종 심기인데 번식법 미선택 시 보낼 값이 없음.
- **필요**: 번식법을 UI 필수로 승격할지, 백엔드에서 optional 허용/기본값 둘지 결정.
- 출처: [심기-2 모종 심기](2026-07-13-record-text-compose-planting-2-seedling.md)

## C-4. 심기 — propagationMethod 단위/그룹 매핑

- **디자인**: "심은 방법"(씨앗/모종)은 UI 그룹핑. 씨앗 심기 → `SEED`. 모종 심기 → 번식법
  드롭다운 값이 실제 enum(CUTTING/GRAFTING/LAYERING/DIVISION/TISSUE_CULTURE).
- **확인 필요**: 이 매핑을 백엔드와 확정. 씨앗 심기 시 `seedAmountUnit`은 항상 `G`(KG 미노출),
  모종 심기 시 `seedlingUnit`은 항상 `JU` — 단위 고정이 백엔드 기대와 일치하는지.
- 출처: [심기-1](2026-07-13-record-text-compose-planting-1-seed.md), [심기-2](2026-07-13-record-text-compose-planting-2-seedling.md)

## C-5. 물주기 — 상세 필수 여부 불일치

- **디자인**: "진행 방식", "물의 양" 둘 다 **필수(`*`)**.
- **API**: `WateringDetailRequest` — `irrigationMethod`, `irrigationAmount` 둘 다 **optional**
  (required 없음).
- **필요**: UI 필수 규칙과 API 계약 일치화(백엔드 required 승격 또는 UI optional 완화).
- 출처: [물주기](2026-07-13-record-text-compose-watering.md)

## C-6. 날씨 온도 — 범위 vs 단일 값

- **디자인**: 기본 정보의 날씨 표시가 "12~16°" **범위**(최저~최고).
- **API**: `SaveRecordRequest.weatherTemperature` = 단일 `int`.
- **확인 필요**: 저장 시 어떤 값을 보낼지(대표 1개? 평균?) 또는 백엔드가 최저/최고 2필드를
  받도록 확장. 날씨는 자동 조회 표시값 추정 → 조회 소스/저장 규칙 확정 필요.
- 출처: [텍스트 작성 default](2026-07-13-record-text-compose-default.md)

## C-7. 물주기 진행 방식 — 옵션 통합 & enum 매핑

- **스펙**: 진행 방식 확정 워딩 **3종**(점적 / 살수 / 기타). 기존 4종(점적/스프링클러/
  살수/수동)에서 통합.
- **API**: `WateringDetailRequest.irrigationMethod` enum = `[DRIP, SPRINKLER, SPRAYING, MANUAL]`(4종).
- **불명확 지점**:
  - "살수" → `SPRINKLER`인지 `SPRAYING`인지 택1 확정 필요(기존 스프링클러+살수 통합 추정).
  - "기타" → 대응 enum 없음(`MANUAL`로 흡수? 신규 값 추가?).
- **필요**: 3 UI 옵션 ↔ 4 enum 매핑 규칙을 백엔드와 확정.
- **참고**: 물의 양(조금/보통/많이)은 `LOW/NORMAL/SUFFICIENT`로 1:1 매핑 — 충돌 없음.
- 출처: [물주기](2026-07-13-record-text-compose-watering.md)

## C-8. 비료 주기 — 사용 비료 드롭다운 vs 자유 문자열

- **디자인**: "사용 비료"가 **드롭다운(선택형)**.
- **API**: `FertilizingDetailRequest.materialName` = 자유 `string`(required).
- **필요**: 드롭다운 옵션 소스 확정(비료 카탈로그 API 존재? 하드코딩 목록? 자유 입력 전환?).
- 출처: [비료 주기](2026-07-13-record-text-compose-fertilizing.md)

## C-9. 비료 주기 — 사용량 단위 enum

- **스펙(확정)**: 비료 사용량 단위 = **g, ml** 2종 (기존 kg → 수정 g/ml).
- **API**: `FertilizingDetailRequest.amountUnit` enum = `[KG]` **단일값뿐**.
- **불일치**: 확정 UI(g/ml)와 API enum(KG)이 **전혀 겹치지 않음**.
- **필요**: 백엔드 enum에 `G`, `ML` 추가(또는 KG→G/ML 교체). 추가 전까지 전송 값 미정.
- 출처: [비료 주기](2026-07-13-record-text-compose-fertilizing.md)

## C-10. 비료 주기 — 진행 방식 필수 여부

- **디자인**: "진행 방식" 필수(`*`).
- **API**: `FertilizingDetailRequest.applicationMethod` enum `[SOIL, FOLIAR]` = **optional**.
- **필요**: 필수 여부 일치화(C-3/C-5와 동일 패턴 — UI 필수 ↔ API optional 반복).
- 출처: [비료 주기](2026-07-13-record-text-compose-fertilizing.md)

## C-11. 병해충 관리 — 사용 농약/대상 병해충 드롭다운 vs 자유 문자열

- **디자인**: "사용 농약", "대상 병해충"이 드롭다운(선택형).
- **API**: `PestControlDetailRequest.pesticideName`(required), `pestTarget`(optional) = 자유 `string`.
- **필요**: 드롭다운 옵션 소스 확정(농약/병해충 카탈로그 API? 자유 입력 전환?). C-8과 동류.
- 출처: [병해충 관리](2026-07-13-record-text-compose-pest-control.md)

## C-12. 병해충 관리 — 총 살포량 단위 (ml vs L)

- **디자인**: "총 살포량 **(ml)**" 라벨 고정.
- **API**: `PestControlDetailRequest.totalSprayAmountUnit` enum = `[L]` 고정.
- **불일치**: UI ml ↔ API L.
- **필요**: 총 살포량 단위를 L로 통일할지 ml 허용(enum 추가)할지 확정.
- **참고**: 농약 사용량 단위(`pesticideAmountUnit`)는 `[ML, G]`로 UI g/ml와 **일치**(충돌 없음).
- 출처: [병해충 관리](2026-07-13-record-text-compose-pest-control.md)

## C-13. 잡초 관리 — 진행 방식 필수 여부

- **디자인**: "진행 방식" 필수(`*`).
- **API**: `WeedingDetailRequest.weedingMethod` enum `[HAND, MACHINE, MULCHING, HERBICIDE]` = **optional**.
- **필요**: 필수 여부 일치화. **C-5(물주기)·C-10(비료)와 동일 반복 패턴** — workType 상세의
  "UI 필수 ↔ API optional"은 일괄 정책으로 결정 권장.
- **참고**: 옵션 워딩(손으로 뽑기=HAND/예초기 사용=MACHINE/멀칭=MULCHING/제초제 사용=HERBICIDE)은
  enum과 1:1 일치 — 워딩 충돌 없음.
- 출처: [잡초 관리](2026-07-13-record-text-compose-weeding.md)

## C-14. 수확 — `harvestSource` 입력 누락

- **API**: `HarvestDetailRequest.harvestSource` enum `[CULTIVATED, FORAGED]`(재배/채취) = **required**.
- **디자인**: 수확 폼에 재배/채취 선택 입력 **없음**.
- **필요**: UI에 추가할지, 기본값(CULTIVATED?) 전송할지, 백엔드에서 optional 완화할지 확정.
- 출처: [수확](2026-07-13-record-text-compose-harvest.md)

## C-15. 수확 — "최종 수확 완료" 토글 대응 필드 없음

- **디자인**: "최종 수확 완료" 토글 존재.
- **API**: `HarvestDetailRequest`에 해당 플래그 **없음**.
- **필요**: 다년생(BR-RECORD-008) 관련 필드로 백엔드 추가 여부 확인. 없으면 저장 불가.
- 출처: [수확](2026-07-13-record-text-compose-harvest.md)

## C-16. 수확 — 재배 기간 단위 (개월 고정 vs YEAR/MONTH)

- **디자인**: "재배 기간 **(개월)**" 라벨 고정, 단위 선택 UI 없음.
- **API**: `growthPeriodUnit` enum `[YEAR, MONTH]`.
- **필요**: MONTH 고정 전송인지, YEAR 지원 필요한지 확정.
- 출처: [수확](2026-07-13-record-text-compose-harvest.md)

---

## C-18 — 상세 화면 "참참참의 코칭"(AI) 데이터 소스 부재

- **Figma**: 상세 화면(`1498:21864`)에 "참참참의 코칭" 카드(칭찬 badge + 코칭 불릿) 존재.
- **배포 Swagger**: `RecordDetailResponse`에 코칭 필드 **없음**. AI/coaching/advice 등 **별도 엔드포인트도 없음**
  (2026-07-14 확인).
- **필요**: 코칭을 어떤 API로 제공하는지(상세 응답 필드 추가 vs 별도 생성 엔드포인트), 생성 시점,
  실패/생성 전 상태 정의. 배포 전까지 프론트는 섹션 생략 또는 placeholder(디자인 확정 필요).
- **심각도**: 높음(기능 자체가 백엔드 미제공).
- 출처: [상세 캡처](2026-07-14-record-detail-planting-seed.md), [상세 구현 계획](2026-07-14-record-detail-implementation-plan.md).

---

## C-19 — 수정(PATCH) 시 기존 사진 보존 불가 (media id 부재)

- **문제**: 수정은 `PATCH /farming-records/{id}` = 생성과 동일한 `SaveRecordRequest`. 그 `mediaIds`는
  **required**(minItems 0). 그러나 상세 조회 `RecordDetailResponse`는 **`imageUrls`(문자열 URL)만** 주고
  **media id를 주지 않는다**(2026-07-14 확인). 편집 폼을 기존 사진으로 초기화할 방법이 없다.
- **영향**: 사진이 있는 기록을 편집 후 저장하면 `mediaIds`를 채울 수 없어 **기존 사진이 서버에서 삭제**된다
  (무손실 편집 불가 = 데이터 손실).
- **필요(백엔드)**: 상세 응답이 media를 `{id, url}` 형태(또는 별도 id 배열)로 반환해야 편집 시 재전송 가능.
  또는 PATCH가 `mediaIds` 생략(=변경 없음)을 허용하도록 부분 업데이트 지원.
- **심각도**: 높음(데이터 손실). 해소 전까지 무손실 편집 구현 불가.
- 출처: [상세 구현 계획](2026-07-14-record-detail-implementation-plan.md).

---

## 상태 요약

| ID | 항목 | 심각도 | 상태 |
|---|---|---|---|
| C-1 | 필터 복수 선택 vs 단일 파라미터 | 높음(기능 제약) | 협의 대기, 프론트 단일선택 우회 |
| C-2 | "시판 구매" enum 부재 | 높음(전송 불가) | 백엔드 enum 추가 대기 |
| C-3 | 모종 번식법 필수 여부 | 중간 | 협의 대기 |
| C-4 | propagationMethod 그룹/단위 매핑 | 중간 | 확인 대기 |
| C-5 | 물주기 상세 필수 여부 | 낮음 | 협의 대기 |
| C-6 | 날씨 온도 범위 vs 단일 | 중간 | 확인 대기 |
| C-7 | 물주기 진행 방식 옵션 통합/enum 매핑 | 중간 | 협의 대기 |
| C-8 | 비료 사용 비료 드롭다운 vs 자유 문자열 | 중간 | 옵션 소스 확인 대기 |
| C-9 | 비료 사용량 단위: 확정 g/ml ↔ API `[KG]` 단일 | 높음(전송 불가) | 백엔드 enum 추가 대기 |
| C-10 | 비료 진행 방식 필수 여부(UI 필수↔API optional) | 낮음 | 협의 대기 |
| C-11 | 병해충 사용 농약/대상 병해충 드롭다운 vs 문자열 | 중간 | 옵션 소스 확인 대기 |
| C-12 | 병해충 총 살포량 단위(ml↔L) | 중간 | 확인 대기 |
| C-13 | 잡초 진행 방식 필수 여부(UI 필수↔API optional) | 낮음 | 협의 대기(C-5/C-10과 일괄) |
| C-14 | 수확 `harvestSource`(재배/채취) UI 누락(API required) | 높음(전송 불가) | UI 추가/기본값 협의 대기 |
| C-15 | 수확 "최종 수확 완료" 토글 대응 필드 없음 | 중간 | 백엔드 필드 추가 확인 대기 |
| C-16 | 수확 재배 기간 단위(개월 고정↔YEAR/MONTH) | 낮음 | 확인 대기 |
| C-17 | 메모 최소 30자 규칙(디자인 미반영) | 중간 | 에러 문구 보완 필요 |
| C-18 | 상세 "참참참의 코칭"(AI) 데이터 소스 부재 | 높음(백엔드 미제공) | 코칭 API 확인/추가 대기, v1 섹션 생략 |
| C-19 | 수정(PATCH) 시 media id 부재로 기존 사진 보존 불가 | 높음(데이터 손실) | 상세 응답 media id 반환 or 부분 업데이트 대기 |

> **dev 반영 상태**: 위 [origin dev 재대조](#️-2026-07-13-origin-dev-재대조-중요) 참고.
> C-2/C-7/C-9/C-14/C-15는 dev에서 해소(미배포). C-11은 id 기반으로 변경(카탈로그 API 필요).
> 나머지는 미해소 또는 경미. **프론트 쓰기 구현은 dev 배포 + Swagger 재동기화 후 착수 권장.**
