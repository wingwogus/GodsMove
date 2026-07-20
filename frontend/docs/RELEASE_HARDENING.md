# Release Hardening — 남은 작업 마스터 추적

- 작성일: 2026-07-19
- 담당: frontend
- 기준 브랜치: `dev`
- 범위: 메인 기능 구현 완료 이후 릴리스 전까지의 남은 작업 전체(디자인 폴리싱, QA 수정,
  앱 안정성, 오프라인/캐싱 견고화)

이 문서는 **단일 마스터 추적 문서**다. 화면별 세부는 `figma/<feature>/` 아래 캡처/HANDOFF/
remaining-work 문서로 링크한다. 새 항목이 생기면 여기에 먼저 추가하고, 작업 완료 시 상태 열을
갱신한다.

## 개요

- **현재 단계**: 릴리스 하드닝(기능 개발 → 품질/안정성 다듬기).
- **최종 목표**: 농업인 사용자는 농장지에서 네트워크가 불안정할 수 있다. 프론트 담당으로서
  **불안정한 네트워크에서도 견고하게 동작하는 오프라인 우선 앱**을 완성하는 것이 최종 목표다
  (`../AGENTS.md`의 Offline-First core constraint 참조).
- **4대 작업 축**:
  - 축 A — 디자인·UIUX 폴리싱(Figma 시안 불일치)
  - 축 B — 팀 QA 수정 항목
  - 축 C — 앱 안정성(Instruments: 메모리 누수 / 크래시 / race condition)
  - 축 D — 오프라인/캐싱 견고화

## 진행 상황 요약

| 축 | 상태 | 비고 |
| --- | --- | --- |
| 메인 기능 | ✅ 대부분 구현 완료 | 홈/기록/리포트/커뮤니티/검색/온보딩/마이 구현됨 |
| A. 디자인 폴리싱 | 🔄 진행 중 | 에셋 화질·스와이프백·백버튼 통일 완료(아래 기록), 화면별 diff는 남음 |
| B. QA 수정 | 🔄 진행 예정 | 아래 표 |
| C. 앱 안정성 | ⏳ 대기 | A·B 반영 후 착수 |
| D. 오프라인/캐싱 | ⏳ 대기(선행 감사 필요) | 최종 목표, 선행 코드 감사부터 |

---

## 축 A — 디자인·UIUX 폴리싱

Figma 시안과 어긋나는 디테일을 화면별 캡처 문서 기준으로 맞춘다. 각 화면 캡처/HANDOFF:

- 홈: [`figma/home/HANDOFF.md`](figma/home/HANDOFF.md)
- 기록: [`figma/record/HANDOFF.md`](figma/record/HANDOFF.md)
- 리포트: [`figma/report/HANDOFF.md`](figma/report/HANDOFF.md)
- 검색: [`figma/search/HANDOFF.md`](figma/search/HANDOFF.md)
- 마이: [`figma/mypage/HANDOFF.md`](figma/mypage/HANDOFF.md)
- 커뮤니티: [`figma/community/README.md`](figma/community/README.md) · [remaining-work](figma/community/2026-07-11-community-remaining-work.md)
- 온보딩: [`figma/onboarding/README.md`](figma/onboarding/README.md)
- 디자인 시스템: [`DESIGN_SYSTEM_HANDOFF.md`](DESIGN_SYSTEM_HANDOFF.md)

> 규칙: Figma가 디자인 시스템과 충돌하면 디자인 시스템 값을 유지하고 불일치를 보고한다
> (`../AGENTS.md` Design-System Source of Truth). 실제 diff 수정은 축 B의 화면 항목과 함께
> 워크트리에서 진행한다.

### 진행 기록 — 공통 디자인 폴리싱 (2026-07-20)

`dev`에 순차 반영 완료. 커밋: `62e7430c`(폴리싱), `6c003ec7`(탭바 원복), `6aa7971a`(백버튼).

| 항목 | 상태 | 내용 |
| --- | --- | --- |
| 에셋 화질 — 날씨 아이콘(5) | ✅ 완료 | 저화질 원인은 SVG가 벡터 보존 없이 1x 슬롯에만 등록(날씨는 24×24 원본이 40/96pt로 업스케일). SVG 유지 + `preserves-vector-representation` 적용으로 전 크기 선명화. |
| 에셋 화질 — 일러스트(16) | ✅ 완료 | 저화질 SVG를 고해상도 PNG(400×400 / 712×400)로 교체. base name 동일해 Swift 코드 변경 없음. |
| 하단 탭바 공간 축소 | ↩️ 원복 | `AppNavBar` 높이 72→60 등 시도했으나 `dev`에서 원복. 재검토 대상. |
| 디테일 화면 엣지 스와이프 뒤로가기 | ✅ 완료 | 커스텀 백버튼이 시스템 back을 숨겨 `interactivePopGestureRecognizer`가 비활성화된 상태. `Core/Navigation/UINavigationController+SwipeBack.swift`로 pop 제스처 delegate 복원(루트에선 미발동). |
| 백버튼 아이콘·크기 통일 | ✅ 완료 | 크기는 이미 32pt(48×48). 아이콘이 `chevron_backward`/`arrow_back_ios_new`로 갈려 있던 것을 Figma 표준 `arrow_back_ios_new`로 전 화면(13곳) 통일. `CropPicker` close(모달 X)는 제외. SearchView·CommunityDetail의 자체 백버튼(구조상 AppTopAppBar 불가)도 포함. |

> 미정리: 디자인 시스템 데모 코드(`AppTopAppBar.swift` #Preview, `DesignSystemGallery.swift`)에
> 예시용 `chevron_backward`가 남아 있음(실제 화면 아님).

---

## 축 B — 팀 QA 수정 항목

팀과 대강 QA를 진행해 나온 문제점/개선점. **병렬 안전성** 열은 워크트리 병렬 실행 가능 여부다
(자세한 규칙은 아래 "워크트리 병렬 실행 가이드").

| 화면 | 항목 | 우선순위 | 병렬 안전성 | 상태 |
| --- | --- | --- | --- | --- |
| 프로필 | (현재 없음) | - | - | - |
| 온보딩 | 폴리곤 / 작물 QA | P1 | 화면 독립 ✅ | 남음 |
| 온보딩 | 생년월일 date picker — 최소한 오늘 이전까지만 선택 | 후순위 | 화면 독립 ✅ | ✅ 완료 (DatePicker `in: ...Date()`) |
| 온보딩 | 폴리곤 진입점 버튼 별도로 두기 + 지도 대한민국 권역 제한 | P1 | 화면 독립 ✅ | ✅ 완료 (상세: [2026-07-20-farm-map-draw-entry-and-korea-restriction.md](figma/onboarding/2026-07-20-farm-map-draw-entry-and-korea-restriction.md)) |
| 홈 | 날씨 UI 정렬 (홈·상세) | P1 | 화면 독립 ✅ | ✅ 완료 (날씨 상세 주소지 좌측 정렬 통일) |
| 홈 | 최고·최저온도 날씨 미반영 문제 | P1 | 화면 독립 ✅ | ⚠️ 백엔드 이슈 (프론트 정상, 아래 주석 참조) |
| 검색 | 검색 인터랙션 개선 — 검색 후 단어 재탭 없이 바로 결과로 | P1 | 화면 독립 ✅ | ✅ 완료 (포커스 바인딩으로 1탭 즉시 결과) |
| 기록 | (현재 없음) | - | - | - |
| 리포트 | 참참참 코칭 섹션 — 레이아웃 하단 잘림 | P0 | 화면 독립 ✅ | ✅ 완료 (LazyHStack→HStack) |
| 리포트 | 도넛 그래프 QA (항목 4개 이상으로 만들어 검증) | P1 | 화면 독립 ✅ | 남음 |
| 커뮤니티 | 댓글 UI (하단 여백 제거) | P1 | 화면 독립 ✅ | 남음 |
| 공통 | 커뮤니티/마이 게시물 좋아요 하트색 반영 | P1 | ⚠️ 공유 컴포넌트 | 남음 |
| 공통 | 텍스트 필드/에디터 키보드 올라올 때 '완료' 버튼이 키보드 위에 붙는 문제 | P0 | 화면별 독립(공유 modifier 아님) | ✅ 완료 (2026-07-20, 커밋 `f63aafbf` + `62e7430c`, 아래 주석) |
| 공통 | 작물 선택뷰 — 전체 탭 추가 + 최대 5개 제한 | P1 | ⚠️ 공유 컴포넌트 | 남음 |

> ⚠️ **최고·최저온도 미반영은 프론트 결함이 아니다.** DTO 디코드 키(`minTemperature`/
> `maxTemperature`)는 Swagger와 일치하고 홈/상세 뷰 모두 값을 정상 바인딩한다. 백엔드
> `KmaShortTermForecastAdapter`가 KMA 단기예보의 TMN/TMX를 **02:00/23:00 발표 시간대에만** 받을 수
> 있어, 그 외 시간에는 의도적으로 null을 내려보낸다(과거 TMP min/max 폴백 버그를 막기 위한 설계).
> 이때 `/weather/*` 응답의 `partial.missing`에 `"todayMinMax"`가 담기고 프론트는 이를 `"-"`로
> 표시한다. **후속(백엔드/제품 결정)**: (1) 백엔드가 최근 02:00 발표분 TMN/TMX를 하루 종일 캐싱하거나,
> (2) 프론트가 `partial.degraded`/`missing`을 읽어 `"-"` 대신 "일부 정보 갱신 중" 인디케이터 노출.
>
> ⚠️ 공유 항목(하트색 / 키보드 완료버튼 / 작물 선택뷰)은 `Core/`의 공유 컴포넌트·modifier를
> 건드릴 가능성이 커, 여러 워크트리가 동시에 만지면 머지 충돌이 난다. **먼저 순차로 처리해
> `dev`에 머지한 뒤** 화면별 병렬 작업을 분기한다.
>
> ✅ **키보드 '완료' 버튼 문제 해결 (2026-07-20).** 원인은 키보드 accessory 툴바가 아니라
> **하단 고정 CTA 버튼**이 SwiftUI 기본 keyboard-avoidance로 밀려 올라간 것이었다. 예상과 달리
> 공유 modifier가 아니라, 각 화면 루트에 `.ignoresSafeArea(.keyboard, edges: .bottom)`를
> 인라인으로 추가하는 화면별 수정이었다(기존 `SearchView.swift`와 동일 패턴, 새 추상화 없음).
> 적용 6화면: RecordCompose(+농약 시트)/CommunityCompose/FarmingRecordPicker/CropSelectionBody
> (커밋 `f63aafbf`), BasicProfile/FarmLocation(디자인 폴리싱 커밋 `62e7430c`에 선반영). 빌드 통과.
> **잔여 검증**: 키보드 실기기/시뮬레이터 육안 확인(특히 RecordCompose 하단 상세 필드가 키보드에
> 가려져 스크롤로도 못 닿는지, iPhone SE 포함) — 공통 QA 체크리스트의 키보드 항목 기준.

---

## 축 C — 앱 안정성 (Instruments)

A·B 반영이 끝난 뒤 착수. 목표는 릴리스 품질의 안정성 확보.

- **메모리 누수 (Leaks / Allocations)**: `@Observable` 뷰모델의 `Task`/클로저 retain cycle,
  네비게이션 반복 시 뷰모델 해제 여부. 특히 **음성 기록 WebRTC 세션**의 연결/해제 라이프사이클
  집중 점검(`record-feature-status` 참조).
- **크래시**: 반복 시나리오(탭 전환, 리스트 스크롤, 작성→저장→목록 복귀)에서 크래시 지점 확인.
- **race condition (Thread Sanitizer)**: Swift 6 strict concurrency로 상당수는 컴파일 타임에
  차단됨. 런타임에서는 actor 경계 이탈, `@MainActor` UI 업데이트 누락, 비동기 이미지/네트워크
  콜백 순서만 집중 확인.

---

## 축 D — 오프라인/캐싱 견고화 (최종 목표)

농업인 사용자의 불안정한 네트워크를 견디는 것이 핵심. `../AGENTS.md`의 **Offline-First core
constraint**(모든 write는 SwiftData first, 네트워크에 블록되지 않음)를 실제로 지키는지 감사부터.

- **[선행] Offline-First 준수 감사**: 현재 각 기능의 write가 정말 "SwiftData first, background
  sync"인지, 아니면 네트워크에 블록되는 얇은 API 클라이언트인지 코드로 확인. 이 결과에 따라
  아래 세부 태스크를 확정한다.
- **리포트 프리캐싱**: 리포트 데이터를 SwiftData에 미리 캐싱해 오프라인에서도 열람 가능하게.
- **이미지 캐싱**: 커뮤니티/기록/리포트 사진을 미리 캐싱(`URLCache` 또는 커스텀 디스크 캐시).
- **기록 로컬 임시저장(draft)**: 기록 작성 중 내용을 로컬(SwiftData draft)에 저장/복원해
  네트워크 단절·앱 종료에도 유실 없게.

---

## 워크트리 병렬 실행 가이드

이 앱은 **단일 앱 타깃**이라 `Core/`·`App/`의 공유 파일을 여러 브랜치가 동시에 수정하면
머지 충돌이 난다. 병렬 작업은 다음 규칙을 따른다.

**핵심 규칙: 화면 독립 항목만 병렬, 공유 파운데이션 항목은 먼저 순차 처리.**

1. **먼저 순차 처리 (공유 파일 충돌원)** — `dev`에 순서대로 머지:
   - 좋아요 하트색 (design system Color 토큰 또는 공유 좋아요 컴포넌트)
   - ~~키보드 '완료' 버튼 문제~~ → ✅ 완료 (공유 modifier 아니라 화면별 인라인 수정으로 판명, 위 참조)
   - 작물 선택뷰 전체 탭 + 최대 5개 (공유 `CropPicker` 계열 컴포넌트)
2. **병렬 안전 (화면 격리)** — 3~4개 워크트리 동시 진행 가능:
   - 온보딩(폴리곤/작물/진입점/date picker)
   - 홈(날씨 정렬·최고최저온도)
   - 검색(인터랙션 개선)
   - 리포트(코칭 잘림·도넛 그래프)
   - 커뮤니티(댓글 여백)
3. 각 워크트리는 작업 완료 시 **해당 `figma/<feature>/` 문서**와 **이 문서의 상태 열**을 갱신.

> 착수 시 공유/독립 판정을 실제 파일 경로로 재확인한다. 예: 하트색이 진짜 design system
> 토큰인지 화면 로컬 값인지 확인 후 병렬 여부를 최종 결정.
>
> 참고: `../AGENTS.md`는 기본적으로 순차 작업을 권장한다("prefer sequential… over multi-agent
> parallel fan-out"). 워크트리 병렬은 위처럼 **화면이 파일 수준에서 독립**일 때에 한한다.

---

## 공통 QA 체크리스트

화면 작업을 마칠 때마다 확인(`../AGENTS.md` Small Device Layout Rule 기준):

- [ ] iPhone SE 2/3에서 주요 텍스트/탭 라벨/핵심 값이 잘리지 않는다.
- [ ] 키보드가 활성 입력 필드나 '완료'/제출 버튼을 가리지 않는다.
- [ ] 콘텐츠가 화면보다 길 때 스크롤된다(고정 높이로 잘리지 않음).
- [ ] loading / empty / error / disabled / submitting / retry 상태가 happy-path 외에도 존재한다.
- [ ] Dynamic Type을 크게 했을 때 주요 액션과 필수 정보가 잘리지 않는다.
- [ ] 아이콘은 `Assets.xcassets/icon/`의 커스텀 SVG를 먼저 사용(SF Symbol은 폴백).
- [ ] Swift diff에 중복 컴포넌트·raw 파운데이션 값·미승인 디자인 시스템 변경이 없다.

---

## 관련 문서

- 프론트 규칙: [`../AGENTS.md`](../AGENTS.md)
- 비즈니스 규칙: [`Business Rule.md`](Business%20Rule.md) (`BR-*`)
- API 계약(소스): [`swagger/`](swagger/) — `docs/API 명세서/`는 **사용 금지 아카이브**
  ([`_archive/README.md`](_archive/README.md) 참조)
- 아키텍처: [`../../docs/superpowers/specs/2026-07-02-frontend-architecture-design.md`](../../docs/superpowers/specs/2026-07-02-frontend-architecture-design.md)
