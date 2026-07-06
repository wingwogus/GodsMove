# Community Comment Pagination And Image Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add latest-first cursor pagination to root comments and allow one optional image on each root comment or reply.

**Architecture:** Reuse the existing `OpaqueCursorCodec` for cursor transport. Keep comment-specific paging rules inside `CommunityCommentService` and `CommunityCommentRepository`, with no generic pagination framework and no comment sort enum. Store the optional comment image as one nullable FK from `CommunityComment` to `UploadedMedia`.

**Tech Stack:** Spring Boot 3.5, Kotlin 1.9, Spring Data JPA/JPQL, Jackson cursor codec, JUnit 5, Mockito, SwiftUI local test harness.

---

## Scope Check

This plan implements one cohesive API-contract slice:

- `GET /api/v1/community/posts/{postId}/comments?cursor=&size=` returns `{ items, nextCursor }`.
- Only root comments are paginated.
- Returned root comments include all replies.
- Root comments are latest-first: `createdAt desc, id desc`.
- Replies are oldest-first: `createdAt asc, id asc`.
- `POST /api/v1/community/posts/{postId}/comments` accepts optional `mediaId`.
- Each comment or reply can have at most one image.

Do not add QueryDSL, a generic pagination abstraction, reply pagination, comment sort options, a new media usage enum, or a separate comment-media mapping table.

## File Structure

Create:

- `backend/application/src/main/kotlin/com/chamchamcham/application/community/CommunityCommentCursorPayload.kt`
  - Opaque cursor payload for comment root pagination.
- `backend/domain/src/test/kotlin/com/chamchamcham/domain/community/CommunityCommentRepositoryTest.kt`
  - Repository tests for root-page and reply-batch query behavior.
- `backend/docs/db/community-comment-media-schema.sql`
  - Manual DDL note because Flyway is not in this backend yet.

Modify:

- `backend/domain/src/main/kotlin/com/chamchamcham/domain/community/CommunityComment.kt`
  - Add nullable `media` association.
- `backend/domain/src/main/kotlin/com/chamchamcham/domain/community/CommunityCommentRepository.kt`
  - Replace all-comment list lookup with root-page and reply-batch queries.
- `backend/application/src/main/kotlin/com/chamchamcham/application/community/CommunityCommentCommand.kt`
  - Add `mediaId` to create command.
- `backend/application/src/main/kotlin/com/chamchamcham/application/community/CommunityCommentResult.kt`
  - Add `Page` and `imageUrl`.
- `backend/application/src/main/kotlin/com/chamchamcham/application/community/CommunityCommentService.kt`
  - Decode/encode cursor, validate page size, attach optional media, page roots and batch-fetch replies.
- `backend/application/src/test/kotlin/com/chamchamcham/application/community/CommunityCommentServiceTest.kt`
  - Cover page contract, cursor behavior, replies, deleted image hiding, media validation.
- `backend/api/src/main/kotlin/com/chamchamcham/api/community/dto/CommunityRequests.kt`
  - Add optional `mediaId`.
- `backend/api/src/main/kotlin/com/chamchamcham/api/community/dto/CommunityResponses.kt`
  - Add `CommentPageResponse` and `imageUrl`.
- `backend/api/src/main/kotlin/com/chamchamcham/api/community/controller/CommunityController.kt`
  - Accept `cursor` and `size` for comment list; map comment `mediaId`.
- `backend/api/src/test/kotlin/com/chamchamcham/api/community/controller/CommunityControllerTest.kt`
  - Cover comment page response, cursor request mapping, comment create media mapping.
- `test-ios/GodsMoveAuthHarness/GodsMoveAuthHarness/APIModels.swift`
  - Add `mediaId` to comment create request and `imageUrl`/page DTO to comment responses.
- `test-ios/GodsMoveAuthHarness/GodsMoveAuthHarness/BackendAPIClient.swift`
  - Add cursor/size path support for comments.
- `test-ios/GodsMoveAuthHarness/GodsMoveAuthHarness/HarnessState.swift`
  - Store `communityCommentsNextCursor`, support append load, pass media id.
- `test-ios/GodsMoveAuthHarness/GodsMoveAuthHarness/CommunityTestView.swift`
  - Allow one uploaded comment image and a next-comment-page button.
- `test-ios/GodsMoveAuthHarness/GodsMoveAuthHarnessTests/BackendAPIClientTests.swift`
  - Update DTO/path tests.
- `test-ios/GodsMoveAuthHarness/GodsMoveAuthHarnessTests/HarnessStateTests.swift`
  - Update state tests.

---

### Task 1: Domain Comment Media And Paging Queries

**Files:**

- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/community/CommunityComment.kt`
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/community/CommunityCommentRepository.kt`
- Create: `backend/domain/src/test/kotlin/com/chamchamcham/domain/community/CommunityCommentRepositoryTest.kt`
- Create: `backend/docs/db/community-comment-media-schema.sql`

- [ ] **Step 1: Add failing repository tests**

Create `backend/domain/src/test/kotlin/com/chamchamcham/domain/community/CommunityCommentRepositoryTest.kt` with focused tests for the new repository contract:

```kotlin
package com.chamchamcham.domain.community

import com.chamchamcham.domain.crop.Crop
import com.chamchamcham.domain.crop.CropUsePartCategory
import com.chamchamcham.domain.member.Member
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import java.time.LocalDateTime
import java.util.UUID

@DataJpaTest
class CommunityCommentRepositoryTest(
    @Autowired private val entityManager: EntityManager,
    @Autowired private val repository: CommunityCommentRepository
) {
    @Test
    fun `find root page orders latest first and applies cursor`() {
        val fixture = persistPostFixture()
        val older = persistComment(fixture.post, fixture.member, "older", createdAt = LocalDateTime.of(2026, 6, 12, 9, 0))
        val middle = persistComment(fixture.post, fixture.member, "middle", createdAt = LocalDateTime.of(2026, 6, 12, 10, 0))
        val newest = persistComment(fixture.post, fixture.member, "newest", createdAt = LocalDateTime.of(2026, 6, 12, 11, 0))
        persistComment(fixture.post, fixture.member, "reply", parent = newest, createdAt = LocalDateTime.of(2026, 6, 12, 11, 1))
        flushAndClear()

        val firstPage = repository.findRootPage(
            postId = requireNotNull(fixture.post.id),
            cursorCreatedAt = null,
            cursorId = null,
            pageable = PageRequest.of(0, 2)
        )
        val secondPage = repository.findRootPage(
            postId = requireNotNull(fixture.post.id),
            cursorCreatedAt = firstPage.last().createdAt,
            cursorId = requireNotNull(firstPage.last().id),
            pageable = PageRequest.of(0, 2)
        )

        assertThat(firstPage.map { it.body }).containsExactly("newest", "middle")
        assertThat(secondPage.map { it.body }).containsExactly("older")
        assertThat(firstPage).allMatch { it.parentComment == null }
        assertThat(older.id).isNotNull()
        assertThat(middle.id).isNotNull()
    }

    @Test
    fun `find replies returns only requested parents oldest first`() {
        val fixture = persistPostFixture()
        val root1 = persistComment(fixture.post, fixture.member, "root1", createdAt = LocalDateTime.of(2026, 6, 12, 9, 0))
        val root2 = persistComment(fixture.post, fixture.member, "root2", createdAt = LocalDateTime.of(2026, 6, 12, 9, 1))
        persistComment(fixture.post, fixture.member, "root1 reply 2", parent = root1, createdAt = LocalDateTime.of(2026, 6, 12, 9, 3))
        persistComment(fixture.post, fixture.member, "root1 reply 1", parent = root1, createdAt = LocalDateTime.of(2026, 6, 12, 9, 2))
        persistComment(fixture.post, fixture.member, "root2 reply", parent = root2, createdAt = LocalDateTime.of(2026, 6, 12, 9, 4))
        flushAndClear()

        val replies = repository.findRepliesByParentIds(listOf(requireNotNull(root1.id)))

        assertThat(replies.map { it.body }).containsExactly("root1 reply 1", "root1 reply 2")
        assertThat(replies).allMatch { it.parentComment?.id == root1.id }
    }

    private fun persistPostFixture(): Fixture {
        val member = Member(id = UUID.randomUUID(), email = "member@example.com", passwordHash = null)
        val crop = Crop(externalNo = 422, name = "황기", usePartCategory = CropUsePartCategory.ROOT_BARK)
        entityManager.persist(member)
        entityManager.persist(crop)
        val post = CommunityPost(
            author = member,
            crop = crop,
            postType = CommunityPostType.QUESTION,
            title = "황기 발아율",
            body = "싹이 거의 올라오지 않아요."
        )
        entityManager.persist(post)
        return Fixture(member, post)
    }

    private fun persistComment(
        post: CommunityPost,
        member: Member,
        body: String,
        parent: CommunityComment? = null,
        createdAt: LocalDateTime
    ): CommunityComment {
        val comment = CommunityComment(post = post, parentComment = parent, author = member, body = body)
        entityManager.persist(comment)
        entityManager.flush()
        setCreatedAt(comment, createdAt)
        return comment
    }

    private fun flushAndClear() {
        entityManager.flush()
        entityManager.clear()
    }

    private fun setCreatedAt(comment: CommunityComment, createdAt: LocalDateTime) {
        entityManager
            .createNativeQuery("update community_comment set created_at = :createdAt where id = :id")
            .setParameter("createdAt", createdAt)
            .setParameter("id", comment.id)
            .executeUpdate()
    }

    private data class Fixture(val member: Member, val post: CommunityPost)
}
```

- [ ] **Step 2: Run repository tests and verify they fail**

Run:

```bash
cd backend
./gradlew :domain:test --tests "com.chamchamcham.domain.community.CommunityCommentRepositoryTest"
```

Expected: FAIL because `findRootPage` and `findRepliesByParentIds` do not exist.

- [ ] **Step 3: Add nullable media association**

Modify `CommunityComment.kt`:

```kotlin
import com.chamchamcham.domain.media.UploadedMedia
```

Add constructor property after `body`:

```kotlin
@Column(nullable = false, columnDefinition = "text")
val body: String,

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "media_id")
val media: UploadedMedia? = null,

@Column(name = "is_deleted", nullable = false)
var isDeleted: Boolean = false,
```

- [ ] **Step 4: Replace comment repository list query with paging queries**

Modify `CommunityCommentRepository.kt`:

```kotlin
package com.chamchamcham.domain.community

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.data.domain.Pageable
import java.time.LocalDateTime
import java.util.UUID

interface CommunityCommentRepository : JpaRepository<CommunityComment, UUID> {
    @Query(
        """
        select c
        from CommunityComment c
        join fetch c.author a
        left join fetch a.profileMedia
        left join fetch c.media
        where c.post.id = :postId
          and c.parentComment is null
          and (
            :cursorCreatedAt is null
            or c.createdAt < :cursorCreatedAt
            or (c.createdAt = :cursorCreatedAt and c.id < :cursorId)
          )
        order by c.createdAt desc, c.id desc
        """
    )
    fun findRootPage(
        @Param("postId") postId: UUID,
        @Param("cursorCreatedAt") cursorCreatedAt: LocalDateTime?,
        @Param("cursorId") cursorId: UUID?,
        pageable: Pageable
    ): List<CommunityComment>

    @Query(
        """
        select c
        from CommunityComment c
        join fetch c.parentComment p
        join fetch c.author a
        left join fetch a.profileMedia
        left join fetch c.media
        where p.id in :parentIds
        order by c.createdAt asc, c.id asc
        """
    )
    fun findRepliesByParentIds(@Param("parentIds") parentIds: Collection<UUID>): List<CommunityComment>

    fun countByPost_IdAndIsDeletedFalse(postId: UUID): Long
}
```

- [ ] **Step 5: Add manual schema note**

Create `backend/docs/db/community-comment-media-schema.sql`:

```sql
-- Community comments may attach at most one uploaded image.
-- Flyway is not installed in this backend yet; apply manually to dev/prod schemas.

alter table community_comment
    add column if not exists media_id uuid;

alter table community_comment
    add constraint fk_community_comment_media
        foreign key (media_id)
        references uploaded_media(id);

create index if not exists idx_community_comment_post_parent_created_id
    on community_comment (post_id, parent_comment_id, created_at desc, id desc);
```

- [ ] **Step 6: Run repository tests**

Run:

```bash
cd backend
./gradlew :domain:test --tests "com.chamchamcham.domain.community.CommunityCommentRepositoryTest"
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/domain/src/main/kotlin/com/chamchamcham/domain/community/CommunityComment.kt \
  backend/domain/src/main/kotlin/com/chamchamcham/domain/community/CommunityCommentRepository.kt \
  backend/domain/src/test/kotlin/com/chamchamcham/domain/community/CommunityCommentRepositoryTest.kt \
  backend/docs/db/community-comment-media-schema.sql
git commit -m "feat(community): 댓글 이미지와 루트 페이지 조회 추가"
```

---

### Task 2: Application Comment Cursor And Image Attachment

**Files:**

- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/community/CommunityCommentCursorPayload.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/community/CommunityCommentCommand.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/community/CommunityCommentResult.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/community/CommunityCommentService.kt`
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/community/CommunityCommentServiceTest.kt`

- [ ] **Step 1: Update service tests first**

Update `CommunityCommentServiceTest` setup to mock the new dependencies:

```kotlin
@Mock private lateinit var uploadedMediaRepository: UploadedMediaRepository
private val cursorCodec = OpaqueCursorCodec()
private val mediaId = UUID.fromString("00000000-0000-0000-0000-000000000601")

service = CommunityCommentService(
    memberRepository = memberRepository,
    communityPostRepository = communityPostRepository,
    communityCommentRepository = communityCommentRepository,
    uploadedMediaRepository = uploadedMediaRepository,
    cursorCodec = cursorCodec
)
```

Update `createCommand` helper:

```kotlin
private fun createCommand(
    parentCommentId: UUID? = null,
    body: String = "저도 궁금해요",
    mediaId: UUID? = null
): CommunityCommentCommand.Create =
    CommunityCommentCommand.Create(
        memberId = memberId,
        postId = postId,
        parentCommentId = parentCommentId,
        body = body,
        mediaId = mediaId
    )
```

Add these tests:

```kotlin
@Test
fun `list returns root comment page with replies and next cursor`() {
    val newest = comment(id = rootCommentId, parent = null, author = member, body = "newest")
    val older = comment(id = UUID.fromString("00000000-0000-0000-0000-000000000203"), parent = null, author = member, body = "older")
    val overflow = comment(id = UUID.fromString("00000000-0000-0000-0000-000000000204"), parent = null, author = member, body = "overflow")
    val reply = comment(id = replyCommentId, parent = newest, author = otherMember, body = "reply")
    setCreatedAt(newest, LocalDateTime.of(2026, 6, 12, 11, 0))
    setCreatedAt(older, LocalDateTime.of(2026, 6, 12, 10, 0))
    setCreatedAt(overflow, LocalDateTime.of(2026, 6, 12, 9, 0))
    setCreatedAt(reply, LocalDateTime.of(2026, 6, 12, 11, 1))
    `when`(communityCommentRepository.findRootPage(postId, null, null, PageRequest.of(0, 3)))
        .thenReturn(listOf(newest, older, overflow))
    `when`(communityCommentRepository.findRepliesByParentIds(listOf(rootCommentId, requireNotNull(older.id))))
        .thenReturn(listOf(reply))

    val page = service.list(postId = postId, cursor = null, size = 2)

    assertEquals(listOf("newest", "older"), page.items.map { it.body })
    assertEquals(listOf("reply"), page.items.first().replies.map { it.body })
    assertTrue(page.nextCursor?.isNotBlank() == true)
}

@Test
fun `list passes decoded cursor to repository`() {
    val payload = CommunityCommentCursorPayload(
        createdAt = LocalDateTime.of(2026, 6, 12, 10, 0),
        id = rootCommentId
    )
    val cursor = cursorCodec.encode(payload)
    `when`(communityCommentRepository.findRootPage(postId, payload.createdAt, payload.id, PageRequest.of(0, 21)))
        .thenReturn(emptyList())

    val page = service.list(postId = postId, cursor = cursor, size = 20)

    assertTrue(page.items.isEmpty())
    assertNull(page.nextCursor)
}

@Test
fun `list rejects invalid page size`() {
    val exception = assertThrows(BusinessException::class.java) {
        service.list(postId = postId, cursor = null, size = 0)
    }

    assertEquals(ErrorCode.INVALID_INPUT, exception.errorCode)
}

@Test
fun `deleted comment hides image url`() {
    val media = uploadedMedia(id = mediaId, owner = member)
    val deleted = comment(id = rootCommentId, parent = null, author = member, body = "before delete", media = media, isDeleted = true)
    `when`(communityCommentRepository.findRootPage(postId, null, null, PageRequest.of(0, 21)))
        .thenReturn(listOf(deleted))
    `when`(communityCommentRepository.findRepliesByParentIds(listOf(rootCommentId)))
        .thenReturn(emptyList())

    val page = service.list(postId = postId, cursor = null, size = 20)

    assertEquals("삭제된 댓글입니다.", page.items.single().body)
    assertNull(page.items.single().imageUrl)
}

@Test
fun `create comment attaches optional media`() {
    val media = uploadedMedia(id = mediaId, owner = member)
    stubCreate()
    `when`(uploadedMediaRepository.findById(mediaId)).thenReturn(Optional.of(media))
    `when`(communityCommentRepository.save(any(CommunityComment::class.java))).thenAnswer { invocation ->
        val comment = invocation.arguments[0] as CommunityComment
        comment(id = rootCommentId, parent = comment.parentComment, author = comment.author, body = comment.body, media = comment.media)
    }

    service.create(createCommand(mediaId = mediaId))

    val savedComment = capturedComment()
    assertEquals(mediaId, savedComment.media?.id)
    assertEquals(UploadedMediaStatus.ATTACHED, media.status)
}
```

Add imports and update the existing comment helper:

```kotlin
import com.chamchamcham.domain.media.UploadedMedia
import com.chamchamcham.domain.media.UploadedMediaRepository
import com.chamchamcham.domain.media.UploadedMediaStatus
import com.chamchamcham.domain.media.UploadedMediaType
import com.chamchamcham.domain.media.UploadedMediaUsageType
import org.springframework.data.domain.PageRequest
```

```kotlin
private fun comment(
    id: UUID,
    parent: CommunityComment?,
    author: Member,
    body: String,
    media: UploadedMedia? = null,
    isDeleted: Boolean = false
): CommunityComment =
    CommunityComment(
        id = id,
        post = post,
        parentComment = parent,
        author = author,
        body = body,
        media = media,
        isDeleted = isDeleted
    )
```

Add the media helper:

```kotlin
private fun uploadedMedia(id: UUID, owner: Member): UploadedMedia =
    UploadedMedia(
        id = id,
        owner = owner,
        mediaType = UploadedMediaType.IMAGE,
        usageType = UploadedMediaUsageType.COMMUNITY_POST,
        fileUrl = "https://example.test/comment.jpg",
        cloudinaryPublicId = "community/comment"
    )
```

- [ ] **Step 2: Run application comment tests and verify they fail**

Run:

```bash
cd backend
./gradlew :application:test --tests "com.chamchamcham.application.community.CommunityCommentServiceTest"
```

Expected: FAIL because command/result/service contracts still use the old list shape.

- [ ] **Step 3: Add comment cursor payload**

Create `CommunityCommentCursorPayload.kt`:

```kotlin
package com.chamchamcham.application.community

import java.time.LocalDateTime
import java.util.UUID

data class CommunityCommentCursorPayload(
    val createdAt: LocalDateTime,
    val id: UUID
)
```

- [ ] **Step 4: Update command and result contracts**

Modify `CommunityCommentCommand.kt`:

```kotlin
data class Create(
    val memberId: UUID,
    val postId: UUID,
    val parentCommentId: UUID?,
    val body: String,
    val mediaId: UUID?
)
```

Modify `CommunityCommentResult.kt`:

```kotlin
object CommunityCommentResult {
    data class CommentId(val id: UUID)

    data class Page(
        val items: List<Comment>,
        val nextCursor: String?
    )

    data class Comment(
        val id: UUID,
        val parentCommentId: UUID?,
        val author: CommunityPostResult.AuthorSummary,
        val body: String,
        val imageUrl: String?,
        val deleted: Boolean,
        val createdAt: LocalDateTime,
        val replies: List<Comment> = emptyList()
    )
}
```

- [ ] **Step 5: Implement service changes**

Modify `CommunityCommentService` constructor:

```kotlin
import com.chamchamcham.application.common.OpaqueCursorCodec
import com.chamchamcham.domain.media.UploadedMedia
import com.chamchamcham.domain.media.UploadedMediaRepository
import com.chamchamcham.domain.media.UploadedMediaUsageType
import org.springframework.data.domain.PageRequest

class CommunityCommentService(
    private val memberRepository: MemberRepository,
    private val communityPostRepository: CommunityPostRepository,
    private val communityCommentRepository: CommunityCommentRepository,
    private val uploadedMediaRepository: UploadedMediaRepository,
    private val cursorCodec: OpaqueCursorCodec
)
```

Update create:

```kotlin
val media = command.mediaId?.let { validateMedia(command.memberId, it) }

val comment = communityCommentRepository.save(
    CommunityComment(
        post = post,
        parentComment = parentComment,
        author = member,
        body = command.body,
        media = media
    )
)
media?.markAttached()
```

Replace list:

```kotlin
@Transactional(readOnly = true)
fun list(postId: UUID, cursor: String?, size: Int): CommunityCommentResult.Page {
    validatePageSize(size)
    val decodedCursor = decodeCursor(cursor)
    val rootComments = communityCommentRepository.findRootPage(
        postId = postId,
        cursorCreatedAt = decodedCursor?.createdAt,
        cursorId = decodedCursor?.id,
        pageable = PageRequest.of(0, size + 1)
    )
    val visibleRoots = rootComments.take(size)
    val rootIds = visibleRoots.map { requireNotNull(it.id) { "Persisted comment id is required" } }
    val repliesByParentId = if (rootIds.isEmpty()) {
        emptyMap()
    } else {
        communityCommentRepository.findRepliesByParentIds(rootIds)
            .groupBy { requireNotNull(it.parentComment?.id) { "Persisted parent comment id is required" } }
    }
    val nextCursor = if (rootComments.size > size) {
        visibleRoots.lastOrNull()?.let(::encodeCursor)
    } else {
        null
    }
    return CommunityCommentResult.Page(
        items = visibleRoots.map { root ->
            toResult(root, repliesByParentId[requireNotNull(root.id)].orEmpty().map(::toResult))
        },
        nextCursor = nextCursor
    )
}
```

Add helpers:

```kotlin
private fun validatePageSize(size: Int) {
    if (size <= 0 || size == Int.MAX_VALUE) {
        throw BusinessException(ErrorCode.INVALID_INPUT)
    }
}

private fun decodeCursor(cursor: String?): CommunityCommentCursorPayload? {
    if (cursor.isNullOrBlank()) {
        return null
    }
    return cursorCodec.decode(cursor, CommunityCommentCursorPayload::class.java)
}

private fun encodeCursor(comment: CommunityComment): String =
    cursorCodec.encode(
        CommunityCommentCursorPayload(
            createdAt = comment.createdAt,
            id = requireNotNull(comment.id) { "Persisted comment id is required" }
        )
    )

private fun validateMedia(memberId: UUID, mediaId: UUID): UploadedMedia {
    val media = uploadedMediaRepository.findById(mediaId).orElseThrow {
        BusinessException(ErrorCode.MEDIA_NOT_FOUND)
    }
    if (media.owner.id != memberId) {
        throw BusinessException(ErrorCode.MEDIA_NOT_OWNED)
    }
    if (media.usageType != UploadedMediaUsageType.COMMUNITY_POST) {
        throw BusinessException(ErrorCode.MEDIA_USAGE_MISMATCH)
    }
    if (!media.isAttachable()) {
        throw BusinessException(ErrorCode.MEDIA_NOT_ATTACHABLE)
    }
    return media
}
```

Update `toResult`:

```kotlin
body = if (comment.isDeleted) DELETED_COMMENT_BODY else comment.body,
imageUrl = if (comment.isDeleted) null else comment.media?.fileUrl,
deleted = comment.isDeleted,
```

- [ ] **Step 6: Run application tests**

Run:

```bash
cd backend
./gradlew :application:test --tests "com.chamchamcham.application.community.CommunityCommentServiceTest"
```

Expected: PASS unless the known unrelated dev/RAG application test compile issue blocks `compileTestKotlin`. If blocked, run `./gradlew :application:compileKotlin` and record the unrelated blocker.

- [ ] **Step 7: Commit**

```bash
git add backend/application/src/main/kotlin/com/chamchamcham/application/community/CommunityCommentCursorPayload.kt \
  backend/application/src/main/kotlin/com/chamchamcham/application/community/CommunityCommentCommand.kt \
  backend/application/src/main/kotlin/com/chamchamcham/application/community/CommunityCommentResult.kt \
  backend/application/src/main/kotlin/com/chamchamcham/application/community/CommunityCommentService.kt \
  backend/application/src/test/kotlin/com/chamchamcham/application/community/CommunityCommentServiceTest.kt
git commit -m "feat(community): 댓글 커서 페이지와 이미지 첨부 추가"
```

---

### Task 3: API Comment Page Contract

**Files:**

- Modify: `backend/api/src/main/kotlin/com/chamchamcham/api/community/dto/CommunityRequests.kt`
- Modify: `backend/api/src/main/kotlin/com/chamchamcham/api/community/dto/CommunityResponses.kt`
- Modify: `backend/api/src/main/kotlin/com/chamchamcham/api/community/controller/CommunityController.kt`
- Modify: `backend/api/src/test/kotlin/com/chamchamcham/api/community/controller/CommunityControllerTest.kt`

- [ ] **Step 1: Update controller tests first**

Add import:

```kotlin
import org.hamcrest.Matchers.nullValue
```

Update comment result fixture:

```kotlin
private fun commentPageResult(): CommunityCommentResult.Page =
    CommunityCommentResult.Page(
        items = listOf(deletedCommentResult()),
        nextCursor = "comment-cursor-1"
    )
```

Update deleted comment fixture:

```kotlin
private fun deletedCommentResult(): CommunityCommentResult.Comment =
    CommunityCommentResult.Comment(
        id = commentId,
        parentCommentId = null,
        author = authorResult(),
        body = "삭제된 댓글입니다.",
        imageUrl = null,
        deleted = true,
        createdAt = createdAt
    )
```

Add or update tests:

```kotlin
@Test
fun `list comments returns cursor page`() {
    `when`(communityCommentService.list(postId, null, 20)).thenReturn(commentPageResult())

    mockMvc.perform(get("/api/v1/community/posts/{postId}/comments", postId))
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.data.items[0].body", equalTo("삭제된 댓글입니다.")))
        .andExpect(jsonPath("$.data.items[0].imageUrl", nullValue()))
        .andExpect(jsonPath("$.data.nextCursor", equalTo("comment-cursor-1")))
}

@Test
fun `list comments maps cursor and size parameters`() {
    `when`(communityCommentService.list(postId, "cursor-1", 10)).thenReturn(commentPageResult())

    mockMvc.perform(
        get("/api/v1/community/posts/{postId}/comments", postId)
            .param("cursor", "cursor-1")
            .param("size", "10")
    )
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.data.nextCursor", equalTo("comment-cursor-1")))
}

@Test
fun `create comment maps optional media id`() {
    `when`(
        communityCommentService.create(
            CommunityCommentCommand.Create(
                memberId = memberId,
                postId = postId,
                parentCommentId = null,
                body = "저도 궁금해요",
                mediaId = mediaId
            )
        )
    ).thenReturn(CommunityCommentResult.CommentId(commentId))

    mockMvc.perform(
        post("/api/v1/community/posts/{postId}/comments", postId)
            .with(authenticatedMember(memberId.toString()))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"body":"저도 궁금해요","mediaId":"$mediaId"}""")
    )
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.data.id", equalTo(commentId.toString())))
}
```

- [ ] **Step 2: Run controller tests and verify they fail**

Run:

```bash
cd backend
./gradlew :api:test --tests "com.chamchamcham.api.community.controller.CommunityControllerTest"
```

Expected: FAIL because DTO/controller still use list response and comment create request lacks `mediaId`. The run may be blocked by existing unrelated dev/RAG test compile errors; if so, continue after confirming `:api:compileKotlin` still passes later.

- [ ] **Step 3: Update request DTO**

Modify `CommunityRequests.CreateCommentRequest`:

```kotlin
data class CreateCommentRequest(
    val parentCommentId: UUID?,
    @field:NotBlank
    val body: String,
    val mediaId: UUID? = null
)
```

- [ ] **Step 4: Update response DTOs**

Add:

```kotlin
data class CommentPageResponse(
    val items: List<CommentResponse>,
    val nextCursor: String?
) {
    companion object {
        fun from(result: CommunityCommentResult.Page): CommentPageResponse =
            CommentPageResponse(
                items = result.items.map(CommentResponse::from),
                nextCursor = result.nextCursor
            )
    }
}
```

Add `imageUrl` to `CommentResponse`:

```kotlin
data class CommentResponse(
    val id: UUID,
    val parentCommentId: UUID?,
    val author: AuthorResponse,
    val body: String,
    val imageUrl: String?,
    val deleted: Boolean,
    val createdAt: LocalDateTime,
    val replies: List<CommentResponse>
)
```

Map it:

```kotlin
imageUrl = result.imageUrl,
```

- [ ] **Step 5: Update controller**

Replace list comments endpoint:

```kotlin
@GetMapping("/posts/{postId}/comments")
fun listComments(
    @PathVariable postId: UUID,
    @RequestParam(required = false) cursor: String?,
    @RequestParam(defaultValue = "20") size: Int
): ResponseEntity<ApiResponse<CommunityResponses.CommentPageResponse>> {
    val comments = communityCommentService.list(postId, cursor, size)
    return ResponseEntity.ok(ApiResponse.ok(CommunityResponses.CommentPageResponse.from(comments)))
}
```

Update create command mapping:

```kotlin
CommunityCommentCommand.Create(
    memberId = parseMemberId(memberId),
    postId = postId,
    parentCommentId = request.parentCommentId,
    body = request.body,
    mediaId = request.mediaId
)
```

- [ ] **Step 6: Run API compile/test**

Run:

```bash
cd backend
./gradlew :api:compileKotlin
./gradlew :api:test --tests "com.chamchamcham.api.community.controller.CommunityControllerTest"
```

Expected: `:api:compileKotlin` PASS. Focused API test may be blocked by unrelated existing dev/RAG test compile failures.

- [ ] **Step 7: Commit**

```bash
git add backend/api/src/main/kotlin/com/chamchamcham/api/community/dto/CommunityRequests.kt \
  backend/api/src/main/kotlin/com/chamchamcham/api/community/dto/CommunityResponses.kt \
  backend/api/src/main/kotlin/com/chamchamcham/api/community/controller/CommunityController.kt \
  backend/api/src/test/kotlin/com/chamchamcham/api/community/controller/CommunityControllerTest.kt
git commit -m "feat(community): 댓글 페이지 응답과 이미지 요청 계약 추가"
```

---

### Task 4: Local iOS Harness Support

**Files:**

- Modify: `test-ios/GodsMoveAuthHarness/GodsMoveAuthHarness/APIModels.swift`
- Modify: `test-ios/GodsMoveAuthHarness/GodsMoveAuthHarness/BackendAPIClient.swift`
- Modify: `test-ios/GodsMoveAuthHarness/GodsMoveAuthHarness/HarnessState.swift`
- Modify: `test-ios/GodsMoveAuthHarness/GodsMoveAuthHarness/CommunityTestView.swift`
- Modify: `test-ios/GodsMoveAuthHarness/GodsMoveAuthHarnessTests/BackendAPIClientTests.swift`
- Modify: `test-ios/GodsMoveAuthHarness/GodsMoveAuthHarnessTests/HarnessStateTests.swift`

- [ ] **Step 1: Update Swift models**

In `APIModels.swift`, add:

```swift
struct CommunityCommentPageDTO: Codable, Equatable {
    let items: [CommunityCommentDTO]
    let nextCursor: String?
}
```

Update:

```swift
struct CommunityCreateCommentRequest: Encodable, Equatable {
    let parentCommentId: UUID?
    let body: String
    let mediaId: UUID?
}

struct CommunityCommentDTO: Codable, Equatable, Identifiable {
    let id: UUID
    let parentCommentId: UUID?
    let author: CommunityAuthorDTO
    let body: String
    let imageUrl: String?
    let deleted: Bool
    let createdAt: String
    let replies: [CommunityCommentDTO]
}
```

- [ ] **Step 2: Update API client**

Change `listCommunityComments`:

```swift
func listCommunityComments(
    postId: UUID,
    cursor: String?,
    size: Int,
    accessToken: String
) async throws -> DebugResponse<CommunityCommentPageDTO> {
    try await send(
        path: BackendAPIPath.communityComments(postId: postId, cursor: cursor, size: size),
        method: "GET",
        accessToken: accessToken
    )
}
```

Change path helper:

```swift
static func communityComments(postId: UUID, cursor: String? = nil, size: Int? = nil) -> String {
    var components = URLComponents()
    components.path = "\(communityPost(postId))/comments"
    var queryItems: [URLQueryItem] = []
    if let cursor = cursor?.trimmingCharacters(in: .whitespacesAndNewlines), !cursor.isEmpty {
        queryItems.append(URLQueryItem(name: "cursor", value: cursor))
    }
    if let size {
        queryItems.append(URLQueryItem(name: "size", value: String(size)))
    }
    components.queryItems = queryItems.isEmpty ? nil : queryItems
    return components.string ?? "\(communityPost(postId))/comments"
}
```

- [ ] **Step 3: Update harness state**

Add:

```swift
@Published private(set) var communityCommentsNextCursor: String?
```

Update comment loading:

```swift
func loadCommunityComments(postId: UUID, cursor: String? = nil, append: Bool = false, size: Int = 20) async
```

Apply response:

```swift
func applyCommunityComments(_ response: DebugResponse<CommunityCommentPageDTO>, append: Bool = false) {
    if append {
        communityComments += response.value.items
    } else {
        communityComments = response.value.items
    }
    communityCommentsNextCursor = response.value.nextCursor
    latestRawResponse = prettyJSON(response.rawBody, redactingSensitiveFields: true)
    latestError = nil
    phase = .success("Community comments loaded")
}
```

Update create comment request call sites to pass `mediaId`.

- [ ] **Step 4: Update SwiftUI controls**

In `CommunityTestView`, add one uploaded comment image:

```swift
@State private var uploadedCommentImage: UploadedImageDTO?
```

Add an `ImageUploadSection` or reuse the existing image upload component with `maxImageCount: 1` near the comment editor. When creating a comment:

```swift
return CommunityCreateCommentRequest(
    parentCommentId: try optionalUUID(parentCommentIDText, error: .invalidParentCommentId),
    body: trimmedBody,
    mediaId: uploadedCommentImage?.mediaId
)
```

Add next-page button:

```swift
Button {
    Task { await loadNextComments() }
} label: {
    Label("Load Next Comments", systemImage: "arrow.down.circle")
}
.disabled(state.isBusy || state.accessToken == nil || selectedPostIDText.trimmed.isEmpty || state.communityCommentsNextCursor == nil)
```

Add:

```swift
private func loadNextComments() async {
    do {
        await state.loadCommunityComments(
            postId: try selectedPostID(),
            cursor: state.communityCommentsNextCursor,
            append: true,
            size: 20
        )
    } catch {
        state.applyFailure(error)
    }
}
```

- [ ] **Step 5: Update Swift tests**

In `BackendAPIClientTests`, change comment list fixture:

```swift
{"success":true,"data":{"items":[\(communityCommentJSON())],"nextCursor":"comment-cursor-1"},"error":null}
```

Assert comment path includes cursor and size:

```swift
let path = BackendAPIPath.communityComments(
    postId: postId,
    cursor: "comment-cursor-1",
    size: 10
)
XCTAssertTrue(path.contains("cursor=comment-cursor-1"))
XCTAssertTrue(path.contains("size=10"))
```

Update `communityCommentJSON()` with `"imageUrl":null`.

- [ ] **Step 6: Run Swift typecheck**

Run:

```bash
swiftc -typecheck test-ios/GodsMoveAuthHarness/GodsMoveAuthHarness/*.swift \
  test-ios/GodsMoveAuthHarness/GodsMoveAuthHarnessTests/*.swift
```

Expected: PASS. If standalone `swiftc` cannot typecheck SwiftUI/XCTest harness wiring, record the compiler output.

- [ ] **Step 7: Commit only if tracked**

Check:

```bash
git status --short --ignored test-ios
```

If `test-ios` is ignored and untracked, do not force-add it. If tracked, commit:

```bash
git add test-ios/GodsMoveAuthHarness/GodsMoveAuthHarness/APIModels.swift \
  test-ios/GodsMoveAuthHarness/GodsMoveAuthHarness/BackendAPIClient.swift \
  test-ios/GodsMoveAuthHarness/GodsMoveAuthHarness/HarnessState.swift \
  test-ios/GodsMoveAuthHarness/GodsMoveAuthHarness/CommunityTestView.swift \
  test-ios/GodsMoveAuthHarness/GodsMoveAuthHarnessTests/BackendAPIClientTests.swift \
  test-ios/GodsMoveAuthHarness/GodsMoveAuthHarnessTests/HarnessStateTests.swift
git commit -m "test(ios): 댓글 커서와 이미지 테스트 지원"
```

---

### Task 5: Integration Verification

**Files:**

- Verify all modified backend and harness files.

- [ ] **Step 1: Run focused domain verification**

```bash
cd backend
./gradlew :domain:test --tests "com.chamchamcham.domain.community.CommunityCommentRepositoryTest"
```

Expected: PASS.

- [ ] **Step 2: Run focused application verification**

```bash
cd backend
./gradlew :application:test --tests "com.chamchamcham.application.community.CommunityCommentServiceTest"
```

Expected: PASS, unless unrelated existing dev/RAG application test compile errors still block the module.

- [ ] **Step 3: Run focused API verification**

```bash
cd backend
./gradlew :api:test --tests "com.chamchamcham.api.community.controller.CommunityControllerTest"
```

Expected: PASS, unless unrelated existing dev/RAG API test compile errors still block `compileTestKotlin`.

- [ ] **Step 4: Run backend compile safety**

```bash
cd backend
./gradlew :domain:compileKotlin :application:compileKotlin :api:compileKotlin
```

Expected: PASS.

- [ ] **Step 5: Run Swift harness typecheck**

```bash
swiftc -typecheck test-ios/GodsMoveAuthHarness/GodsMoveAuthHarness/*.swift \
  test-ios/GodsMoveAuthHarness/GodsMoveAuthHarnessTests/*.swift
```

Expected: PASS or record standalone typecheck limitation.

- [ ] **Step 6: Review final diff**

```bash
git status --short
git diff --check
git diff --stat
```

Expected:

- No whitespace errors.
- Only community comment pagination/image files, schema doc, spec/plan docs, and optional tracked harness files are changed.
- Existing untracked `.claude/` remains untouched.

- [ ] **Step 7: Commit verification-only docs if needed**

If the implementation plan or design spec still needs to be committed:

```bash
git add docs/superpowers/specs/2026-07-06-community-comment-pagination-image-design.md \
  docs/superpowers/plans/2026-07-06-community-comment-pagination-image-plan.md
git commit -m "docs(community): 댓글 커서 이미지 구현 계획 추가"
```
