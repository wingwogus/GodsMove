# 영농기록 상세 — 심기 작업 결과(씨앗)

- Captured: 2026-07-14
- Source MCP calls: `get_selection` → `read_my_design` → `export_node_as_image(PNG, 2x)`
- Figma node: `1498:21864` / frame "심기 작업 결과 - 씨앗" / 390 × 1135
- State: 영농기록 리스트에서 한 건(심기·씨앗)을 탭했을 때의 **결과 상세(읽기)** 화면.
- PNG 저장: 클라이언트 인라인 한계로 미저장. 구조·텍스트·색상은 아래 `read_my_design` 값으로 정밀 기록.
- **범위 참고(2026-07-14 보강)**: workType별로 화면에서 달라지는 부분은 **'작업 정보' 섹션뿐**이다
  (제목·메모·사진·AI 코칭 구조는 8종 공통). 따라서 나머지 workType은 별도 Figma 캡처 없이,
  이 문서의 [workType별 '작업 정보' 항목](#worktype별-작업-정보-항목) 절에 항목 목록으로 통합 기록한다.

---

## 레이아웃 (위 → 아래)

```
Status Bar (iPhone, 54h)
top-app-bar (390×60)  ← INSTANCE, 재사용
  ├ icon-leading: icon/arrow_back_ios_new (뒤로)
  ├ 타이틀: (이 프레임에선 빈 텍스트)  #242428 / 28 SemiBold
  └ icon-trailing: icon/more_vert (⋮ — 수정/삭제 메뉴 추정)
content (390×969, 좌우 padding 20)
  ├ text (218h)
  │   ├ title row: "심기"(28 SemiBold #1a1a1a) + badge-label(날짜·날씨 칩)
  │   └ 메모 본문 (18 Medium #4f4f4f, lineHeight 27)
  ├ divider (#f3f3f3, 2px)
  ├ info (185h)
  │   ├ "작업 정보" (24 SemiBold #4f4f4f)
  │   └ info card (#fafafa, radius 16)
  │       ├ "1번밭 - 도라지" (20 SemiBold #4f4f4f)  ← 농지 - 작물
  │       ├ line (#e0e0e0)
  │       └ details (label #878787 15 SemiBold / value #4f4f4f 16 SemiBold)
  │           ├ 심기 방법 : 씨앗 심기
  │           └ 심은 씨앗량 : 50g
  ├ image (191h)
  │   ├ "작업 사진" (24 SemiBold #4f4f4f)
  │   └ image-area: 144×144 썸네일 가로 스크롤 (radius 8, 프레임 밖까지 이어짐)
  ├ divider
  └ ai (211h)
      ├ "참참참의 코칭" (24 SemiBold #4f4f4f)
      └ content card (#f9fcf3, radius 12)
          ├ 칭찬 badge (#e6f7bf 배경 / #27865c 텍스트, 16 SemiBold, radius 8)
          └ 코칭 본문 (불릿 3줄, 16 Medium #4f4f4f, lineHeight 24)
```

## 텍스트 스타일 / 색상 (증거)

| 요소 | 폰트 | 크기/웨이트 | 색상 |
|---|---|---|---|
| workType 제목("심기") | Pretendard SemiBold | 28 / -0.28 | `#1a1a1a` |
| 날짜·날씨 칩 텍스트 | Pretendard Medium | 15 / -0.3 | `#878787` |
| 칩 구분선(Line) | — | stroke | `#acacac` |
| 메모 본문 | Pretendard Medium | 18 / -0.36, lh27 | `#4f4f4f` |
| 섹션 헤딩(작업 정보/사진/코칭) | Pretendard SemiBold | 24 / -0.24 | `#4f4f4f` |
| info 카드 제목(농지-작물) | Pretendard SemiBold | 20 / -0.2 | `#4f4f4f` |
| detail 라벨(심기 방법 등) | Pretendard SemiBold | 15 / -0.3 | `#878787` |
| detail 값(씨앗 심기/50g) | Pretendard SemiBold | 16 / -0.32 | `#4f4f4f` |
| 코칭 칭찬 badge 텍스트 | Pretendard SemiBold | 16 / -0.32 | `#27865c` |
| 코칭 본문 | Pretendard Medium | 16 / -0.32, lh24 | `#4f4f4f` |

## 주요 색/치수

- 배경: `#ffffff`. info 카드 `#fafafa` r16. 코칭 카드 `#f9fcf3` r12. 칭찬 badge `#e6f7bf` r8.
- 날짜·날씨 칩 배경 `#fafafa` r8. divider `#f3f3f3` 2px. 카드 내부 구분선 `#e0e0e0`.
- content 좌우 padding 20 (8152→8172). 썸네일 144×144, r8.

## 데이터 매핑(가정 — API 확인 필요)

| UI | 소스(추정) |
|---|---|
| 제목 "심기" | `workType.label` |
| 날짜 칩 | `workedAt` (YYYY. MM. DD.) |
| 날씨 칩 | `weatherCondition` + 온도 범위 |
| 메모 본문 | `memo` |
| info: 농지-작물 | `farmName` - `cropName` |
| info: 심기 방법 / 심은 씨앗량 | `PlantingDetail.plantingMethod` / `seedAmount(g)` |
| 작업 사진 | media 목록(0~5) 썸네일 |
| 참참참의 코칭 | **AI 구조화 결과**(칭찬 문구 + 코칭 불릿) |

## workType별 '작업 정보' 항목

상세 화면에서 workType에 따라 달라지는 유일한 영역이 **'작업 정보' 카드**다. 아래는
각 workType이 표시할 수 있는 **항목(라벨) 목록**이며, compose 시 입력된 값을 읽어 표시한다.

> **표시 규칙 (사용자 확정, 2026-07-14)**
> - 카드 맨 위 `농지 - 작물`(예: `1번밭 - 도라지`)은 **모든 workType 공통·항상 표시**.
> - 그 아래 `항목 - 값` 행은 **입력된 것만** 렌더한다. 값이 없으면(미입력) **행 자체를 숨김**.
> - 아래 값은 **예시**일 뿐 — 실제 값은 입력에 따라 달라지거나 필드 자체가 없을 수 있다(항목만 참고).
> - 값의 표기(단위/라벨)는 compose 상세 도메인(`RecordComposeModels.swift`)의 enum `label`과 동일 소스.

| workType | 표시 항목(라벨) | compose 상세 필드/enum | 예시 값 |
|---|---|---|---|
| 심기 (씨앗) | 심기 방법 / 심은 씨앗량 | `plantingMethod`(=씨앗 심기) / `seedAmount`+`SeedAmountUnit(g)` | 씨앗 심기 / 50g |
| 심기 (모종) | 심기 방법 / 모종 번식법 / 심은 모종 수 | `plantingMethod`(=모종 심기) / `PropagationMethod` / `seedlingCount`+`SeedlingUnit(주)` | 모종 심기 / 꺾꽂이 / 50주 |
| 물주기 | 물의 양 / 진행 방식 | `IrrigationAmount`(조금·보통·많이) / `IrrigationMethod`(점적·살수·기타) | 보통 / 살수 |
| 비료 주기 | 사용 비료 / 비료 사용량 / 진행 방식 | 비료명(자유입력) / `amount`+`FertilizerAmountUnit` / `FertilizingMethod`(토양에 주기·엽면에 뿌리기) | 신의한수비료 / 50(단위) / 토양에 주기 |
| 병해충 관리 | 사용 농약 / 농약 사용량 / 총 살포량 / 대상 병해충 | `pesticideId`→명 / `amount`+`PesticideAmountUnit`(ml·g) / `sprayAmount`+`SprayAmountUnit`(L) / `pestId`→명 | 신의한수농약 / 20ml / 150ml / 짱큰나방 |
| 잡초 관리 | 진행 방식 | `WeedingMethod`(손으로 뽑기·예초기·멀칭·제초제) | 손으로 뽑기 |
| 가지·순 정리 | (없음) | — | — |
| 기타 | (없음) | — | — |
| 수확 | 재배 기간 / 수확량 / 수확 부위 | `growthPeriod`+`GrowthPeriodUnit`(년·개월) / 수확량+단위 / `MedicinalPart`(전초 등) | 24개월 / 500kg / 전초 |

**항목 표기 정합 확인 필요:**
- 비료 사용량 예시 `50kg` ↔ 현재 `FertilizerAmountUnit` = **G/ML만** (KG 없음). 상세 표시 단위와
  compose/서버 단위가 어긋나면 잘못 렌더됨 → [백엔드 충돌](2026-07-13-record-backend-conflicts.md) C-9 라인과 함께 확인.
- 병해충 `농약 사용량(ml)`과 `총 살포량` 예시가 둘 다 ml로 왔으나, compose는 총 살포량 단위 **L 고정** 표기.
  상세 렌더 시 서버 저장 단위를 그대로 보여줄지, L 환산할지 확정 필요.
- 물주기 `물의 양`은 수치가 아니라 **선택값(조금/보통/많이)** — 상세에선 라벨 그대로 표시.
- 가지·순 정리/기타는 상세 필드가 없어 '작업 정보' 카드가 `농지 - 작물` 한 줄만 남음(카드 자체는 유지).

## 패딩 재검증 (2026-07-15)

기존 구현(`RecordDetailView.swift`)이 Figma 수치와 어긋나 재검증. 동일 노드(`1498:21864`)
`read_my_design`로 절대좌표 재확인 후 코드 수정 완료 (빌드 통과).

- **top-app-bar 하단 → 본문 시작 / 제목행 → 메모**: 둘 다 리터럴 `20`(어느 `Spacing` 토큰과도
  불일치, 16과 24 사이) — `headerGap` 상수로 분리.
- **구분선(divider) 좌우 여백**: 모든 구분선이 앞뒤로 대칭 `Spacing.xl`(32)을 갖는다
  (예: 메모 하단→divider 32, divider→'작업 정보' 제목 32). 기존 코드는 이 여백이 아예
  없거나(헤더→divider 0) 위아래로 중복 적용(정보 카드→사진 섹션이 24+24=48로 과다)되어 있었음.
  → `divider`를 `.padding(.vertical, Spacing.xl)`을 자체 내장하도록 바꾸고, 인접 섹션은
  divider 쪽 여백을 추가하지 않도록 정리.
- **정보 카드 내부**: 농지-작물 제목→구분선, 구분선→상세행 모두 리터럴 `12`(8·16 사이, 토큰 없음).
  기존은 `Spacing.md`(16)를 재사용해 살짝 넓었음.
- **라벨-값 행**: 라벨 컬럼이 고정 84pt인데 `HStack(spacing: Spacing.md)`를 추가로 얹어 값이
  16pt 더 밀려나 있었음(합계 100pt) — Figma는 84pt만큼만 띄움. `spacing: 0`으로 수정.
- **사진 섹션**: 구분선 없이 직접 이어지므로 자체 top padding `Spacing.xl`(32) 필요 — 기존은
  위아래 `Spacing.lg`(24)라 30% 부족했음.

## 관찰 / 열린 질문

1. **상세 조회 API 미확인**: `GET /farming-records/{id}` 존재 여부·응답 shape(공통 필드 + workType 상세 +
   media + AI 코칭)을 배포 Swagger에서 확인 필요. list 응답과 별도인지, AI 코칭 필드가 있는지 미확인.
2. **"참참참의 코칭"(AI)**: ⚠️ **배포 Swagger 확인 결과(2026-07-14) 데이터 소스 부재** — `RecordDetailResponse`에
   코칭 필드 없고 별도 코칭/AI 엔드포인트도 없음(충돌 [C-18](2026-07-13-record-backend-conflicts.md)).
   v1은 섹션 생략 권장, 백엔드 코칭 제공 방식 확정 후 추가. Figma엔 성공 상태만 있음.
   - ⚠️ 이 프레임의 코칭 **더미 텍스트가 심기와 불일치**(칭찬은 "약초 전체에 고르게 뿌린 점", 불릿은
     병해충 관리 문구 "반점과 변색/병든 줄기/라벨 사용량"). Figma 목업의 placeholder 혼용으로 판단 —
     실제 스펙 아님. 코칭은 workType 문맥에 맞게 서버 생성.
3. **top-app-bar ⋮(more_vert)**: 수정/삭제 진입점으로 추정. 상세→수정→삭제 플로우 캡처가 이어져야 함.
   타이틀이 빈 값 — 상세 화면은 top-app-bar 타이틀 없이 본문 제목("심기")으로 대체하는지 확인.
4. **작업 사진 가로 스크롤**: 썸네일 5장까지, 프레임 밖으로 이어짐. 사진 0장일 때 섹션 노출 여부 미확인.
5. **색상 `#4f4f4f`**: 섹션 헤딩·값 전반에 쓰이는 톤. `Color.Text`에 대응 토큰 있는지 코드 대조 필요
   (기존 열린 질문의 `#242428`와 함께). raw 추가 금지 원칙 → 기존 토큰 매핑 우선.

## 디자인시스템 후보

- `AppTopAppBar`(detail, trailing = more_vert) — 기존 재사용.
- **info 카드**(라벨-값 행 반복): 상세 화면 workType 8종에서 반복될 구조 → 재사용 컴포넌트 후보.
- **날짜·날씨 칩**: 리스트 caption과 동일 소스 → 공통화 여지.
- **참참참의 코칭 카드**(연초록 카드 + 칭찬 badge + 불릿): AI 코칭 표시가 여러 화면에 반복되면 승격 후보.
- 칭찬 badge(`#e6f7bf`/`#27865c`)는 기존 `AppBadge(secondary)` 후보와 색 일치 여부 대조 필요(기존 열린 질문).

---

**수집 상태**: 상세 화면 **구조는 심기·씨앗 프레임(`1498:21864`) 1건으로 확정**(8종 공통).
workType별 차이는 '작업 정보' 항목뿐이라 나머지 8종은 위 [항목 표](#worktype별-작업-정보-항목)로
통합 기록(별도 캡처 없음, 사용자 확정 2026-07-14). **미수집: 수정·삭제 플로우(⋮ 메뉴 진입),
AI 코칭 비성공 상태(생성 전/실패/로딩·빈), 사진 0장 상태.**
