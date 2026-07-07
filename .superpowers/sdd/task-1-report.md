# Task 1 Report: Policy Domain Model, Schema Contract, And Query Repository

Status: DONE_WITH_CONCERNS

## Summary

Implemented the NongupEZ policy domain persistence model, sync-job model, repository contracts, query repository, schema review SQL, and focused domain tests for Task 1 only. No application, API, or batch behavior was added.

## Changed Files

- `backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicyProgram.kt`
  - Added NongupEZ source identity fields, list/detail payload fields, card summary fields, tag JSON fields, sync job reference, and source/external/year JPA uniqueness.
  - Added `applyListFields(...)`, `applyDetailFields(...)`, `markDetailSyncFailed(...)`, and `isOpenOn(...)`.
  - Made existing mutable fields `var` and made `targetManagementType` nullable for list-only policies.
- `backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicyRecommendation.kt`
  - Added required `sourceSyncJob` relationship.
- `backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicyProgramRepository.kt`
  - Added source lookup, recommendable candidate lookup, and recommendable detail lookup methods.
- `backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicyRecommendationRepository.kt`
  - Added member/sync-job existence check and member delete method.
- `backend/domain/src/main/kotlin/com/chamchamcham/domain/farm/FarmRepository.kt`
  - Added `findByOwner_Id(...)`.
- `backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicySource.kt`
- `backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicySyncJob.kt`
- `backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicySyncJobRepository.kt`
- `backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicySyncJobStatus.kt`
- `backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicySyncTriggerType.kt`
- `backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicyRecommendationQueryRepository.kt`
- `backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicyRecommendationQueryRepositoryImpl.kt`
- `backend/domain/src/test/kotlin/com/chamchamcham/domain/policy/PolicyProgramTest.kt`
- `backend/domain/src/test/kotlin/com/chamchamcham/domain/policy/PolicySyncJobTest.kt`
- `backend/domain/src/test/kotlin/com/chamchamcham/domain/policy/PolicyRecommendationQueryRepositoryTest.kt`
- `docs/database/2026-07-07-policy-recommendation-schema.sql`
  - Includes `policy_sync_job`, policy program extension columns, `uk_policy_program_source_external_year`, and recommendation sync-job index.

## TDD Evidence

- RED: `./gradlew :domain:test --tests "com.chamchamcham.domain.policy.PolicyProgramTest" --tests "com.chamchamcham.domain.policy.PolicySyncJobTest" --tests "com.chamchamcham.domain.policy.PolicyRecommendationQueryRepositoryTest"` failed at `:domain:compileTestKotlin` with unresolved `PolicySyncJob`, `PolicySource`, `PolicySyncTriggerType`, `PolicyRecommendationQueryRepository`, new `PolicyProgram` methods/fields, nullable `targetManagementType`, and `sourceSyncJob`.
- GREEN: same focused command passed after implementation.

## Verification

- Focused Task 1 tests:
  - Command: `cd backend && ./gradlew :domain:test --tests "com.chamchamcham.domain.policy.PolicyProgramTest" --tests "com.chamchamcham.domain.policy.PolicySyncJobTest" --tests "com.chamchamcham.domain.policy.PolicyRecommendationQueryRepositoryTest"`
  - Result: PASS
- Full domain module tests:
  - Command: `cd backend && ./gradlew :domain:test`
  - Result: PASS
- Full backend baseline:
  - Command: `cd backend && ./gradlew test`
  - Result: FAIL in pre-existing RAG/dev test compilation areas: `:api:compileTestKotlin` and `:application:compileTestKotlin` reference missing classes such as `DevRagSeedCommand`, `DevRagSeedService`, `DevRecordFeedbackController`, `TodayRecordFeedbackService`, `FarmingRecordDocumentFactory`, `RagProperties`, `VectorStore`, `PdfTextExtractor`, and related RAG/coaching types.
- Diff sanity:
  - Command: `git diff --check`
  - Result: PASS

## Constraints Observed

- Preserved `member` naming; did not add project-owned `userId` or `users` naming.
- Added no dependencies.
- Kept card summary fields at database length 19 and domain `require(...)` checks in `applyDetailFields(...)`.
- Did not add separate target, crop, region, contact, or attachment tables.
- Did not modify unrelated `.claude/` files or existing RAG/dev tests.
- Kept scope to domain policy model/repository/query/schema files named in the brief.

## Concerns

- Full `./gradlew test` remains blocked by unrelated pre-existing RAG/dev test compile failures outside Task 1 scope.
- Flyway is not present, so the SQL file is a review/schema contract artifact and must be applied separately in dev/prod environments.
