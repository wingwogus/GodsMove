# My Page 구현 계획 (Implementation Plan)

- 작성: `2026-07-13 KST`
- 근거: `docs/figma/mypage/` 캡처 문서 7건 + 현재 코드/DesignSystem/배포 Swagger 대조.
- 범위: 프로필 메인, 프로필 수정(기본/농업), 게시판 선택 바텀시트,
  setting-card(FarmCard) 컴포넌트, 설정 화면(신규). 알림 화면은 범위 제외.
- 원칙: AGENTS(SwiftUI/iOS17+/Swift6, `@Observable`, SwiftData, URLSession,
  offline-first, `member` 용어, DS 우선) 준수. 캡처 완료 화면부터 구현.

## 1. 현재 코드 상태 (사실)

| 레이어 | 상태 |
|---|---|
| `Features/MyPage/Domain` | `MyMemberProfile`/`PublicMemberProfile`/`MyMemberFarm`/`MemberCropProfile` 존재 |
| `Features/MyPage/Data` | `MemberProfileRepository`(fetchMyProfile/fetchPublicProfile/updateMyProfile), `MemberEndpoint`, DTO 존재 |
| `Core/Networking` | `FarmRepository`/`FarmEndpoint`/`FarmDTOs` 존재 (farm CRUD) |
| `Features/Community` | `CommunityRepository` 등 Data+Domain 완료 (mineOnly/likedOnly 지원) |
| `Features/MyPage/Presentation` | `MyPageView`는 **플레이스홀더**(로그아웃/탈퇴 버튼만), `MyPageViewModel`도 인증만 |
| `Core/DesignSystem` | 컴포넌트 다수 존재(AppCard/AppBadge/AppChip/AppSegmentedControl/AppTextField/AppDateField/AppButton/AppTabBar/AppTopAppBar/AppAvatar/AppDropdown/AppDivider/EmptyStateView/LoadingView 등) |

즉 **Data/API/Domain은 대부분 준비**, **Presentation은 신규 구축** 대상.

## 2. API 매핑 (배포 Swagger 기준)

| 화면/동작 | Endpoint | DTO/스키마 | 상태 |
|---|---|---|---|
| 프로필 메인 헤더 | `GET /members/me/profile` | `MyProfileResponse` | 준비 (repo 존재) |
| 나의 게시물 | `GET /community/posts?mineOnly=true&...` | community posts | 준비 (Community repo) |
| 좋아요 누른 글 | `GET /community/posts?likedOnly=true&...` | community posts | 준비 |
| 게시판 선택 필터 | `?cropId=`, `?postType=` + `GET /crops`, `GET /crops/categories` | Crop | 준비 |
| 기본 정보 저장 | `PUT /members/me/profile` | `UpdateMyProfileRequest` | 준비 (repo 존재) |
| 아바타 업로드 | 미디어 업로드 → `profileMediaId` | (업로드 경로 확인 필요) | 확인 필요 |
| 밭 목록 | `GET /farms` 또는 `GET /members/me/farm-crops` | `FarmResponse`/`FarmCropsResponse` | 준비 (repo 존재, 소스 결정 필요) |
| 밭 추가/수정 | `POST`/`PUT /farms/{id}` | `SaveFarmRequest` | 준비 (좌표/PNU/지목/면적/cropIds 포함) |
| 밭 삭제 | `DELETE /farms/{id}` | — | 준비 |

확정된 enum:

- 자격 `managementType`: `AGRICULTURAL_INDIVIDUAL`(개인 농업인) /
  `AGRICULTURAL_CORPORATION`(농업경영 법인) / `NON_REGISTERED_FARMER`(비경영체).
- 귀농 연차 `experienceLevel`: Int (표시 `N년차`).

> AGENTS의 "Farm DTO에 좌표/PNU/지목/면적 없음" 경고는 **현행 Swagger에서 해소됨**
> (`SaveFarmRequest`/`FarmResponse`에 존재). 이 계획은 최신 Swagger를 기준으로 한다.

## 3. 화면 구성 & DS 매핑

### 3-1. 프로필 메인 (`ProfileMainView`)
- 앱 셸: `AppTopAppBar`(나의 프로필 + 설정/알림), 하단 `AppNavBar`(프로필 선택).
  헤더+탭은 리스트와 함께 스크롤, 하단 내비 고정.
- 프로필 카드: 아바타(`AppAvatar`) + 닉네임/지역·귀농연차 + 작물 뱃지(`AppBadge`).
  작물 표시는 [작물 키워드 배치 규칙](2026-07-13-mypage-profile-crops-expanded.md)
  (`≤3` 전부 / `>3` 3개+`외 n종`, `외 n종` 탭 시 전체). 카드 높이 가변.
- 콘텐츠 탭: `AppTabBar`(나의 게시물 / 좋아요 누른 글).
- 필터 칩: `AppChip`(게시판 선택), 기본/활성 2상태
  ([필터칩 상태](2026-07-13-mypage-profile-main-default.md#filter-chip-states-게시판-선택)).
- 게시물 리스트: `AppListItem` 또는 Community 기존 로우 재사용 검토 + 페이지네이션(cursor).

### 3-2. 게시판 선택 바텀시트 (`BoardSelectSheet`)
- grabber + 섹션(진행중인 작물/기타 작물) 선택 칩(`AppChip` 토글) + `AppButton`(완료).
- 선택 결과를 프로필 메인 필터에 반영(칩 활성 상태 전환).

### 3-3. 프로필 수정 (`ProfileEditView`, 탭 컨테이너)
- `AppTopAppBar`(뒤로가기 + 프로필 수정) + `AppTabBar`(기본 정보 / 농업 정보).
- **기본 정보 탭**: 아바타 편집 + 폼(`AppTextField`/`AppDateField` + `AppSegmentedControl`(자격))
  + `AppButton`(저장). 필드 상태: editable/disabled/placeholder
  ([기본 정보](2026-07-13-mypage-profile-edit-basic.md)).
- **농업 정보 탭**: `등록한 밭` 헤더(개수 뱃지) + `FarmCard`(신규 DS, 아래) 목록
  + 추가/삭제 ([농업 정보](2026-07-13-mypage-profile-edit-farm.md)).

### 3-4. 신규 DS 컴포넌트: `FarmCard` (Figma `setting-card`)
- `Core/DesignSystem/Components/`에 추가. `isSelected` variant(배경 반전).
- 내부는 `AppCard`+행(`AppListItem`/`AppFieldContainer`)+`AppBadge` 조합, raw 값 금지.
- 스펙: [setting-card 문서](2026-07-13-mypage-setting-card-component.md). 네이밍은
  범용 `SettingCard` 대신 도메인 `FarmCard` 검토(YAGNI).

## 4. 상태 모델 (`@Observable` 뷰모델)

- `ProfileMainViewModel`: 프로필 로드 상태(idle/loading/loaded/error), 선택 탭,
  필터(선택된 작물/게시판), 게시물 페이지(cursor, isLoadingMore), 작물 펼침 여부.
- `ProfileEditViewModel`: 폼 draft(name/phone/birthDate/nickname/experienceLevel/
  managementType/profileMediaId), 필드별 검증, isSubmitting, 저장 결과.
- 농업 정보: `FarmListViewModel`(밭 목록, 삭제 선택 모드, 추가 플로우 결과 반영).
- 각 상태에 loading/empty/error/retry 포함(캡처에 없는 런타임 상태는 여기서 정의).

## 5. 네비게이션 & 재사용

- 프로필 탭 루트 = `MyPageView` 대체 → `ProfileMainView`.
- 프로필 메인 상단 **설정 아이콘 → 신규 `SettingsView`**(현재 `MyPageView`의
  로그아웃/회원탈퇴 이관). 알림 아이콘은 비활성/추후.
- **프로필 수정 진입 경로 미정**(설정 화면 항목? 프로필 카드/아바타 편집 탭?) —
  Figma 캡처에 진입점이 없어 구현 중 확정 필요.
- 밭 추가: `추가하기` → `FarmLocationView` → `CropSelectionView` → `SaveFarmRequest`로 밭 생성.
- **선행 과제(블로커)**: `FarmLocationView`/`CropSelectionView`가 `OnboardingViewModel`에
  강결합. 재사용하려면 (a) 의존성 분리(파라미터/콜백/공유VM) 또는 (b) 얇은 래퍼.
  상세: [농업 정보 Implementation Mapping](2026-07-13-mypage-profile-edit-farm.md#implementation-mapping-사용자-지정-2026-07-13).

## 6. Offline-first

- 프로필/밭 조회는 로컬 캐시(SwiftData) 우선 표시 후 백그라운드 갱신 검토.
- 수정 저장(프로필/밭)은 로컬 반영 우선, 네트워크 실패 시 재시도 큐
  (온보딩 `PendingFarmSyncService` 패턴 참고). 네트워크 블로킹 금지(AGENTS).

## 7. 구현 순서 (제안)

1. ✅ **DS `FarmCard` 컴포넌트** 추가 (+ 갤러리 등록, 프리뷰). — 완료(2026-07-13),
   `Core/DesignSystem/Components/FarmCard.swift`, 빌드 통과.
2. ✅ **프로필 메인** — 핵심 구현 완료(2026-07-13), 빌드 통과.
   `Features/MyPage/Presentation/{ViewModels/ProfileMainViewModel, Views/ProfileMainView}.swift`,
   `MainTabView`의 마이페이지 탭을 `ProfileMainView`로 교체.
   - 헤더 카드(아바타/닉네임/지역·연차/작물 뱃지 `외 n종` 펼침), 탭(나의 게시물/좋아요),
     게시물 리스트(`CommunityPostRow` 재사용, mineOnly/likedOnly, cursor 페이지네이션,
     like 토글), 로딩/빈/오류·재시도 상태.
   - 설정 아이콘 → 기존 `MyPageView`(로그아웃/탈퇴)를 sheet로 임시 연결(설정 화면은 4단계).
   - **후속(이 단계에서 미완)**: 게시판 필터 칩 탭 → 바텀시트(3단계),
     프로필 편집 진입점(아바타 편집 어포던스는 시각만), 프로필 읽기 offline 캐시,
     인증 게이트 뒤라 시뮬레이터 런타임 시각 검증은 로그인 후 필요.
3. ✅ **게시판 선택 바텀시트** + 필터 연동(단일 선택) — 완료(2026-07-13), 빌드 통과.
   `Features/MyPage/Presentation/Views/BoardSelectSheet.swift`(+ 로컬 `ChipFlowLayout`),
   `ProfileMainViewModel`에 boards 로딩/분류/선택 추가, 프로필 메인 필터 칩과 연동.
   - grabber + `진행중인 작물`/`기타 작물` 섹션 + `AppChip(.solidPastel)` 단일 선택
     (선택 칩 재탭 시 해제) + `AppButton(.secondary)` 완료.
   - **⚠️ 미결/주의**: 진행중/기타 구분은 boards API에 플래그가 없어 `profile.crops`
     기준으로 client-side 파생 — 의도 확인 필요. 미선택 칩 스타일은 DS `AppChip`
     (흰 배경/그린 텍스트)이 Figma(회색 배경/회색 텍스트)와 다름 — DS 유지, 아래 참조.
4. ✅ **설정 화면(`SettingsView`)** — 완료(2026-07-13), 빌드 통과.
   `Features/MyPage/Presentation/{Views/SettingsView, ViewModels/SettingsViewModel}.swift`.
   로그아웃/회원탈퇴를 이관하고 옛 `MyPageView`/`MyPageViewModel` 삭제. 프로필 메인
   설정 아이콘 → `SettingsView` sheet. 로그아웃/탈퇴는 confirmationDialog 확인.
   회원탈퇴 API 미배포 → 기존 안내 메시지 유지. Figma 캡처 없는 신규 화면이라 DS로 구성.
5. ✅ **프로필 수정 - 기본 정보** — 완료(2026-07-13), 빌드 통과.
   `ProfileEditView`(기본/농업 탭 컨테이너) + `ProfileBasicInfoView`/`ProfileBasicInfoViewModel`.
   폼(이름 readonly / 닉네임 선택 / 연락처 / 생년월일 / 자격 세그먼트 / 귀농 연차) + 검증 +
   `PUT /members/me/profile` 저장. 진입점: 프로필 메인 아바타 편집 → `fullScreenCover`,
   dismiss 시 프로필 리로드. `ManagementType` enum·birthDate 포맷은 온보딩과 동일.
6. ✅ **온보딩 결합 분리 — 방식 변경(중요)**. 온보딩 View 셸을 리팩터하는 대신,
   이미 결합이 없는 **엔진 `FarmLocationViewModel` + `AddressSearchSheet` +
   `CropPickerSheet`(커뮤니티) + `FarmRepository`를 재사용**했다. 온보딩 화면은
   전혀 건드리지 않음(critical flow 회귀 방지). → 사용자가 말한 "FarmLocationView/
   CropSelectionView 재사용"과 문자 그대로는 다르나, 기능(주소검색·지오코딩·필지·작물선택)은
   동일하게 재사용. 필요 시 온보딩 View 자체 재사용은 별도 리팩터 과제.
7. ✅ **프로필 수정 - 농업 정보** — 완료(2026-07-13), 빌드 통과.
   `FarmListView`/`FarmListViewModel`(밭 목록 `GET /farms` + 삭제 모드),
   `FarmAddView`/`FarmAddViewModel`(추가 흐름 → `POST /farms`). `FarmCard`(DS) 재사용.
   **`DELETE /farms/{id}`가 `FarmRepository`/`FarmEndpoint`에 없어 신규 추가함**
   (+ 온보딩 `PreviewFarmRepository` 목 준수 보강). 삭제는 `FarmCard.isSelected` 변형으로
   선택 모드 + 확인 다이얼로그.
8. ✅ **마감** — 아래 "미완/후속" 정리, 문서 갱신, 전체 빌드 통과 확인.

## 미완 / 후속 (마감 시점)

- **런타임 시각 검증**: 마이페이지는 인증 게이트 뒤 → 시뮬레이터 검증은 로그인 후 필요.
  전 화면 빌드는 통과. Figma 대조 시각 QA는 로그인 세션에서 수행.
- **미수집 캡처 반영**: 좋아요 누른 글 탭 세부 레이아웃, 밭 삭제 모드 정식 디자인은
  미수집이라 합리적 기본으로 구현(추후 캡처 시 조정).
- ✅ **뷰모델 테스트 작성 완료**(2026-07-13): `ProfileMainViewModelTests`(작물 표시 규칙,
  탭별 mineOnly/likedOnly 쿼리, 게시판 필터/분류), `ProfileBasicInfoViewModelTests`(프리필,
  검증 게이트, 저장 DTO 매핑), `FarmListViewModelTests`(삭제 모드/선택/삭제 호출),
  `FarmAddViewModelTests`(검증, SaveFarmRequest 빌드), `SettingsViewModelTests`(로그아웃/탈퇴).
  공유 목은 `ChamChamChamTests/Support/MyPageTestSupport.swift`. 옛 `MyPageViewModelTests`는
  `SettingsViewModelTests`로 이관. **전체 117 tests / 30 suites 통과, 회귀 없음.**
- **디자인 시스템 승인 대기**: 게시판 선택 미선택 칩 스타일(3단계) — `AppChip` variant 추가 여부.
- **후속 기능**: 프로필 아바타 이미지 업로드(profileMediaId), 밭 편집(update API 미배포),
  자격 info 가이드, 알림 화면, 프로필 읽기 offline 캐시.

각 단계 후 시뮬레이터 빌드/실행으로 골든 패스 검증(AGENTS Development Loop).

## 8. 확정된 제품 결정 (2026-07-13)

1. **이름 필드 = 수정 불가(표시 전용)**. Figma대로 disabled. 저장 시 `name`은
   기존 값 그대로 전송.
2. **닉네임 = 필수 아님(선택), 편집 가능**. 빈 값 허용, 저장 검증에서 제외.
3. **게시판 필터 = 단일 선택**. 칩 하나만 선택(선택 시 기존 해제), `cropId` 단일 전달.
4. **저장 버튼 활성 조건** = 필수 편집 필드(연락처·생년월일·자격·귀농 연차)가
   모두 유효할 때 활성. 이름(readonly)·닉네임(선택)은 조건에서 제외. 검증 실패는
   해당 필드 헬퍼 메시지로 표시.
5. **밭 목록 데이터 소스 = `GET /farms`** (full `FarmResponse`: 주소/좌표/crops
   포함). 편집에 주소·좌표가 필요하므로 `/members/me/farm-crops`(crops만)보다 적합.
6. **빈 상태 = 기존 `EmptyStateView`** 재사용. 게시물 0 / 밭 0 / 작물 0 각각 처리.
   밭 0개는 카드 없이 `추가하기`만 노출.
7. **설정 = 신규 설정 화면**(현재 `MyPageView`의 로그아웃·회원탈퇴를 이관).
   설정 아이콘 → 설정 화면. **알림 = 이번 범위 제외**(알림 아이콘 비활성/추후).

### 남은 세부(구현 중 자연 확정)

- 단일 선택 필터가 `나의 게시물`/`좋아요` 탭 전환 시 유지되는지 → 공유 상태로
  유지(같은 선택 게시판이 두 탭에 적용)로 기본 처리, 구현 중 확인.
- 검증 실패 헬퍼 색상 토큰(`#ef4444` 계열) → `Color+App`에서 확인 후 사용.

## 9. 미수집 캡처 (마감 전 필요)

- 프로필 메인 / 좋아요 누른 글 탭(레이아웃 차이 확인용)
- 밭 삭제하기 모드 UI
- 콘텐츠 로딩 / 빈 목록 / 오류·재시도 / 추가 로딩

## 10. SE 2/3 레이아웃

- 프로필 카드 작물 뱃지: 폭 375에서 한 줄 개수/줄바꿈 유동 처리(wrap).
- 프로필 수정 폼: 스크롤 + 키보드가 활성 입력/저장 버튼 가리지 않게(safe-area 하단 액션).
- 탭/필터 라벨 축약, `minimumScaleFactor`/`lineLimit` 폴백.

## 11. 검증

```bash
cd /Users/user/Project/ChamChamCham/GodsMove/frontend/ChamChamCham
xcodebuild -scheme ChamChamCham -destination 'platform=iOS Simulator,name=iPhone 17' build
```

뷰모델 단위 테스트(검증 규칙, 필터/페이지네이션 상태) 우선 추가(AGENTS).
