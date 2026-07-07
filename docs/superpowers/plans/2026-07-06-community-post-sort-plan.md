# Community Post Sort Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `CommunityPostSort` support to the community post list with latest, like-count, comment-count, and popularity sorting backed by stateless opaque cursor pagination.

**Architecture:** Keep API transport concerns in `api`, use-case cursor encode/decode in `application`, and post query ordering in `domain`. Reuse one small common `OpaqueCursorCodec` for Base64 URL-safe JSON cursor transport while keeping community-specific sort, score, and query predicate logic inside the community feature.

**Tech Stack:** Spring Boot 3.5, Kotlin 1.9, JPA/JPQL, Jackson, JUnit 5, Mockito, SwiftUI test harness.

---

## Scope Check

This is one implementation slice:

- Add a common stateless cursor string codec.
- Change the community post list request and response contract from
  `cursorCreatedAt/cursorId` to `cursor/nextCursor`.
- Add `LATEST`, `LIKE`, `COMMENT`, and `POPULAR` sorting to the community post
  query.
- Update the local `test-ios` harness so the new sort and cursor contract can be
  exercised manually.

Do not add QueryDSL, a shared pagination framework, ranking snapshots, signed
cursors, count denormalization, or admin/moderation sorting.

## File Structure

Create:

- `backend/application/src/main/kotlin/com/chamchamcham/application/common/OpaqueCursorCodec.kt`
  - Common cursor transport codec. It only serializes/deserializes JSON and
    Base64 URL-safe encodes/decodes strings.
- `backend/application/src/test/kotlin/com/chamchamcham/application/common/OpaqueCursorCodecTest.kt`
  - Unit tests for round-trip and malformed cursor handling.
- `backend/domain/src/main/kotlin/com/chamchamcham/domain/community/CommunityPostSort.kt`
  - Domain enum for community post list sort modes.
- `backend/application/src/main/kotlin/com/chamchamcham/application/community/CommunityPostCursorPayload.kt`
  - Community-specific opaque cursor payload.

Modify:

- `backend/application/build.gradle.kts`
  - Add Jackson dependencies to the application module. These are already part
    of the project stack through the API module, but the codec lives in
    `application`.
- `backend/application/src/main/kotlin/com/chamchamcham/application/exception/ErrorCode.kt`
  - Add a common invalid cursor error.
- `backend/application/src/main/kotlin/com/chamchamcham/application/community/CommunityPostSearchCondition.kt`
  - Replace split cursor fields with `sort` and `cursor`.
- `backend/application/src/main/kotlin/com/chamchamcham/application/community/CommunityPostResult.kt`
  - Replace `nextCursorCreatedAt` and `nextCursorId` with `nextCursor`.
- `backend/application/src/main/kotlin/com/chamchamcham/application/community/CommunityPostService.kt`
  - Decode request cursor, validate cursor sort and score, request `size + 1`
    rows, trim results, and encode `nextCursor` only when there is another row.
- `backend/application/src/test/kotlin/com/chamchamcham/application/community/CommunityPostServiceTest.kt`
  - Cover default latest search, cursor mismatch rejection, malformed cursor
    rejection, and `nextCursor` generation.
- `backend/domain/src/main/kotlin/com/chamchamcham/domain/community/CommunityPostQueryRepository.kt`
  - Add `sort`, structured cursor, and row score.
- `backend/domain/src/main/kotlin/com/chamchamcham/domain/community/CommunityPostQueryRepositoryImpl.kt`
  - Compute counts in the main post selection query and apply sort-specific
    cursor predicates.
- `backend/domain/src/test/kotlin/com/chamchamcham/domain/community/CommunityPostQueryRepositoryTest.kt`
  - Cover sort order and cursor pagination.
- `backend/api/src/main/kotlin/com/chamchamcham/api/community/controller/CommunityController.kt`
  - Accept `sort` and `cursor`; stop accepting `cursorCreatedAt` and `cursorId`.
- `backend/api/src/main/kotlin/com/chamchamcham/api/community/dto/CommunityResponses.kt`
  - Return `nextCursor`.
- `backend/api/src/test/kotlin/com/chamchamcham/api/community/controller/CommunityControllerTest.kt`
  - Cover request mapping and response shape.
- `test-ios/GodsMoveAuthHarness/GodsMoveAuthHarness/APIModels.swift`
  - Update community page DTO to `nextCursor`.
- `test-ios/GodsMoveAuthHarness/GodsMoveAuthHarness/BackendAPIClient.swift`
  - Add `sort` and `cursor` query parameters.
- `test-ios/GodsMoveAuthHarness/GodsMoveAuthHarness/HarnessState.swift`
  - Store `communityNextCursor`, reset it on fresh searches, and support loading
    the next page.
- `test-ios/GodsMoveAuthHarness/GodsMoveAuthHarness/CommunityTestView.swift`
  - Add a sort picker and a “Load Next Page” button.
- `test-ios/GodsMoveAuthHarness/GodsMoveAuthHarnessTests/BackendAPIClientTests.swift`
  - Update decoding/path tests for `nextCursor`, `sort`, and `cursor`.
- `test-ios/GodsMoveAuthHarness/GodsMoveAuthHarnessTests/HarnessStateTests.swift`
  - Update state tests for `nextCursor`.

---

### Task 1: Shared Opaque Cursor Codec

**Files:**

- Modify: `backend/application/build.gradle.kts`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/exception/ErrorCode.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/common/OpaqueCursorCodec.kt`
- Create: `backend/application/src/test/kotlin/com/chamchamcham/application/common/OpaqueCursorCodecTest.kt`

- [ ] **Step 1: Add the failing codec tests**

Create `backend/application/src/test/kotlin/com/chamchamcham/application/common/OpaqueCursorCodecTest.kt`:

```kotlin
package com.chamchamcham.application.common

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class OpaqueCursorCodecTest {
    private val codec = OpaqueCursorCodec()

    @Test
    fun `encode and decode cursor payload`() {
        val payload = TestCursorPayload(
            sort = "LIKE",
            score = 8,
            createdAt = LocalDateTime.of(2026, 6, 12, 9, 0),
            id = UUID.fromString("00000000-0000-0000-0000-000000000101")
        )

        val encoded = codec.encode(payload)
        val decoded = codec.decode(encoded, TestCursorPayload::class.java)

        assertThat(encoded).doesNotContain("{")
        assertThat(decoded).isEqualTo(payload)
    }

    @Test
    fun `decode rejects malformed cursor`() {
        val exception = assertThrows(BusinessException::class.java) {
            codec.decode("not-a-valid-cursor", TestCursorPayload::class.java)
        }

        assertThat(exception.errorCode).isEqualTo(ErrorCode.INVALID_CURSOR)
    }

    data class TestCursorPayload(
        val sort: String,
        val score: Long?,
        val createdAt: LocalDateTime,
        val id: UUID
    )
}
```

- [ ] **Step 2: Run the codec test and verify it fails**

Run:

```bash
cd backend
./gradlew :application:test --tests "com.chamchamcham.application.common.OpaqueCursorCodecTest"
```

Expected: FAIL because `OpaqueCursorCodec` and `ErrorCode.INVALID_CURSOR` do not
exist.

- [ ] **Step 3: Add minimal Jackson dependencies to the application module**

Modify `backend/application/build.gradle.kts` dependencies:

```kotlin
dependencies {
    implementation(project(":domain"))
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.5.4"))
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("io.jsonwebtoken:jjwt-api:0.11.5")
    implementation("io.jsonwebtoken:jjwt-impl:0.11.5")
    implementation("io.jsonwebtoken:jjwt-jackson:0.11.5")
    implementation("org.apache.commons:commons-lang3:3.14.0")
    implementation("commons-codec:commons-codec:1.16.0")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.security:spring-security-oauth2-jose")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
```

- [ ] **Step 4: Add invalid cursor error code**

Modify `backend/application/src/main/kotlin/com/chamchamcham/application/exception/ErrorCode.kt`:

```kotlin
INVALID_INPUT("COMMON_001", "error.invalid_input", 400),
INVALID_JSON("COMMON_002", "error.invalid_json", 400),
INVALID_CURSOR("COMMON_003", "error.invalid_cursor", 400),
INTERNAL_ERROR("COMMON_999", "error.internal_error", 500),
```

- [ ] **Step 5: Implement the common codec**

Create `backend/application/src/main/kotlin/com/chamchamcham/application/common/OpaqueCursorCodec.kt`:

```kotlin
package com.chamchamcham.application.common

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.util.Base64

@Component
class OpaqueCursorCodec {
    private val objectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    fun encode(payload: Any): String {
        val json = objectMapper.writeValueAsString(payload)
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(json.toByteArray(StandardCharsets.UTF_8))
    }

    fun <T : Any> decode(cursor: String, payloadType: Class<T>): T {
        return try {
            val json = String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8)
            objectMapper.readValue(json, payloadType)
        } catch (exception: RuntimeException) {
            throw BusinessException(ErrorCode.INVALID_CURSOR)
        }
    }
}
```

- [ ] **Step 6: Run the codec test and verify it passes**

Run:

```bash
cd backend
./gradlew :application:test --tests "com.chamchamcham.application.common.OpaqueCursorCodecTest"
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/application/build.gradle.kts \
  backend/application/src/main/kotlin/com/chamchamcham/application/exception/ErrorCode.kt \
  backend/application/src/main/kotlin/com/chamchamcham/application/common/OpaqueCursorCodec.kt \
  backend/application/src/test/kotlin/com/chamchamcham/application/common/OpaqueCursorCodecTest.kt
git commit -m "feat(community): 공통 커서 코덱 추가"
```

---

### Task 2: Domain Sort and Query Contract

**Files:**

- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/community/CommunityPostSort.kt`
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/community/CommunityPostQueryRepository.kt`
- Modify: `backend/domain/src/test/kotlin/com/chamchamcham/domain/community/CommunityPostQueryRepositoryTest.kt`

- [ ] **Step 1: Add the domain query contract test expectations**

Modify the helper in
`backend/domain/src/test/kotlin/com/chamchamcham/domain/community/CommunityPostQueryRepositoryTest.kt`:

```kotlin
private fun condition(
    cropId: UUID? = null,
    postType: CommunityPostType? = null,
    keyword: String? = null,
    likedOnly: Boolean = false,
    mineOnly: Boolean = false,
    sort: CommunityPostSort = CommunityPostSort.LATEST,
    cursor: CommunityPostQueryRepository.Cursor? = null,
    size: Int = 20
): CommunityPostQueryRepository.SearchCondition =
    CommunityPostQueryRepository.SearchCondition(
        memberId = memberId,
        cropId = cropId,
        postType = postType,
        keyword = keyword,
        likedOnly = likedOnly,
        mineOnly = mineOnly,
        sort = sort,
        cursor = cursor,
        size = size
    )
```

Add a focused compile-driving test:

```kotlin
@Test
fun `search condition supports sort and structured cursor`() {
    val cursor = CommunityPostQueryRepository.Cursor(
        sort = CommunityPostSort.LIKE,
        score = 3,
        createdAt = LocalDateTime.of(2026, 6, 12, 9, 0),
        id = UUID.fromString("00000000-0000-0000-0000-000000000101")
    )

    val condition = condition(sort = CommunityPostSort.LIKE, cursor = cursor)

    assertThat(condition.sort).isEqualTo(CommunityPostSort.LIKE)
    assertThat(condition.cursor).isEqualTo(cursor)
}
```

- [ ] **Step 2: Run the domain test and verify it fails**

Run:

```bash
cd backend
./gradlew :domain:test --tests "com.chamchamcham.domain.community.CommunityPostQueryRepositoryTest"
```

Expected: FAIL because `CommunityPostSort`, `SearchCondition.sort`,
`SearchCondition.cursor`, and `Cursor` do not exist yet.

- [ ] **Step 3: Add the sort enum**

Create `backend/domain/src/main/kotlin/com/chamchamcham/domain/community/CommunityPostSort.kt`:

```kotlin
package com.chamchamcham.domain.community

enum class CommunityPostSort {
    LATEST,
    LIKE,
    COMMENT,
    POPULAR
}
```

- [ ] **Step 4: Update query repository contract**

Modify `backend/domain/src/main/kotlin/com/chamchamcham/domain/community/CommunityPostQueryRepository.kt`:

```kotlin
interface CommunityPostQueryRepository {
    fun search(condition: SearchCondition): SearchResult

    data class SearchCondition(
        val memberId: UUID,
        val cropId: UUID?,
        val postType: CommunityPostType?,
        val keyword: String?,
        val likedOnly: Boolean,
        val mineOnly: Boolean,
        val sort: CommunityPostSort,
        val cursor: Cursor?,
        val size: Int
    )

    data class Cursor(
        val sort: CommunityPostSort,
        val score: Long?,
        val createdAt: LocalDateTime,
        val id: UUID
    )

    data class Row(
        val post: CommunityPost,
        val thumbnailUrl: String?,
        val commentCount: Long,
        val likeCount: Long,
        val likedByMe: Boolean,
        val score: Long?
    )

    data class SearchResult(
        val rows: List<Row>
    )
}
```

- [ ] **Step 5: Run the domain test and verify the contract compiles**

Run:

```bash
cd backend
./gradlew :domain:test --tests "com.chamchamcham.domain.community.CommunityPostQueryRepositoryTest"
```

Expected: FAIL in `CommunityPostQueryRepositoryImpl` because the implementation
still reads `cursorCreatedAt` and `cursorId`. There must be no missing symbol
errors for `CommunityPostSort`, `SearchCondition.sort`, `SearchCondition.cursor`,
or `CommunityPostQueryRepository.Cursor`.

- [ ] **Step 6: Commit**

```bash
git add backend/domain/src/main/kotlin/com/chamchamcham/domain/community/CommunityPostSort.kt \
  backend/domain/src/main/kotlin/com/chamchamcham/domain/community/CommunityPostQueryRepository.kt \
  backend/domain/src/test/kotlin/com/chamchamcham/domain/community/CommunityPostQueryRepositoryTest.kt
git commit -m "feat(community): 게시글 정렬 쿼리 계약 추가"
```

---

### Task 3: Application Search Cursor Wiring

**Files:**

- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/community/CommunityPostCursorPayload.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/community/CommunityPostSearchCondition.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/community/CommunityPostResult.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/community/CommunityPostService.kt`
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/community/CommunityPostServiceTest.kt`

- [ ] **Step 1: Update application service tests first**

In `backend/application/src/test/kotlin/com/chamchamcham/application/community/CommunityPostServiceTest.kt`,
add imports:

```kotlin
import com.chamchamcham.application.common.OpaqueCursorCodec
import com.chamchamcham.domain.community.CommunityPostSort
```

Add a codec field:

```kotlin
private val cursorCodec = OpaqueCursorCodec()
```

Update service construction:

```kotlin
service = CommunityPostService(
    memberRepository = memberRepository,
    cropRepository = cropRepository,
    farmingRecordRepository = farmingRecordRepository,
    uploadedMediaRepository = uploadedMediaRepository,
    communityPostRepository = communityPostRepository,
    communityPostMediaRepository = communityPostMediaRepository,
    communityCommentRepository = communityCommentRepository,
    communityPostLikeRepository = communityPostLikeRepository,
    memberCropRepository = memberCropRepository,
    communityPostQueryRepository = communityPostQueryRepository,
    cursorCodec = cursorCodec
)
```

Update the existing `search` test setup so the query repository receives
`size = requestedSize + 1`:

```kotlin
val expectedCondition = CommunityPostQueryRepository.SearchCondition(
    memberId = memberId,
    cropId = cropId,
    postType = CommunityPostType.QUESTION,
    keyword = "발아",
    likedOnly = true,
    mineOnly = false,
    sort = CommunityPostSort.LATEST,
    cursor = null,
    size = 21
)
```

Use two returned rows to prove trimming and `nextCursor` generation:

```kotlin
val firstPost = existingPost(member, crop).also {
    setId(it, postId)
    setCreatedAt(it, postCreatedAt)
}
val secondPost = existingPost(member, crop).also {
    setId(it, UUID.fromString("00000000-0000-0000-0000-000000000102"))
    setCreatedAt(it, LocalDateTime.of(2026, 6, 12, 8, 0))
}

`when`(communityPostQueryRepository.search(expectedCondition)).thenReturn(
    CommunityPostQueryRepository.SearchResult(
        rows = listOf(
            queryRow(firstPost, commentCount = 3, likeCount = 8, likedByMe = true, score = null),
            queryRow(secondPost, commentCount = 1, likeCount = 1, likedByMe = false, score = null)
        )
    )
)
```

Assert:

```kotlin
assertThat(page.items).hasSize(1)
assertThat(page.nextCursor).isNotBlank()
```

Add a count-based cursor mismatch test:

```kotlin
@Test
fun `search rejects cursor when embedded sort differs from request sort`() {
    val cursor = cursorCodec.encode(
        CommunityPostCursorPayload(
            sort = CommunityPostSort.LIKE,
            score = 8,
            createdAt = postCreatedAt,
            id = postId
        )
    )

    val exception = assertThrows(BusinessException::class.java) {
        service.search(
            CommunityPostSearchCondition(
                memberId = memberId,
                cropId = null,
                postType = null,
                keyword = null,
                likedOnly = false,
                mineOnly = false,
                sort = CommunityPostSort.POPULAR,
                cursor = cursor,
                size = 20
            )
        )
    }

    assertEquals(ErrorCode.INVALID_CURSOR, exception.errorCode)
    verifyNoInteractions(communityPostQueryRepository)
}
```

Add a malformed cursor test:

```kotlin
@Test
fun `search rejects malformed cursor`() {
    val exception = assertThrows(BusinessException::class.java) {
        service.search(
            CommunityPostSearchCondition(
                memberId = memberId,
                cropId = null,
                postType = null,
                keyword = null,
                likedOnly = false,
                mineOnly = false,
                sort = CommunityPostSort.LIKE,
                cursor = "bad-cursor",
                size = 20
            )
        )
    }

    assertEquals(ErrorCode.INVALID_CURSOR, exception.errorCode)
    verifyNoInteractions(communityPostQueryRepository)
}
```

Add a helper near other test helpers:

```kotlin
private fun queryRow(
    post: CommunityPost,
    commentCount: Long,
    likeCount: Long,
    likedByMe: Boolean,
    score: Long?
): CommunityPostQueryRepository.Row =
    CommunityPostQueryRepository.Row(
        post = post,
        thumbnailUrl = null,
        commentCount = commentCount,
        likeCount = likeCount,
        likedByMe = likedByMe,
        score = score
    )
```

- [ ] **Step 2: Run application tests and verify they fail**

Run:

```bash
cd backend
./gradlew :application:test --tests "com.chamchamcham.application.community.CommunityPostServiceTest"
```

Expected: FAIL because application DTO/result/service code still uses old cursor
fields and the service does not accept `OpaqueCursorCodec`.

- [ ] **Step 3: Add community cursor payload**

Create `backend/application/src/main/kotlin/com/chamchamcham/application/community/CommunityPostCursorPayload.kt`:

```kotlin
package com.chamchamcham.application.community

import com.chamchamcham.domain.community.CommunityPostSort
import java.time.LocalDateTime
import java.util.UUID

data class CommunityPostCursorPayload(
    val sort: CommunityPostSort,
    val score: Long?,
    val createdAt: LocalDateTime,
    val id: UUID
)
```

- [ ] **Step 4: Update search condition**

Modify `backend/application/src/main/kotlin/com/chamchamcham/application/community/CommunityPostSearchCondition.kt`:

```kotlin
package com.chamchamcham.application.community

import com.chamchamcham.domain.community.CommunityPostSort
import com.chamchamcham.domain.community.CommunityPostType
import java.util.UUID

data class CommunityPostSearchCondition(
    val memberId: UUID,
    val cropId: UUID?,
    val postType: CommunityPostType?,
    val keyword: String?,
    val likedOnly: Boolean,
    val mineOnly: Boolean,
    val sort: CommunityPostSort,
    val cursor: String?,
    val size: Int
)
```

- [ ] **Step 5: Update page result**

Modify `backend/application/src/main/kotlin/com/chamchamcham/application/community/CommunityPostResult.kt`:

```kotlin
data class Page(
    val items: List<PostSummary>,
    val nextCursor: String?
)
```

- [ ] **Step 6: Update search service constructor and cursor logic**

Modify the `CommunityPostService` constructor:

```kotlin
class CommunityPostService(
    private val memberRepository: MemberRepository,
    private val cropRepository: CropRepository,
    private val farmingRecordRepository: FarmingRecordRepository,
    private val uploadedMediaRepository: UploadedMediaRepository,
    private val communityPostRepository: CommunityPostRepository,
    private val communityPostMediaRepository: CommunityPostMediaRepository,
    private val communityCommentRepository: CommunityCommentRepository,
    private val communityPostLikeRepository: CommunityPostLikeRepository,
    private val memberCropRepository: MemberCropRepository,
    private val communityPostQueryRepository: CommunityPostQueryRepository,
    private val cursorCodec: OpaqueCursorCodec
)
```

Replace `search`:

```kotlin
@Transactional(readOnly = true)
fun search(condition: CommunityPostSearchCondition): CommunityPostResult.Page {
    val cursor = decodeCursor(condition.sort, condition.cursor)
    val result = communityPostQueryRepository.search(
        CommunityPostQueryRepository.SearchCondition(
            memberId = condition.memberId,
            cropId = condition.cropId,
            postType = condition.postType,
            keyword = condition.keyword,
            likedOnly = condition.likedOnly,
            mineOnly = condition.mineOnly,
            sort = condition.sort,
            cursor = cursor,
            size = condition.size + 1
        )
    )

    val visibleRows = result.rows.take(condition.size)
    val hasNext = result.rows.size > condition.size
    val nextCursor = if (hasNext) {
        encodeCursor(condition.sort, visibleRows.last())
    } else {
        null
    }

    return CommunityPostResult.Page(
        items = visibleRows.map(::toSummary),
        nextCursor = nextCursor
    )
}
```

Add helpers:

```kotlin
private fun decodeCursor(
    sort: CommunityPostSort,
    cursor: String?
): CommunityPostQueryRepository.Cursor? {
    if (cursor.isNullOrBlank()) {
        return null
    }

    val payload = cursorCodec.decode(cursor, CommunityPostCursorPayload::class.java)
    if (payload.sort != sort) {
        throw BusinessException(ErrorCode.INVALID_CURSOR)
    }
    if (sort != CommunityPostSort.LATEST && payload.score == null) {
        throw BusinessException(ErrorCode.INVALID_CURSOR)
    }

    return CommunityPostQueryRepository.Cursor(
        sort = payload.sort,
        score = payload.score,
        createdAt = payload.createdAt,
        id = payload.id
    )
}

private fun encodeCursor(
    sort: CommunityPostSort,
    row: CommunityPostQueryRepository.Row
): String {
    val post = row.post
    return cursorCodec.encode(
        CommunityPostCursorPayload(
            sort = sort,
            score = if (sort == CommunityPostSort.LATEST) null else row.score,
            createdAt = post.createdAt,
            id = requireNotNull(post.id) { "Persisted post id is required" }
        )
    )
}
```

Add imports:

```kotlin
import com.chamchamcham.application.common.OpaqueCursorCodec
import com.chamchamcham.domain.community.CommunityPostSort
```

- [ ] **Step 7: Run application tests and verify they pass**

Run:

```bash
cd backend
./gradlew :application:test --tests "com.chamchamcham.application.community.CommunityPostServiceTest" \
  --tests "com.chamchamcham.application.common.OpaqueCursorCodecTest"
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add backend/application/src/main/kotlin/com/chamchamcham/application/community/CommunityPostCursorPayload.kt \
  backend/application/src/main/kotlin/com/chamchamcham/application/community/CommunityPostSearchCondition.kt \
  backend/application/src/main/kotlin/com/chamchamcham/application/community/CommunityPostResult.kt \
  backend/application/src/main/kotlin/com/chamchamcham/application/community/CommunityPostService.kt \
  backend/application/src/test/kotlin/com/chamchamcham/application/community/CommunityPostServiceTest.kt
git commit -m "feat(community): 게시글 검색 커서 계약 변경"
```

---

### Task 4: Domain Query Sorting

**Files:**

- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/community/CommunityPostQueryRepositoryImpl.kt`
- Modify: `backend/domain/src/test/kotlin/com/chamchamcham/domain/community/CommunityPostQueryRepositoryTest.kt`

- [ ] **Step 1: Add sort behavior tests**

In `backend/domain/src/test/kotlin/com/chamchamcham/domain/community/CommunityPostQueryRepositoryTest.kt`,
add helpers:

```kotlin
private fun persistComment(post: CommunityPost, createdAt: LocalDateTime = LocalDateTime.of(2026, 6, 12, 9, 30)) {
    persist(
        CommunityComment(
            post = post,
            author = member,
            body = "댓글"
        ),
        createdAt
    )
}

private fun persistDeletedComment(post: CommunityPost) {
    persist(
        CommunityComment(
            post = post,
            author = member,
            body = "삭제 댓글",
            isDeleted = true
        ),
        LocalDateTime.of(2026, 6, 12, 9, 31)
    )
}

private fun persistLike(post: CommunityPost, liker: Member = otherMember) {
    persist(CommunityPostLike(post = post, member = liker), LocalDateTime.of(2026, 6, 12, 9, 40))
}
```

Adjust `persistPost` so it no longer auto-creates one comment and one like.
Tests that need default counts must explicitly call `persistComment` and
`persistLike`.

Add tests:

```kotlin
@Test
fun `search sorts by like count then latest`() {
    val twoLikes = persistPost(title = "좋아요 2", createdAt = LocalDateTime.of(2026, 6, 12, 8, 0))
    val oneLikeLatest = persistPost(title = "좋아요 1 최신", createdAt = LocalDateTime.of(2026, 6, 12, 10, 0))
    val oneLikeOld = persistPost(title = "좋아요 1 오래됨", createdAt = LocalDateTime.of(2026, 6, 12, 9, 0))
    persistLike(twoLikes, member)
    persistLike(twoLikes, otherMember)
    persistLike(oneLikeLatest, member)
    persistLike(oneLikeOld, member)
    entityManager.flush()
    entityManager.clear()

    val result = queryRepository.search(condition(sort = CommunityPostSort.LIKE))

    assertThat(result.rows.map { it.post.title }).containsExactly("좋아요 2", "좋아요 1 최신", "좋아요 1 오래됨")
    assertThat(result.rows.map { it.score }).containsExactly(2L, 1L, 1L)
}

@Test
fun `search sorts by non deleted comment count then latest`() {
    val twoComments = persistPost(title = "댓글 2", createdAt = LocalDateTime.of(2026, 6, 12, 8, 0))
    val oneCommentLatest = persistPost(title = "댓글 1 최신", createdAt = LocalDateTime.of(2026, 6, 12, 10, 0))
    val oneCommentOld = persistPost(title = "댓글 1 오래됨", createdAt = LocalDateTime.of(2026, 6, 12, 9, 0))
    persistComment(twoComments)
    persistComment(twoComments, LocalDateTime.of(2026, 6, 12, 9, 32))
    persistDeletedComment(twoComments)
    persistComment(oneCommentLatest)
    persistComment(oneCommentOld)
    entityManager.flush()
    entityManager.clear()

    val result = queryRepository.search(condition(sort = CommunityPostSort.COMMENT))

    assertThat(result.rows.map { it.post.title }).containsExactly("댓글 2", "댓글 1 최신", "댓글 1 오래됨")
    assertThat(result.rows.map { it.score }).containsExactly(2L, 1L, 1L)
}

@Test
fun `search sorts by popular score then latest`() {
    val popular = persistPost(title = "인기 3", createdAt = LocalDateTime.of(2026, 6, 12, 8, 0))
    val tiedLatest = persistPost(title = "인기 2 최신", createdAt = LocalDateTime.of(2026, 6, 12, 10, 0))
    val tiedOld = persistPost(title = "인기 2 오래됨", createdAt = LocalDateTime.of(2026, 6, 12, 9, 0))
    persistLike(popular, member)
    persistComment(popular)
    persistComment(popular, LocalDateTime.of(2026, 6, 12, 9, 32))
    persistLike(tiedLatest, member)
    persistComment(tiedLatest)
    persistLike(tiedOld, member)
    persistComment(tiedOld)
    entityManager.flush()
    entityManager.clear()

    val result = queryRepository.search(condition(sort = CommunityPostSort.POPULAR))

    assertThat(result.rows.map { it.post.title }).containsExactly("인기 3", "인기 2 최신", "인기 2 오래됨")
    assertThat(result.rows.map { it.score }).containsExactly(3L, 2L, 2L)
}

@Test
fun `search paginates count based sort with cursor`() {
    val extraMember = persist(Member(email = "cursor-extra@example.com", passwordHash = null), now)
    val top = persistPost(title = "좋아요 3", createdAt = LocalDateTime.of(2026, 6, 12, 10, 0))
    val middle = persistPost(title = "좋아요 2", createdAt = LocalDateTime.of(2026, 6, 12, 9, 0))
    val bottom = persistPost(title = "좋아요 1", createdAt = LocalDateTime.of(2026, 6, 12, 8, 0))
    listOf(member, otherMember, extraMember).forEach { persistLike(top, it) }
    listOf(member, otherMember).forEach { persistLike(middle, it) }
    persistLike(bottom, member)
    entityManager.flush()
    entityManager.clear()

    val firstPage = queryRepository.search(condition(sort = CommunityPostSort.LIKE, size = 2))
    val last = firstPage.rows.last()
    val secondPage = queryRepository.search(
        condition(
            sort = CommunityPostSort.LIKE,
            cursor = CommunityPostQueryRepository.Cursor(
                sort = CommunityPostSort.LIKE,
                score = last.score,
                createdAt = last.post.createdAt,
                id = requireNotNull(last.post.id)
            ),
            size = 2
        )
    )

    assertThat(firstPage.rows.map { it.post.title }).containsExactly("좋아요 3", "좋아요 2")
    assertThat(secondPage.rows.map { it.post.title }).containsExactly("좋아요 1")
}
```

- [ ] **Step 2: Run repository tests and verify they fail**

Run:

```bash
cd backend
./gradlew :domain:test --tests "com.chamchamcham.domain.community.CommunityPostQueryRepositoryTest"
```

Expected: FAIL because repository implementation still hardcodes latest order
and old cursor fields.

- [ ] **Step 3: Implement selected post rows and count expressions**

Modify `CommunityPostQueryRepositoryImpl` by adding:

```kotlin
private data class SelectedPost(
    val post: CommunityPost,
    val commentCount: Long,
    val likeCount: Long,
    val score: Long?
)

private val commentCountExpression =
    "(select count(c) from CommunityComment c where c.post = p and c.isDeleted = false)"

private val likeCountExpression =
    "(select count(l) from CommunityPostLike l where l.post = p)"
```

Update `search` to use selected rows:

```kotlin
override fun search(condition: CommunityPostQueryRepository.SearchCondition): CommunityPostQueryRepository.SearchResult {
    val selectedPosts = findPosts(condition)
    if (selectedPosts.isEmpty()) {
        return CommunityPostQueryRepository.SearchResult(emptyList())
    }

    val postIds = selectedPosts.map { requireNotNull(it.post.id) { "Persisted post id is required" } }
    val thumbnails = findThumbnails(postIds)
    val likedPostIds = findLikedPostIds(postIds, condition.memberId)

    return CommunityPostQueryRepository.SearchResult(
        rows = selectedPosts.map { selected ->
            val postId = requireNotNull(selected.post.id) { "Persisted post id is required" }
            CommunityPostQueryRepository.Row(
                post = selected.post,
                thumbnailUrl = thumbnails[postId],
                commentCount = selected.commentCount,
                likeCount = selected.likeCount,
                likedByMe = likedPostIds.contains(postId),
                score = selected.score
            )
        }
    )
}
```

- [ ] **Step 4: Replace `findPosts` with sort-aware query**

Replace `findPosts`:

```kotlin
private fun findPosts(condition: CommunityPostQueryRepository.SearchCondition): List<SelectedPost> {
    val where = mutableListOf("p.isDeleted = false")
    val params = mutableMapOf<String, Any>()

    condition.cropId?.let {
        where += "p.crop.id = :cropId"
        params["cropId"] = it
    }
    condition.postType?.let {
        where += "p.postType = :postType"
        params["postType"] = it
    }
    condition.keyword?.trim()?.lowercase()?.takeIf(String::isNotEmpty)?.let {
        where += "(lower(p.title) like :keyword or lower(p.body) like :keyword)"
        params["keyword"] = "%$it%"
    }
    if (condition.mineOnly) {
        where += "p.author.id = :memberId"
        params["memberId"] = condition.memberId
    }
    if (condition.likedOnly) {
        where += "exists (select 1 from CommunityPostLike l where l.post = p and l.member.id = :memberId)"
        params["memberId"] = condition.memberId
    }

    applyCursor(condition, where, params)

    val query = entityManager.createQuery(
        """
        select p, $commentCountExpression, $likeCountExpression
        from CommunityPost p
        where ${where.joinToString(" and ")}
        order by ${orderBy(condition.sort)}
        """.trimIndent()
    )
    params.forEach(query::setParameter)
    query.maxResults = condition.size

    @Suppress("UNCHECKED_CAST")
    val rows = query.resultList as List<Array<Any>>
    return rows.map { row ->
        val post = row[0] as CommunityPost
        val commentCount = row[1] as Long
        val likeCount = row[2] as Long
        SelectedPost(
            post = post,
            commentCount = commentCount,
            likeCount = likeCount,
            score = scoreOf(condition.sort, likeCount, commentCount)
        )
    }
}
```

Add helpers:

```kotlin
private fun applyCursor(
    condition: CommunityPostQueryRepository.SearchCondition,
    where: MutableList<String>,
    params: MutableMap<String, Any>
) {
    val cursor = condition.cursor ?: return
    when (condition.sort) {
        CommunityPostSort.LATEST -> {
            where += "(p.createdAt < :cursorCreatedAt or (p.createdAt = :cursorCreatedAt and p.id < :cursorId))"
        }
        CommunityPostSort.LIKE,
        CommunityPostSort.COMMENT,
        CommunityPostSort.POPULAR -> {
            where += """
                (${scoreExpression(condition.sort)} < :cursorScore
                or (${scoreExpression(condition.sort)} = :cursorScore and p.createdAt < :cursorCreatedAt)
                or (${scoreExpression(condition.sort)} = :cursorScore and p.createdAt = :cursorCreatedAt and p.id < :cursorId))
            """.trimIndent()
            params["cursorScore"] = requireNotNull(cursor.score) { "Cursor score is required for count based sort" }
        }
    }
    params["cursorCreatedAt"] = cursor.createdAt
    params["cursorId"] = cursor.id
}

private fun orderBy(sort: CommunityPostSort): String =
    when (sort) {
        CommunityPostSort.LATEST -> "p.createdAt desc, p.id desc"
        CommunityPostSort.LIKE -> "${scoreExpression(sort)} desc, p.createdAt desc, p.id desc"
        CommunityPostSort.COMMENT -> "${scoreExpression(sort)} desc, p.createdAt desc, p.id desc"
        CommunityPostSort.POPULAR -> "${scoreExpression(sort)} desc, p.createdAt desc, p.id desc"
    }

private fun scoreExpression(sort: CommunityPostSort): String =
    when (sort) {
        CommunityPostSort.LATEST -> "0"
        CommunityPostSort.LIKE -> likeCountExpression
        CommunityPostSort.COMMENT -> commentCountExpression
        CommunityPostSort.POPULAR -> "($likeCountExpression + $commentCountExpression)"
    }

private fun scoreOf(sort: CommunityPostSort, likeCount: Long, commentCount: Long): Long? =
    when (sort) {
        CommunityPostSort.LATEST -> null
        CommunityPostSort.LIKE -> likeCount
        CommunityPostSort.COMMENT -> commentCount
        CommunityPostSort.POPULAR -> likeCount + commentCount
    }
```

Delete unused methods:

```kotlin
private fun countComments(postIds: List<UUID>): Map<UUID, Long>
private fun countLikes(postIds: List<UUID>): Map<UUID, Long>
private fun aggregateCount(queryString: String, postIds: List<UUID>): Map<UUID, Long>
```

- [ ] **Step 5: Run repository tests and verify they pass**

Run:

```bash
cd backend
./gradlew :domain:test --tests "com.chamchamcham.domain.community.CommunityPostQueryRepositoryTest"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/domain/src/main/kotlin/com/chamchamcham/domain/community/CommunityPostQueryRepositoryImpl.kt \
  backend/domain/src/test/kotlin/com/chamchamcham/domain/community/CommunityPostQueryRepositoryTest.kt
git commit -m "feat(community): 게시글 정렬 쿼리 구현"
```

---

### Task 5: API Contract Update

**Files:**

- Modify: `backend/api/src/main/kotlin/com/chamchamcham/api/community/controller/CommunityController.kt`
- Modify: `backend/api/src/main/kotlin/com/chamchamcham/api/community/dto/CommunityResponses.kt`
- Modify: `backend/api/src/test/kotlin/com/chamchamcham/api/community/controller/CommunityControllerTest.kt`

- [ ] **Step 1: Update controller tests first**

In `CommunityControllerTest`, import:

```kotlin
import com.chamchamcham.domain.community.CommunityPostSort
```

Update `postPageResult`:

```kotlin
private fun postPageResult(): CommunityPostResult.Page =
    CommunityPostResult.Page(
        items = listOf(postSummaryResult()),
        nextCursor = "cursor-1"
    )
```

Update `anySearchCondition`:

```kotlin
private fun anySearchCondition(): CommunityPostSearchCondition =
    CommunityPostSearchCondition(
        memberId = memberId,
        cropId = null,
        postType = null,
        keyword = null,
        likedOnly = false,
        mineOnly = false,
        sort = CommunityPostSort.LATEST,
        cursor = null,
        size = 20
    )
```

Update `list posts returns cursor page` assertion:

```kotlin
.andExpect(jsonPath("$.data.nextCursor", equalTo("cursor-1")))
```

Add a request mapping test:

```kotlin
@Test
fun `list posts maps sort and cursor parameters`() {
    val cursor = "cursor-1"
    `when`(
        communityPostService.search(
            CommunityPostSearchCondition(
                memberId = memberId,
                cropId = cropId,
                postType = CommunityPostType.QUESTION,
                keyword = "발아",
                likedOnly = true,
                mineOnly = false,
                sort = CommunityPostSort.POPULAR,
                cursor = cursor,
                size = 10
            )
        )
    ).thenReturn(postPageResult())

    mockMvc.perform(
        get("/api/v1/community/posts")
            .with(authenticatedMember(memberId.toString()))
            .param("cropId", cropId.toString())
            .param("postType", "QUESTION")
            .param("keyword", "발아")
            .param("likedOnly", "true")
            .param("sort", "POPULAR")
            .param("cursor", cursor)
            .param("size", "10")
    )
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.data.nextCursor", equalTo("cursor-1")))
}
```

- [ ] **Step 2: Run API controller tests and verify they fail**

Run:

```bash
cd backend
./gradlew :api:test --tests "com.chamchamcham.api.community.controller.CommunityControllerTest"
```

Expected: FAIL because controller/response still use old cursor parameters.

- [ ] **Step 3: Update response DTO**

Modify `CommunityResponses.PostPageResponse`:

```kotlin
data class PostPageResponse(
    val items: List<PostSummaryResponse>,
    val nextCursor: String?
) {
    companion object {
        fun from(result: CommunityPostResult.Page): PostPageResponse =
            PostPageResponse(
                items = result.items.map(PostSummaryResponse::from),
                nextCursor = result.nextCursor
            )
    }
}
```

- [ ] **Step 4: Update controller request parameters**

Modify `CommunityController.listPosts`:

```kotlin
@GetMapping("/posts")
fun listPosts(
    @AuthenticationPrincipal memberId: String?,
    @RequestParam(required = false) cropId: UUID?,
    @RequestParam(required = false) postType: CommunityPostType?,
    @RequestParam(required = false) keyword: String?,
    @RequestParam(defaultValue = "false") likedOnly: Boolean,
    @RequestParam(defaultValue = "false") mineOnly: Boolean,
    @RequestParam(defaultValue = "LATEST") sort: CommunityPostSort,
    @RequestParam(required = false) cursor: String?,
    @RequestParam(defaultValue = "20") size: Int
): ResponseEntity<ApiResponse<CommunityResponses.PostPageResponse>> {
    val page = communityPostService.search(
        CommunityPostSearchCondition(
            memberId = parseMemberId(memberId),
            cropId = cropId,
            postType = postType,
            keyword = keyword,
            likedOnly = likedOnly,
            mineOnly = mineOnly,
            sort = sort,
            cursor = cursor,
            size = size
        )
    )
    return ResponseEntity.ok(ApiResponse.ok(CommunityResponses.PostPageResponse.from(page)))
}
```

Remove unused import:

```kotlin
import org.springframework.format.annotation.DateTimeFormat
import java.time.LocalDateTime
```

Add import:

```kotlin
import com.chamchamcham.domain.community.CommunityPostSort
```

- [ ] **Step 5: Run API controller tests and verify they pass**

Run:

```bash
cd backend
./gradlew :api:test --tests "com.chamchamcham.api.community.controller.CommunityControllerTest"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/api/src/main/kotlin/com/chamchamcham/api/community/controller/CommunityController.kt \
  backend/api/src/main/kotlin/com/chamchamcham/api/community/dto/CommunityResponses.kt \
  backend/api/src/test/kotlin/com/chamchamcham/api/community/controller/CommunityControllerTest.kt
git commit -m "feat(community): 게시글 정렬 API 계약 변경"
```

---

### Task 6: iOS Harness Sort and Cursor Controls

**Files:**

- Modify: `test-ios/GodsMoveAuthHarness/GodsMoveAuthHarness/APIModels.swift`
- Modify: `test-ios/GodsMoveAuthHarness/GodsMoveAuthHarness/BackendAPIClient.swift`
- Modify: `test-ios/GodsMoveAuthHarness/GodsMoveAuthHarness/HarnessState.swift`
- Modify: `test-ios/GodsMoveAuthHarness/GodsMoveAuthHarness/CommunityTestView.swift`
- Modify: `test-ios/GodsMoveAuthHarness/GodsMoveAuthHarnessTests/BackendAPIClientTests.swift`
- Modify: `test-ios/GodsMoveAuthHarness/GodsMoveAuthHarnessTests/HarnessStateTests.swift`

- [ ] **Step 1: Update Swift tests first**

In `BackendAPIClientTests.swift`, update the community page response fixture:

```swift
let body = """
{"success":true,"data":{"items":[\(communityPostSummaryJSON())],"nextCursor":"cursor-1"},"error":null}
"""
```

Assert:

```swift
XCTAssertEqual(response.value.nextCursor, "cursor-1")
```

Add a path test:

```swift
func testCommunityPostListPathIncludesSortAndCursor() {
    let cropId = UUID(uuidString: "00000000-0000-0000-0000-000000000201")!

    let path = BackendAPIPath.communityPostList(
        cropId: cropId,
        postType: .question,
        keyword: "발아",
        likedOnly: true,
        mineOnly: false,
        sort: .popular,
        cursor: "cursor-1",
        size: 10
    )

    XCTAssertTrue(path.contains("sort=POPULAR"))
    XCTAssertTrue(path.contains("cursor=cursor-1"))
    XCTAssertTrue(path.contains("cropId=\(cropId.uuidString)"))
}
```

In `HarnessStateTests.swift`, update `CommunityPostPageDTO` construction:

```swift
CommunityPostPageDTO(
    items: [communityPostSummary()],
    nextCursor: "cursor-1"
)
```

Assert:

```swift
XCTAssertEqual(state.communityNextCursor, "cursor-1")
```

- [ ] **Step 2: Run Swift tests and verify they fail**

Run from repository root:

```bash
swiftc -typecheck test-ios/GodsMoveAuthHarness/GodsMoveAuthHarness/*.swift \
  test-ios/GodsMoveAuthHarness/GodsMoveAuthHarnessTests/*.swift
```

Expected: FAIL because Swift DTOs and functions still use old cursor fields.

- [ ] **Step 3: Update Swift DTOs**

In `APIModels.swift`, add:

```swift
enum CommunityPostSortDTO: String, Codable, CaseIterable, Identifiable {
    case latest = "LATEST"
    case like = "LIKE"
    case comment = "COMMENT"
    case popular = "POPULAR"

    var id: String { rawValue }

    var title: String {
        switch self {
        case .latest:
            return "Latest"
        case .like:
            return "Like"
        case .comment:
            return "Comment"
        case .popular:
            return "Popular"
        }
    }
}
```

Update page DTO:

```swift
struct CommunityPostPageDTO: Codable, Equatable {
    let items: [CommunityPostSummaryDTO]
    let nextCursor: String?
}
```

- [ ] **Step 4: Update Swift API client paths**

In `BackendAPIClient.swift`, update `listCommunityPosts` signature:

```swift
func listCommunityPosts(
    cropId: UUID?,
    postType: CommunityPostTypeDTO?,
    keyword: String?,
    likedOnly: Bool,
    mineOnly: Bool,
    sort: CommunityPostSortDTO,
    cursor: String?,
    size: Int,
    accessToken: String
) async throws -> DebugResponse<CommunityPostPageDTO>
```

Pass values into `BackendAPIPath.communityPostList`.

Update `BackendAPIPath.communityPostList` signature:

```swift
static func communityPostList(
    cropId: UUID?,
    postType: CommunityPostTypeDTO?,
    keyword: String?,
    likedOnly: Bool,
    mineOnly: Bool,
    sort: CommunityPostSortDTO,
    cursor: String?,
    size: Int
) -> String
```

Add query items:

```swift
URLQueryItem(name: "sort", value: sort.rawValue),
URLQueryItem(name: "size", value: String(size))
```

Append cursor only when nonblank:

```swift
if let cursor = cursor?.trimmingCharacters(in: .whitespacesAndNewlines),
   !cursor.isEmpty {
    queryItems.append(URLQueryItem(name: "cursor", value: cursor))
}
```

- [ ] **Step 5: Update harness state**

In `HarnessState.swift`, add published cursor state:

```swift
@Published private(set) var communityNextCursor: String?
```

Update `loadCommunityPosts` signature:

```swift
func loadCommunityPosts(
    cropId: UUID?,
    postType: CommunityPostTypeDTO?,
    keyword: String?,
    likedOnly: Bool,
    mineOnly: Bool,
    sort: CommunityPostSortDTO,
    cursor: String?,
    append: Bool,
    size: Int
) async
```

When applying response:

```swift
func applyCommunityPosts(_ response: DebugResponse<CommunityPostPageDTO>, append: Bool = false) {
    if append {
        communityPosts += response.value.items
    } else {
        communityPosts = response.value.items
    }
    communityNextCursor = response.value.nextCursor
    latestRawResponse = prettyJSON(response.rawBody, redactingSensitiveFields: true)
    latestError = nil
    phase = .success("Community posts loaded")
}
```

Use the same `append` value in the private request-id guarded apply method.

- [ ] **Step 6: Update SwiftUI controls**

In `CommunityTestView.swift`, add state:

```swift
@State private var sort: CommunityPostSortDTO = .latest
```

In `filterSection`, add a segmented picker:

```swift
Picker("Sort", selection: $sort) {
    ForEach(CommunityPostSortDTO.allCases) { sort in
        Text(sort.title).tag(sort)
    }
}
.pickerStyle(.segmented)
```

Add a next page button:

```swift
Button {
    Task { await loadNextPosts() }
} label: {
    Label("Load Next Page", systemImage: "arrow.down.circle")
}
.disabled(state.isBusy || state.accessToken == nil || state.communityNextCursor == nil)
```

Update `loadPosts`:

```swift
private func loadPosts() async {
    do {
        await state.loadCommunityPosts(
            cropId: try cropIDForSearch(),
            postType: filterType.postType,
            keyword: keyword,
            likedOnly: likedOnly,
            mineOnly: mineOnly,
            sort: sort,
            cursor: nil,
            append: false,
            size: pageSize
        )
    } catch {
        state.applyFailure(error)
    }
}
```

Add `loadNextPosts`:

```swift
private func loadNextPosts() async {
    do {
        await state.loadCommunityPosts(
            cropId: try cropIDForSearch(),
            postType: filterType.postType,
            keyword: keyword,
            likedOnly: likedOnly,
            mineOnly: mineOnly,
            sort: sort,
            cursor: state.communityNextCursor,
            append: true,
            size: pageSize
        )
    } catch {
        state.applyFailure(error)
    }
}
```

- [ ] **Step 7: Run Swift typecheck**

Run:

```bash
swiftc -typecheck test-ios/GodsMoveAuthHarness/GodsMoveAuthHarness/*.swift \
  test-ios/GodsMoveAuthHarness/GodsMoveAuthHarnessTests/*.swift
```

Expected: PASS.

- [ ] **Step 8: Commit backend-independent harness changes if tracked**

Check:

```bash
git status --short test-ios
```

If `test-ios` is ignored and untracked in this repository, do not force-add it.
If these files are tracked in the current branch, commit:

```bash
git add test-ios/GodsMoveAuthHarness/GodsMoveAuthHarness/APIModels.swift \
  test-ios/GodsMoveAuthHarness/GodsMoveAuthHarness/BackendAPIClient.swift \
  test-ios/GodsMoveAuthHarness/GodsMoveAuthHarness/HarnessState.swift \
  test-ios/GodsMoveAuthHarness/GodsMoveAuthHarness/CommunityTestView.swift \
  test-ios/GodsMoveAuthHarness/GodsMoveAuthHarnessTests/BackendAPIClientTests.swift \
  test-ios/GodsMoveAuthHarness/GodsMoveAuthHarnessTests/HarnessStateTests.swift
git commit -m "test(ios): 커뮤니티 정렬 커서 테스트 지원"
```

---

### Task 7: Integration Verification

**Files:**

- Verify: backend modules and changed plan/spec docs.

- [ ] **Step 1: Run focused backend tests**

Run:

```bash
cd backend
./gradlew :domain:test --tests "com.chamchamcham.domain.community.CommunityPostQueryRepositoryTest" \
  :application:test --tests "com.chamchamcham.application.common.OpaqueCursorCodecTest" \
  --tests "com.chamchamcham.application.community.CommunityPostServiceTest" \
  :api:test --tests "com.chamchamcham.api.community.controller.CommunityControllerTest"
```

Expected: PASS.

- [ ] **Step 2: Run broader backend test suite for touched modules**

Run:

```bash
cd backend
./gradlew :domain:test :application:test :api:test
```

Expected: PASS.

- [ ] **Step 3: Verify public API behavior manually**

Start the backend with the local profile if no server is running:

```bash
cd backend
./gradlew :api:bootRun
```

Using the existing authenticated test flow, call:

```http
GET /api/v1/community/posts?sort=LATEST&size=20
GET /api/v1/community/posts?sort=LIKE&size=20
GET /api/v1/community/posts?sort=COMMENT&size=20
GET /api/v1/community/posts?sort=POPULAR&size=20
```

Expected:

- Each response contains `data.items`.
- Each response contains `data.nextCursor`.
- When `nextCursor` is non-null, sending it back as `cursor` returns the next
  page without repeating the last post from the previous page.
- Sending a `LIKE` cursor with `sort=POPULAR` returns `400` with
  `COMMON_003`.

- [ ] **Step 4: Check git diff for unintended files**

Run:

```bash
git status --short
git diff --stat dev...HEAD
```

Expected:

- Backend source and tests reflect only community sort/cursor changes.
- `.claude/` remains untracked and unstaged.
- `test-ios` changes are present only if intentionally tracked.

- [ ] **Step 5: Push the branch**

Run:

```bash
git push origin feat/community
```

Expected: Push succeeds and PR `feat/community -> dev` updates.
