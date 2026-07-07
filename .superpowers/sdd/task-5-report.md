# Task 5 Report: Recommendation Scoring, Regeneration, Cursor Paging, And Detail Read

Date: 2026-07-07
Workspace: `/Users/wingwogus/Projects/ChamChamCham`
Branch: `feat/policy-recommendation-nongupez`

## Result

Implemented Task 5 application-layer recommendation behavior:

- deterministic member-policy scoring
- member profile reading from stored member/crop/farm data
- recommendation regeneration for missing or stale latest-sync recommendations
- cursor paging contract using `score desc, applyEndsOn asc, id asc` cursor keys
- stale cursor sync-job mismatch rejection with `INVALID_INPUT`
- empty recommendation page when no successful sync job exists
- detail read for `detailSynced=true` and `recommendable=true` policy programs only
- best-effort contact and attachment mapping from `rawPayload`

No API or batch code was changed. No live NongupEZ calls were introduced.

## Changed Files

- `backend/application/src/main/kotlin/com/chamchamcham/application/exception/ErrorCode.kt`
  - Added `POLICY_PROGRAM_NOT_FOUND` and `POLICY_SYNC_JOB_NOT_FOUND`.
- `backend/application/src/main/kotlin/com/chamchamcham/application/policy/PolicyRecommendationCursorPayload.kt`
  - Added opaque cursor payload containing `sourceSyncJobId`, `score`, `applyEndsOn`, and recommendation `id`.
- `backend/application/src/main/kotlin/com/chamchamcham/application/policy/PolicyRecommendationResult.kt`
  - Added application result models for recommendation pages, cards, details, contacts, and attachments.
- `backend/application/src/main/kotlin/com/chamchamcham/application/policy/PolicyRecommendationScorer.kt`
  - Added deterministic scoring and reason generation.
- `backend/application/src/main/kotlin/com/chamchamcham/application/policy/PolicyMemberProfileReader.kt`
  - Added member profile aggregation from member crops and farms.
- `backend/application/src/main/kotlin/com/chamchamcham/application/policy/PolicyRecommendationService.kt`
  - Added list, regeneration, cursor, and detail read use cases.
- `backend/application/src/test/kotlin/com/chamchamcham/application/policy/PolicyRecommendationScorerTest.kt`
  - Added scorer behavior test from the Task 5 brief.
- `backend/application/src/test/kotlin/com/chamchamcham/application/policy/PolicyRecommendationServiceTest.kt`
  - Added focused service tests for empty sync, regeneration, stale cursor, and detail visibility/parsing.

## Verification

### RED

Command:

```bash
cd backend
./gradlew :application:test --tests "com.chamchamcham.application.policy.PolicyRecommendationScorerTest" --tests "com.chamchamcham.application.policy.PolicyRecommendationServiceTest"
```

Result:

- Failed before production implementation.
- Expected Task 5 missing-class failures appeared.
- The known unrelated `DevRagSeedServiceTest.kt` RAG/dev compile failures also appeared.

### Compile

Command:

```bash
cd backend
./gradlew :application:compileKotlin
```

Result:

- PASS.
- Application production code compiles successfully.

### Focused Task 5 Test Command

Command:

```bash
cd backend
./gradlew :application:test --tests "com.chamchamcham.application.policy.PolicyRecommendationScorerTest" --tests "com.chamchamcham.application.policy.PolicyRecommendationServiceTest"
```

Result:

- BLOCKED at `:application:compileTestKotlin` by unrelated existing RAG/dev test compilation errors in `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/seed/DevRagSeedServiceTest.kt`.
- Final rerun did not show Task 5 compile errors before the blocker.

Representative unrelated errors:

- `Unresolved reference: FarmingRecordDocumentFactory`
- `Unresolved reference: RagProperties`
- `Unresolved reference: DevRagSeedService`
- `Unresolved reference: DevRagSeedCommand`
- `Unresolved reference: VectorStore`
- `Unresolved reference: PdfTextExtractor`

### Isolated Behavior Probe

Because Gradle cannot compile any test subset while the unrelated RAG/dev test source is broken, I ran an isolated temporary Gradle Java probe against the real application/domain runtime classpath.

Command:

```bash
cd backend
./gradlew -I /private/tmp/cham-task5-probe/task5-probe.init.gradle :application:runTask5Probe
```

Result:

- PASS.
- Output: `TASK5_PROBE_OK`.

Probe coverage:

- no successful sync returns an empty recommendation page
- missing latest-sync recommendations are deleted and recreated
- deterministic scoring produces the expected reason
- stale cursor source sync job mismatch throws `INVALID_INPUT`
- detail read parses contacts and attachments from `rawPayload`

## Constraints Confirmed

- Preserved `member` naming.
- Added no dependencies.
- Did not touch `.claude/`.
- Did not touch API or batch work.
- Did not change unrelated RAG/dev tests.
- Member recommendation logic is deterministic and does not call NongupEZ.
- Candidate selection delegates to the existing Task 1 repository filter for latest job, latest year, `detailSynced=true`, `recommendable=true`, and open/not-closed policies.

## Remaining Risks / Concerns

- The focused JUnit tests are present but cannot be executed through Gradle until the unrelated `DevRagSeedServiceTest.kt` compilation issues are fixed.
- Detail raw payload parsing is intentionally best effort. It supports `bizPicList` / `bizAtchFileList` and generic `contacts` / `attachments`, but malformed JSON returns empty lists rather than failing detail reads.
- Exact token usage was unavailable in this execution surface.
