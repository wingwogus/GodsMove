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

---

## Reviewer Fix Report: Policy Schema Contract

Status: DONE

### Fixes

- Reworked `docs/database/2026-07-07-policy-recommendation-schema.sql` into a staged migration contract for existing data:
  - Adds `policy_program.source`, `external_id`, and `source_year` as nullable first.
  - Drops unsafe shared defaults and temporary not-null assumptions.
  - Backfills existing policy rows with unique identities using `source = 'NONGUP_EZ'`, `external_id = id::text`, and `source_year = '0000'`.
  - Sets the three identity columns `NOT NULL` only after backfill, then creates `uk_policy_program_source_external_year`.
- Aligned the SQL contract with non-null JPA `PolicyRecommendation.sourceSyncJob`:
  - Adds `source_sync_job_id` first.
  - Inserts a deterministic legacy `policy_sync_job` only when existing recommendations need a backfill.
  - Backfills null recommendation `source_sync_job_id` values to that legacy job.
  - Sets `policy_recommendation.source_sync_job_id` `NOT NULL` after backfill.
- Made the MVP JSON preservation contract explicit:
  - Added a `policy_program.raw_payload` SQL column comment documenting that contacts, attachments, and source tags stay in the original NongupEZ JSON payload for MVP.
  - Adjusted `PolicyProgramTest` to verify `rawPayload` preserves contacts, attachments, and source tag keys instead of adding unused tables or columns.

### Verification

- Command: `cd backend && ./gradlew :domain:test`
- Result: PASS

Relevant output:

```text
> Task :domain:compileTestKotlin
> Task :domain:test

BUILD SUCCESSFUL in 4s
4 actionable tasks: 3 executed, 1 up-to-date
```

- Command: `git diff --check`
- Result: PASS

### Concerns

- The SQL remains a reviewed schema contract artifact because Flyway is not present in the backend. It has not been executed against a live PostgreSQL database in this fix pass.
