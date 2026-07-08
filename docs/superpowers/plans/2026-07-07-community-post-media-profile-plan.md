# Community Post Media Edit And Member Profile Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make community post image update synchronize the final ordered image list and add authenticated/public member profile 조회 APIs.

**Architecture:** Keep the existing `api -> application -> domain` boundary. Post image state transitions stay in `CommunityPostService`, while profile read aggregation lives in a new `application.member.MemberProfileService`. Member API DTOs are isolated under `api.member.dto`.

**Tech Stack:** Spring Boot 3.5, Kotlin 1.9, Spring Data JPA, JUnit 5, Mockito, MockMvc.

## Global Constraints

- Do not add QueryDSL or another query abstraction for this slice.
- Do not add new dependencies.
- Do not use `@PreAuthorize`; controllers pass authenticated member ids to application services.
- Keep post images limited to 5.
- `SavePostRequest.mediaIds` is the final ordered image list.
- Same-post existing `ATTACHED` images are valid during update.
- Other-post `ATTACHED` images and `DELETED` images fail with `MEDIA_NOT_ATTACHABLE`.
- Public profile must not expose email, phone, real name, birth date, or full addresses.
- `displayRegion` uses the first two address tokens.
- Preserve project term `member`; do not introduce project-owned `user` naming.

---

## Scope Check

This plan implements:

- `CommunityPostService.update()` image final-list sync.
- `MemberProfileService` for my profile and public profile.
- `GET /api/v1/members/me`.
- `GET /api/v1/members/{memberId}/profile`.
- Unit/controller tests listed in the spec.

This plan does not implement profile update, image cleanup jobs, Cloudinary deletion, global search, or frontend/test-ios changes.

## File Structure

Modify:

- `backend/domain/src/main/kotlin/com/chamchamcham/domain/community/CommunityPostMediaRepository.kt`
  - Add lookup by uploaded media ids so update can reject images attached to another post.
- `backend/application/src/main/kotlin/com/chamchamcham/application/community/CommunityPostService.kt`
  - Split create/update media validation and implement final-list sync.
- `backend/application/src/test/kotlin/com/chamchamcham/application/community/CommunityPostServiceTest.kt`
  - Add focused post-image update tests.

Create:

- `backend/application/src/main/kotlin/com/chamchamcham/application/member/MemberProfileResult.kt`
  - Application result models for profile responses.
- `backend/application/src/main/kotlin/com/chamchamcham/application/member/MemberProfileService.kt`
  - Aggregates `Member`, `Farm`, and `MemberCrop` data.
- `backend/application/src/test/kotlin/com/chamchamcham/application/member/MemberProfileServiceTest.kt`
  - Covers private/public profile data and display region.
- `backend/api/src/main/kotlin/com/chamchamcham/api/member/controller/MemberController.kt`
  - Member profile endpoints.
- `backend/api/src/main/kotlin/com/chamchamcham/api/member/dto/MemberResponses.kt`
  - API DTO mapping from application result models.
- `backend/api/src/test/kotlin/com/chamchamcham/api/member/controller/MemberControllerTest.kt`
  - MockMvc controller contract tests.

---

### Task 1: Community Post Image Final-List Sync

**Files:**

- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/community/CommunityPostMediaRepository.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/community/CommunityPostService.kt`
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/community/CommunityPostServiceTest.kt`

**Interfaces:**

- Consumes: `CommunityPostCommand.Create.mediaIds`, `CommunityPostCommand.Update.mediaIds`
- Produces:
  - `CommunityPostMediaRepository.findByUploadedMedia_IdIn(mediaIds: Collection<UUID>): List<CommunityPostMedia>`
  - `CommunityPostService.update()` accepts same-post existing `ATTACHED` media and rejects other-post attached media.

- [ ] **Step 1: Add failing service tests for keeping and removing existing post images**

Modify `CommunityPostServiceTest` by adding these tests near the existing update tests:

```kotlin
@Test
fun `update keeps existing post image and reorders final media list`() {
    val existingMedia = uploadedMedia(id = mediaId1, owner = member, index = 1, status = UploadedMediaStatus.ATTACHED)
    val newMedia = uploadedMedia(id = mediaId2, owner = member, index = 2, status = UploadedMediaStatus.TEMP)
    val existingMapping = CommunityPostMedia(post = existingPost, uploadedMedia = existingMedia, displayOrder = 0)
    `when`(communityPostRepository.findByIdAndIsDeletedFalse(postId)).thenReturn(existingPost)
    `when`(cropRepository.findById(secondCropId)).thenReturn(Optional.of(secondCrop))
    `when`(farmingRecordRepository.findByIdAndMember_Id(recordId, memberId)).thenReturn(farmingRecord(member, secondCrop))
    `when`(communityPostMediaRepository.findByPost_IdOrderByDisplayOrderAsc(postId)).thenReturn(listOf(existingMapping))
    `when`(uploadedMediaRepository.findAllById(listOf(mediaId2, mediaId1))).thenReturn(listOf(newMedia, existingMedia))
    `when`(communityPostMediaRepository.findByUploadedMedia_IdIn(listOf(mediaId2, mediaId1))).thenReturn(listOf(existingMapping))

    service.update(updateCommand(mediaIds = listOf(mediaId2, mediaId1)))

    assertEquals(UploadedMediaStatus.ATTACHED, existingMedia.status)
    assertEquals(UploadedMediaStatus.ATTACHED, newMedia.status)
    verify(communityPostMediaRepository).deleteByPost(existingPost)
    assertThat(capturedPostMedia().map { it.uploadedMedia.id }).containsExactly(mediaId2, mediaId1)
    assertThat(capturedPostMedia().map { it.displayOrder }).containsExactly(0, 1)
}

@Test
fun `update marks removed existing post image as deleted`() {
    val removedMedia = uploadedMedia(id = mediaId1, owner = member, index = 1, status = UploadedMediaStatus.ATTACHED)
    val keptMedia = uploadedMedia(id = mediaId2, owner = member, index = 2, status = UploadedMediaStatus.ATTACHED)
    val removedMapping = CommunityPostMedia(post = existingPost, uploadedMedia = removedMedia, displayOrder = 0)
    val keptMapping = CommunityPostMedia(post = existingPost, uploadedMedia = keptMedia, displayOrder = 1)
    `when`(communityPostRepository.findByIdAndIsDeletedFalse(postId)).thenReturn(existingPost)
    `when`(cropRepository.findById(secondCropId)).thenReturn(Optional.of(secondCrop))
    `when`(farmingRecordRepository.findByIdAndMember_Id(recordId, memberId)).thenReturn(farmingRecord(member, secondCrop))
    `when`(communityPostMediaRepository.findByPost_IdOrderByDisplayOrderAsc(postId)).thenReturn(listOf(removedMapping, keptMapping))
    `when`(uploadedMediaRepository.findAllById(listOf(mediaId2))).thenReturn(listOf(keptMedia))
    `when`(communityPostMediaRepository.findByUploadedMedia_IdIn(listOf(mediaId2))).thenReturn(listOf(keptMapping))

    service.update(updateCommand(mediaIds = listOf(mediaId2)))

    assertEquals(UploadedMediaStatus.DELETED, removedMedia.status)
    assertEquals(UploadedMediaStatus.ATTACHED, keptMedia.status)
    assertThat(capturedPostMedia().map { it.uploadedMedia.id }).containsExactly(mediaId2)
}
```

- [ ] **Step 2: Add failing tests for new image attachment and invalid images**

Add these tests to `CommunityPostServiceTest`:

```kotlin
@Test
fun `update marks newly added temp image as attached`() {
    val newMedia = uploadedMedia(id = mediaId1, owner = member, index = 1, status = UploadedMediaStatus.TEMP)
    `when`(communityPostRepository.findByIdAndIsDeletedFalse(postId)).thenReturn(existingPost)
    `when`(cropRepository.findById(secondCropId)).thenReturn(Optional.of(secondCrop))
    `when`(farmingRecordRepository.findByIdAndMember_Id(recordId, memberId)).thenReturn(farmingRecord(member, secondCrop))
    `when`(communityPostMediaRepository.findByPost_IdOrderByDisplayOrderAsc(postId)).thenReturn(emptyList())
    `when`(uploadedMediaRepository.findAllById(listOf(mediaId1))).thenReturn(listOf(newMedia))
    `when`(communityPostMediaRepository.findByUploadedMedia_IdIn(listOf(mediaId1))).thenReturn(emptyList())

    service.update(updateCommand(mediaIds = listOf(mediaId1)))

    assertEquals(UploadedMediaStatus.ATTACHED, newMedia.status)
    assertThat(capturedPostMedia().map { it.uploadedMedia.id }).containsExactly(mediaId1)
}

@Test
fun `update rejects media owned by another member`() {
    val otherMedia = uploadedMedia(id = mediaId1, owner = otherMember, index = 1, status = UploadedMediaStatus.TEMP)
    `when`(communityPostRepository.findByIdAndIsDeletedFalse(postId)).thenReturn(existingPost)
    `when`(cropRepository.findById(secondCropId)).thenReturn(Optional.of(secondCrop))
    `when`(farmingRecordRepository.findByIdAndMember_Id(recordId, memberId)).thenReturn(farmingRecord(member, secondCrop))
    `when`(communityPostMediaRepository.findByPost_IdOrderByDisplayOrderAsc(postId)).thenReturn(emptyList())
    `when`(uploadedMediaRepository.findAllById(listOf(mediaId1))).thenReturn(listOf(otherMedia))
    `when`(communityPostMediaRepository.findByUploadedMedia_IdIn(listOf(mediaId1))).thenReturn(emptyList())

    val exception = assertThrows(BusinessException::class.java) {
        service.update(updateCommand(mediaIds = listOf(mediaId1)))
    }

    assertEquals(ErrorCode.MEDIA_NOT_OWNED, exception.errorCode)
    verify(communityPostMediaRepository, never()).deleteByPost(any(CommunityPost::class.java))
}

@Test
fun `update rejects media attached to another post`() {
    val otherPost = existingPost(member, crop, secondPostId)
    val attachedElsewhere = uploadedMedia(id = mediaId1, owner = member, index = 1, status = UploadedMediaStatus.ATTACHED)
    val otherMapping = CommunityPostMedia(post = otherPost, uploadedMedia = attachedElsewhere, displayOrder = 0)
    `when`(communityPostRepository.findByIdAndIsDeletedFalse(postId)).thenReturn(existingPost)
    `when`(cropRepository.findById(secondCropId)).thenReturn(Optional.of(secondCrop))
    `when`(farmingRecordRepository.findByIdAndMember_Id(recordId, memberId)).thenReturn(farmingRecord(member, secondCrop))
    `when`(communityPostMediaRepository.findByPost_IdOrderByDisplayOrderAsc(postId)).thenReturn(emptyList())
    `when`(uploadedMediaRepository.findAllById(listOf(mediaId1))).thenReturn(listOf(attachedElsewhere))
    `when`(communityPostMediaRepository.findByUploadedMedia_IdIn(listOf(mediaId1))).thenReturn(listOf(otherMapping))

    val exception = assertThrows(BusinessException::class.java) {
        service.update(updateCommand(mediaIds = listOf(mediaId1)))
    }

    assertEquals(ErrorCode.MEDIA_NOT_ATTACHABLE, exception.errorCode)
    verify(communityPostMediaRepository, never()).deleteByPost(any(CommunityPost::class.java))
}
```

- [ ] **Step 3: Run the focused tests and verify failure**

Run:

```bash
cd backend
./gradlew :application:test --tests "com.chamchamcham.application.community.CommunityPostServiceTest"
```

Expected: FAIL because `findByUploadedMedia_IdIn` does not exist and update media validation rejects same-post `ATTACHED` media.

- [ ] **Step 4: Add repository lookup by uploaded media ids**

Modify `CommunityPostMediaRepository.kt`:

```kotlin
package com.chamchamcham.domain.community

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CommunityPostMediaRepository : JpaRepository<CommunityPostMedia, UUID> {
    fun findByPost_IdOrderByDisplayOrderAsc(postId: UUID): List<CommunityPostMedia>
    fun findByUploadedMedia_IdIn(mediaIds: Collection<UUID>): List<CommunityPostMedia>
    fun deleteByPost(post: CommunityPost)
}
```

- [ ] **Step 5: Split create/update media validation**

Modify `CommunityPostService.kt`.

Keep `create()` using strict `TEMP` attach validation:

```kotlin
val media = validateNewMedia(command.memberId, command.mediaIds)
```

Change `update()` to load existing mappings before validating final media:

```kotlin
val existingMedia = communityPostMediaRepository.findByPost_IdOrderByDisplayOrderAsc(requireNotNull(post.id))
val media = validateUpdatedMedia(
    memberId = command.memberId,
    post = post,
    mediaIds = command.mediaIds,
    existingMedia = existingMedia
)
```

Replace the existing `validateMedia()` helper with:

```kotlin
private fun validateNewMedia(memberId: UUID, mediaIds: List<UUID>): List<UploadedMedia> {
    validateDistinctMediaIds(mediaIds)
    if (mediaIds.isEmpty()) {
        return emptyList()
    }
    val mediaById = uploadedMediaRepository.findAllById(mediaIds)
        .associateBy { requireNotNull(it.id) { "Persisted media id is required" } }

    return mediaIds.map { mediaId ->
        val media = mediaById[mediaId] ?: throw BusinessException(ErrorCode.MEDIA_NOT_FOUND)
        validateMediaOwnerAndUsage(memberId, media)
        if (!media.isAttachable()) {
            throw BusinessException(ErrorCode.MEDIA_NOT_ATTACHABLE)
        }
        media
    }
}

private fun validateUpdatedMedia(
    memberId: UUID,
    post: CommunityPost,
    mediaIds: List<UUID>,
    existingMedia: List<CommunityPostMedia>
): List<UploadedMedia> {
    validateDistinctMediaIds(mediaIds)
    if (mediaIds.isEmpty()) {
        return emptyList()
    }

    val existingPostId = requireNotNull(post.id) { "Persisted post id is required" }
    val existingByMediaId = existingMedia.associateBy {
        requireNotNull(it.uploadedMedia.id) { "Persisted media id is required" }
    }
    val mappingsByMediaId = communityPostMediaRepository.findByUploadedMedia_IdIn(mediaIds)
        .associateBy { requireNotNull(it.uploadedMedia.id) { "Persisted media id is required" } }
    val mediaById = uploadedMediaRepository.findAllById(mediaIds)
        .associateBy { requireNotNull(it.id) { "Persisted media id is required" } }

    return mediaIds.map { mediaId ->
        val media = mediaById[mediaId] ?: throw BusinessException(ErrorCode.MEDIA_NOT_FOUND)
        validateMediaOwnerAndUsage(memberId, media)

        val existingForThisPost = existingByMediaId[mediaId] != null
        val mapping = mappingsByMediaId[mediaId]
        val mappedPostId = mapping?.post?.id
        if (mapping != null && mappedPostId != existingPostId) {
            throw BusinessException(ErrorCode.MEDIA_NOT_ATTACHABLE)
        }
        if (!existingForThisPost && !media.isAttachable()) {
            throw BusinessException(ErrorCode.MEDIA_NOT_ATTACHABLE)
        }
        media
    }
}

private fun validateDistinctMediaIds(mediaIds: List<UUID>) {
    if (mediaIds.distinct().size != mediaIds.size) {
        throw BusinessException(ErrorCode.INVALID_INPUT)
    }
}

private fun validateMediaOwnerAndUsage(memberId: UUID, media: UploadedMedia) {
    if (media.owner.id != memberId) {
        throw BusinessException(ErrorCode.MEDIA_NOT_OWNED)
    }
    if (media.usageType != UploadedMediaUsageType.COMMUNITY_POST) {
        throw BusinessException(ErrorCode.MEDIA_USAGE_MISMATCH)
    }
}
```

- [ ] **Step 6: Implement removed/new media state transitions**

In `update()`, replace:

```kotlin
communityPostMediaRepository.deleteByPost(post)
attachMedia(post, media)
```

with:

```kotlin
syncMedia(post, existingMedia, media)
```

Add:

```kotlin
private fun syncMedia(
    post: CommunityPost,
    existingMedia: List<CommunityPostMedia>,
    finalMedia: List<UploadedMedia>
) {
    val finalIds = finalMedia.map { requireNotNull(it.id) { "Persisted media id is required" } }.toSet()
    existingMedia
        .map { it.uploadedMedia }
        .filter { requireNotNull(it.id) { "Persisted media id is required" } !in finalIds }
        .forEach(UploadedMedia::markDeleted)

    finalMedia
        .filter(UploadedMedia::isAttachable)
        .forEach(UploadedMedia::markAttached)

    communityPostMediaRepository.deleteByPost(post)
    attachMedia(post, finalMedia)
}
```

Keep `attachMedia()` as the single method that saves rows in request order.

- [ ] **Step 7: Run focused application tests**

Run:

```bash
cd backend
./gradlew :application:test --tests "com.chamchamcham.application.community.CommunityPostServiceTest"
```

Expected: PASS.

- [ ] **Step 8: Commit Task 1**

```bash
git add backend/domain/src/main/kotlin/com/chamchamcham/domain/community/CommunityPostMediaRepository.kt \
  backend/application/src/main/kotlin/com/chamchamcham/application/community/CommunityPostService.kt \
  backend/application/src/test/kotlin/com/chamchamcham/application/community/CommunityPostServiceTest.kt
git commit -m "fix(community): 게시글 이미지 수정 동기화 보강"
```

---

### Task 2: Member Profile Application Service

**Files:**

- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/member/MemberProfileResult.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/member/MemberProfileService.kt`
- Create: `backend/application/src/test/kotlin/com/chamchamcham/application/member/MemberProfileServiceTest.kt`

**Interfaces:**

- Produces:
  - `MemberProfileService.getMyProfile(memberId: UUID): MemberProfileResult.MyProfile`
  - `MemberProfileService.getPublicProfile(memberId: UUID): MemberProfileResult.PublicProfile`
  - `MemberProfileResult.FarmSummary.displayRegion: String?`
  - `MemberProfileResult.CropSummary`
- Consumes:
  - `MemberRepository.findById(memberId)`
  - `FarmRepository.findByOwner_Id(memberId)`
  - `MemberCropRepository.findByMember_Id(memberId)`

- [ ] **Step 1: Add failing service tests**

Create `MemberProfileServiceTest.kt`:

```kotlin
package com.chamchamcham.application.member

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.crop.Crop
import com.chamchamcham.domain.crop.CropUsePartCategory
import com.chamchamcham.domain.crop.MemberCrop
import com.chamchamcham.domain.crop.MemberCropRepository
import com.chamchamcham.domain.farm.Farm
import com.chamchamcham.domain.farm.FarmRepository
import com.chamchamcham.domain.media.UploadedMedia
import com.chamchamcham.domain.media.UploadedMediaStatus
import com.chamchamcham.domain.media.UploadedMediaType
import com.chamchamcham.domain.media.UploadedMediaUsageType
import com.chamchamcham.domain.member.ManagementType
import com.chamchamcham.domain.member.Member
import com.chamchamcham.domain.member.MemberRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class MemberProfileServiceTest {
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val farmId = UUID.fromString("00000000-0000-0000-0000-000000000201")
    private val cropId = UUID.fromString("00000000-0000-0000-0000-000000000301")

    @Mock private lateinit var memberRepository: MemberRepository
    @Mock private lateinit var farmRepository: FarmRepository
    @Mock private lateinit var memberCropRepository: MemberCropRepository

    private lateinit var service: MemberProfileService
    private lateinit var member: Member
    private lateinit var farm: Farm
    private lateinit var crop: Crop

    @BeforeEach
    fun setUp() {
        service = MemberProfileService(memberRepository, farmRepository, memberCropRepository)
        member = Member(
            id = memberId,
            email = "farmer@example.com",
            passwordHash = "hash",
            phone = "010-1234-5678",
            name = "이황기",
            birthDate = LocalDate.of(1986, 3, 12),
            nickname = "황기농부",
            experienceLevel = 2,
            managementType = ManagementType.AGRICULTURAL_INDIVIDUAL
        )
        member.updateProfileMedia(profileMedia(member))
        farm = Farm(
            id = farmId,
            owner = member,
            name = "횡성 황기밭",
            roadAddress = "강원특별자치도 횡성군 둔내면 샘물로 12",
            jibunAddress = "강원특별자치도 횡성군 둔내면 현천리 101"
        )
        crop = Crop(id = cropId, externalNo = 191, name = "황기", usePartCategory = CropUsePartCategory.ROOT_BARK)
    }

    @Test
    fun `my profile returns private fields farms and crops`() {
        `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))
        `when`(farmRepository.findByOwner_Id(memberId)).thenReturn(listOf(farm))
        `when`(memberCropRepository.findByMember_Id(memberId)).thenReturn(listOf(MemberCrop(member = member, farm = farm, crop = crop)))

        val result = service.getMyProfile(memberId)

        assertEquals(memberId, result.memberId)
        assertEquals("farmer@example.com", result.email)
        assertEquals("이황기", result.name)
        assertEquals("010-1234-5678", result.phone)
        assertEquals(LocalDate.of(1986, 3, 12), result.birthDate)
        assertEquals("황기농부", result.nickname)
        assertEquals(2, result.experienceLevel)
        assertEquals("AGRICULTURAL_INDIVIDUAL", result.managementType)
        assertEquals("https://example.test/profile.jpg", result.profileImageUrl)
        assertEquals("횡성 황기밭", result.farms.single().name)
        assertEquals("강원특별자치도 횡성군", result.farms.single().displayRegion)
        assertEquals("황기", result.crops.single().cropName)
    }

    @Test
    fun `public profile excludes private fields and returns display region and crops`() {
        `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))
        `when`(farmRepository.findByOwner_Id(memberId)).thenReturn(listOf(farm))
        `when`(memberCropRepository.findByMember_Id(memberId)).thenReturn(listOf(MemberCrop(member = member, farm = farm, crop = crop)))

        val result = service.getPublicProfile(memberId)

        assertEquals(memberId, result.memberId)
        assertEquals("황기농부", result.nickname)
        assertEquals(2, result.experienceLevel)
        assertEquals("AGRICULTURAL_INDIVIDUAL", result.managementType)
        assertEquals("https://example.test/profile.jpg", result.profileImageUrl)
        assertEquals("강원특별자치도 횡성군", result.farms.single().displayRegion)
        assertEquals("황기", result.crops.single().cropName)
    }

    @Test
    fun `display region uses first two address tokens and falls back to jibun address`() {
        val oneTokenFarm = Farm(id = farmId, owner = member, name = "제주밭", roadAddress = "   ", jibunAddress = "제주특별자치도")
        `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))
        `when`(farmRepository.findByOwner_Id(memberId)).thenReturn(listOf(oneTokenFarm))
        `when`(memberCropRepository.findByMember_Id(memberId)).thenReturn(emptyList())

        val result = service.getPublicProfile(memberId)

        assertEquals("제주특별자치도", result.farms.single().displayRegion)
    }

    @Test
    fun `missing member fails with member not found`() {
        `when`(memberRepository.findById(memberId)).thenReturn(Optional.empty())

        val exception = assertThrows(BusinessException::class.java) {
            service.getMyProfile(memberId)
        }

        assertEquals(ErrorCode.MEMBER_NOT_FOUND, exception.errorCode)
    }

    private fun profileMedia(owner: Member): UploadedMedia =
        UploadedMedia(
            id = UUID.fromString("00000000-0000-0000-0000-000000000601"),
            owner = owner,
            mediaType = UploadedMediaType.IMAGE,
            usageType = UploadedMediaUsageType.PROFILE,
            fileUrl = "https://example.test/profile.jpg",
            cloudinaryPublicId = "profiles/profile",
            status = UploadedMediaStatus.ATTACHED
        )
}
```

- [ ] **Step 2: Run service tests and verify failure**

Run:

```bash
cd backend
./gradlew :application:test --tests "com.chamchamcham.application.member.MemberProfileServiceTest"
```

Expected: FAIL because `MemberProfileService` and `MemberProfileResult` do not exist.

- [ ] **Step 3: Add application result models**

Create `MemberProfileResult.kt`:

```kotlin
package com.chamchamcham.application.member

import java.time.LocalDate
import java.util.UUID

object MemberProfileResult {
    data class MyProfile(
        val memberId: UUID,
        val email: String?,
        val name: String?,
        val phone: String?,
        val birthDate: LocalDate?,
        val nickname: String?,
        val experienceLevel: Int?,
        val managementType: String?,
        val profileImageUrl: String?,
        val farms: List<FarmSummary>,
        val crops: List<CropSummary>
    )

    data class PublicProfile(
        val memberId: UUID,
        val nickname: String?,
        val experienceLevel: Int?,
        val managementType: String?,
        val profileImageUrl: String?,
        val farms: List<PublicFarmSummary>,
        val crops: List<CropSummary>
    )

    data class FarmSummary(
        val farmId: UUID,
        val name: String,
        val roadAddress: String,
        val jibunAddress: String?,
        val displayRegion: String?
    )

    data class PublicFarmSummary(
        val farmId: UUID,
        val displayRegion: String?
    )

    data class CropSummary(
        val cropId: UUID,
        val cropName: String
    )
}
```

- [ ] **Step 4: Add profile service**

Create `MemberProfileService.kt`:

```kotlin
package com.chamchamcham.application.member

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.crop.MemberCrop
import com.chamchamcham.domain.crop.MemberCropRepository
import com.chamchamcham.domain.farm.Farm
import com.chamchamcham.domain.farm.FarmRepository
import com.chamchamcham.domain.member.Member
import com.chamchamcham.domain.member.MemberRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class MemberProfileService(
    private val memberRepository: MemberRepository,
    private val farmRepository: FarmRepository,
    private val memberCropRepository: MemberCropRepository
) {
    fun getMyProfile(memberId: UUID): MemberProfileResult.MyProfile {
        val member = findMember(memberId)
        val farms = farmRepository.findByOwner_Id(memberId)
        val memberCrops = memberCropRepository.findByMember_Id(memberId)
        return MemberProfileResult.MyProfile(
            memberId = requireNotNull(member.id) { "Persisted member id is required" },
            email = member.email,
            name = member.name,
            phone = member.phone,
            birthDate = member.birthDate,
            nickname = member.nickname,
            experienceLevel = member.experienceLevel,
            managementType = member.managementType?.name,
            profileImageUrl = member.profileMedia?.fileUrl,
            farms = farms.map(::farmSummary),
            crops = cropSummaries(memberCrops)
        )
    }

    fun getPublicProfile(memberId: UUID): MemberProfileResult.PublicProfile {
        val member = findMember(memberId)
        val farms = farmRepository.findByOwner_Id(memberId)
        val memberCrops = memberCropRepository.findByMember_Id(memberId)
        return MemberProfileResult.PublicProfile(
            memberId = requireNotNull(member.id) { "Persisted member id is required" },
            nickname = member.nickname,
            experienceLevel = member.experienceLevel,
            managementType = member.managementType?.name,
            profileImageUrl = member.profileMedia?.fileUrl,
            farms = farms.map(::publicFarmSummary),
            crops = cropSummaries(memberCrops)
        )
    }

    private fun findMember(memberId: UUID): Member =
        memberRepository.findById(memberId).orElseThrow {
            BusinessException(ErrorCode.MEMBER_NOT_FOUND)
        }

    private fun farmSummary(farm: Farm): MemberProfileResult.FarmSummary =
        MemberProfileResult.FarmSummary(
            farmId = requireNotNull(farm.id) { "Persisted farm id is required" },
            name = farm.name,
            roadAddress = farm.roadAddress,
            jibunAddress = farm.jibunAddress,
            displayRegion = displayRegion(farm)
        )

    private fun publicFarmSummary(farm: Farm): MemberProfileResult.PublicFarmSummary =
        MemberProfileResult.PublicFarmSummary(
            farmId = requireNotNull(farm.id) { "Persisted farm id is required" },
            displayRegion = displayRegion(farm)
        )

    private fun cropSummaries(memberCrops: List<MemberCrop>): List<MemberProfileResult.CropSummary> {
        val seen = linkedSetOf<UUID>()
        return memberCrops.map { it.crop }
            .filter { crop -> seen.add(requireNotNull(crop.id) { "Persisted crop id is required" }) }
            .map { crop ->
                MemberProfileResult.CropSummary(
                    cropId = requireNotNull(crop.id) { "Persisted crop id is required" },
                    cropName = crop.name
                )
            }
    }

    private fun displayRegion(farm: Farm): String? {
        val source = farm.roadAddress.takeIf { it.isNotBlank() } ?: farm.jibunAddress
        val tokens = source
            ?.trim()
            ?.split(Regex("\\s+"))
            ?.filter { it.isNotBlank() }
            ?: return null
        return tokens.take(2).joinToString(" ").ifBlank { null }
    }
}
```

- [ ] **Step 5: Run focused profile service tests**

Run:

```bash
cd backend
./gradlew :application:test --tests "com.chamchamcham.application.member.MemberProfileServiceTest"
```

Expected: PASS.

- [ ] **Step 6: Commit Task 2**

```bash
git add backend/application/src/main/kotlin/com/chamchamcham/application/member \
  backend/application/src/test/kotlin/com/chamchamcham/application/member/MemberProfileServiceTest.kt
git commit -m "feat(member): 프로필 조회 서비스 추가"
```

---

### Task 3: Member Profile API

**Files:**

- Create: `backend/api/src/main/kotlin/com/chamchamcham/api/member/controller/MemberController.kt`
- Create: `backend/api/src/main/kotlin/com/chamchamcham/api/member/dto/MemberResponses.kt`
- Create: `backend/api/src/test/kotlin/com/chamchamcham/api/member/controller/MemberControllerTest.kt`

**Interfaces:**

- Consumes:
  - `MemberProfileService.getMyProfile(memberId: UUID)`
  - `MemberProfileService.getPublicProfile(memberId: UUID)`
- Produces:
  - `GET /api/v1/members/me`
  - `GET /api/v1/members/{memberId}/profile`

- [ ] **Step 1: Add failing controller tests**

Create `MemberControllerTest.kt`:

```kotlin
package com.chamchamcham.api.member.controller

import com.chamchamcham.application.member.MemberProfileResult
import com.chamchamcham.application.member.MemberProfileService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.util.UUID

@WebMvcTest(MemberController::class)
class MemberControllerTest(
    @Autowired private val mockMvc: MockMvc
) {
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val farmId = UUID.fromString("00000000-0000-0000-0000-000000000201")
    private val cropId = UUID.fromString("00000000-0000-0000-0000-000000000301")

    @MockBean private lateinit var memberProfileService: MemberProfileService

    @Test
    fun `me returns private profile`() {
        `when`(memberProfileService.getMyProfile(memberId)).thenReturn(myProfile())

        mockMvc.perform(get("/api/v1/members/me").with(jwt().jwt { it.subject(memberId.toString()) }))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.memberId").value(memberId.toString()))
            .andExpect(jsonPath("$.data.email").value("farmer@example.com"))
            .andExpect(jsonPath("$.data.name").value("이황기"))
            .andExpect(jsonPath("$.data.phone").value("010-1234-5678"))
            .andExpect(jsonPath("$.data.birthDate").value("1986-03-12"))
            .andExpect(jsonPath("$.data.farms[0].roadAddress").value("강원특별자치도 횡성군 둔내면 샘물로 12"))
            .andExpect(jsonPath("$.data.farms[0].displayRegion").value("강원특별자치도 횡성군"))
            .andExpect(jsonPath("$.data.crops[0].cropName").value("황기"))
    }

    @Test
    fun `public profile excludes private fields`() {
        `when`(memberProfileService.getPublicProfile(memberId)).thenReturn(publicProfile())

        mockMvc.perform(get("/api/v1/members/{memberId}/profile", memberId).with(jwt().jwt { it.subject(memberId.toString()) }))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.memberId").value(memberId.toString()))
            .andExpect(jsonPath("$.data.nickname").value("황기농부"))
            .andExpect(jsonPath("$.data.email").doesNotExist())
            .andExpect(jsonPath("$.data.name").doesNotExist())
            .andExpect(jsonPath("$.data.phone").doesNotExist())
            .andExpect(jsonPath("$.data.birthDate").doesNotExist())
            .andExpect(jsonPath("$.data.farms[0].roadAddress").doesNotExist())
            .andExpect(jsonPath("$.data.farms[0].displayRegion").value("강원특별자치도 횡성군"))
    }

    private fun myProfile(): MemberProfileResult.MyProfile =
        MemberProfileResult.MyProfile(
            memberId = memberId,
            email = "farmer@example.com",
            name = "이황기",
            phone = "010-1234-5678",
            birthDate = LocalDate.of(1986, 3, 12),
            nickname = "황기농부",
            experienceLevel = 2,
            managementType = "AGRICULTURAL_INDIVIDUAL",
            profileImageUrl = "https://example.test/profile.jpg",
            farms = listOf(
                MemberProfileResult.FarmSummary(
                    farmId = farmId,
                    name = "횡성 황기밭",
                    roadAddress = "강원특별자치도 횡성군 둔내면 샘물로 12",
                    jibunAddress = "강원특별자치도 횡성군 둔내면 현천리 101",
                    displayRegion = "강원특별자치도 횡성군"
                )
            ),
            crops = listOf(MemberProfileResult.CropSummary(cropId = cropId, cropName = "황기"))
        )

    private fun publicProfile(): MemberProfileResult.PublicProfile =
        MemberProfileResult.PublicProfile(
            memberId = memberId,
            nickname = "황기농부",
            experienceLevel = 2,
            managementType = "AGRICULTURAL_INDIVIDUAL",
            profileImageUrl = "https://example.test/profile.jpg",
            farms = listOf(MemberProfileResult.PublicFarmSummary(farmId = farmId, displayRegion = "강원특별자치도 횡성군")),
            crops = listOf(MemberProfileResult.CropSummary(cropId = cropId, cropName = "황기"))
        )
}
```

If existing controller tests use a different authentication helper than
`jwt().jwt { it.subject(memberId.toString()) }`, use that same helper and keep
the assertions unchanged.

- [ ] **Step 2: Run controller tests and verify failure**

Run:

```bash
cd backend
./gradlew :api:test --tests "com.chamchamcham.api.member.controller.MemberControllerTest"
```

Expected: FAIL because member API controller and DTOs do not exist.

- [ ] **Step 3: Add member API DTOs**

Create `MemberResponses.kt`:

```kotlin
package com.chamchamcham.api.member.dto

import com.chamchamcham.application.member.MemberProfileResult
import java.time.LocalDate
import java.util.UUID

object MemberResponses {
    data class MyProfileResponse(
        val memberId: UUID,
        val email: String?,
        val name: String?,
        val phone: String?,
        val birthDate: LocalDate?,
        val nickname: String?,
        val experienceLevel: Int?,
        val managementType: String?,
        val profileImageUrl: String?,
        val farms: List<FarmResponse>,
        val crops: List<CropResponse>
    ) {
        companion object {
            fun from(result: MemberProfileResult.MyProfile): MyProfileResponse =
                MyProfileResponse(
                    memberId = result.memberId,
                    email = result.email,
                    name = result.name,
                    phone = result.phone,
                    birthDate = result.birthDate,
                    nickname = result.nickname,
                    experienceLevel = result.experienceLevel,
                    managementType = result.managementType,
                    profileImageUrl = result.profileImageUrl,
                    farms = result.farms.map(FarmResponse::from),
                    crops = result.crops.map(CropResponse::from)
                )
        }
    }

    data class PublicProfileResponse(
        val memberId: UUID,
        val nickname: String?,
        val experienceLevel: Int?,
        val managementType: String?,
        val profileImageUrl: String?,
        val farms: List<PublicFarmResponse>,
        val crops: List<CropResponse>
    ) {
        companion object {
            fun from(result: MemberProfileResult.PublicProfile): PublicProfileResponse =
                PublicProfileResponse(
                    memberId = result.memberId,
                    nickname = result.nickname,
                    experienceLevel = result.experienceLevel,
                    managementType = result.managementType,
                    profileImageUrl = result.profileImageUrl,
                    farms = result.farms.map(PublicFarmResponse::from),
                    crops = result.crops.map(CropResponse::from)
                )
        }
    }

    data class FarmResponse(
        val farmId: UUID,
        val name: String,
        val roadAddress: String,
        val jibunAddress: String?,
        val displayRegion: String?
    ) {
        companion object {
            fun from(result: MemberProfileResult.FarmSummary): FarmResponse =
                FarmResponse(
                    farmId = result.farmId,
                    name = result.name,
                    roadAddress = result.roadAddress,
                    jibunAddress = result.jibunAddress,
                    displayRegion = result.displayRegion
                )
        }
    }

    data class PublicFarmResponse(
        val farmId: UUID,
        val displayRegion: String?
    ) {
        companion object {
            fun from(result: MemberProfileResult.PublicFarmSummary): PublicFarmResponse =
                PublicFarmResponse(
                    farmId = result.farmId,
                    displayRegion = result.displayRegion
                )
        }
    }

    data class CropResponse(
        val cropId: UUID,
        val cropName: String
    ) {
        companion object {
            fun from(result: MemberProfileResult.CropSummary): CropResponse =
                CropResponse(cropId = result.cropId, cropName = result.cropName)
        }
    }
}
```

- [ ] **Step 4: Add member controller**

Create `MemberController.kt`:

```kotlin
package com.chamchamcham.api.member.controller

import com.chamchamcham.api.common.ApiResponse
import com.chamchamcham.api.member.dto.MemberResponses
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.application.member.MemberProfileService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/members")
class MemberController(
    private val memberProfileService: MemberProfileService
) {
    @GetMapping("/me")
    fun me(
        @AuthenticationPrincipal memberId: String?
    ): ResponseEntity<ApiResponse<MemberResponses.MyProfileResponse>> {
        val profile = memberProfileService.getMyProfile(parseMemberId(memberId))
        return ResponseEntity.ok(ApiResponse.ok(MemberResponses.MyProfileResponse.from(profile)))
    }

    @GetMapping("/{memberId}/profile")
    fun publicProfile(
        @PathVariable memberId: UUID
    ): ResponseEntity<ApiResponse<MemberResponses.PublicProfileResponse>> {
        val profile = memberProfileService.getPublicProfile(memberId)
        return ResponseEntity.ok(ApiResponse.ok(MemberResponses.PublicProfileResponse.from(profile)))
    }

    private fun parseMemberId(memberId: String?): UUID =
        memberId?.let(UUID::fromString)
            ?: throw BusinessException(ErrorCode.UNAUTHORIZED)
}
```

- [ ] **Step 5: Run focused API tests**

Run:

```bash
cd backend
./gradlew :api:test --tests "com.chamchamcham.api.member.controller.MemberControllerTest"
```

Expected: PASS.

- [ ] **Step 6: Commit Task 3**

```bash
git add backend/api/src/main/kotlin/com/chamchamcham/api/member \
  backend/api/src/test/kotlin/com/chamchamcham/api/member/controller/MemberControllerTest.kt
git commit -m "feat(member): 프로필 조회 API 추가"
```

---

### Task 4: Cross-Module Verification

**Files:**

- Verify only; no expected source edits.

**Interfaces:**

- Consumes all changes from Tasks 1-3.
- Produces verified backend behavior for image update and member profiles.

- [ ] **Step 1: Run application tests for changed services**

Run:

```bash
cd backend
./gradlew :application:test --tests "com.chamchamcham.application.community.CommunityPostServiceTest" \
  --tests "com.chamchamcham.application.member.MemberProfileServiceTest"
```

Expected: PASS.

- [ ] **Step 2: Run API tests for changed controllers**

Run:

```bash
cd backend
./gradlew :api:test --tests "com.chamchamcham.api.community.controller.CommunityControllerTest" \
  --tests "com.chamchamcham.api.member.controller.MemberControllerTest"
```

Expected: PASS.

- [ ] **Step 3: Run broader compile check**

Run:

```bash
cd backend
./gradlew :application:compileKotlin :api:compileKotlin
```

Expected: PASS.

- [ ] **Step 4: Commit verification notes if any test-only fixes were needed**

If Tasks 1-3 already committed all source changes and verification required no edits, do not create a commit. If verification revealed a small test-only fix, commit it with:

```bash
git add backend/application/src/test backend/api/src/test
git commit -m "test(member): 프로필 조회 검증 보강"
```

---

## Self-Review Checklist

- Spec coverage:
  - Post update keeps same-post existing images.
  - Removed images become `DELETED`.
  - New `TEMP` images become `ATTACHED`.
  - Other-owner and other-post images are rejected.
  - My profile includes private fields.
  - Public profile excludes private fields.
  - `displayRegion` uses the first two tokens.
- Placeholder scan:
  - No unresolved placeholders are intentionally left in this plan.
- Type consistency:
  - `MemberProfileResult` names match `MemberResponses`.
  - `MemberProfileService` methods match controller calls.
  - Repository method `findByUploadedMedia_IdIn` matches service usage.

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-07-07-community-post-media-profile-plan.md`. Two execution options:

1. Subagent-Driven (recommended) - Dispatch a fresh subagent per task, review between tasks, fast iteration.
2. Inline Execution - Execute tasks in this session using `superpowers:executing-plans`, batch execution with checkpoints.
