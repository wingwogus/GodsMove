# Community Posts by Member Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:test-driven-development while implementing this plan task-by-task.

**Goal:** Allow authenticated clients to filter the existing community post list by a specific author member ID without changing viewer-dependent fields such as `likedByMe`.

**Architecture:** Extend the existing `GET /api/v1/community/posts` search contract with an optional `authorMemberId` query parameter and carry it through the application search condition into the domain query condition, where it adds an author predicate independently from the authenticated viewer's `memberId`.

**Tech Stack:** Kotlin, Spring Boot MVC, JPA/HQL, JUnit 5, Mockito, AssertJ

## Global Constraints

- Preserve `member` terminology and do not introduce project-owned `user` naming.
- Keep the existing route, pagination, sorting, and response shape backward compatible.
- Keep authenticated viewer identity separate from the target author identity.
- Add no dependencies or unrelated refactors.

---

### Task 1: HTTP and application contract

**Files:**
- Modify: `backend/api/src/main/kotlin/com/chamchamcham/api/community/controller/CommunityController.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/community/CommunityPostSearchCondition.kt`
- Test: `backend/api/src/test/kotlin/com/chamchamcham/api/community/controller/CommunityControllerTest.kt`

**Interfaces:**
- Consumes: authenticated viewer UUID from `@AuthenticationPrincipal`
- Produces: `CommunityPostSearchCondition.authorMemberId: UUID?`

- [x] Add a controller test that calls `GET /api/v1/community/posts?authorMemberId=<uuid>` and expects `CommunityPostService.search` with the viewer ID and target author ID kept separate.
- [x] Run `./gradlew :api:test --tests '*CommunityControllerTest'` and confirm compilation/test failure because `authorMemberId` is not supported.
- [x] Add the optional request parameter and application condition field, updating existing construction sites with `null` where no author filter applies.
- [x] Re-run the controller test and confirm it passes.

### Task 2: Query filtering

**Files:**
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/community/CommunityPostQueryRepository.kt`
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/community/CommunityPostQueryRepositoryImpl.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/community/CommunityPostService.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/community/CommunityPostServiceTest.kt`
- Test: `backend/domain/src/test/kotlin/com/chamchamcham/domain/community/CommunityPostQueryRepositoryTest.kt`

**Interfaces:**
- Consumes: `CommunityPostSearchCondition.authorMemberId`
- Produces: `CommunityPostQueryRepository.SearchCondition.authorMemberId` and HQL predicate `p.author.id = :authorMemberId`

- [x] Add a service test proving the target author ID is forwarded to the query repository.
- [x] Add a repository test proving search returns only posts by the requested author while `likedByMe` is still calculated for the authenticated viewer; also prove count uses the same filter.
- [x] Run the focused service and repository tests and confirm failure before implementation.
- [x] Forward `authorMemberId` through search/count and add the optional author predicate to the shared filter builder.
- [x] Re-run the focused tests and confirm they pass.

### Task 3: Regression verification

**Files:**
- Verify all files changed by Tasks 1 and 2.

**Interfaces:**
- Consumes: completed member-author filter implementation
- Produces: verified backward-compatible backend behavior

- [x] Run `./gradlew :domain:test :application:test :api:test` from `backend`.
- [x] Run `git diff --check` and inspect `git diff --stat` plus the final diff for unrelated changes.
- [x] Report exact commands, changed files, preserved untracked files, and any remaining risk.
