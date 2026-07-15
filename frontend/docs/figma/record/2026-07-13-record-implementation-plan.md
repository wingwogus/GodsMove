# 영농 기록(Record) 구현 계획서

- 작성일: 2026-07-13 ("기록 디자인 수집 끝" 시점 종합)
- 범위: 지금까지 캡처된 화면 기준. 음성 기록/리포트/상세/수정/삭제는 미수집 → 이 계획 밖.
- 선행 참고: [HANDOFF.md](HANDOFF.md), [백엔드 충돌 트래킹](2026-07-13-record-backend-conflicts.md),
  [작업 유형 확정 워딩](2026-07-13-record-work-type-labels.md).

---

## 1. 수집/구현 현황

| 화면 | 캡처 | 구현 |
|---|---|---|
| 기록 메인 / default (리스트) | ✅ | ✅ 완료 (`RecordListView`, 읽기 전용) |
| 필터 바텀시트 3종(작물/영농 활동/기간) | ✅ | ✅ 완료 (`RecordFilterSheets`, 단일 선택) |
| FAB 스피드다이얼(음성/텍스트/닫기) | ✅ | ✅ 완료 (`RecordListView`, 음성은 비활성) |
| 텍스트 작성 / default | ✅ | ✅ 완료 (`RecordComposeView`) ⚠️dev-계약 |
| 텍스트 작성 / workType 8종 분기 | ✅ (심기 씨앗·모종, 물주기, 비료, 병해충, 잡초, 수확 / 가지·순 정리·기타=상세없음) | ✅ 완료 (8종 서브폼) ⚠️dev-계약 |
| 텍스트 작성 / 입력 검증 에러 | ✅ (규칙·문구) | ✅ 완료 (일부 조합 문구 근사) |
| 음성 기록 / 리포트 / 상세·수정·삭제 | ❌ 미수집 | ❌ |

**이번 계획 대상 = FAB 스피드다이얼 + 텍스트 기록 작성(생성) 플로우 → 구현 완료.**

> ⚠️ **dev-계약 선구현**: 텍스트 작성(쓰기) 경로는 배포 Swagger가 아니라 미배포
> `origin/dev` 계약 기준으로 선구현됨. **dev 배포 + `sync_swagger_spec.py` 재동기화
> 전에는 실제 제출이 실패할 수 있음** (`backend-conflicts.md` C-2/C-9/C-14 등). 아래
> "구현 완료" 블록의 상세 참조.

### 구현 완료 (2026-07-13, origin dev 계약 기준, 빌드 성공)

- FAB **스피드다이얼**(딤 `#1a1a1a`@64% + 음성/텍스트/닫기) — `RecordListView`. 음성은 후속(비활성).
- **`RecordComposeView`** + `RecordComposeViewModel`: 공통 필드(날짜 / **농지→작물 종속** /
  작업유형 / 메모) + **날씨 자동조회**(`GET /farms/{id}/weather`) + 사진(PhotosPicker→media 업로드)
  + workType **8종 상세 서브폼 전부** + 입력 검증 + `POST /farming-records` 제출.
- **⚠️ origin dev 계약 대상**: `plantingMethod`/`propagationMethod`(PURCHASED), `FertilizerAmountUnit`
  G/ML, `IrrigationMethod` 점적/살수/기타, `isLastHarvest`, `pesticideId`/`pestId`(농약 검색 시트 +
  병해충 종속 드롭다운), memo min 30. **배포 Swagger(구 형태)와는 다르므로 dev 배포 전엔 실제
  제출이 실패할 수 있음** — 사용자 승인 하에 dev 배포 예정 기준으로 선구현.
- 신규 DS 컴포넌트 0 (기존 재사용). 사용 비료/체크박스/심은 방법 세그먼트는 피처 로컬 처리.
- 파일: `Features/Record/{Domain/RecordComposeModels, Data/DTOs/RecordComposeDTOs,
  Data/RecordEndpoint·RecordRepository(확장), Presentation/ViewModels/RecordComposeViewModel,
  Presentation/Views/RecordComposeView}`.

### 알려진 후속(미완/근사)

- 검증 문구: 2필드 1영역은 농지·작물 조합 문구만 구현. 작업/메모는 필드별 문구(에러 케이스
  문서의 완전 조합 문구는 근사) — 후속 정교화.
- 사용 비료: 카탈로그 API 부재로 자유 입력(디자인은 드롭다운). 총 살포량 단위 L 고정(라벨 L로 표기).
- 오프라인 우선 쓰기(SwiftData 큐잉)는 미적용 — 현재 네트워크 제출. `PendingRecordStore` 후속.
- 상세/수정/삭제·음성·리포트 미구현.

---

## 2. API 준비도 — ⚠️ 쓰기 경로는 백엔드 이슈로 부분 차단

- 생성 엔드포인트 `POST /api/v1/farming-records` (`SaveRecordRequest`)는 배포됨.
- 그러나 [충돌 문서](2026-07-13-record-backend-conflicts.md) C-2~C-16 중 **전송 불가(높음)** 항목이
  특정 workType 제출을 막음:
  - **C-2** 심기 "시판 구매" propagationMethod enum 부재
  - **C-9** 비료 사용량 단위 g/ml ↔ API `[KG]` 단일 (겹치는 값 없음)
  - **C-14** 수확 `harvestSource`(required) UI 입력 없음
- 결론: **필드/enum이 API와 깨끗하게 맞는 workType부터** 쓰기 구현, 차단된 workType은 백엔드
  반영 후. 아래 3단계로 분리.

### workType별 제출 가능 여부(현재 API 기준)

| workType | 상세 API | 제출 가능? | 막는 충돌 |
|---|---|---|---|
| 가지·순 정리 / 기타 | 없음 | ✅ 가능 | — |
| 물주기 | watering | ✅ 가능 | (C-5 필수여부는 UI측 완화로 처리 가능) |
| 잡초 관리 | weeding | ✅ 가능 | (C-13 동일) |
| 병해충 관리 | pestControl | △ 대체로 가능 | C-11(농약/병해충 옵션 소스), C-12(총 살포량 ml↔L) |
| 심기 | planting | △ 부분 | C-2(시판 구매), 씨앗/모종 매핑 |
| 비료 주기 | fertilizing | ❌ 차단 | C-9(단위 g/ml↔KG) |
| 수확 | harvest | ❌ 차단 | C-14(harvestSource) |

---

## 3. 화면/네비게이션 구성

```
RecordListView (기존)
 └─ FAB 탭 → 스피드다이얼(딤 #1a1a1a@64% + 음성/텍스트/닫기)
     ├─ "텍스트로 기록하기" → RecordComposeView (신규, fullScreenCover 또는 push)
     └─ "음성으로 기록하기" → (미수집, 비활성/후속)
```

- 스피드다이얼: 기존 FAB를 토글해 3버튼(음성/텍스트/닫기) + 전면 딤. `RecordListView`에
  오버레이 상태(`@State isSpeedDialOpen`)로 구현. 딤 탭 또는 닫기(X)로 닫힘.
- `RecordComposeView`: top-app-bar(뒤로가기 + "기록하기") + 스크롤 폼 + 하단 고정 완료.

---

## 4. 상태 모델 (RecordComposeViewModel, @Observable)

```
// 공통
date: Date?                    // 필수
weather: (condition, temp)     // 자동 조회 표시(입력 아님) — C-6
farmId: UUID?                  // 필수
cropId: UUID?                  // 필수 (농지 선택 후 좁힘)
workType: WorkType?            // 필수
memo: String                   // 필수, ≤500
mediaIds: [UUID]               // 0~5 (사진 업로드 후 id)

// workType별 상세 (선택된 것만)
planting / watering / fertilizing / pestControl / weeding / harvest  // 각 Detail 서브상태
```

- workType 변경 시 상세 서브폼 교체(가지·순 정리/기타는 상세 없음).
- **오프라인 우선(AGENTS 핵심 제약)**: 쓰기는 SwiftData에 먼저 저장 후 백그라운드 동기화.
  현 리스트는 네트워크 전용이나, **작성(쓰기)은 로컬 우선**이 원칙 → `PendingFarmStore`
  (온보딩 밭 등록)의 pending 큐 패턴을 참고해 `PendingRecordStore` 도입 검토.
  (단, 사진/미디어 업로드는 네트워크 필요 — 오프라인 시 큐잉 정책 별도 결정.)

## 5. 입력 검증

- [에러 케이스 문서](2026-07-13-record-text-compose-error-cases.md)의 규칙:
  - 2필드 1영역(진행 작물=농지+작물, 작업 내용=작업유형+메모)은 **영역 단위 조합 문구** +
    필드별 red border.
  - 500자 초과 문구 우선.
- 완료 버튼: 필수 미충족 시 비활성(`#e0e0e0`), 충족 시 활성(primary/bold) — 트리거 시점
  (완료 탭 일괄 vs blur) 확인 필요.

## 6. 디자인 시스템 매핑 (전부 기존 재사용, 신규 DS 컴포넌트 0 목표)

| UI | 컴포넌트 |
|---|---|
| top-app-bar(뒤로+타이틀) | `AppTopAppBar` (detail) |
| 날짜 | `AppDateField` |
| 날씨(읽기전용, 연초록) | 화면 로컬 표시 박스(입력 아님) |
| 농지/작물/작업유형/각종 드롭다운 | `AppDropdown` |
| 메모 | `AppTextEditor` (0/500) |
| 심기 방법·모종 등 2택 세그먼트 | `AppSegmentedControl` 또는 select-area(확인) |
| 수량/기간 등 숫자 입력 | `AppTextField` |
| 잘 모르겠음 | 체크박스 컴포넌트(DS 유무 확인 — 없으면 후보) |
| 최종 수확 완료 | `AppToggle` |
| 사진 첨부(0/5) | `AppImageUploadSlot` + 목록 |
| 완료 | `AppButton` (secondary/bold, 비활성/활성) |
| 스피드다이얼 버튼 | `AppButton`(xlarge 원형) 조합 + 딤 오버레이 |

## 7. SE 2·3 레이아웃

- 작성 폼이 길다(수확 1406pt). `ScrollView` + 하단 safe-area-aware 고정 완료 버튼.
- 키보드 노출 시 활성 입력/완료 버튼 가림 방지(스크롤 보정).
- 2필드 좌우 분할(171+171)은 SE 폭에서 최소 탭 타깃 유지 확인.

---

## 8. 권장 구현 순서 (제안)

1. **FAB 스피드다이얼**(딤 + 음성/텍스트/닫기). 텍스트→`RecordComposeView` 진입까지. (백엔드 무관)
2. **RecordComposeView 골격**: 공통 필드(날짜/농지/작물/작업유형/메모/사진) + 검증 + 완료.
   상세 없는 workType(가지·순 정리/기타)부터 end-to-end 제출 동작.
3. **상세 서브폼**을 제출 가능한 workType부터 추가: 물주기 → 잡초 → (병해충) → (심기).
4. **차단 workType(비료 C-9, 수확 C-14, 심기 시판구매 C-2)**은 백엔드 반영 후 상세 제출 연결.
   그 전까지 UI만(제출 비활성 or 로컬 저장) 처리하고 사유 표시.
5. 음성 기록/리포트/상세·수정·삭제는 캡처 수집 후 별도 계획.

## 9. 착수 전 확정 필요(사용자/백엔드)

- **백엔드 협의**: 충돌 C-1~C-16 (특히 전송불가 C-2/C-9/C-14). 리스트 복수 선택(C-1)도.
- 오프라인 쓰기 정책(로컬 우선 + 미디어 업로드 큐잉) 범위.
- 완료 버튼 검증 트리거 시점.
- 날씨 자동 조회 소스/저장 규칙(C-6).
- 농지→작물 종속(농지 선택 시 작물 목록 필터) 데이터 소스.
