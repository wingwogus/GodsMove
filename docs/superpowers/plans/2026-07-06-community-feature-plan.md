# Community Feature Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the approved crop community MVP: common Cloudinary image upload, optional onboarding profile image, crop-scoped posts, post images, comments, cursor search, and post like toggle.

**Architecture:** Keep the existing `api -> application -> domain` module direction. Put JPA entities and repositories in `domain`, use application services for ownership and business rules, and keep controllers as DTO mapping surfaces. Use a custom `CommunityPostQueryRepository` boundary now so a future QueryDSL implementation can replace only the query implementation.

**Tech Stack:** Spring Boot 3.5, Kotlin, Spring Data JPA, Spring MVC, Bean Validation, Spring Security authentication principal, Cloudinary Java SDK `com.cloudinary:cloudinary-http5:2.3.1`, JUnit 5, Mockito, MockMvc, H2 for JPA tests.

---

## References

- Spec: `docs/superpowers/specs/2026-07-06-community-feature-design.md`
- Cloudinary Java SDK docs: https://cloudinary.com/documentation/java_integration
- Cloudinary Java SDK repository and release: https://github.com/cloudinary/cloudinary_java

## File Map

### Build And Configuration

- Modify: `backend/api/build.gradle.kts`  
  Add Cloudinary SDK dependency.
- Modify: `backend/domain/build.gradle.kts`  
  Add H2 test runtime for repository tests.
- Modify: `backend/api/src/main/resources/application.yml`  
  Add non-secret Cloudinary property keys and media upload limit defaults.
- Modify: `backend/api/src/main/resources/application-local.yml`  
  Add local property placeholders using environment variables.
- Modify: `backend/api/src/test/resources/application-test.yml`  
  Add fake Cloudinary property values for controller context tests.

### Domain

- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/member/Member.kt`  
  Add optional `profileMedia` relation and method for assigning profile media.
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/media/UploadedMedia.kt`
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/media/UploadedMediaType.kt`
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/media/UploadedMediaUsageType.kt`
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/media/UploadedMediaStatus.kt`
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/media/UploadedMediaRepository.kt`
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/community/CommunityPost.kt`  
  Replace status/string draft shape with crop, type enum, `isDeleted`, and media-aware relations.
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/community/CommunityComment.kt`  
  Remove `acceptedAnswer`; keep one-level reply support with `isDeleted`.
- Delete: `backend/domain/src/main/kotlin/com/chamchamcham/domain/community/CommunityPostStatus.kt`
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/community/CommunityPostType.kt`
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/community/CommunityPostMedia.kt`
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/community/CommunityPostLike.kt`
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/community/CommunityPostRepository.kt`
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/community/CommunityCommentRepository.kt`
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/community/CommunityPostMediaRepository.kt`
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/community/CommunityPostLikeRepository.kt`
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/community/CommunityPostQueryRepository.kt`
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/community/CommunityPostQueryRepositoryImpl.kt`
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/crop/MemberCropRepository.kt`
- Test: `backend/domain/src/test/kotlin/com/chamchamcham/domain/community/CommunityPostQueryRepositoryTest.kt`

### Application

- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/exception/ErrorCode.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/auth/common/AuthCommand.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/auth/common/AuthResult.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/auth/common/OnboardingService.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/media/ImageUploader.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/media/MediaCommand.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/media/MediaResult.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/media/MediaUploadService.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/community/CommunityPostCommand.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/community/CommunityCommentCommand.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/community/CommunityPostSearchCondition.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/community/CommunityPostResult.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/community/CommunityCommentResult.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/community/CommunityPostService.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/community/CommunityCommentService.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/media/MediaUploadServiceTest.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/auth/common/OnboardingServiceTest.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/community/CommunityPostServiceTest.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/community/CommunityCommentServiceTest.kt`

### API

- Modify: `backend/api/src/main/kotlin/com/chamchamcham/config/SecurityConfig.kt`  
  No new public endpoints; community/media APIs remain authenticated by the existing `.anyRequest().authenticated()` rule.
- Create: `backend/api/src/main/kotlin/com/chamchamcham/config/CloudinaryProperties.kt`
- Create: `backend/api/src/main/kotlin/com/chamchamcham/config/CloudinaryConfig.kt`
- Create: `backend/api/src/main/kotlin/com/chamchamcham/api/media/CloudinaryImageUploader.kt`
- Create: `backend/api/src/main/kotlin/com/chamchamcham/api/media/controller/MediaController.kt`
- Create: `backend/api/src/main/kotlin/com/chamchamcham/api/media/dto/MediaRequests.kt`
- Create: `backend/api/src/main/kotlin/com/chamchamcham/api/media/dto/MediaResponses.kt`
- Create: `backend/api/src/main/kotlin/com/chamchamcham/api/community/controller/CommunityController.kt`
- Create: `backend/api/src/main/kotlin/com/chamchamcham/api/community/dto/CommunityRequests.kt`
- Create: `backend/api/src/main/kotlin/com/chamchamcham/api/community/dto/CommunityResponses.kt`
- Modify: `backend/api/src/main/kotlin/com/chamchamcham/api/auth/dto/AuthRequests.kt`
- Modify: `backend/api/src/main/kotlin/com/chamchamcham/api/auth/dto/AuthResponses.kt`
- Modify: `backend/api/src/main/kotlin/com/chamchamcham/api/auth/controller/AuthController.kt`
- Test: `backend/api/src/test/kotlin/com/chamchamcham/api/media/controller/MediaControllerTest.kt`
- Test: `backend/api/src/test/kotlin/com/chamchamcham/api/community/controller/CommunityControllerTest.kt`
- Test: `backend/api/src/test/kotlin/com/chamchamcham/api/auth/controller/AuthControllerBusinessTest.kt`

---

## Task 1: Branch And Build Setup

**Files:**
- Modify: `backend/api/build.gradle.kts`
- Modify: `backend/domain/build.gradle.kts`
- Modify: `backend/api/src/main/resources/application.yml`
- Modify: `backend/api/src/main/resources/application-local.yml`
- Modify: `backend/api/src/test/resources/application-test.yml`

- [ ] **Step 1: Confirm branch and baseline status**

Run:

```bash
git branch --show-current
git status --short
```

Expected:

```text
feat/community
```

Existing untracked local files may appear. Do not add them unless they are created by this implementation plan.

- [ ] **Step 2: Add Cloudinary and H2 test dependencies**

In `backend/api/build.gradle.kts`, add:

```kotlin
implementation("com.cloudinary:cloudinary-http5:2.3.1")
```

In `backend/domain/build.gradle.kts`, add:

```kotlin
testRuntimeOnly("com.h2database:h2")
```

- [ ] **Step 3: Add Cloudinary configuration properties**

In `backend/api/src/main/resources/application.yml`, add:

```yaml
cloudinary:
  cloud-name: ${CLOUDINARY_CLOUD_NAME:}
  api-key: ${CLOUDINARY_API_KEY:}
  api-secret: ${CLOUDINARY_API_SECRET:}
  secure: true

app:
  media:
    max-image-bytes: 10485760
```

If the file already has an `app:` block, merge `media.max-image-bytes` into the existing `app:` block instead of creating a duplicate.

In `backend/api/src/main/resources/application-local.yml`, add the same keys only when they are absent:

```yaml
cloudinary:
  cloud-name: ${CLOUDINARY_CLOUD_NAME:}
  api-key: ${CLOUDINARY_API_KEY:}
  api-secret: ${CLOUDINARY_API_SECRET:}
  secure: true
```

In `backend/api/src/test/resources/application-test.yml`, add:

```yaml
cloudinary:
  cloud-name: test-cloud
  api-key: test-key
  api-secret: test-secret
  secure: true

app:
  media:
    max-image-bytes: 10485760
```

- [ ] **Step 4: Run compile check**

Run:

```bash
cd backend && ./gradlew :domain:compileKotlin :application:compileKotlin :api:compileKotlin
```

Expected: build succeeds.

- [ ] **Step 5: Commit build setup**

```bash
git add backend/api/build.gradle.kts backend/domain/build.gradle.kts backend/api/src/main/resources/application.yml backend/api/src/main/resources/application-local.yml backend/api/src/test/resources/application-test.yml
git commit -m "chore(community): 이미지 업로드 설정 기반 추가" \
  -m "Cloudinary 서버 업로드와 JPA 쿼리 테스트를 위한 최소 빌드 설정을 추가한다." \
  -m "Constraint: 이미지 업로드는 서버가 Cloudinary에 대행한다" \
  -m "Confidence: high" \
  -m "Scope-risk: narrow" \
  -m "Tested: ./gradlew :domain:compileKotlin :application:compileKotlin :api:compileKotlin"
```

---

## Task 2: Media Domain And Upload Service

**Files:**
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/media/UploadedMediaType.kt`
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/media/UploadedMediaUsageType.kt`
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/media/UploadedMediaStatus.kt`
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/media/UploadedMedia.kt`
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/media/UploadedMediaRepository.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/media/ImageUploader.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/media/MediaCommand.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/media/MediaResult.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/media/MediaUploadService.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/exception/ErrorCode.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/media/MediaUploadServiceTest.kt`

- [ ] **Step 1: Write failing upload service tests**

Create `backend/application/src/test/kotlin/com/chamchamcham/application/media/MediaUploadServiceTest.kt`:

```kotlin
package com.chamchamcham.application.media

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.media.UploadedMedia
import com.chamchamcham.domain.media.UploadedMediaRepository
import com.chamchamcham.domain.media.UploadedMediaStatus
import com.chamchamcham.domain.media.UploadedMediaType
import com.chamchamcham.domain.media.UploadedMediaUsageType
import com.chamchamcham.domain.member.Member
import com.chamchamcham.domain.member.MemberRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class MediaUploadServiceTest {
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val mediaId = UUID.fromString("00000000-0000-0000-0000-000000000501")

    @Mock
    private lateinit var memberRepository: MemberRepository

    @Mock
    private lateinit var uploadedMediaRepository: UploadedMediaRepository

    private lateinit var uploader: FakeImageUploader
    private lateinit var service: MediaUploadService

    @BeforeEach
    fun setUp() {
        uploader = FakeImageUploader()
        service = MediaUploadService(
            memberRepository = memberRepository,
            uploadedMediaRepository = uploadedMediaRepository,
            imageUploader = uploader,
            maxImageBytes = 10 * 1024 * 1024
        )
    }

    @Test
    fun `upload stores temp image media owned by member`() {
        val member = member()
        `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))
        `when`(uploadedMediaRepository.save(org.mockito.ArgumentMatchers.any(UploadedMedia::class.java)))
            .thenAnswer { invocation ->
                val media = invocation.arguments[0] as UploadedMedia
                UploadedMedia(
                    id = mediaId,
                    owner = media.owner,
                    mediaType = media.mediaType,
                    usageType = media.usageType,
                    fileUrl = media.fileUrl,
                    cloudinaryPublicId = media.cloudinaryPublicId,
                    status = media.status
                )
            }

        val result = service.upload(
            MediaCommand.UploadImage(
                memberId = memberId,
                usageType = UploadedMediaUsageType.COMMUNITY_POST,
                base64Image = "aGVsbG8=",
                originalFilename = "sprout.jpg",
                contentType = "image/jpeg"
            )
        )

        assertEquals(mediaId, result.mediaId)
        assertEquals("https://example.test/sprout.jpg", result.imageUrl)
        assertEquals(UploadedMediaStatus.TEMP, result.status)
        val saved = capturedMedia()
        assertEquals(member, saved.owner)
        assertEquals(UploadedMediaType.IMAGE, saved.mediaType)
        assertEquals(UploadedMediaUsageType.COMMUNITY_POST, saved.usageType)
        assertEquals("cloud/community/sprout", saved.cloudinaryPublicId)
        assertEquals(UploadedMediaStatus.TEMP, saved.status)
    }

    @Test
    fun `upload rejects missing member before calling uploader`() {
        `when`(memberRepository.findById(memberId)).thenReturn(Optional.empty())

        val exception = assertThrows(BusinessException::class.java) {
            service.upload(
                MediaCommand.UploadImage(
                    memberId = memberId,
                    usageType = UploadedMediaUsageType.PROFILE,
                    base64Image = "aGVsbG8=",
                    originalFilename = "profile.jpg",
                    contentType = "image/jpeg"
                )
            )
        }

        assertEquals(ErrorCode.MEMBER_NOT_FOUND, exception.errorCode)
        assertEquals(0, uploader.calls)
        verifyNoInteractions(uploadedMediaRepository)
    }

    @Test
    fun `upload rejects image larger than configured decoded byte limit`() {
        `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member()))

        val exception = assertThrows(BusinessException::class.java) {
            service.upload(
                MediaCommand.UploadImage(
                    memberId = memberId,
                    usageType = UploadedMediaUsageType.COMMUNITY_POST,
                    base64Image = "aGVsbG8=",
                    originalFilename = "too-large.jpg",
                    contentType = "image/jpeg"
                ),
                decodedBytesForTest = ByteArray(11 * 1024 * 1024)
            )
        }

        assertEquals(ErrorCode.MEDIA_TOO_LARGE, exception.errorCode)
        assertEquals(0, uploader.calls)
        verifyNoInteractions(uploadedMediaRepository)
    }

    private fun member(): Member =
        Member(id = memberId, email = "farmer@example.com", passwordHash = null)

    private fun capturedMedia(): UploadedMedia {
        val captor = ArgumentCaptor.forClass(UploadedMedia::class.java)
        verify(uploadedMediaRepository).save(captor.capture())
        return captor.value
    }

    private class FakeImageUploader : ImageUploader {
        var calls = 0

        override fun upload(command: ImageUploader.UploadCommand): ImageUploader.UploadedImage {
            calls += 1
            return ImageUploader.UploadedImage(
                imageUrl = "https://example.test/sprout.jpg",
                cloudinaryPublicId = "cloud/community/sprout"
            )
        }
    }
}
```

- [ ] **Step 2: Run media upload test and verify it fails**

Run:

```bash
cd backend && ./gradlew :application:test --tests "com.chamchamcham.application.media.MediaUploadServiceTest"
```

Expected: compilation fails because media types and service do not exist.

- [ ] **Step 3: Add media enums**

Create `UploadedMediaType.kt`:

```kotlin
package com.chamchamcham.domain.media

enum class UploadedMediaType {
    IMAGE,
}
```

Create `UploadedMediaUsageType.kt`:

```kotlin
package com.chamchamcham.domain.media

enum class UploadedMediaUsageType {
    PROFILE,
    COMMUNITY_POST,
}
```

Create `UploadedMediaStatus.kt`:

```kotlin
package com.chamchamcham.domain.media

enum class UploadedMediaStatus {
    TEMP,
    ATTACHED,
    DELETED,
}
```

- [ ] **Step 4: Add UploadedMedia entity and repository**

Create `UploadedMedia.kt`:

```kotlin
package com.chamchamcham.domain.media

import com.chamchamcham.domain.common.BaseTimeEntity
import com.chamchamcham.domain.member.Member
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "uploaded_media")
class UploadedMedia(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_member_id", nullable = false)
    val owner: Member,

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 32)
    val mediaType: UploadedMediaType,

    @Enumerated(EnumType.STRING)
    @Column(name = "usage_type", nullable = false, length = 32)
    val usageType: UploadedMediaUsageType,

    @Column(name = "file_url", nullable = false, length = 2048)
    val fileUrl: String,

    @Column(name = "cloudinary_public_id", nullable = false, length = 255)
    val cloudinaryPublicId: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: UploadedMediaStatus = UploadedMediaStatus.TEMP,
) : BaseTimeEntity() {
    fun markAttached() {
        status = UploadedMediaStatus.ATTACHED
    }

    fun markDeleted() {
        status = UploadedMediaStatus.DELETED
    }

    fun isAttachable(): Boolean = status == UploadedMediaStatus.TEMP
}
```

Create `UploadedMediaRepository.kt`:

```kotlin
package com.chamchamcham.domain.media

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UploadedMediaRepository : JpaRepository<UploadedMedia, UUID>
```

- [ ] **Step 5: Add media application port, command, result, and service**

Create `ImageUploader.kt`:

```kotlin
package com.chamchamcham.application.media

interface ImageUploader {
    fun upload(command: UploadCommand): UploadedImage

    data class UploadCommand(
        val bytes: ByteArray,
        val originalFilename: String?,
        val contentType: String?,
        val usageFolder: String
    )

    data class UploadedImage(
        val imageUrl: String,
        val cloudinaryPublicId: String
    )
}
```

Create `MediaCommand.kt`:

```kotlin
package com.chamchamcham.application.media

import com.chamchamcham.domain.media.UploadedMediaUsageType
import java.util.UUID

object MediaCommand {
    data class UploadImage(
        val memberId: UUID,
        val usageType: UploadedMediaUsageType,
        val base64Image: String,
        val originalFilename: String?,
        val contentType: String?
    )
}
```

Create `MediaResult.kt`:

```kotlin
package com.chamchamcham.application.media

import com.chamchamcham.domain.media.UploadedMedia
import com.chamchamcham.domain.media.UploadedMediaStatus
import java.util.UUID

object MediaResult {
    data class UploadedImage(
        val mediaId: UUID,
        val imageUrl: String,
        val status: UploadedMediaStatus
    ) {
        companion object {
            fun from(media: UploadedMedia): UploadedImage =
                UploadedImage(
                    mediaId = requireNotNull(media.id) { "Persisted media id is required" },
                    imageUrl = media.fileUrl,
                    status = media.status
                )
        }
    }
}
```

Create `MediaUploadService.kt`:

```kotlin
package com.chamchamcham.application.media

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.media.UploadedMedia
import com.chamchamcham.domain.media.UploadedMediaRepository
import com.chamchamcham.domain.media.UploadedMediaType
import com.chamchamcham.domain.media.UploadedMediaUsageType
import com.chamchamcham.domain.member.MemberRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Base64

@Service
@Transactional
class MediaUploadService(
    private val memberRepository: MemberRepository,
    private val uploadedMediaRepository: UploadedMediaRepository,
    private val imageUploader: ImageUploader,
    @Value("\${app.media.max-image-bytes:10485760}")
    private val maxImageBytes: Int
) {
    fun upload(
        command: MediaCommand.UploadImage,
        decodedBytesForTest: ByteArray? = null
    ): MediaResult.UploadedImage {
        val member = memberRepository.findById(command.memberId).orElseThrow {
            BusinessException(ErrorCode.MEMBER_NOT_FOUND)
        }
        val decoded = decodedBytesForTest ?: decodeBase64(command.base64Image)
        if (decoded.size > maxImageBytes) {
            throw BusinessException(ErrorCode.MEDIA_TOO_LARGE)
        }

        val uploaded = imageUploader.upload(
            ImageUploader.UploadCommand(
                bytes = decoded,
                originalFilename = command.originalFilename,
                contentType = command.contentType,
                usageFolder = command.usageType.folderName()
            )
        )

        val media = uploadedMediaRepository.save(
            UploadedMedia(
                owner = member,
                mediaType = UploadedMediaType.IMAGE,
                usageType = command.usageType,
                fileUrl = uploaded.imageUrl,
                cloudinaryPublicId = uploaded.cloudinaryPublicId
            )
        )

        return MediaResult.UploadedImage.from(media)
    }

    private fun decodeBase64(value: String): ByteArray {
        return try {
            val payload = value.substringAfter(",", value)
            Base64.getDecoder().decode(payload)
        } catch (exception: IllegalArgumentException) {
            throw BusinessException(ErrorCode.INVALID_INPUT)
        }
    }

    private fun UploadedMediaUsageType.folderName(): String =
        when (this) {
            UploadedMediaUsageType.PROFILE -> "profiles"
            UploadedMediaUsageType.COMMUNITY_POST -> "community-posts"
        }
}
```

- [ ] **Step 6: Add media error codes**

Modify `ErrorCode.kt` by adding values before `INTERNAL_ERROR`:

```kotlin
MEDIA_NOT_FOUND("MEDIA_001", "error.media_not_found", 404),
MEDIA_NOT_OWNED("MEDIA_002", "error.media_not_owned", 403),
MEDIA_USAGE_MISMATCH("MEDIA_003", "error.media_usage_mismatch", 400),
MEDIA_NOT_ATTACHABLE("MEDIA_004", "error.media_not_attachable", 400),
MEDIA_TOO_LARGE("MEDIA_005", "error.media_too_large", 400),
MEDIA_UPLOAD_FAILED("MEDIA_006", "error.media_upload_failed", 503),
```

- [ ] **Step 7: Run media upload tests**

Run:

```bash
cd backend && ./gradlew :application:test --tests "com.chamchamcham.application.media.MediaUploadServiceTest"
```

Expected: tests pass.

- [ ] **Step 8: Commit media domain and service**

```bash
git add backend/domain/src/main/kotlin/com/chamchamcham/domain/media backend/application/src/main/kotlin/com/chamchamcham/application/media backend/application/src/main/kotlin/com/chamchamcham/application/exception/ErrorCode.kt backend/application/src/test/kotlin/com/chamchamcham/application/media/MediaUploadServiceTest.kt
git commit -m "feat(media): Cloudinary 업로드 미디어 원장 추가" \
  -m "이미지 업로드 결과를 uploaded_media로 추적하고, TEMP 상태 미디어를 저장하는 application 서비스를 추가한다." \
  -m "Constraint: 업로드는 서버가 Cloudinary에 수행하고 DB에는 URL과 public id를 저장한다" \
  -m "Confidence: high" \
  -m "Scope-risk: moderate" \
  -m "Tested: ./gradlew :application:test --tests \"com.chamchamcham.application.media.MediaUploadServiceTest\""
```

---

## Task 3: Cloudinary Adapter And Media API

**Files:**
- Create: `backend/api/src/main/kotlin/com/chamchamcham/config/CloudinaryProperties.kt`
- Create: `backend/api/src/main/kotlin/com/chamchamcham/config/CloudinaryConfig.kt`
- Create: `backend/api/src/main/kotlin/com/chamchamcham/api/media/CloudinaryImageUploader.kt`
- Create: `backend/api/src/main/kotlin/com/chamchamcham/api/media/controller/MediaController.kt`
- Create: `backend/api/src/main/kotlin/com/chamchamcham/api/media/dto/MediaRequests.kt`
- Create: `backend/api/src/main/kotlin/com/chamchamcham/api/media/dto/MediaResponses.kt`
- Test: `backend/api/src/test/kotlin/com/chamchamcham/api/media/controller/MediaControllerTest.kt`

- [ ] **Step 1: Write failing media controller test**

Create `MediaControllerTest.kt`:

```kotlin
package com.chamchamcham.api.media.controller

import com.chamchamcham.api.exception.GlobalExceptionHandler
import com.chamchamcham.application.media.MediaCommand
import com.chamchamcham.application.media.MediaResult
import com.chamchamcham.application.media.MediaUploadService
import com.chamchamcham.domain.media.UploadedMediaStatus
import com.chamchamcham.domain.media.UploadedMediaUsageType
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@WebMvcTest(MediaController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler::class)
class MediaControllerTest(
    @Autowired private val mockMvc: MockMvc
) {
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val mediaId = UUID.fromString("00000000-0000-0000-0000-000000000501")

    @MockBean
    private lateinit var mediaUploadService: MediaUploadService

    @Test
    fun `upload image returns uploaded media response`() {
        `when`(
            mediaUploadService.upload(
                MediaCommand.UploadImage(
                    memberId = memberId,
                    usageType = UploadedMediaUsageType.COMMUNITY_POST,
                    base64Image = "aGVsbG8=",
                    originalFilename = "sprout.jpg",
                    contentType = "image/jpeg"
                )
            )
        ).thenReturn(
            MediaResult.UploadedImage(
                mediaId = mediaId,
                imageUrl = "https://example.test/sprout.jpg",
                status = UploadedMediaStatus.TEMP
            )
        )

        mockMvc.perform(
            post("/api/v1/media/images")
                .with(authenticatedMember(memberId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "usageType": "COMMUNITY_POST",
                      "base64Image": "aGVsbG8=",
                      "originalFilename": "sprout.jpg",
                      "contentType": "image/jpeg"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.mediaId", equalTo(mediaId.toString())))
            .andExpect(jsonPath("$.data.imageUrl", equalTo("https://example.test/sprout.jpg")))
            .andExpect(jsonPath("$.data.status", equalTo("TEMP")))
    }

    private fun authenticatedMember(memberId: String): RequestPostProcessor {
        return RequestPostProcessor { request ->
            val authentication = UsernamePasswordAuthenticationToken(
                memberId,
                null,
                listOf(SimpleGrantedAuthority("ROLE_USER"))
            )
            request.userPrincipal = authentication
            request
        }
    }
}
```

- [ ] **Step 2: Run media controller test and verify it fails**

Run:

```bash
cd backend && ./gradlew :api:test --tests "com.chamchamcham.api.media.controller.MediaControllerTest"
```

Expected: compilation fails because media API classes do not exist.

- [ ] **Step 3: Add media DTOs and controller**

Create `MediaRequests.kt`:

```kotlin
package com.chamchamcham.api.media.dto

import com.chamchamcham.domain.media.UploadedMediaUsageType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

object MediaRequests {
    data class UploadImageRequest(
        @field:NotNull(message = "이미지 용도를 입력해주세요")
        val usageType: UploadedMediaUsageType?,
        @field:NotBlank(message = "이미지 데이터를 입력해주세요")
        val base64Image: String,
        val originalFilename: String? = null,
        val contentType: String? = null
    )
}
```

Create `MediaResponses.kt`:

```kotlin
package com.chamchamcham.api.media.dto

import com.chamchamcham.application.media.MediaResult
import com.chamchamcham.domain.media.UploadedMediaStatus
import java.util.UUID

object MediaResponses {
    data class UploadedImageResponse(
        val mediaId: UUID,
        val imageUrl: String,
        val status: UploadedMediaStatus
    ) {
        companion object {
            fun from(result: MediaResult.UploadedImage): UploadedImageResponse =
                UploadedImageResponse(
                    mediaId = result.mediaId,
                    imageUrl = result.imageUrl,
                    status = result.status
                )
        }
    }
}
```

Create `MediaController.kt`:

```kotlin
package com.chamchamcham.api.media.controller

import com.chamchamcham.api.common.ApiResponse
import com.chamchamcham.api.media.dto.MediaRequests
import com.chamchamcham.api.media.dto.MediaResponses
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.application.media.MediaCommand
import com.chamchamcham.application.media.MediaUploadService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/media")
class MediaController(
    private val mediaUploadService: MediaUploadService
) {
    @PostMapping("/images")
    fun uploadImage(
        @AuthenticationPrincipal memberId: String?,
        @Valid @RequestBody request: MediaRequests.UploadImageRequest
    ): ResponseEntity<ApiResponse<MediaResponses.UploadedImageResponse>> {
        val result = mediaUploadService.upload(
            MediaCommand.UploadImage(
                memberId = parseMemberId(memberId),
                usageType = requireNotNull(request.usageType),
                base64Image = request.base64Image,
                originalFilename = request.originalFilename,
                contentType = request.contentType
            )
        )
        return ResponseEntity.ok(ApiResponse.ok(MediaResponses.UploadedImageResponse.from(result)))
    }

    private fun parseMemberId(memberId: String?): UUID {
        if (memberId.isNullOrBlank()) {
            throw BusinessException(ErrorCode.UNAUTHORIZED)
        }
        return try {
            UUID.fromString(memberId)
        } catch (exception: IllegalArgumentException) {
            throw BusinessException(ErrorCode.UNAUTHORIZED)
        }
    }
}
```

- [ ] **Step 4: Add Cloudinary properties and adapter**

Create `CloudinaryProperties.kt`:

```kotlin
package com.chamchamcham.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "cloudinary")
data class CloudinaryProperties(
    val cloudName: String,
    val apiKey: String,
    val apiSecret: String,
    val secure: Boolean = true
)
```

Create `CloudinaryConfig.kt`:

```kotlin
package com.chamchamcham.config

import com.cloudinary.Cloudinary
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(CloudinaryProperties::class)
class CloudinaryConfig {
    @Bean
    fun cloudinary(properties: CloudinaryProperties): Cloudinary {
        return Cloudinary(
            mapOf(
                "cloud_name" to properties.cloudName,
                "api_key" to properties.apiKey,
                "api_secret" to properties.apiSecret,
                "secure" to properties.secure
            )
        )
    }
}
```

Create `CloudinaryImageUploader.kt`:

```kotlin
package com.chamchamcham.api.media

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.application.media.ImageUploader
import com.cloudinary.Cloudinary
import com.cloudinary.utils.ObjectUtils
import org.springframework.stereotype.Component

@Component
class CloudinaryImageUploader(
    private val cloudinary: Cloudinary
) : ImageUploader {
    override fun upload(command: ImageUploader.UploadCommand): ImageUploader.UploadedImage {
        return try {
            val uploadResult = cloudinary.uploader().upload(
                command.bytes,
                ObjectUtils.asMap(
                    "folder", command.usageFolder,
                    "resource_type", "image"
                )
            )
            ImageUploader.UploadedImage(
                imageUrl = uploadResult["secure_url"] as String,
                cloudinaryPublicId = uploadResult["public_id"] as String
            )
        } catch (exception: RuntimeException) {
            throw BusinessException(ErrorCode.MEDIA_UPLOAD_FAILED)
        }
    }
}
```

- [ ] **Step 5: Run media API tests**

Run:

```bash
cd backend && ./gradlew :api:test --tests "com.chamchamcham.api.media.controller.MediaControllerTest"
```

Expected: tests pass.

- [ ] **Step 6: Commit media API**

```bash
git add backend/api/src/main/kotlin/com/chamchamcham/config/CloudinaryProperties.kt backend/api/src/main/kotlin/com/chamchamcham/config/CloudinaryConfig.kt backend/api/src/main/kotlin/com/chamchamcham/api/media backend/api/src/test/kotlin/com/chamchamcham/api/media/controller/MediaControllerTest.kt
git commit -m "feat(media): 이미지 업로드 API 추가" \
  -m "Base64 이미지 요청을 application 업로드 서비스로 전달하고 Cloudinary adapter를 통해 서버 업로드를 수행한다." \
  -m "Constraint: Cloudinary 비밀값은 환경 변수로만 주입한다" \
  -m "Confidence: high" \
  -m "Scope-risk: moderate" \
  -m "Tested: ./gradlew :api:test --tests \"com.chamchamcham.api.media.controller.MediaControllerTest\""
```

---

## Task 4: Onboarding Profile Media Attachment

**Files:**
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/member/Member.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/auth/common/AuthCommand.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/auth/common/AuthResult.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/auth/common/OnboardingService.kt`
- Modify: `backend/api/src/main/kotlin/com/chamchamcham/api/auth/dto/AuthRequests.kt`
- Modify: `backend/api/src/main/kotlin/com/chamchamcham/api/auth/dto/AuthResponses.kt`
- Modify: `backend/api/src/main/kotlin/com/chamchamcham/api/auth/controller/AuthController.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/auth/common/OnboardingServiceTest.kt`
- Test: `backend/api/src/test/kotlin/com/chamchamcham/api/auth/controller/AuthControllerBusinessTest.kt`

- [ ] **Step 1: Add failing onboarding service tests for profile media**

Extend `OnboardingServiceTest` with mocks and tests:

```kotlin
@Mock
private lateinit var uploadedMediaRepository: UploadedMediaRepository
```

Update `setUp()` construction after modifying `OnboardingService`:

```kotlin
service = OnboardingService(
    memberRepository = memberRepository,
    farmRepository = farmRepository,
    cropRepository = cropRepository,
    memberCropRepository = memberCropRepository,
    uploadedMediaRepository = uploadedMediaRepository,
    onboardingStatusResolver = onboardingStatusResolver
)
```

Add test:

```kotlin
@Test
fun `complete attaches optional profile media`() {
    val member = member()
    val crop = crop(id = cropId, externalNo = 422, name = "참당귀")
    val media = profileMedia(member)
    val command = completeOnboardingCommand(cropIds = listOf(cropId), profileMediaId = requireNotNull(media.id))

    `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))
    `when`(uploadedMediaRepository.findById(requireNotNull(media.id))).thenReturn(Optional.of(media))
    `when`(cropRepository.findAllById(listOf(cropId))).thenReturn(listOf(crop))
    `when`(farmRepository.save(any(Farm::class.java))).thenReturn(savedFarm(member, command.farm))

    val result = service.complete(command)

    assertEquals(media, member.profileMedia)
    assertEquals(UploadedMediaStatus.ATTACHED, media.status)
    assertEquals("https://example.test/profile.jpg", result.member.profileImageUrl)
}
```

Add rejection test:

```kotlin
@Test
fun `complete rejects profile media owned by another member`() {
    val member = member()
    val otherMember = Member(
        id = UUID.fromString("00000000-0000-0000-0000-000000000099"),
        email = "other@example.com",
        passwordHash = null
    )
    val media = UploadedMedia(
        id = UUID.fromString("00000000-0000-0000-0000-000000000501"),
        owner = otherMember,
        mediaType = UploadedMediaType.IMAGE,
        usageType = UploadedMediaUsageType.PROFILE,
        fileUrl = "https://example.test/profile.jpg",
        cloudinaryPublicId = "profiles/profile"
    )

    `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))
    `when`(uploadedMediaRepository.findById(requireNotNull(media.id))).thenReturn(Optional.of(media))

    val exception = assertThrows(BusinessException::class.java) {
        service.complete(completeOnboardingCommand(profileMediaId = requireNotNull(media.id)))
    }

    assertEquals(ErrorCode.MEDIA_NOT_OWNED, exception.errorCode)
    verifyNoInteractions(cropRepository, farmRepository, memberCropRepository)
}
```

Add helper:

```kotlin
private fun profileMedia(member: Member): UploadedMedia {
    return UploadedMedia(
        id = UUID.fromString("00000000-0000-0000-0000-000000000501"),
        owner = member,
        mediaType = UploadedMediaType.IMAGE,
        usageType = UploadedMediaUsageType.PROFILE,
        fileUrl = "https://example.test/profile.jpg",
        cloudinaryPublicId = "profiles/profile"
    )
}
```

Update helper command signature:

```kotlin
private fun completeOnboardingCommand(
    cropIds: List<UUID> = listOf(cropId),
    profileMediaId: UUID? = null
): AuthCommand.CompleteOnboarding
```

- [ ] **Step 2: Run onboarding service test and verify it fails**

Run:

```bash
cd backend && ./gradlew :application:test --tests "com.chamchamcham.application.auth.common.OnboardingServiceTest"
```

Expected: compilation fails because `profileMedia`, `profileMediaId`, and repository constructor changes do not exist.

- [ ] **Step 3: Add profile media relation to Member**

Modify `Member.kt` imports and constructor:

```kotlin
import com.chamchamcham.domain.media.UploadedMedia
import jakarta.persistence.OneToOne
```

Add constructor property:

```kotlin
@OneToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "profile_media_id")
var profileMedia: UploadedMedia? = null,
```

Add method:

```kotlin
fun updateProfileMedia(media: UploadedMedia?) {
    this.profileMedia = media
}
```

Use `FetchType.LAZY` from existing imports or add it when missing.

- [ ] **Step 4: Extend onboarding command and result**

In `AuthCommand.CompleteOnboarding`, add:

```kotlin
val profileMediaId: UUID?,
```

In `AuthResult.MemberProfile`, add:

```kotlin
val profileImageUrl: String?,
```

Update `MemberProfile.from(member)`:

```kotlin
profileImageUrl = member.profileMedia?.fileUrl
```

- [ ] **Step 5: Update OnboardingService validation**

Add `UploadedMediaRepository` constructor dependency and validation before crop lookup:

```kotlin
val profileMedia = command.profileMediaId?.let { mediaId ->
    val media = uploadedMediaRepository.findById(mediaId).orElseThrow {
        BusinessException(ErrorCode.MEDIA_NOT_FOUND)
    }
    if (media.owner.id != member.id) {
        throw BusinessException(ErrorCode.MEDIA_NOT_OWNED)
    }
    if (media.usageType != UploadedMediaUsageType.PROFILE) {
        throw BusinessException(ErrorCode.MEDIA_USAGE_MISMATCH)
    }
    if (!media.isAttachable()) {
        throw BusinessException(ErrorCode.MEDIA_NOT_ATTACHABLE)
    }
    media
}
```

After the `member.completeOnboarding` call, attach:

```kotlin
profileMedia?.let {
    member.updateProfileMedia(it)
    it.markAttached()
}
```

- [ ] **Step 6: Update auth API DTO/controller mapping**

In `CompleteOnboardingRequest`, add:

```kotlin
val profileMediaId: UUID? = null,
```

In `AuthController.completeOnboarding`, pass:

```kotlin
profileMediaId = request.profileMediaId,
```

In `AuthResponses.MemberProfileResponse`, add `profileImageUrl: String?` and map from result.

- [ ] **Step 7: Extend auth controller business test**

Update expected `AuthCommand.CompleteOnboarding` in `AuthControllerBusinessTest` with:

```kotlin
profileMediaId = UUID.fromString("00000000-0000-0000-0000-000000000501"),
```

Add JSON field to the request body:

```json
"profileMediaId": "00000000-0000-0000-0000-000000000501"
```

Assert:

```kotlin
.andExpect(jsonPath("$.data.member.profileImageUrl", equalTo("https://example.test/profile.jpg")))
```

- [ ] **Step 8: Run onboarding tests**

Run:

```bash
cd backend && ./gradlew :application:test --tests "com.chamchamcham.application.auth.common.OnboardingServiceTest" :api:test --tests "com.chamchamcham.api.auth.controller.AuthControllerBusinessTest"
```

Expected: tests pass.

- [ ] **Step 9: Commit onboarding media attachment**

```bash
git add backend/domain/src/main/kotlin/com/chamchamcham/domain/member/Member.kt backend/application/src/main/kotlin/com/chamchamcham/application/auth/common backend/api/src/main/kotlin/com/chamchamcham/api/auth backend/application/src/test/kotlin/com/chamchamcham/application/auth/common/OnboardingServiceTest.kt backend/api/src/test/kotlin/com/chamchamcham/api/auth/controller/AuthControllerBusinessTest.kt
git commit -m "feat(auth): 온보딩 프로필 이미지 연결 추가" \
  -m "업로드된 PROFILE 미디어를 온보딩 완료 시 member.profile_media_id에 연결하고 응답에 프로필 이미지 URL을 포함한다." \
  -m "Constraint: profile_image_url은 member에 중복 저장하지 않음" \
  -m "Confidence: high" \
  -m "Scope-risk: moderate" \
  -m "Tested: ./gradlew :application:test --tests \"com.chamchamcham.application.auth.common.OnboardingServiceTest\" :api:test --tests \"com.chamchamcham.api.auth.controller.AuthControllerBusinessTest\""
```

---

## Task 5: Community Domain Model

**Files:**
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/community/CommunityPost.kt`
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/community/CommunityComment.kt`
- Delete: `backend/domain/src/main/kotlin/com/chamchamcham/domain/community/CommunityPostStatus.kt`
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/community/CommunityPostType.kt`
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/community/CommunityPostMedia.kt`
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/community/CommunityPostLike.kt`
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/community/CommunityPostRepository.kt`
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/community/CommunityCommentRepository.kt`
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/community/CommunityPostLikeRepository.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/exception/ErrorCode.kt`

- [ ] **Step 1: Add community error codes**

In `ErrorCode.kt`, add:

```kotlin
COMMUNITY_POST_NOT_FOUND("COMMUNITY_001", "error.community_post_not_found", 404),
COMMUNITY_COMMENT_NOT_FOUND("COMMUNITY_002", "error.community_comment_not_found", 404),
COMMUNITY_FORBIDDEN("COMMUNITY_003", "error.community_forbidden", 403),
COMMUNITY_INVALID_REPLY_PARENT("COMMUNITY_004", "error.community_invalid_reply_parent", 400),
COMMUNITY_TOO_MANY_IMAGES("COMMUNITY_005", "error.community_too_many_images", 400),
COMMUNITY_FARMING_RECORD_CROP_MISMATCH("COMMUNITY_006", "error.community_farming_record_crop_mismatch", 400),
FARMING_RECORD_NOT_FOUND("FARMING_001", "error.farming_record_not_found", 404),
```

- [ ] **Step 2: Replace post type/status model**

Create `CommunityPostType.kt`:

```kotlin
package com.chamchamcham.domain.community

enum class CommunityPostType {
    GENERAL,
    QUESTION,
}
```

Delete `CommunityPostStatus.kt`.

- [ ] **Step 3: Update CommunityPost entity**

Replace `CommunityPost.kt` with:

```kotlin
package com.chamchamcham.domain.community

import com.chamchamcham.domain.common.BaseTimeEntity
import com.chamchamcham.domain.crop.Crop
import com.chamchamcham.domain.farming.FarmingRecord
import com.chamchamcham.domain.member.Member
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "community_post")
class CommunityPost(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_member_id", nullable = false)
    val author: Member,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "crop_id", nullable = false)
    var crop: Crop,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "farming_record_id")
    var farmingRecord: FarmingRecord? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "post_type", nullable = false, length = 32)
    var postType: CommunityPostType,

    @Column(nullable = false, length = 255)
    var title: String,

    @Column(nullable = false, columnDefinition = "text")
    var body: String,

    @Column(name = "is_deleted", nullable = false)
    var isDeleted: Boolean = false,
) : BaseTimeEntity() {
    fun update(
        crop: Crop,
        farmingRecord: FarmingRecord?,
        postType: CommunityPostType,
        title: String,
        body: String
    ) {
        this.crop = crop
        this.farmingRecord = farmingRecord
        this.postType = postType
        this.title = title
        this.body = body
    }

    fun softDelete() {
        isDeleted = true
    }
}
```

- [ ] **Step 4: Add CommunityPostMedia**

Create `CommunityPostMedia.kt`:

```kotlin
package com.chamchamcham.domain.community

import com.chamchamcham.domain.common.BaseTimeEntity
import com.chamchamcham.domain.media.UploadedMedia
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.util.UUID

@Entity
@Table(
    name = "community_post_media",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_community_post_media_uploaded_media", columnNames = ["uploaded_media_id"])
    ]
)
class CommunityPostMedia(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    val post: CommunityPost,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_media_id", nullable = false)
    val uploadedMedia: UploadedMedia,

    @Column(name = "display_order", nullable = false)
    val displayOrder: Int
) : BaseTimeEntity()
```

- [ ] **Step 5: Update CommunityComment entity**

Replace `CommunityComment.kt` constructor body with the spec shape and remove `acceptedAnswer`:

```kotlin
@Column(name = "is_deleted", nullable = false)
var isDeleted: Boolean = false,
) : BaseTimeEntity() {
    fun softDelete() {
        isDeleted = true
    }
}
```

- [ ] **Step 6: Add CommunityPostLike**

Create `CommunityPostLike.kt`:

```kotlin
package com.chamchamcham.domain.community

import com.chamchamcham.domain.common.BaseTimeEntity
import com.chamchamcham.domain.member.Member
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.util.UUID

@Entity
@Table(
    name = "community_post_like",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_community_post_like_post_member", columnNames = ["post_id", "member_id"])
    ]
)
class CommunityPostLike(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    val post: CommunityPost,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    val member: Member
) : BaseTimeEntity()
```

- [ ] **Step 7: Update repositories**

`CommunityPostRepository.kt`:

```kotlin
package com.chamchamcham.domain.community

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CommunityPostRepository : JpaRepository<CommunityPost, UUID> {
    fun findByIdAndIsDeletedFalse(id: UUID): CommunityPost?
}
```

`CommunityCommentRepository.kt`:

```kotlin
package com.chamchamcham.domain.community

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CommunityCommentRepository : JpaRepository<CommunityComment, UUID> {
    fun findByPost_IdOrderByCreatedAtAscIdAsc(postId: UUID): List<CommunityComment>
    fun countByPost_IdAndIsDeletedFalse(postId: UUID): Long
}
```

Create `CommunityPostMediaRepository.kt`:

```kotlin
package com.chamchamcham.domain.community

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CommunityPostMediaRepository : JpaRepository<CommunityPostMedia, UUID> {
    fun findByPost_IdOrderByDisplayOrderAsc(postId: UUID): List<CommunityPostMedia>
    fun deleteByPost(post: CommunityPost)
}
```

Create `CommunityPostLikeRepository.kt`:

```kotlin
package com.chamchamcham.domain.community

import com.chamchamcham.domain.member.Member
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CommunityPostLikeRepository : JpaRepository<CommunityPostLike, UUID> {
    fun findByPost_IdAndMember_Id(postId: UUID, memberId: UUID): CommunityPostLike?
    fun existsByPost_IdAndMember_Id(postId: UUID, memberId: UUID): Boolean
    fun countByPost_Id(postId: UUID): Long
    fun deleteByPostAndMember(post: CommunityPost, member: Member)
}
```

Modify `MemberCropRepository.kt`:

```kotlin
package com.chamchamcham.domain.crop

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MemberCropRepository : JpaRepository<MemberCrop, UUID> {
    fun countByMember_Id(memberId: UUID): Long
    fun findByMember_Id(memberId: UUID): List<MemberCrop>
}
```

- [ ] **Step 8: Compile domain**

Run:

```bash
cd backend && ./gradlew :domain:compileKotlin :application:compileKotlin
```

Expected: compilation succeeds.

- [ ] **Step 9: Commit community domain**

```bash
git add backend/domain/src/main/kotlin/com/chamchamcham/domain/community backend/application/src/main/kotlin/com/chamchamcham/application/exception/ErrorCode.kt
git commit -m "feat(community): 게시글 댓글 좋아요 도메인 모델 정리" \
  -m "커뮤니티 게시글을 crop 필수와 is_deleted 기반으로 정리하고 게시글 미디어, 댓글, 좋아요 모델을 추가한다." \
  -m "Constraint: 채택 답변과 게시글 status enum은 1차 범위에서 제외" \
  -m "Confidence: high" \
  -m "Scope-risk: moderate" \
  -m "Tested: ./gradlew :domain:compileKotlin :application:compileKotlin"
```

---

## Task 6: Community Post Application Service

**Files:**
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/community/CommunityPostCommand.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/community/CommunityPostResult.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/community/CommunityPostSearchCondition.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/community/CommunityPostService.kt`
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/crop/MemberCropRepository.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/community/CommunityPostServiceTest.kt`

- [ ] **Step 1: Write failing create/update/delete/toggle tests**

Create `CommunityPostServiceTest.kt` with Mockito like `OnboardingServiceTest`. Define fixed ids for `memberId`, `otherMemberId`, `postId`, `cropId`, `secondCropId`, `recordId`, `mediaId1`, and `mediaId2`. Add these tests with the listed assertions:

```kotlin
@Test
fun `create stores post with crop farming record and attached images`() {
    val result = service.create(createCommand(mediaIds = listOf(mediaId1, mediaId2)))

    assertEquals(postId, result.id)
    val savedPost = capturedPost()
    assertEquals(memberId, savedPost.author.id)
    assertEquals(cropId, savedPost.crop.id)
    assertEquals(recordId, savedPost.farmingRecord?.id)
    assertEquals(CommunityPostType.QUESTION, savedPost.postType)
    assertEquals("황기 발아율이 낮아요", savedPost.title)
    assertEquals("싹이 거의 올라오지 않아요.", savedPost.body)
    assertEquals(UploadedMediaStatus.ATTACHED, media1.status)
    assertEquals(UploadedMediaStatus.ATTACHED, media2.status)
    assertThat(capturedPostMedia().map { it.displayOrder }).containsExactly(0, 1)
}

@Test
fun `create rejects farming record with different crop`() {
    val exception = assertThrows(BusinessException::class.java) {
        service.create(createCommand(farmingRecordId = recordId))
    }

    assertEquals(ErrorCode.COMMUNITY_FARMING_RECORD_CROP_MISMATCH, exception.errorCode)
    verify(communityPostRepository, never()).save(any(CommunityPost::class.java))
}

@Test
fun `create rejects more than five images`() {
    val exception = assertThrows(BusinessException::class.java) {
        service.create(createCommand(mediaIds = listOf(mediaId1, mediaId2, mediaId3, mediaId4, mediaId5, mediaId6)))
    }

    assertEquals(ErrorCode.COMMUNITY_TOO_MANY_IMAGES, exception.errorCode)
    verifyNoInteractions(uploadedMediaRepository)
    verify(communityPostRepository, never()).save(any(CommunityPost::class.java))
}

@Test
fun `update allows author to change crop farming record content and images`() {
    val result = service.update(updateCommand())

    assertEquals(postId, result.id)
    assertEquals(secondCropId, existingPost.crop.id)
    assertEquals(CommunityPostType.GENERAL, existingPost.postType)
    assertEquals("수정된 제목", existingPost.title)
    assertEquals("수정된 본문", existingPost.body)
    assertEquals(recordId, existingPost.farmingRecord?.id)
    assertEquals(UploadedMediaStatus.ATTACHED, replacementMedia.status)
}

@Test
fun `delete rejects non author`() {
    val exception = assertThrows(BusinessException::class.java) {
        service.delete(CommunityPostCommand.Delete(memberId = otherMemberId, postId = postId))
    }

    assertEquals(ErrorCode.COMMUNITY_FORBIDDEN, exception.errorCode)
    assertFalse(existingPost.isDeleted)
}

@Test
fun `toggle like creates then removes like row`() {
    val first = service.toggleLike(CommunityPostCommand.ToggleLike(memberId = memberId, postId = postId))
    val second = service.toggleLike(CommunityPostCommand.ToggleLike(memberId = memberId, postId = postId))

    assertTrue(first.liked)
    assertEquals(1, first.likeCount)
    verify(communityPostLikeRepository).save(any(CommunityPostLike::class.java))
    assertFalse(second.liked)
    assertEquals(0, second.likeCount)
    verify(communityPostLikeRepository).delete(existingLike)
}

@Test
fun `list boards de duplicates member crops by crop id preserving first encounter order`() {
    val boards = service.listBoards(memberId)

    assertThat(boards.map { it.cropId }).containsExactly(hwanggiCropId, ginsengCropId)
    assertThat(boards.map { it.cropName }).containsExactly("황기", "인삼")
}

@Test
fun `get detail returns ordered images counts and liked by me`() {
    val detail = service.getDetail(memberId = memberId, postId = postId)

    assertEquals(postId, detail.id)
    assertThat(detail.imageUrls).containsExactly("https://example.test/1.jpg", "https://example.test/2.jpg")
    assertEquals(3, detail.commentCount)
    assertEquals(8, detail.likeCount)
    assertTrue(detail.likedByMe)
}

@Test
fun `search maps application condition to query repository and next cursor`() {
    val page = service.search(
        CommunityPostSearchCondition(
            memberId = memberId,
            cropId = cropId,
            postType = CommunityPostType.QUESTION,
            keyword = "발아",
            likedOnly = false,
            mineOnly = false,
            cursorCreatedAt = null,
            cursorId = null,
            size = 20
        )
    )

    assertEquals(postId, page.items.single().id)
    assertEquals(postCreatedAt, page.nextCursorCreatedAt)
    assertEquals(postId, page.nextCursorId)
}
```

Support the tests with helper functions in the same test file: `member(id)`, `crop(id, name)`, `memberCrop(member, crop)`, `farmingRecord(member, crop)`, `uploadedMedia(member, id, usageType, status)`, `existingPost(author, crop)`, `createCommand(mediaIds: List<UUID> = emptyList(), farmingRecordId: UUID? = recordId)`, `updateCommand(mediaIds: List<UUID> = listOf(replacementMediaId), farmingRecordId: UUID? = recordId)`, `queryRow(post, thumbnailUrl, commentCount, likeCount, likedByMe)`, `capturedPost()`, and `capturedPostMedia()`. Mock repositories instead of using Spring context.

- [ ] **Step 2: Run post service tests and verify failure**

Run:

```bash
cd backend && ./gradlew :application:test --tests "com.chamchamcham.application.community.CommunityPostServiceTest"
```

Expected: compilation fails because community application classes do not exist.

- [ ] **Step 3: Add command and result types**

Create `CommunityPostCommand.kt`:

```kotlin
package com.chamchamcham.application.community

import com.chamchamcham.domain.community.CommunityPostType
import java.util.UUID

object CommunityPostCommand {
    data class Create(
        val memberId: UUID,
        val cropId: UUID,
        val postType: CommunityPostType,
        val title: String,
        val body: String,
        val farmingRecordId: UUID?,
        val mediaIds: List<UUID>
    )

    data class Update(
        val memberId: UUID,
        val postId: UUID,
        val cropId: UUID,
        val postType: CommunityPostType,
        val title: String,
        val body: String,
        val farmingRecordId: UUID?,
        val mediaIds: List<UUID>
    )

    data class Delete(
        val memberId: UUID,
        val postId: UUID
    )

    data class ToggleLike(
        val memberId: UUID,
        val postId: UUID
    )
}
```

Create `CommunityPostResult.kt`:

```kotlin
package com.chamchamcham.application.community

import com.chamchamcham.domain.community.CommunityPost
import com.chamchamcham.domain.community.CommunityPostType
import java.time.LocalDateTime
import java.util.UUID

object CommunityPostResult {
    data class PostId(val id: UUID)

    data class LikeToggle(
        val liked: Boolean,
        val likeCount: Long
    )

    data class AuthorSummary(
        val memberId: UUID,
        val nickname: String?,
        val profileImageUrl: String?
    )

    data class PostSummary(
        val id: UUID,
        val cropId: UUID,
        val cropName: String,
        val postType: CommunityPostType,
        val title: String,
        val bodyPreview: String,
        val thumbnailUrl: String?,
        val author: AuthorSummary,
        val commentCount: Long,
        val likeCount: Long,
        val likedByMe: Boolean,
        val createdAt: LocalDateTime
    )

    data class PostDetail(
        val id: UUID,
        val cropId: UUID,
        val cropName: String,
        val postType: CommunityPostType,
        val title: String,
        val body: String,
        val imageUrls: List<String>,
        val farmingRecordId: UUID?,
        val author: AuthorSummary,
        val commentCount: Long,
        val likeCount: Long,
        val likedByMe: Boolean,
        val createdAt: LocalDateTime
    )

    data class Page(
        val items: List<PostSummary>,
        val nextCursorCreatedAt: LocalDateTime?,
        val nextCursorId: UUID?
    )

    data class Board(
        val cropId: UUID,
        val cropName: String
    )

    fun idOf(post: CommunityPost): PostId =
        PostId(requireNotNull(post.id) { "Persisted post id is required" })
}
```

Create `CommunityPostSearchCondition.kt`:

```kotlin
package com.chamchamcham.application.community

import com.chamchamcham.domain.community.CommunityPostType
import java.time.LocalDateTime
import java.util.UUID

data class CommunityPostSearchCondition(
    val memberId: UUID,
    val cropId: UUID?,
    val postType: CommunityPostType?,
    val keyword: String?,
    val likedOnly: Boolean,
    val mineOnly: Boolean,
    val cursorCreatedAt: LocalDateTime?,
    val cursorId: UUID?,
    val size: Int
)
```

- [ ] **Step 4: Implement CommunityPostService**

Create service with these public methods:

```kotlin
fun listBoards(memberId: UUID): List<CommunityPostResult.Board>
fun search(condition: CommunityPostSearchCondition): CommunityPostResult.Page
fun getDetail(memberId: UUID, postId: UUID): CommunityPostResult.PostDetail
fun create(command: CommunityPostCommand.Create): CommunityPostResult.PostId
fun update(command: CommunityPostCommand.Update): CommunityPostResult.PostId
fun delete(command: CommunityPostCommand.Delete)
fun toggleLike(command: CommunityPostCommand.ToggleLike): CommunityPostResult.LikeToggle
```

Implement private helpers:

```kotlin
private fun validateMedia(memberId: UUID, mediaIds: List<UUID>): List<UploadedMedia>
private fun resolveFarmingRecord(memberId: UUID, cropId: UUID, farmingRecordId: UUID?): FarmingRecord?
private fun assertAuthor(post: CommunityPost, memberId: UUID)
```

Validation details:

- `mediaIds.size > 5` throws `COMMUNITY_TOO_MANY_IMAGES`.
- Missing crop throws `CROP_NOT_FOUND`.
- Missing member throws `MEMBER_NOT_FOUND`.
- Missing post or deleted post throws `COMMUNITY_POST_NOT_FOUND`.
- Non-author update/delete throws `COMMUNITY_FORBIDDEN`.
- Farming record lookup uses `farmingRecordRepository.findByIdAndMember_Id`.
- Missing owned farming record throws `FARMING_RECORD_NOT_FOUND`.
- Farming record crop mismatch throws `COMMUNITY_FARMING_RECORD_CROP_MISMATCH`.
- Each media must exist, be owned by member, have `usageType = COMMUNITY_POST`, and be attachable.
- `listBoards` uses `memberCropRepository.findByMember_Id(memberId)` and de-duplicates by `crop.id`.
- `search` maps `CommunityPostSearchCondition` to `CommunityPostQueryRepository.SearchCondition` and maps rows to `PostSummary`.
- `getDetail` loads a non-deleted post, ordered post media, comment count, like count, and `likedByMe`.

- [ ] **Step 5: Run post service tests**

Run:

```bash
cd backend && ./gradlew :application:test --tests "com.chamchamcham.application.community.CommunityPostServiceTest"
```

Expected: tests pass.

- [ ] **Step 6: Commit post service**

```bash
git add backend/application/src/main/kotlin/com/chamchamcham/application/community backend/application/src/test/kotlin/com/chamchamcham/application/community/CommunityPostServiceTest.kt
git commit -m "feat(community): 게시글 작성 수정 삭제 좋아요 토글 추가" \
  -m "게시글 생성과 수정에서 crop, 영농기록, 미디어 소유권을 검증하고 삭제와 좋아요 토글 유스케이스를 추가한다." \
  -m "Constraint: 좋아요는 별도 like/unlike가 아닌 토글 API로 제공" \
  -m "Confidence: high" \
  -m "Scope-risk: broad" \
  -m "Tested: ./gradlew :application:test --tests \"com.chamchamcham.application.community.CommunityPostServiceTest\""
```

---

## Task 7: Community Comment Application Service

**Files:**
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/community/CommunityCommentCommand.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/community/CommunityCommentResult.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/community/CommunityCommentService.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/community/CommunityCommentServiceTest.kt`

- [ ] **Step 1: Write failing comment service tests**

Create `CommunityCommentServiceTest.kt` with Mockito like `OnboardingServiceTest`. Define fixed ids for `memberId`, `otherMemberId`, `postId`, `rootCommentId`, and `replyCommentId`. Add these tests with the listed assertions:

```kotlin
@Test
fun `create root comment stores comment on active post`() {
    val result = service.create(createCommand(parentCommentId = null, body = "저도 궁금해요"))

    assertEquals(rootCommentId, result.id)
    val savedComment = capturedComment()
    assertEquals(postId, savedComment.post.id)
    assertNull(savedComment.parentComment)
    assertEquals(memberId, savedComment.author.id)
    assertEquals("저도 궁금해요", savedComment.body)
}

@Test
fun `create reply stores one level reply under root comment`() {
    val result = service.create(createCommand(parentCommentId = rootCommentId, body = "스크래치 작업을 해보세요"))

    assertEquals(replyCommentId, result.id)
    val savedComment = capturedComment()
    assertEquals(rootCommentId, savedComment.parentComment?.id)
    assertEquals("스크래치 작업을 해보세요", savedComment.body)
}

@Test
fun `create reply rejects reply parent that is already a reply`() {
    val exception = assertThrows(BusinessException::class.java) {
        service.create(createCommand(parentCommentId = replyCommentId))
    }

    assertEquals(ErrorCode.COMMUNITY_INVALID_REPLY_PARENT, exception.errorCode)
    verify(communityCommentRepository, never()).save(any(CommunityComment::class.java))
}

@Test
fun `create reply rejects deleted parent comment`() {
    val exception = assertThrows(BusinessException::class.java) {
        service.create(createCommand(parentCommentId = rootCommentId))
    }

    assertEquals(ErrorCode.COMMUNITY_INVALID_REPLY_PARENT, exception.errorCode)
    verify(communityCommentRepository, never()).save(any(CommunityComment::class.java))
}

@Test
fun `delete soft deletes author comment`() {
    service.delete(CommunityCommentCommand.Delete(memberId = memberId, commentId = rootCommentId))

    assertTrue(rootComment.isDeleted)
}

@Test
fun `list returns deleted comment body as deleted message`() {
    val comments = service.list(postId)

    assertEquals("삭제된 댓글입니다.", comments.first { it.id == rootCommentId }.body)
    assertTrue(comments.first { it.id == rootCommentId }.deleted)
}
```

- [ ] **Step 2: Run comment service tests and verify failure**

Run:

```bash
cd backend && ./gradlew :application:test --tests "com.chamchamcham.application.community.CommunityCommentServiceTest"
```

Expected: compilation fails because comment service classes do not exist.

- [ ] **Step 3: Add comment command/result/service**

Create `CommunityCommentCommand.kt`:

```kotlin
package com.chamchamcham.application.community

import java.util.UUID

object CommunityCommentCommand {
    data class Create(
        val memberId: UUID,
        val postId: UUID,
        val parentCommentId: UUID?,
        val body: String
    )

    data class Delete(
        val memberId: UUID,
        val commentId: UUID
    )
}
```

Create `CommunityCommentResult.kt`:

```kotlin
package com.chamchamcham.application.community

import java.time.LocalDateTime
import java.util.UUID

object CommunityCommentResult {
    data class CommentId(val id: UUID)

    data class Comment(
        val id: UUID,
        val parentCommentId: UUID?,
        val author: CommunityPostResult.AuthorSummary,
        val body: String,
        val deleted: Boolean,
        val createdAt: LocalDateTime,
        val replies: List<Comment> = emptyList()
    )
}
```

Implement `CommunityCommentService` with:

```kotlin
fun create(command: CommunityCommentCommand.Create): CommunityCommentResult.CommentId
fun delete(command: CommunityCommentCommand.Delete)
fun list(postId: UUID): List<CommunityCommentResult.Comment>
```

The deleted body constant must be:

```kotlin
private const val DELETED_COMMENT_BODY = "삭제된 댓글입니다."
```

- [ ] **Step 4: Run comment service tests**

Run:

```bash
cd backend && ./gradlew :application:test --tests "com.chamchamcham.application.community.CommunityCommentServiceTest"
```

Expected: tests pass.

- [ ] **Step 5: Commit comment service**

```bash
git add backend/application/src/main/kotlin/com/chamchamcham/application/community/CommunityCommentCommand.kt backend/application/src/main/kotlin/com/chamchamcham/application/community/CommunityCommentResult.kt backend/application/src/main/kotlin/com/chamchamcham/application/community/CommunityCommentService.kt backend/application/src/test/kotlin/com/chamchamcham/application/community/CommunityCommentServiceTest.kt
git commit -m "feat(community): 댓글과 1단계 대댓글 유스케이스 추가" \
  -m "댓글 작성, 1단계 대댓글 제한, 삭제 댓글 표시, 댓글 소프트 삭제 규칙을 application 서비스에 추가한다." \
  -m "Constraint: 댓글 수정과 채택 답변은 1차 범위에서 제외" \
  -m "Confidence: high" \
  -m "Scope-risk: moderate" \
  -m "Tested: ./gradlew :application:test --tests \"com.chamchamcham.application.community.CommunityCommentServiceTest\""
```

---

## Task 8: Community Query Repository

**Files:**
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/community/CommunityPostQueryRepository.kt`
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/community/CommunityPostQueryRepositoryImpl.kt`
- Test: `backend/domain/src/test/kotlin/com/chamchamcham/domain/community/CommunityPostQueryRepositoryTest.kt`

- [ ] **Step 1: Write failing query repository tests**

Create `CommunityPostQueryRepositoryTest.kt` with `@DataJpaTest`. Persist members, crops, posts, likes, comments, and media. Add these tests with the listed assertions:

```kotlin
@Test
fun `search returns active posts latest first with cursor`() {
    val firstPage = queryRepository.search(condition(size = 2))
    val secondPage = queryRepository.search(
        condition(
            size = 2,
            cursorCreatedAt = firstPage.rows.last().post.createdAt,
            cursorId = requireNotNull(firstPage.rows.last().post.id)
        )
    )

    assertThat(firstPage.rows.map { it.post.title }).containsExactly("최신 글", "중간 글")
    assertThat(firstPage.rows.map { it.post.title }).doesNotContain("삭제된 글")
    assertThat(secondPage.rows.map { it.post.title }).containsExactly("오래된 글")
}

@Test
fun `search filters by crop post type and keyword`() {
    val result = queryRepository.search(
        condition(
            cropId = hwanggiCropId,
            postType = CommunityPostType.QUESTION,
            keyword = "발아"
        )
    )

    assertThat(result.rows.map { it.post.title }).containsExactly("황기 발아율 질문")
    assertThat(result.rows.all { it.post.crop.id == hwanggiCropId }).isTrue()
    assertThat(result.rows.all { it.post.postType == CommunityPostType.QUESTION }).isTrue()
}

@Test
fun `search filters likedOnly and mineOnly`() {
    val liked = queryRepository.search(condition(likedOnly = true))
    val mine = queryRepository.search(condition(mineOnly = true))

    assertThat(liked.rows.map { it.post.title }).containsExactly("내가 좋아요한 글")
    assertThat(liked.rows.first().likedByMe).isTrue()
    assertThat(mine.rows.map { it.post.author.id }).containsOnly(memberId)
}
```

- [ ] **Step 2: Run query tests and verify failure**

Run:

```bash
cd backend && ./gradlew :domain:test --tests "com.chamchamcham.domain.community.CommunityPostQueryRepositoryTest"
```

Expected: compilation fails because query repository classes do not exist.

- [ ] **Step 3: Add query repository contract**

Create `CommunityPostQueryRepository.kt`:

```kotlin
package com.chamchamcham.domain.community

import java.time.LocalDateTime
import java.util.UUID

interface CommunityPostQueryRepository {
    fun search(condition: SearchCondition): SearchResult

    data class SearchCondition(
        val memberId: UUID,
        val cropId: UUID?,
        val postType: CommunityPostType?,
        val keyword: String?,
        val likedOnly: Boolean,
        val mineOnly: Boolean,
        val cursorCreatedAt: LocalDateTime?,
        val cursorId: UUID?,
        val size: Int
    )

    data class Row(
        val post: CommunityPost,
        val thumbnailUrl: String?,
        val commentCount: Long,
        val likeCount: Long,
        val likedByMe: Boolean
    )

    data class SearchResult(
        val rows: List<Row>
    )
}
```

- [ ] **Step 4: Add custom JPA implementation**

Create `CommunityPostQueryRepositoryImpl.kt` using `EntityManager` and JPQL. Use two queries:

1. Main post query with optional filters and cursor.
2. Batch aggregate queries for thumbnail, comment counts, like counts, and liked ids.

The main query must:

```text
where p.isDeleted = false
and (:cropId is null or p.crop.id = :cropId)
and (:postType is null or p.postType = :postType)
and (:keyword is null or lower(p.title) like :keyword or lower(p.body) like :keyword)
and (:mineOnly = false or p.author.id = :memberId)
and (:likedOnly = false or exists select like row for member)
and cursor condition for latest order
order by p.createdAt desc, p.id desc
```

Cursor condition:

```text
p.createdAt < :cursorCreatedAt
or (p.createdAt = :cursorCreatedAt and p.id < :cursorId)
```

Fetch `size + 1` rows in application service if the service needs to compute next cursor; otherwise return at most `size` rows and compute cursor from the last row.

- [ ] **Step 5: Run query repository tests**

Run:

```bash
cd backend && ./gradlew :domain:test --tests "com.chamchamcham.domain.community.CommunityPostQueryRepositoryTest"
```

Expected: tests pass.

- [ ] **Step 6: Commit query repository**

```bash
git add backend/domain/src/main/kotlin/com/chamchamcham/domain/community/CommunityPostQueryRepository.kt backend/domain/src/main/kotlin/com/chamchamcham/domain/community/CommunityPostQueryRepositoryImpl.kt backend/domain/src/test/kotlin/com/chamchamcham/domain/community/CommunityPostQueryRepositoryTest.kt
git commit -m "feat(community): 게시글 조건 검색 쿼리 경계 추가" \
  -m "커서, 작물, Q&A, 키워드, 좋아요한 글, 내가 쓴 글 조건을 하나의 custom query repository로 묶는다." \
  -m "Rejected: QueryDSL 선도입 | 현재는 JPA custom 구현으로 조건 조합을 검증 가능" \
  -m "Confidence: medium" \
  -m "Scope-risk: moderate" \
  -m "Tested: ./gradlew :domain:test --tests \"com.chamchamcham.domain.community.CommunityPostQueryRepositoryTest\""
```

---

## Task 9: Community API Controller And DTOs

**Files:**
- Create: `backend/api/src/main/kotlin/com/chamchamcham/api/community/controller/CommunityController.kt`
- Create: `backend/api/src/main/kotlin/com/chamchamcham/api/community/dto/CommunityRequests.kt`
- Create: `backend/api/src/main/kotlin/com/chamchamcham/api/community/dto/CommunityResponses.kt`
- Test: `backend/api/src/test/kotlin/com/chamchamcham/api/community/controller/CommunityControllerTest.kt`

- [ ] **Step 1: Write failing controller tests**

Create `CommunityControllerTest.kt` with `@WebMvcTest(CommunityController::class)`, `@AutoConfigureMockMvc(addFilters = false)`, and mocked `CommunityPostService` plus `CommunityCommentService`. Add these tests:

```kotlin
@Test
fun `create post maps request to command and returns post id`() {
    `when`(communityPostService.create(createPostCommand())).thenReturn(CommunityPostResult.PostId(postId))

    mockMvc.perform(post("/api/v1/community/posts").with(authenticatedMember(memberId.toString())).contentType(MediaType.APPLICATION_JSON).content(savePostJson()))
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.data.id", equalTo(postId.toString())))
}

@Test
fun `list posts returns cursor page`() {
    `when`(communityPostService.search(anySearchCondition())).thenReturn(postPageResult())

    mockMvc.perform(get("/api/v1/community/posts").with(authenticatedMember(memberId.toString())).param("size", "20"))
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.data.items[0].id", equalTo(postId.toString())))
        .andExpect(jsonPath("$.data.items[0].likedByMe", equalTo(true)))
}

@Test
fun `get post detail returns detail`() {
    `when`(communityPostService.getDetail(memberId, postId)).thenReturn(postDetailResult())

    mockMvc.perform(get("/api/v1/community/posts/{postId}", postId).with(authenticatedMember(memberId.toString())))
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.data.title", equalTo("황기 발아율 질문")))
}

@Test
fun `update post maps request to command`() {
    `when`(communityPostService.update(updatePostCommand())).thenReturn(CommunityPostResult.PostId(postId))

    mockMvc.perform(patch("/api/v1/community/posts/{postId}", postId).with(authenticatedMember(memberId.toString())).contentType(MediaType.APPLICATION_JSON).content(savePostJson()))
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.data.id", equalTo(postId.toString())))
}

@Test
fun `delete post delegates to service`() {
    mockMvc.perform(delete("/api/v1/community/posts/{postId}", postId).with(authenticatedMember(memberId.toString())))
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.success", equalTo(true)))
}

@Test
fun `create comment maps request to command`() {
    `when`(communityCommentService.create(createCommentCommand())).thenReturn(CommunityCommentResult.CommentId(commentId))

    mockMvc.perform(post("/api/v1/community/posts/{postId}/comments", postId).with(authenticatedMember(memberId.toString())).contentType(MediaType.APPLICATION_JSON).content("""{"body":"저도 궁금해요"}"""))
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.data.id", equalTo(commentId.toString())))
}

@Test
fun `list comments returns deleted comment body from service result`() {
    `when`(communityCommentService.list(postId)).thenReturn(listOf(deletedCommentResult()))

    mockMvc.perform(get("/api/v1/community/posts/{postId}/comments", postId).with(authenticatedMember(memberId.toString())))
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.data[0].body", equalTo("삭제된 댓글입니다.")))
}

@Test
fun `toggle like returns liked state and count`() {
    `when`(communityPostService.toggleLike(CommunityPostCommand.ToggleLike(memberId, postId))).thenReturn(CommunityPostResult.LikeToggle(liked = true, likeCount = 9))

    mockMvc.perform(post("/api/v1/community/posts/{postId}/like-toggle", postId).with(authenticatedMember(memberId.toString())))
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.data.liked", equalTo(true)))
        .andExpect(jsonPath("$.data.likeCount", equalTo(9)))
}
```

Support the tests with helper methods in the same test file: `authenticatedMember(memberId)`, `savePostJson()`, `createPostCommand()`, `updatePostCommand()`, `createCommentCommand()`, `postPageResult()`, `postDetailResult()`, `deletedCommentResult()`, and `anySearchCondition()`.

- [ ] **Step 2: Run community controller tests and verify failure**

Run:

```bash
cd backend && ./gradlew :api:test --tests "com.chamchamcham.api.community.controller.CommunityControllerTest"
```

Expected: compilation fails because API classes do not exist.

- [ ] **Step 3: Add request DTOs**

Create `CommunityRequests.kt`:

```kotlin
package com.chamchamcham.api.community.dto

import com.chamchamcham.domain.community.CommunityPostType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.util.UUID

object CommunityRequests {
    data class SavePostRequest(
        @field:NotNull(message = "작물을 선택해주세요")
        val cropId: UUID?,
        @field:NotNull(message = "게시글 유형을 선택해주세요")
        val postType: CommunityPostType?,
        @field:NotBlank(message = "제목을 입력해주세요")
        @field:Size(max = 50, message = "제목은 50자 이하여야 합니다")
        val title: String,
        @field:NotBlank(message = "본문을 입력해주세요")
        val body: String,
        val farmingRecordId: UUID? = null,
        @field:Size(max = 5, message = "사진은 최대 5장까지 첨부할 수 있습니다")
        val mediaIds: List<UUID> = emptyList()
    )

    data class CreateCommentRequest(
        val parentCommentId: UUID? = null,
        @field:NotBlank(message = "댓글 내용을 입력해주세요")
        val body: String
    )
}
```

- [ ] **Step 4: Add response DTOs**

Create `CommunityResponses.kt` mapping all result objects to API response objects. Include:

```kotlin
data class PostIdResponse(val id: UUID)
data class LikeToggleResponse(val liked: Boolean, val likeCount: Long)
data class BoardResponse(val cropId: UUID, val cropName: String)
data class PostPageResponse(val items: List<PostSummaryResponse>, val nextCursorCreatedAt: LocalDateTime?, val nextCursorId: UUID?)
data class PostSummaryResponse(
    val id: UUID,
    val cropId: UUID,
    val cropName: String,
    val postType: CommunityPostType,
    val title: String,
    val bodyPreview: String,
    val thumbnailUrl: String?,
    val author: AuthorResponse,
    val commentCount: Long,
    val likeCount: Long,
    val likedByMe: Boolean,
    val createdAt: LocalDateTime
)
data class PostDetailResponse(
    val id: UUID,
    val cropId: UUID,
    val cropName: String,
    val postType: CommunityPostType,
    val title: String,
    val body: String,
    val imageUrls: List<String>,
    val farmingRecordId: UUID?,
    val author: AuthorResponse,
    val commentCount: Long,
    val likeCount: Long,
    val likedByMe: Boolean,
    val createdAt: LocalDateTime
)
data class CommentResponse(
    val id: UUID,
    val parentCommentId: UUID?,
    val author: AuthorResponse,
    val body: String,
    val deleted: Boolean,
    val createdAt: LocalDateTime,
    val replies: List<CommentResponse>
)
```

Each companion `from` method must copy fields directly from the corresponding `CommunityPostResult` or `CommunityCommentResult`.

- [ ] **Step 5: Add CommunityController**

Create controller with mappings:

```kotlin
@GetMapping("/boards")
fun listBoards(@AuthenticationPrincipal memberId: String?): ResponseEntity<ApiResponse<List<BoardResponse>>>

@GetMapping("/posts")
fun listPosts(
    @AuthenticationPrincipal memberId: String?,
    @RequestParam cropId: UUID?,
    @RequestParam postType: CommunityPostType?,
    @RequestParam keyword: String?,
    @RequestParam(defaultValue = "false") likedOnly: Boolean,
    @RequestParam(defaultValue = "false") mineOnly: Boolean,
    @RequestParam cursorCreatedAt: LocalDateTime?,
    @RequestParam cursorId: UUID?,
    @RequestParam(defaultValue = "20") size: Int
): ResponseEntity<ApiResponse<PostPageResponse>>

@PostMapping("/posts")
fun createPost(
    @AuthenticationPrincipal memberId: String?,
    @Valid @RequestBody request: SavePostRequest
): ResponseEntity<ApiResponse<PostIdResponse>>

@GetMapping("/posts/{postId}")
fun getPost(
    @AuthenticationPrincipal memberId: String?,
    @PathVariable postId: UUID
): ResponseEntity<ApiResponse<PostDetailResponse>>

@PatchMapping("/posts/{postId}")
fun updatePost(
    @AuthenticationPrincipal memberId: String?,
    @PathVariable postId: UUID,
    @Valid @RequestBody request: SavePostRequest
): ResponseEntity<ApiResponse<PostIdResponse>>

@DeleteMapping("/posts/{postId}")
fun deletePost(
    @AuthenticationPrincipal memberId: String?,
    @PathVariable postId: UUID
): ResponseEntity<ApiResponse<Unit>>

@GetMapping("/posts/{postId}/comments")
fun listComments(
    @PathVariable postId: UUID
): ResponseEntity<ApiResponse<List<CommentResponse>>>

@PostMapping("/posts/{postId}/comments")
fun createComment(
    @AuthenticationPrincipal memberId: String?,
    @PathVariable postId: UUID,
    @Valid @RequestBody request: CreateCommentRequest
): ResponseEntity<ApiResponse<CommentIdResponse>>

@DeleteMapping("/comments/{commentId}")
fun deleteComment(
    @AuthenticationPrincipal memberId: String?,
    @PathVariable commentId: UUID
): ResponseEntity<ApiResponse<Unit>>

@PostMapping("/posts/{postId}/like-toggle")
fun toggleLike(
    @AuthenticationPrincipal memberId: String?,
    @PathVariable postId: UUID
): ResponseEntity<ApiResponse<LikeToggleResponse>>
```

Use a private `parseMemberId(memberId: String?): UUID` helper identical in behavior to `AuthController`.

- [ ] **Step 6: Run community controller tests**

Run:

```bash
cd backend && ./gradlew :api:test --tests "com.chamchamcham.api.community.controller.CommunityControllerTest"
```

Expected: tests pass.

- [ ] **Step 7: Commit community API**

```bash
git add backend/api/src/main/kotlin/com/chamchamcham/api/community backend/api/src/test/kotlin/com/chamchamcham/api/community/controller/CommunityControllerTest.kt
git commit -m "feat(community): 커뮤니티 HTTP API 추가" \
  -m "게시글 목록, 상세, 작성, 수정, 삭제, 댓글, 좋아요 토글 API를 application 서비스에 연결한다." \
  -m "Constraint: @PreAuthorize 없이 기존 JWT 인증 principal과 서비스 검증을 사용" \
  -m "Confidence: high" \
  -m "Scope-risk: broad" \
  -m "Tested: ./gradlew :api:test --tests \"com.chamchamcham.api.community.controller.CommunityControllerTest\""
```

---

## Task 10: Full Verification And Cleanup

**Files:**
- Review all files changed by Tasks 1-9.

- [ ] **Step 1: Run focused application and API tests**

Run:

```bash
cd backend && ./gradlew :domain:test :application:test :api:test
```

Expected: all tests pass.

- [ ] **Step 2: Run compile for all backend modules**

Run:

```bash
cd backend && ./gradlew :domain:compileKotlin :application:compileKotlin :api:compileKotlin :batch:compileKotlin
```

Expected: all compile tasks pass.

- [ ] **Step 3: Search for removed concepts and forbidden names**

Run:

```bash
rg -n "acceptedAnswer|accepted_answer|CommunityPostStatus|author_user_id|userId|users" backend/domain/src/main/kotlin backend/application/src/main/kotlin backend/api/src/main/kotlin
```

Expected:

- No `acceptedAnswer`, `accepted_answer`, or `CommunityPostStatus` references.
- No new project-owned `userId` or `users` naming for community code.

- [ ] **Step 4: Check git diff**

Run:

```bash
git status --short
git diff --stat
```

Expected:

- Only intended community/media/auth/profile files are modified or added.
- Existing unrelated untracked local files remain unstaged.

- [ ] **Step 5: Final commit for verification-only cleanup when needed**

If Step 1-4 required small fixes, stage only those concrete files shown by `git status --short`:

```bash
git add backend/domain/src/main/kotlin backend/application/src/main/kotlin backend/api/src/main/kotlin backend/domain/src/test/kotlin backend/application/src/test/kotlin backend/api/src/test/kotlin
git commit -m "fix(community): 커뮤니티 구현 검증 오류 수정" \
  -m "전체 테스트와 컴파일 검증에서 드러난 누락을 보완한다." \
  -m "Confidence: high" \
  -m "Scope-risk: narrow" \
  -m "Tested: ./gradlew :domain:test :application:test :api:test" \
  -m "Tested: ./gradlew :domain:compileKotlin :application:compileKotlin :api:compileKotlin :batch:compileKotlin"
```

Skip this commit when there are no fixes after verification.
