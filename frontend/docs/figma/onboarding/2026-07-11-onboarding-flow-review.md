# Onboarding Flow and Offline Review

- 작성일: 2026-07-11
- 범위: 현재 SwiftUI 인증·온보딩 구현의 사전 검토. Figma 화면 캡처가 추가되면 화면별 판단을 이 문서에 연결한다.
- 기준: `Business Rule.md`의 오프라인 임시저장 원칙, `BR-USER-001`, `BR-USER-002`, 현재 frontend/backend 소스

## 현재 구현 범위

### 인증과 앱 시작

1. 카카오·네이버·Apple 로그인을 제공한다.
2. 로그인 성공 시 access/refresh token을 Keychain에 저장하고, 로그인 응답의 onboarding 상태로 Home 또는 온보딩을 분기한다.
3. 앱 재시작 시 refresh token과 SwiftData의 `CachedMemberProfile`로 즉시 라우팅한 뒤 토큰을 백그라운드에서 갱신한다.
4. `isAuthenticated && isOnboarded`일 때만 `MainTabView`를 노출한다. 따라서 온보딩 미완료 회원은 Home, 영농기록, 커뮤니티로 진입하지 못한다.

### 온보딩

1. 현재 구현은 랜딩 → 기본 정보·프로필 → 작물 선택 → 농지 위치 → 완료 제출 순서다.
2. 입력 초안은 `UserDefaults`에, 프로필 사진 원본은 Application Support에 저장한다.
3. 작물은 인증 후 서버 목록을 불러와 클라이언트에서 검색·카테고리 필터링한다.
4. 농지 위치는 JUSO 주소 검색 → V-World 좌표/필지 → 토지특성 조회로 구성되며, 필지를 찾지 못하면 면적 직접 입력으로 진행한다.
5. 최종 제출은 선택 프로필 사진 업로드 후 `POST /api/v1/auth/onboarding/complete` 한 번으로 member, farm, member_crop를 생성한다.

## 보존해야 할 설계 원칙

| 영역 | 현재 의도 | Figma 적용 시 지켜야 할 점 |
| --- | --- | --- |
| 앱 시작 | 완료 회원은 로컬 캐시로 즉시 Home 진입 | 네트워크 대기 화면으로 Home 진입을 막지 않는다. |
| 온보딩 진입 | 미완료 회원은 Home 대신 온보딩 | 시각적 back/close 동작이 Home 우회를 만들지 않게 한다. |
| 초안 | 단계 이동·백그라운드에서 로컬 복구 | 새 레이아웃에서도 입력값, 선택 작물, 사진 참조가 유실되지 않게 한다. |
| 최종 저장 | 회원·기본 농장·작물을 원자적으로 생성 | 단계마다 별도 서버 저장을 추가하지 않는다. |
| 네트워크 | 인증, 작물 조회, 주소/필지 조회, 사진 업로드, 완료 제출은 온라인 필요 | 연결 불가·로딩·재시도 상태를 화면 설계에 포함한다. |

## 확인된 보완 후보

### P0 — 닉네임 필수 규칙이 Step 1 검증과 요청 DTO 방어에서 누락됨

**최신 계약 재검증 완료 (2026-07-11).** 배포 Swagger snapshot SHA-256
`3cc2a1870dbc6006a9dd3591e7e1c1aee5bb188c4ac836c15d58657babdf2541`와
`origin/dev` 병합 후 backend `AuthRequests.CompleteOnboardingRequest`가 모두
nickname을 필수로 선언한다. 상세 근거는
[Step 1 Figma-계약 충돌](2026-07-11-onboarding-step-1-figma-contract-conflict.md)을 참고한다.

Step 1 Figma는 닉네임에 필수 표시를 한다. 실제 backend
`CompleteOnboardingRequest.nickname`도 `@NotBlank`다. 반면 현재
`BasicProfileView.isValid`는 nickname을 확인하지 않아 빈 값으로 다음 단계로
진행할 수 있고, `OnboardingCompleteRequestDTO`도 빈 nickname을 막지 않는다.
그 결과 사용자는 마지막 제출에서만 일반적인 실패 메시지를 받는다.

- 근본 원인: Step 1 UI 검증과 client DTO 검증이 backend 계약의 nickname 필수 조건과 동기화되지 않았다.
- 보완 방향: 화면 CTA와 DTO 양쪽에서 공백을 제거한 nickname 필수 검증을 적용하고, 이 흐름을 회귀 테스트로 고정한다.
- Figma 영향: 닉네임 빈 값에서는 `다음`을 활성화하지 않으며, validation state가 캡처되지 않았다면 기존 입력 컴포넌트 패턴으로 오류 메시지를 제공한다.
- 캡처 주의: `onboarding / step 1 - 필수 정보 전체 입력` Figma 프레임은 nickname placeholder가 남아 있는데도 `다음`을 활성화한다. 이는 화면 목업의 상태 불일치로 기록하며, 구현 조건을 완화하는 근거로 사용하지 않는다.

### P0 — 오프라인에서 토큰 갱신 실패가 로그아웃으로 처리됨

`TokenRefreshCoordinator.performRefresh()`는 네트워크 단절, 서버 응답 형식 오류,
실제 인증 거부를 모두 같은 실패로 보고 token store를 비운다. 앱 시작 시
`RootView.bootstrap()`이 이 갱신을 백그라운드로 실행하므로, 캐시로 Home에 들어온
완료 회원도 농가 현장에서 인터넷이 없으면 곧 로그인 화면으로 되돌아갈 수 있다.

- 현재 의도와 충돌: `RootView` 주석은 네트워크 실패가 로그아웃을 만들면 안 된다고 명시한다.
- 보완 방향: 전송 실패·타임아웃·일시적 5xx는 기존 세션/캐시를 보존하고, 명시적 인증 거부(예: 401/403 또는 refresh token 무효)일 때만 토큰을 제거한다.
- Figma 영향: Home 진입 후 일시적 네트워크 끊김을 로그인 화면으로 표현하지 않는다. 필요 시 비차단 연결 상태 또는 다음 요청의 재시도 UI를 사용한다.

### P0 — 미완료 상태에서 세션 만료 시 로그인 화면으로 복귀하지 않음

온보딩 초안이 `basicProfile` 등으로 복구된 상태에서 세션이 만료되면,
`AppState`만 비인증으로 바뀌고 현재 `AuthFlowView`의 `OnboardingViewModel` 단계는
초기화되지 않는다. 이 경우 로그인 랜딩 대신 인증이 없는 온보딩 화면에 계속 남아
작물 조회·최종 제출에서 실패할 수 있다.

- 보완 방향: 비인증 전환 시 명시적으로 랜딩으로 이동시킨다. 초안을 보존할지 삭제할지는 계정 단위 정책과 함께 결정한다.
- Figma 영향: 세션 만료 또는 다시 로그인 필요 상태의 안내/CTA가 필요하다면 별도 상태로 캡처한다.

### P0 — 온보딩 초안이 member에 귀속되지 않음

초안 저장 키가 전역 단일 키여서, 만료 후 다른 계정으로 로그인하거나 향후 로그아웃을
지원할 때 이전 회원의 이름·연락처·농장 정보·사진 참조를 새 회원에게 보여줄 위험이 있다.

- 보완 방향: 로그인 성공 후 member id를 기준으로 초안을 귀속하고, 계정 변경·로그아웃·명시적 취소 시 처리 정책을 정한다.
- 최소 정책: 다른 member의 초안은 절대 자동으로 불러오지 않는다.

### P1 — 단계 이동 전의 입력은 즉시 내구 저장되지 않음

초안은 다음/뒤로 이동과 앱 백그라운드에서만 저장된다. 앱 강제 종료·크래시·OS 종료가
그 전에 발생하면 현재 화면에서 막 입력한 값이 사라질 수 있다.

- 보완 방향: 텍스트·선택 값 변경 시 짧게 debounce한 저장, 또는 포커스 이탈 시 저장을 적용한다.
- Figma 영향: 자동 저장임을 별도로 노출할 필요는 없지만, 재진입 시 복구된 입력 상태를 정상 상태로 다뤄야 한다.

### P1 — 작은 화면과 키보드에서 필수 조작이 가려질 수 있음

기본 정보와 농지 위치 화면은 긴 `VStack` 기반이며 scroll, keyboard avoidance,
safe-area-aware bottom CTA가 없다. iPhone SE 2/3에서 필드·지도·오류 안내가 함께
나오면 `다음` 버튼과 활성 입력란이 가려질 수 있다.

- 보완 방향: 스크롤 가능한 콘텐츠와 safe area에 고정된 하단 CTA를 사용하고, 키보드가 열린 상태를 QA한다.
- Figma 영향: iPhone 13 프레임을 시각 목표로 쓰되 SE에서는 가독성·버튼 접근성을 우선한다.

### P1 — Step 1 진행률과 경력 필드의 디자인·현재 구현이 불일치함

Step 1 Figma는 350pt 진행 바 중 88pt만 활성화해 4단계 중 첫 단계(25%)를
표현한다. 현재 구현은 랜딩과 완료를 포함한 5개 열거형을 그대로 표시하므로
기본 정보 화면에서 40%가 활성화된다. 또한 Figma 라벨은 `귀농 년차`이고
현재 구현·사업 규칙은 `영농 경력(년차)`이다.

- 보완 방향: 화면 단계 수는 랜딩/제출 상태를 제외한 사용자 입력 단계 기준으로 재정의한다.
- 결정 필요: `귀농 년차`가 영농 경력과 같은 `experienceLevel`을 뜻하는지, 별도 제품 데이터인지 확인한다. 별도 의미라면 현재 backend 계약에는 보낼 필드가 없다.

### P1 — Figma Step 2/3와 현재 작물·재배지 단계 순서가 반대임

최신 Figma는 `대표 재배지 설정하기` 지도 화면을 Step 2, `대표 재배지의 작물 설정하기`
화면을 Step 3으로 둔다. 현재 `OnboardingViewModel.Step`은
`basicProfile → cropSelection → farmLocation` 순서다.

- Figma가 최종 사용자 흐름이라면 `basicProfile → farmLocation → cropSelection` 순서로 바꿔야 한다.
- 저장된 `OnboardingDraftSnapshot.step`이 기존 enum raw value를 보존하므로, 단계 순서를
  단순 `CaseIterable` index에 의존하는 현재 `goNext`/`goBack` 설계도 함께 명시적으로 바꿔야 한다.
- 마지막 `완료` CTA가 곧바로 제출인지, 제출 확인/완료 화면으로 이동하는지는 후속 캡처에서 확정한다.

### P1 — 대표 재배지의 지도 UX는 Figma placeholder가 아니라 기존 위치 해석 흐름을 유지해야 함

Step 2 Figma의 지도는 이미지 fill placeholder다. 실제 구현은 JUSO 주소 검색,
V-World 좌표·필지·토지특성 조회, 지도 탭 필지 재선택, 필지 없음 시 수동 면적 입력을
유지해야 한다. 최신 Swagger `FarmRequest`는 name, roadAddress, latitude, longitude,
dataSource 등을 요구하므로 단순 주소/농지명 입력만으로 완료 처리할 수 없다.

- Figma의 map area, search overlay, zoom controls는 presentation 목표다.
- 좌표 resolving, parcel loading, 실패·retry, 수동 면적 fallback은 Figma에 없더라도
  runtime state로 반드시 포함한다.
- Figma의 `농지명`이 backend `FarmRequest.name`과 같은 제품 개념인지 확인이 필요하다.

### P1 — 대표 재배지 필수값 누락 UI가 주소·농지명 조합별로 필요함

추가 screenshot 기준 Step 2는 주소와 농지명 누락 상태를 세 가지로 분리한다. 주소만
누락되면 `주소지는 필수로 입력해주세요.`, 농지명만 누락되면
`농지명은 필수로 입력해주세요.`, 둘 다 누락되면
`주소지와 농지명은 필수로 입력해주세요.`를 보여준다.

현재 `FarmLocationView`는 `farmName`과 위치 조회 완료 여부로 CTA를 비활성화하고,
`OnboardingCompleteRequestDTO`도 farmName, farmRoadAddress, coordinate를 방어한다.
따라서 API 계약 충돌은 아니지만, Figma 적용 시 입력별 red border와 조합별 메시지
상태를 구현해야 한다.

### P1 — 온라인 전용 단계의 연결 불가 상태가 제품 흐름으로 정리돼 있지 않음

온보딩은 서버 계정 생성 흐름이어서 최종 제출은 온라인이어야 한다. 현재 작물 조회,
주소·필지 조회, 사진 업로드, 완료 제출에 개별 오류 문구 또는 재시도는 일부 있으나,
"현재 연결이 없어 다음 단계로 갈 수 없음"이라는 일관된 상태와 복귀 경로는 없다.

- 보완 방향: 각 온라인 의존 단계의 loading/error/retry/cancel 상태와 재시도 단위를 확정한다.
- 권장: 이미 입력한 초안은 보존하고, 연결 복구 후 같은 단계에서 재시도하게 한다. 로컬에서 완료된 것처럼 Home을 열지는 않는다.

### P1 — 작물 선택은 sticky 검색·카테고리, 최대 5개, 하단 선택 tray가 필요함

Step 3 Figma capture는 검색창과 카테고리 레이블을 scroll 시 상단에 sticky로 고정하고,
작물 리스트를 가나다순으로 보여주며, 선택한 작물을 하단 tray의 chip으로 모아
`완료` CTA를 제공한다. TalkToFigma-selected frame `631:13561`로 scroll 후 sticky
상태를 캡처했다. 선택 최대값은 5개로 확정한다. TalkToFigma-selected frames
`631:12568`/`631:13342`은 최대 5개를 말하고, `631:13342`는 5개 selected chip과
활성 `완료` CTA를 직접 보여준다.

현재 `CropSelectionView`는 4열 chip grid, client-only `인기` category, 제한 없는 다중 선택,
`다음` CTA를 사용한다. 또한 `Crop` domain model이 category code를 버리고 label만 들고 있어
표시 label 변경에 취약하다.

- 보완 방향: category는 `code + label`을 보존하고, Figma tab label로 표시한다.
- 선택 정책: 최소 1개, 최대 5개. 6번째 선택은 추가하지 않고 안내한다.
- 초안 정책: 선택 chip의 순서는 사용자가 선택한 순서를 유지하고, chip `x`와 list row가 같은 상태를 공유한다.
- 오프라인 정책: crop catalog 로드 실패 시 retry를 제공하고 기존 초안 선택을 유지한다.

### P1 — 작물 카테고리 수·label·선택 최대값은 backend 요청 후보로 분리 확인 필요함

Step 3 screenshot은 category를 `총 10개`라고 쓰지만 실제 보이는 label은 9개다. 최신
Swagger와 backend enum도 9개다. 또한 Figma는 `열매·과실`, `줄기·가지` 표기를 쓰지만
backend label은 `열매/과실`, `줄기/가지`다. 마지막으로 Figma-selected frames와 제품 결정은
최대 5개를 요구하지만 backend `cropIds`는 non-empty만 검증한다.

상세 근거는 [Step 3 Figma-계약 후보](2026-07-11-onboarding-step-3-figma-contract-candidate.md)에
분리했다. Figma 요구가 제품 규칙으로 확정되면 backend team 요청 항목으로 전환한다.

### P1 — 가입 완료 화면의 `재배지 추가하기`는 다중 재배지 draft와 별도 farm endpoint가 필요함

Figma `onboarding / 가입 완료` 화면은 `재배지 추가하기`와 `시작하기` 두 CTA를 가진다.
사용자 확인 기준으로 `재배지 추가하기`는 Step 2로 돌아가고, 추가 재배지도 위치/농지명
입력 Step 2와 작물 선택 Step 3을 한 쌍으로 수행한다. `시작하기`는 회원가입과 로그인
완료 후 Home으로 이동한다.

2026-07-12 구현 기준 frontend `OnboardingDraft`는 `farms[]`와 `activeFarmIndex`를
가지며, `OnboardingCompleteView`는 화면 진입 즉시 자동 제출하지 않는다. 사용자가
`시작하기`를 누를 때만 완료 요청을 보낸다. 최신 backend `CompleteOnboardingRequest`는
여전히 단일 `farm`과 top-level `cropIds`만 받는다.

- frontend 방향: Figma 흐름대로 여러 재배지를 draft에 받을 수 있게 한다.
- backend 합의 방향: onboarding complete를 `farms[]`로 확장하지 않고, 마이페이지 농지 추가/수정에도
  재사용 가능한 standalone farm 추가 endpoint를 만든다.
- 현재 DTO 정책: `POST /api/v1/auth/onboarding/complete`에는 첫 번째/대표 재배지만 보낸다.
- 남은 보완: 두 번째 이후 재배지는 standalone farm endpoint가 확정되면 순차 저장 또는 outbox 저장으로 연결한다.
- 상세 근거: [가입 완료 capture](2026-07-11-onboarding-completion.md),
  [가입 완료 / 추가 재배지 저장 계약 메모](2026-07-11-onboarding-completion-multi-farm-contract-candidate.md)

### P2 — 로그인 응답의 기존 프로필 정보를 온보딩 폼에 반영하지 않음

소셜 로그인 응답에는 name, phone, birthDate 등의 값이 있을 수 있지만 현재 새
`OnboardingDraft`로 옮기지 않는다. 사용자는 이미 provider가 제공한 값을 다시 입력해야
할 수 있다.

- 보완 방향: 서버 응답의 비어 있지 않은 값만 초안에 prefill하고, 사용자가 수정할 수 있게 한다.
- Figma 영향: 자동 입력된 필드, 수정 가능 여부, 선택 프로필 사진의 출처를 확인한다.

### P2 — 소셜 prefill의 수정 가능·복구 규칙을 정해야 함

Step 1 Figma는 소셜에서 들어온 것으로 보이는 이름·연락처 필드를 회색 배경으로
표현하며, 생년월일 등은 값이 있을 때도 같은 상태로 표현할 필요가 있다. 다만
회색이 읽기 전용을 뜻하는지 단순 prefill 상태인지는 캡처만으로 확정할 수 없다.

- 권장: provider 값은 초기에 채우되 사용자가 수정할 수 있게 한다. provider가 주지 않은 필드만 입력을 요구한다.
- 초안 규칙: 같은 member의 재진입 초안이 있으면 사용자가 수정한 초안을 우선하고, 다른 member의 값으로 덮어쓰지 않는다.

### P2 — 수동 면적 입력의 값 검증이 부족함

필지를 찾지 못했을 때 면적 입력란은 비어 있지 않은지만 확인한다. `0`, 소수점만 있는
문자열 등은 화면을 통과할 수 있고, 결과적으로 서버 검증 실패 또는 면적 없는 수동 입력이
만들어질 수 있다.

- 보완 방향: 유한한 양수인지 클라이언트에서 검증하고, 오류 문구와 CTA 비활성 조건을 확정한다.

### P2 — 로그아웃 API는 준비됐지만 제품 진입점은 없음

`AuthRepository.logout()`은 구현되어 있으나 현재 화면에서 호출하는 경로는 없다.
마이페이지 로그아웃 화면이 Figma/제품 범위에 포함되면 token, member cache,
onboarding draft의 정리 정책을 함께 연결해야 한다.

## Figma 캡처마다 확인할 흐름 항목

각 화면 문서에 아래 항목을 함께 기록한다.

- 화면이 어떤 인증/온보딩 상태에서 열리는지
- back, close, skip, submit이 초안과 라우팅에 미치는 영향
- loading, empty, error, retry, disabled, keyboard 상태의 디자인 유무
- 네트워크 단절 시 보존할 로컬 데이터와 재시도할 요청
- iPhone SE 2/3와 Dynamic Type에서 스크롤·하단 CTA·필수 텍스트가 유지되는지
- Home으로 전환되는 정확한 성공 조건

## 최종 계획 전 결정할 사항

1. refresh 실패를 `일시적 네트워크 오류`와 `명시적 인증 만료`로 분리하는 기준
2. member 단위 초안 키 및 계정 전환·로그아웃·세션 만료 시 초안 보존 정책
3. 자동 저장의 저장 시점과 사진 파일 정리 정책
4. 각 온라인 의존 화면의 표준 연결 불가·재시도 UX
5. 소셜 provider 프로필 값 prefill 여부와 우선순위
6. Figma에 없는 런타임 상태를 디자인 시스템으로 구현할 범위
7. standalone farm 추가/수정 endpoint의 최종 path, method, request body, 실패 복구 정책

2026-07-12 결정 완료:

- `가입 완료` 화면은 서버 제출 전 진입한다.
- `시작하기` tap 시 서버 제출을 수행한다.
- frontend 온보딩 draft는 여러 재배지를 지원한다.
- 현 onboarding complete API에는 첫 번째/대표 재배지만 보내고, 추가 재배지는 standalone farm endpoint가 확정되면 연결한다.
