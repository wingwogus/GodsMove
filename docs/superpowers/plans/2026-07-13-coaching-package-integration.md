# 코칭 패키지 통합 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 최신 `feat/coaching-rag`에 `ReportFeedback`을 병합하고 common/chat/indexing/recordfeedback/reportfeedback 경계를 전 계층에서 일치시킨다.

**Architecture:** 최신 `feat/coaching-rag`에서 별도 통합 브랜치와 워크트리를 만든 뒤 `feat/report-feedback`을 병합한다. 기록 피드백 충돌은 대상 브랜치의 최신 문구·검증 규칙을 보존하고 리포트 기능만 결합한다. 병합 후 package와 물리 경로를 함께 이동하며 공용 RAG 타입만 `application.coaching.common`에 둔다.

**Tech Stack:** Kotlin, Spring Boot 3, Spring Data JPA, Spring AI, Gradle, JUnit 5, Git worktree.

## Global Constraints

- 기존 `/Users/wingwogus/Projects/ChamChamCham`의 `application-local.yml`과 `.claude/`를 수정하거나 스테이징하지 않는다.
- 새 의존성, 공통 Feedback 부모, 공통 generation service를 추가하지 않는다.
- `member` 용어와 api → application → domain 의존 방향을 유지한다.
- 최신 `feat/coaching-rag`의 RecordFeedback 문구 길이·검증 규칙을 우선 보존한다.
- 지원되는 CHAT·RecordFeedback·ReportFeedback API 응답 계약을 변경하지 않는다.
- 미지원 `CoachingMode.RECORD_AUTO`, `REPORT_MANUAL`만 제거한다.
- 이동 후 `recordfeedback`과 `reportfeedback`은 서로 참조하지 않는다.

---

### Task 1: 격리 통합 기준선 준비

**Files:**
- Create worktree: `/Users/wingwogus/Projects/ChamChamCham/.worktrees/coaching-package-integration`
- Create branch: `feat/coaching-package-integration` from `feat/coaching-rag`
- Test: full backend suite in the new worktree

**Interfaces:**
- Consumes: `feat/coaching-rag` HEAD `a9be8f9f` or newer at execution time.
- Produces: clean isolated branch whose first parent is the latest target branch.

- [ ] **Step 1: Verify the target checkout's user changes are out of scope**

Run: `git -C /Users/wingwogus/Projects/ChamChamCham status --short`

Expected: only user-owned local config and `.claude/` entries; do not stage or edit them.

- [ ] **Step 2: Create the integration worktree**

Run: `git worktree add .worktrees/coaching-package-integration -b feat/coaching-package-integration feat/coaching-rag`

Expected: new branch starts at the latest committed `feat/coaching-rag` HEAD.

- [ ] **Step 3: Run the target baseline**

Run from `backend`: `./gradlew :domain:test :application:test :api:test`

Expected: BUILD SUCCESSFUL before report changes enter the branch.

### Task 2: ReportFeedback 병합과 의미 충돌 해결

**Files:**
- Merge: `feat/report-feedback`
- Resolve: RecordFeedback controller/tests, prompt/validator/tests, report projection files, API docs
- Preserve deletion: `frontend/dev-rag-test.html`

**Interfaces:**
- Consumes: the target branch's current RecordFeedback behavior and the report branch's ReportFeedback commits.
- Produces: both features compiling before package moves.

- [ ] **Step 1: Merge without auto-commit**

Run: `git merge --no-ff --no-commit feat/report-feedback`

Expected: conflicts in files modified independently after the shared base.

- [ ] **Step 2: Resolve RecordFeedback conflicts using target behavior**

Keep the target branch versions of RecordFeedback prompt, output validator, content tests, generation tests, prompt tests, and their latest 15–25 character text rule. Apply only the unified controller route:

```kotlin
@RequestMapping("/api/v1/farming-records/{recordId}/feedback")
```

Update the matching controller tests to `/feedback` and `/feedback/regenerate` without replacing their current assertions.

- [ ] **Step 3: Resolve report and frontend conflicts**

Keep all new `ReportFeedback` domain/application/API files and report projection lifecycle wiring. Keep `frontend/dev-rag-test.html` deleted because the target branch intentionally removed the console. Retain API documentation changes that describe `/feedback` paths.

- [ ] **Step 4: Verify the pure merge**

Run from `backend`: `./gradlew :domain:test :application:test :api:test`

Expected: BUILD SUCCESSFUL before any package relocation.

- [ ] **Step 5: Commit the semantic merge**

Commit title: `merge(coaching): 리포트 피드백 기능 통합`

The Lore body records target-wins RecordFeedback rules, preserved report functionality, full test command, and removed dev console.

### Task 3: Domain 대상 패키지 분리

**Files:**
- Move: `backend/domain/src/main/kotlin/com/chamchamcham/domain/coaching/CoachingMode.kt` → `.../coaching/chat/CoachingMode.kt`
- Move: all `RecordFeedback*.kt` → `.../coaching/recordfeedback/`
- Move: all `ReportFeedback*.kt` → `.../coaching/reportfeedback/`
- Move matching domain tests to the same package paths
- Modify imports across backend production/tests

**Interfaces:**
- Produces: `com.chamchamcham.domain.coaching.chat`, `.recordfeedback`, `.reportfeedback`.

- [ ] **Step 1: Lock package expectations with searches**

Run: `rg -n 'com\.chamchamcham\.domain\.coaching\.(RecordFeedback|ReportFeedback)' backend`

Expected: references exist before the move and become empty afterward.

- [ ] **Step 2: Move files and update declarations/imports**

Use these exact declarations:

```kotlin
package com.chamchamcham.domain.coaching.chat
package com.chamchamcham.domain.coaching.recordfeedback
package com.chamchamcham.domain.coaching.reportfeedback
```

Remove `RECORD_AUTO` and `REPORT_MANUAL` so `CoachingMode` contains only `CHAT`. Remove the application test that iterates over those two unsupported enum values.

- [ ] **Step 3: Run domain and application compilation/tests**

Run: `./gradlew :domain:test :application:test`

Expected: BUILD SUCCESSFUL with no flat feedback-domain imports.

### Task 4: Application common/chat/indexing 분리

**Files:**
- Move: `application/coaching/rag/common/RagAudit.kt`, `RagModelInfo.kt`, `RagProperties.kt`, `RagSourceType.kt` → `application/coaching/common/`
- Move: `CoachingStructuredResult.kt` and all `rag/chat/*.kt` → `application/coaching/chat/`
- Move: `rag/indexing/FarmingRecordDocumentFactory.kt` → `application/coaching/indexing/`
- Move matching tests to `common`, `chat`, `indexing`
- Modify all imports in recordfeedback/reportfeedback/chat/config/API/tests

**Interfaces:**
- Produces: shared types under `application.coaching.common`; feature code under `chat` and `indexing`.

- [ ] **Step 1: Move common types only**

The resulting common package contains exactly `RagAudit`, `RagModelInfo`, `RagProperties`, and `RagSourceType`. `CoachingStructuredResult` moves to `chat` because record/report output types do not use it.

- [ ] **Step 2: Move chat and indexing files with matching tests**

Use these exact package declarations:

```kotlin
package com.chamchamcham.application.coaching.chat
package com.chamchamcham.application.coaching.indexing
```

- [ ] **Step 3: Verify the old wrapper is gone**

Run: `rg -n 'application\.coaching\.rag' backend --glob '!**/build/**'`

Expected: no results.

- [ ] **Step 4: Run application tests**

Run: `./gradlew :application:test`

Expected: BUILD SUCCESSFUL.

### Task 5: API 패키지 대칭화

**Files:**
- Move chat controller/DTO/tests → `api/coaching/chat/`
- Move record controller/DTO/tests and API integration/smoke tests → `api/coaching/recordfeedback/`
- Move report feedback controller/DTO/test → `api/coaching/reportfeedback/`
- Keep FarmingCycleReport controller/DTO/tests under `api/report/`

**Interfaces:**
- Produces: symmetric API packages while preserving all endpoint paths.

- [ ] **Step 1: Move API files and update packages/imports**

Use:

```kotlin
package com.chamchamcham.api.coaching.chat.controller
package com.chamchamcham.api.coaching.chat.dto
package com.chamchamcham.api.coaching.recordfeedback.controller
package com.chamchamcham.api.coaching.recordfeedback.dto
package com.chamchamcham.api.coaching.reportfeedback.controller
package com.chamchamcham.api.coaching.reportfeedback.dto
```

- [ ] **Step 2: Run API contract tests**

Run: `./gradlew :api:test --tests '*CoachingRagControllerTest' --tests '*RecordFeedbackControllerTest' --tests '*ReportFeedbackControllerTest'`

Expected: all supported URLs and response assertions pass.

- [ ] **Step 3: Verify report API ownership**

Run: `rg -n 'ReportFeedback' backend/api/src/main/kotlin/com/chamchamcham/api/report`

Expected: no results; farming report statistics remain under `api.report`.

### Task 6: 전체 검증, 커밋, target fast-forward

**Files:**
- Verify all moved production/test files
- Commit package integration
- Fast-forward `feat/coaching-rag` from the main checkout

- [ ] **Step 1: Run stale-path checks**

Run searches for `application.coaching.rag`, flat domain feedback imports, old API packages, and `RECORD_AUTO|REPORT_MANUAL` in Kotlin production/test code.

Expected: no results. Historical SQL migration comments may retain legacy mode names.

- [ ] **Step 2: Run full verification**

Run: `git diff --check`

Run from `backend`: `./gradlew :domain:test :application:test :api:test`

Expected: no whitespace errors and BUILD SUCCESSFUL.

- [ ] **Step 3: Commit package integration**

Commit title: `refactor(coaching): 코칭 패키지 책임 분리`

Lore records the selected common boundary, rejected generic Feedback abstraction, exact tests, and supported API compatibility.

- [ ] **Step 4: Fast-forward the target branch without touching user files**

Run from `/Users/wingwogus/Projects/ChamChamCham`:

```bash
git merge --ff-only feat/coaching-package-integration
```

Expected: `feat/coaching-rag` advances to the verified integration commit while `application-local.yml` and `.claude/` remain unchanged and untracked/modified exactly as before.

## Plan self-review

- Spec coverage: merge isolation, common/chat/indexing split, record/report symmetry, unsupported mode deletion, API compatibility, testing, and target fast-forward are assigned to explicit tasks.
- Conflict policy: current target RecordFeedback behavior wins; ReportFeedback and unified endpoint paths are added independently.
- Type consistency: all paths use `recordfeedback`, `reportfeedback`, `chat`, `common`, `indexing` consistently across domain/application/api.
- Scope: no new abstraction, dependency, product behavior, frontend screen, stale policy, or report regeneration is included.
