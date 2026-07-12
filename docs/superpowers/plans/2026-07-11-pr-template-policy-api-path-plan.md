# 공통 PR 템플릿과 정책 API 경로 정리 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 저장소 공통 PR 작성 계약을 추가하고 정책 조회·관리자 동기화 API를 복수형 `policies` 리소스 경로로 교체한다.

**Architecture:** `.github` 템플릿은 사람과 AI가 공유하고 루트 `AGENTS.md`는 AI의 작성 의무를 정의한다. Spring MVC 컨트롤러의 경로만 교체해 DTO·서비스·인증 계약은 유지하며, backend API 테스트와 ignored test-ios 하네스 테스트가 같은 경로를 검증한다.

**Tech Stack:** Markdown, Spring Boot 3.x, Kotlin, MockMvc, Swift, XCTest, Xcode Simulator

## Global Constraints

- 기존 정책 API 경로 alias와 deprecation 계층은 추가하지 않는다.
- 정식 경로는 `/api/v1/policies/recommendations`, `/api/v1/policies/{policyProgramId}`, `/api/v1/admin/policies/sync-jobs`, `/api/v1/admin/policies/sync-jobs/{jobId}`다.
- 요청·응답 DTO, 인증, 정렬, 필터, 커서 계약은 변경하지 않는다.
- PR 템플릿은 backend와 frontend가 공유하는 단일 기본 템플릿이다.
- `test-ios` 변경은 로컬 하네스에 적용하지만 Git ignore 상태를 유지하고 커밋하지 않는다.
- 새 의존성, DB 변경, 과거 설계·계획 문서의 소급 수정은 하지 않는다.
- 비밀키, 토큰, 실제 환경변수 값을 커밋이나 PR 본문에 기록하지 않는다.

---

## File Map

- Create: `.github/pull_request_template.md` — 저장소 공통 PR 본문 구조
- Modify: `AGENTS.md` — AI가 공통 템플릿을 따르는 저장소 작업 규칙
- Modify: `backend/api/src/main/kotlin/com/chamchamcham/api/policy/controller/PolicyController.kt` — 일반 정책 API 경로
- Modify: `backend/api/src/main/kotlin/com/chamchamcham/api/policy/controller/AdminPolicySyncController.kt` — 관리자 동기화 API 경로
- Modify: `backend/api/src/test/kotlin/com/chamchamcham/api/policy/controller/PolicyControllerTest.kt` — 일반 정책 경로 계약
- Modify: `backend/api/src/test/kotlin/com/chamchamcham/api/policy/controller/AdminPolicySyncControllerTest.kt` — 관리자 경로 계약
- Modify: `backend/api/src/test/kotlin/com/chamchamcham/config/SecurityConfigAdminTest.kt` — 관리자 경로 보안 계약
- Local-only Modify: `test-ios/GodsMoveAuthHarness/GodsMoveAuthHarness/BackendAPIClient.swift` — 하네스 경로 상수
- Local-only Modify: `test-ios/GodsMoveAuthHarness/GodsMoveAuthHarnessTests/BackendAPIClientTests.swift` — 하네스 요청 경로 계약
- Local-only Modify: `test-ios/GodsMoveAuthHarness/README.md` — 수동 테스트 경로 안내

---

### Task 1: 공통 PR 본문 계약

**Files:**
- Create: `.github/pull_request_template.md`
- Modify: `AGENTS.md:60`

**Interfaces:**
- Consumes: GitHub의 기본 PR 템플릿 탐색 규칙
- Produces: 모든 PR이 사용하는 여섯 섹션과 AI PR 작성 규칙

- [ ] **Step 1: 공통 PR 템플릿 추가**

`.github/pull_request_template.md`를 다음 내용으로 만든다.

```markdown
## 변경 내용

<!-- 무엇을 변경했는지 핵심 결과를 작성합니다. -->

## 변경 이유

<!-- 변경이 필요한 배경, 문제 또는 사용자 영향을 작성합니다. -->

## 영향 범위

<!-- 영향받는 기능, 모듈, API 또는 사용자를 작성합니다. 해당 없으면 `해당 없음`으로 기록합니다. -->

## 검증

<!-- 실행한 명령과 결과를 작성합니다. 실행하지 못한 검증은 이유와 함께 명시합니다. -->

## 배포 및 호환성

<!-- API·DB·설정 호환성, 마이그레이션, 별도 배포 작업을 작성합니다. 해당 없으면 `해당 없음`으로 기록합니다. -->

## 체크리스트

- [ ] 관련 테스트를 추가하거나 수정했습니다.
- [ ] 로컬 검증을 완료했습니다.
- [ ] API·설정·DB 호환성 영향을 확인했습니다.
- [ ] 민감한 정보가 포함되지 않았습니다.
```

- [ ] **Step 2: 루트 AI PR 작성 규칙 추가**

`AGENTS.md`의 Commit Messages 섹션 뒤에 다음 내용을 추가한다.

```markdown
## Pull Requests

- Use `.github/pull_request_template.md` for every pull request, including pull
  requests created by AI agents.
- Complete every template section from the actual diff. Write `해당 없음` when
  a section does not apply instead of deleting it.
- Record exact verification commands and results. Disclose skipped checks and
  remaining risks.
- State API, database, configuration, deployment, and compatibility impact.
- Never include secrets, tokens, private keys, or raw environment values in a
  pull request body.
- Create a Draft pull request unless the user explicitly requests Ready for
  review.
```

- [ ] **Step 3: 템플릿 구조 검증**

Run from repository root:

```bash
rg -n '^## (변경 내용|변경 이유|영향 범위|검증|배포 및 호환성|체크리스트)$' .github/pull_request_template.md
rg -n '^## Pull Requests$|pull_request_template.md|Draft pull request' AGENTS.md
git diff --check
```

Expected: 템플릿에서 여섯 제목이 각각 한 번 검색되고, AGENTS의 PR 규칙이 검색되며 `git diff --check`는 출력 없이 종료한다.

- [ ] **Step 4: 문서 변경 커밋**

```bash
git add .github/pull_request_template.md AGENTS.md
git commit \
  -m "docs(workflow): 공통 PR 작성 품질을 고정" \
  -m "사람과 AI가 변경 목적, 영향, 검증, 호환성을 같은 형식으로 전달하도록 저장소 기본 템플릿과 작성 규칙을 둔다." \
  -m "Constraint: backend와 frontend가 템플릿 하나를 공유한다." \
  -m "Rejected: PR 유형별 템플릿 분리 | 현재 구분을 강제할 구체적 필요가 없다." \
  -m "Confidence: high" \
  -m "Scope-risk: narrow" \
  -m "Directive: AI가 여는 PR은 사용자가 달리 요청하지 않으면 Draft로 만든다." \
  -m "Tested: 템플릿 제목 검색, AGENTS 규칙 검색, git diff --check" \
  -m "Not-tested: GitHub 새 PR 화면의 렌더링은 push 후 확인"
```

---

### Task 2: backend 정책 경로 교체

**Files:**
- Modify: `backend/api/src/test/kotlin/com/chamchamcham/api/policy/controller/PolicyControllerTest.kt`
- Modify: `backend/api/src/test/kotlin/com/chamchamcham/api/policy/controller/AdminPolicySyncControllerTest.kt`
- Modify: `backend/api/src/test/kotlin/com/chamchamcham/config/SecurityConfigAdminTest.kt`
- Modify: `backend/api/src/main/kotlin/com/chamchamcham/api/policy/controller/PolicyController.kt`
- Modify: `backend/api/src/main/kotlin/com/chamchamcham/api/policy/controller/AdminPolicySyncController.kt`

**Interfaces:**
- Consumes: 기존 `PolicyRecommendationService`, `PolicySyncService`, DTO와 인증 principal
- Produces: 네 개의 새 Spring MVC 요청 경로, 기존 응답과 상태 코드

- [ ] **Step 1: 일반 정책 테스트를 새 경로로 변경**

`PolicyControllerTest.kt`의 요청 경로를 다음 값으로 교체한다.

```text
/api/v1/policy-recommendations           -> /api/v1/policies/recommendations
/api/v1/policy-programs/{policyProgramId} -> /api/v1/policies/{policyProgramId}
```

- [ ] **Step 2: 관리자 테스트를 새 경로로 변경**

`AdminPolicySyncControllerTest.kt`와 `SecurityConfigAdminTest.kt`의 요청 경로를 다음 값으로 교체한다.

```text
/api/v1/admin/policy-sync-jobs          -> /api/v1/admin/policies/sync-jobs
/api/v1/admin/policy-sync-jobs/{jobId}  -> /api/v1/admin/policies/sync-jobs/{jobId}
```

- [ ] **Step 3: 변경된 테스트가 기존 컨트롤러에서 실패하는지 확인**

Run from `backend/`:

```bash
./gradlew :api:test \
  --tests 'com.chamchamcham.api.policy.controller.PolicyControllerTest' \
  --tests 'com.chamchamcham.api.policy.controller.AdminPolicySyncControllerTest' \
  --tests 'com.chamchamcham.config.SecurityConfigAdminTest'
```

Expected: 새 경로가 아직 매핑되지 않아 정책 컨트롤러 또는 관리자 역할 성공 테스트가 404로 실패한다.

- [ ] **Step 4: 일반 정책 컨트롤러에 새 경로 적용**

`PolicyController.kt`의 클래스와 메서드 매핑 세 줄만 다음처럼 교체한다.

```text
@RequestMapping("/api/v1")                         -> @RequestMapping("/api/v1/policies")
@GetMapping("/policy-recommendations")             -> @GetMapping("/recommendations")
@GetMapping("/policy-programs/{policyProgramId}")  -> @GetMapping("/{policyProgramId}")
```

실제 함수 시그니처와 본문은 수정하지 않고 세 mapping annotation만 변경한다.

- [ ] **Step 5: 관리자 컨트롤러에 새 경로 적용**

`AdminPolicySyncController.kt`의 클래스 매핑만 다음 값으로 변경한다.

```kotlin
@RequestMapping("/api/v1/admin/policies/sync-jobs")
```

`@PostMapping`과 `@GetMapping("/{jobId}")` 및 함수 본문은 유지한다.

- [ ] **Step 6: focused backend 테스트 통과 확인**

Step 3의 Gradle 명령을 다시 실행한다.

Expected: 세 테스트 클래스가 모두 통과하고 `BUILD SUCCESSFUL`이 출력된다.

- [ ] **Step 7: backend 변경 커밋**

```bash
git add \
  backend/api/src/main/kotlin/com/chamchamcham/api/policy/controller/PolicyController.kt \
  backend/api/src/main/kotlin/com/chamchamcham/api/policy/controller/AdminPolicySyncController.kt \
  backend/api/src/test/kotlin/com/chamchamcham/api/policy/controller/PolicyControllerTest.kt \
  backend/api/src/test/kotlin/com/chamchamcham/api/policy/controller/AdminPolicySyncControllerTest.kt \
  backend/api/src/test/kotlin/com/chamchamcham/config/SecurityConfigAdminTest.kt
git commit \
  -m "refactor(policy): 정책 API 리소스 경로를 일관되게 정리" \
  -m "일반 정책 조회와 관리자 동기화를 복수형 policies 리소스 아래에 배치해 API 구조를 예측 가능하게 만든다." \
  -m "Constraint: 앱 정식 연동 전이므로 기존 경로 alias를 유지하지 않는다." \
  -m "Rejected: 기존 경로 동시 지원 | 사용처가 제한된 개발 단계에서 불필요한 호환 계층이 된다." \
  -m "Confidence: high" \
  -m "Scope-risk: moderate" \
  -m "Directive: 정책 API 신규 경로는 /api/v1/policies와 /api/v1/admin/policies 아래에 둔다." \
  -m "Tested: PolicyControllerTest, AdminPolicySyncControllerTest, SecurityConfigAdminTest" \
  -m "Not-tested: 전체 backend 회귀와 test-ios는 최종 검증에서 실행"
```

---

### Task 3: ignored test-ios 하네스 경로 동기화

**Files:**
- Local-only Modify: `test-ios/GodsMoveAuthHarness/GodsMoveAuthHarnessTests/BackendAPIClientTests.swift`
- Local-only Modify: `test-ios/GodsMoveAuthHarness/GodsMoveAuthHarness/BackendAPIClient.swift`
- Local-only Modify: `test-ios/GodsMoveAuthHarness/README.md`

**Interfaces:**
- Consumes: Task 2의 네 backend 경로
- Produces: 로컬 관리자 동기화 및 정책 조회 하네스가 호출하는 동일한 경로

- [ ] **Step 1: Swift 요청 경로 기대값 변경**

`BackendAPIClientTests.swift`의 기대 경로를 다음 값으로 변경한다.

```text
/api/v1/policies/recommendations
/api/v1/policies/{UUID}
/api/v1/admin/policies/sync-jobs
/api/v1/admin/policies/sync-jobs/{UUID}
```

- [ ] **Step 2: 테스트가 기존 상수에서 실패하는지 확인**

Run from `test-ios/GodsMoveAuthHarness/` using an available simulator ID:

```bash
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer \
xcodebuild test \
  -project GodsMoveAuthHarness.xcodeproj \
  -scheme GodsMoveAuthHarness \
  -destination 'platform=iOS Simulator,id=DB710316-3B6C-48D9-9F6D-D1A88C7AD9E2' \
  -only-testing:GodsMoveAuthHarnessTests/BackendAPIClientTests
```

Expected: 정책 경로 assertion이 이전 경로를 받아 실패한다. 시뮬레이터 ID가 없으면 `xcrun simctl list devices available`로 현재 ID를 선택한다.

- [ ] **Step 3: Swift 경로 상수 변경**

`BackendAPIClient.swift`의 정책 경로 상수를 다음 값으로 바꾼다.

```swift
static let policyRecommendations = "/api/v1/policies/recommendations"
static let policyPrograms = "/api/v1/policies"
static let adminPolicySyncJobs = "/api/v1/admin/policies/sync-jobs"
```

- [ ] **Step 4: test-ios README 경로 변경**

README의 수동 테스트 안내를 다음 경로로 갱신한다.

```text
/api/v1/admin/policies/sync-jobs
/api/v1/policies/recommendations
/api/v1/policies/{policyProgramId}
```

- [ ] **Step 5: focused iOS 테스트 통과 확인**

Step 2의 Xcode 명령을 다시 실행한다.

Expected: `BackendAPIClientTests`가 통과하고 `** TEST SUCCEEDED **`가 출력된다.

- [ ] **Step 6: ignore 계약 확인**

Run from repository root:

```bash
git check-ignore -v \
  test-ios/GodsMoveAuthHarness/GodsMoveAuthHarness/BackendAPIClient.swift \
  test-ios/GodsMoveAuthHarness/GodsMoveAuthHarnessTests/BackendAPIClientTests.swift \
  test-ios/GodsMoveAuthHarness/README.md
git status --short
```

Expected: 세 파일 모두 `.gitignore`의 `test-ios/` 규칙과 일치하고 Git status에 나타나지 않는다. 이 Task에서는 커밋을 만들지 않는다.

---

### Task 4: 전체 검증과 PR 갱신

**Files:**
- No source file changes
- External update: GitHub PR `#14`

**Interfaces:**
- Consumes: Task 1~3의 문서·backend·test-ios 변경
- Produces: 검증된 원격 브랜치와 새 템플릿 형식의 Draft PR

- [ ] **Step 1: 기존 경로 제거와 공백 검사**

Run from repository root:

```bash
rg -n '/api/v1/(policy-recommendations|policy-programs|admin/policy-sync-jobs)' \
  backend/api/src test-ios/GodsMoveAuthHarness/GodsMoveAuthHarness \
  test-ios/GodsMoveAuthHarness/GodsMoveAuthHarnessTests test-ios/GodsMoveAuthHarness/README.md
git diff --check
```

Expected: `rg`는 결과 없이 종료하고 `git diff --check`도 출력이 없다.

- [ ] **Step 2: backend 전체 테스트**

Run from `backend/`:

```bash
./gradlew test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: test-ios 전체 테스트**

Run from `test-ios/GodsMoveAuthHarness/` with the available simulator destination used in Task 3:

```bash
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer \
xcodebuild test \
  -project GodsMoveAuthHarness.xcodeproj \
  -scheme GodsMoveAuthHarness \
  -destination 'platform=iOS Simulator,id=DB710316-3B6C-48D9-9F6D-D1A88C7AD9E2'
```

Expected: `** TEST SUCCEEDED **`.

- [ ] **Step 4: 브랜치 상태 확인과 푸시**

```bash
git status --short --branch
git log --oneline origin/fix/policy-category-sort..HEAD
git push origin fix/policy-category-sort
```

Expected: tracked working tree가 깨끗하고 `.claude/`, `.worktrees/`만 untracked로 남으며 새 커밋이 원격에 push된다.

- [ ] **Step 5: PR 본문을 공통 템플릿으로 갱신**

GitHub PR `#14` 본문을 다음 내용으로 갱신한다. 검증 결과는 해당 명령이 실제로
성공한 경우에만 `성공`으로 기록한다.

```markdown
## 변경 내용

- 정책 혜택 카테고리를 제품의 10개 분류로 정리했습니다.
- 추천 목록에 `RECOMMENDED`와 `LATEST` 정렬 및 정렬별 keyset cursor를 추가했습니다.
- 정책 카드와 회원 농장·작물 조회의 N+1을 fetch join과 query-count 테스트로 방지했습니다.
- 정책 API를 `/api/v1/policies`와 `/api/v1/admin/policies` 아래로 통합했습니다.
- 저장소 공통 PR 템플릿과 AI PR 작성 규칙을 추가했습니다.

## 변경 이유

제품 분류와 backend 계약을 일치시키고, 최신순 페이지네이션의 중복·누락을 방지하며,
정책 조회 데이터 수에 비례하는 추가 SELECT를 제거하기 위해 변경했습니다. 정책 API와
PR 작성 형식도 저장소 전체에서 예측 가능한 구조로 통일했습니다.

## 영향 범위

- backend 정책 추천·상세·관리자 동기화 API
- 정책 정렬·필터·커서와 연관 조회
- ignored test-ios 정책 테스트 하네스
- 저장소의 향후 모든 PR 본문

## 검증

- `./gradlew test` — 성공
- `xcodebuild test -project GodsMoveAuthHarness.xcodeproj -scheme GodsMoveAuthHarness ...` — 성공
- 기존 정책 API 경로 검색 — 실행 코드·테스트·test-ios에서 결과 없음
- `git diff --check` — 성공

## 배포 및 호환성

- 기존 정책 API 경로 alias를 제공하지 않으므로 호출자는 새 경로로 함께 변경해야 합니다.
- 요청·응답 DTO, 인증, 정렬·필터·커서 데이터 계약은 유지됩니다.
- DB 스키마 변경과 새 의존성은 없습니다.
- `test-ios`의 JWT secret과 로컬 경로 변경은 Git ignore 대상이며 PR에 포함되지 않습니다.

## 체크리스트

- [x] 관련 테스트를 추가하거나 수정했습니다.
- [x] 로컬 검증을 완료했습니다.
- [x] API·설정·DB 호환성 영향을 확인했습니다.
- [x] 민감한 정보가 포함되지 않았습니다.
```

- [ ] **Step 6: 원격 PR 최종 상태 확인**

GitHub connector로 PR `#14`를 다시 조회한다.

Expected: base는 `dev`, head는 `fix/policy-category-sort`, Draft는 `true`, mergeable은 `true`, head SHA는 로컬 `HEAD`와 같다.
