# ChamChamCham Frontend — 사전 개발 감사(Pre-Dev Audit) 인수인계

작성일: 2026-07-06 · 브랜치: `feat/init-front`

## 이 문서의 목적

본격적인 기능 개발(마이페이지, 영농기록, 커뮤니티 등)에 들어가기 전에, 온보딩 플로우 중심으로 아키텍처/UI/문서 정합성을 점검한 세션의 인수인계 기록이다. 다음 세션이 이 맥락을 다시 조사하지 않고 이어서 작업할 수 있도록, **무엇을 확인했는지 / 무엇을 고쳤는지 / 무엇이 아직 결정되지 않았는지**를 남긴다.

## 확정된 사실 (다시 조사하지 말고 그대로 신뢰해도 되는 것들)

백엔드 소스(`backend/api`, `backend/domain`)를 직접 읽어서 검증한 내용이다. 문서가 아니라 코드 기준으로 확인했다.

- **구현된 컨트롤러는 3개뿐**: `AuthController`(카카오/네이버/애플 로그인 + 온보딩 완료), `CropController`, `TestController`. Farm/Community/Voice/Report/Policy 도메인은 컨트롤러가 아예 없다 — 이 도메인들에 대해 네트워킹 코드를 미리 만들지 말 것.
- **`Member` 엔티티**: `id, email?, phone, status(ACTIVE/WITHDRAWN), name, birthDate, nickname, experienceLevel: Int?, managementType?, withdrawnAt`. **`region` 필드는 없다.**
- **`ManagementType`**: `AGRICULTURAL_INDIVIDUAL`, `AGRICULTURAL_CORPORATION`, `NON_REGISTERED_FARMER` 3값.
- **`Farm` 엔티티**: `ownerMember, name, roadAddress, jibunAddress?, latitude?, longitude?, pnu?, landCategory?, areaSqm?, areaIsManualEntry, boundaryCoordinates(자식 테이블), dataSource`. `region`/`city`/`street`/`farmType` **없음**.
- **`MemberCrop`**(테이블명 `member_crop`): `member_id, farm_id, crop_id` 뿐 — `planting_year`/`status`/`started_on` 없음.
- **`Crop`**: `externalNo, name, usePartCategory` — `category`/`lifecycleType`/`defaultUnit` 없음.
- **온보딩 완료(`POST /api/v1/auth/onboarding/complete`)**: 한 번의 호출로 member + farm + member_crop을 함께 생성한다. `farm`, `cropIds`는 서버단에서 `@NotNull`/`@NotEmpty`로 강제 — 프론트 UX 게이팅이 아니라 백엔드가 요구하는 필수값이다.
- **`OnboardingStatusResolver.missingFields` 토큰**: `NAME, PHONE, BIRTH_DATE, NICKNAME, EXPERIENCE_LEVEL, MANAGEMENT_TYPE` — `REGION`/`CROP` 없음.
- **`CropController`**: 쿼리 파라미터/페이지네이션 없는 단순 `GET` 3종(`/crops`, `/crops/categories`, `/crops/categories/{category}/crops`). 검색은 클라이언트에서 로컬 필터링.

## 이번 세션에서 한 일 (커밋 순서대로)

1. `1529e5d` — SyncEngine의 `fatalError` 스텁, 미채택 `Syncable` 프로토콜 제거. AuthViewModel/OnboardingViewModel이 View로부터 `ModelContext`를 직접 받아 SwiftData를 만지던 걸 `MemberProfileCache` 추상화로 교체(리포지토리 패턴 위반 수정). AGENTS.md(루트/프론트엔드) 백엔드 연동 섹션 최신화. API 명세서의 "온보딩 완료 시 기본 농장 생성 안 함" 오기 수정.
2. `e591402` — 온보딩 3개 화면(BasicProfile/CropSelection/FarmLocation)의 레이아웃을 통일(불필요한 ScrollView 제거, CTA 버튼 하단 고정). 지도 크기 확대(220→300pt). 주소 검색 취소 처리 버그 수정. AddressSearchSheet 자동 포커스 추가. 빈 AccentColor를 브랜드 그린으로 채움.
3. `2d9a674` — `ERD 초안.md`를 실제 백엔드 스키마로 재작성(member/farms/crops/member_crop). 저장소 공통 규칙(`Preserve the domain term member`)을 위반하던 `users`/`user_id`/`user_crops`/`user_consents` 네이밍을 전부 `member` 기준으로 교체. 정의 없이 관계에만 존재하던 유령 엔티티 `report_snapshots` 삭제(BR-REPORT-001과 모순). `coaching_feedback.risk_signals`, `farming_records.deleted_at` 컬럼 추가(BR 문서와 정합). `Business Rule.md`의 BR-USER-002/003/004 수정. 아무도 참조하지 않던 `Route.swift`/`ScreenPathRouter.swift`/`FarmLocationViewModel.retry()` 삭제.
4. **(미커밋)** `docs/superpowers/specs/2026-07-02-frontend-onboarding-flow-plan.md`의 "backend와 협의 필요" 액션아이템들을 실제 코드 확인 결과로 전부 갱신(위 "확정된 사실" 참고).
5. **(미커밋)** `frontend/ChamChamCham/Info-Additions.plist`에 `UIUserInterfaceStyle = Light` 추가 — 앱을 다크모드 없이 라이트 전용으로 고정. 빌드된 앱의 실제 Info.plist에 반영된 것까지 확인함.

> 4, 5번은 이 문서를 작성하는 시점에 아직 커밋되지 않았다. 다음 세션(또는 사용자)이 커밋 여부를 확인할 것.

## 아직 결정되지 않은 것 (제품/PM 판단 필요 — 코드로 풀 수 없음)

- **BR-RECORD-008**(다년생 작물 재배연차 필수): 이 규칙이 전제하는 "다년생/일반 작물" 구분(`lifecycle_type`)이 실제 `Crop` 스키마에 없다. 다른 방식으로 구분할지, 규칙을 재검토할지 결정 필요.
- **BR-STATE-001 vs BR-VOICE-008**: Voice Session 상태머신은 `FAILED`를 `PROCESSING`에서만 허용하는데, BR-VOICE-008은 "비정상 종료 시 무조건 FAILED"라고 되어 있어 `RECORDING`/`WAITING_CONFIRMATION` 중 종료 시 상태가 불명확.
- **BR-SEARCH-001**: 검색 대상에 "공지사항"이 있는데 ERD 어디에도 해당 엔티티가 없음. 신규 추가할지 규칙에서 뺄지 결정 필요.

## 백로그 (급하지 않음, 실제 다음 기능 작업 때 같이 정리 권장)

- **Auth 파일 위치**: `Features/Onboarding`에 얹혀있는 Auth 관련 파일(`AuthViewModel`, `AuthRepository`, `KakaoLoginBridge`, `NaverLoginBridge`, `AppleSignInCoordinator` 등)을 `Features/Auth`로 분리 — 마이페이지/로그아웃 기능을 시작할 때 진행.
- **테스트 타겟 부재**: 프로젝트에 테스트 타겟이 아예 없다(`project.pbxproj`에 `PBXNativeTarget`이 앱 타겟 하나뿐). pbxproj 직접 수정은 위험하므로 Xcode GUI(File > New > Target)로 만들 것을 권장. 만들어지면 `OnboardingViewModel`/`FarmLocationViewModel`부터 테스트(순수 로직, 규칙 밀도 높음).
- **디자인시스템 정리**: `Color.Background/Object/Text/Icon/Border`, `AppTypography` 같은 시맨틱 토큰이 정의만 있고 실사용은 legacy 축약형(`appPrimary` 등)뿐이다. 둘 중 하나로 정리 필요.
- **네트워킹 마이너 이슈**: `APIClient`가 디코딩 실패를 `try?`로 뭉뚱그림, Keychain OSStatus 에러 처리 미흡.

## 감사 범위 밖 (진짜 다음 기능 개발)

`MainTabView`의 홈/영농기록/커뮤니티/마이페이지 4탭이 전부 `Text` 플레이스홀더. 백엔드도 아직 Farm/Community/Voice/Report/Policy 컨트롤러가 없으므로, 온보딩 이후가 진짜 다음 개발 범위다.

## 참고

- 이 감사에서 수정한 문서: [`docs/ERD 초안.md`](docs/ERD%20초안.md), [`docs/Business Rule.md`](docs/Business%20Rule.md), [`docs/superpowers/specs/2026-07-02-frontend-onboarding-flow-plan.md`](../docs/superpowers/specs/2026-07-02-frontend-onboarding-flow-plan.md), [`AGENTS.md`](AGENTS.md), 루트 [`AGENTS.md`](../AGENTS.md)
- 원본 조사 결과(81건 발견사항)는 이 세션의 워크플로우 산출물에 있었으나 현재 대화 세션 컨텍스트에만 존재 — 필요하면 이 문서의 "확정된 사실"/"백로그" 목록이 요약본이라고 보면 된다.
