# Member Profile Edit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `PUT /api/v1/members/me/profile` so an authenticated member can update basic profile fields, final profile image, requested farms, and each requested farm's final crop list.

**Architecture:** Keep one API endpoint for the mobile save flow. Extend the existing `MemberProfileService` and keep internal sections small: basic profile fields, profile image sync, farm upsert, farm crop sync. Use existing domain repositories and entity methods; do not add new abstraction layers.

**Tech Stack:** Kotlin, Spring Boot, Spring MVC, Spring Data JPA, JUnit 5, Mockito, MockMvc.

## Global Constraints

- Preserve the domain term `member`; do not introduce project-owned `user` naming.
- Keep the API endpoint as `PUT /api/v1/members/me/profile`.
- Do not add farm deletion, farm soft delete, profile history, audit log, Cloudinary physical deletion, `@PreAuthorize`, or new repository abstraction layers.
- `profileMediaId` is the final profile image value: `null` removes, current id keeps, different id replaces with a `PROFILE` `TEMP` image.
- `farms` contains only farms to update or create; omitted existing farms are kept unchanged.
- Each requested farm's `cropIds` is the final crop list for that farm.
- Add no new dependencies.
- Existing dev test source blockers in RAG/dev tests are outside this plan. Do not edit those files for this feature. Verify with main compilation and `git diff --check`; run focused tests when the existing blockers are resolved.

---

## File Structure

- Modify `backend/domain/src/main/kotlin/com/chamchamcham/domain/farm/Farm.kt`
  - Convert editable farm fields from `val` to `var`.
  - Add `updateProfile(...)` to update an existing farm.
- Modify `backend/domain/src/main/kotlin/com/chamchamcham/domain/crop/MemberCropRepository.kt`
  - Add `deleteByMember_IdAndFarm_Id(memberId: UUID, farmId: UUID)`.
- Create `backend/application/src/main/kotlin/com/chamchamcham/application/member/MemberProfileCommand.kt`
  - Define `UpdateMyProfile`, nested `Farm`, `FarmBoundaryCoordinate`, and `FarmDataSource`.
- Modify `backend/application/src/main/kotlin/com/chamchamcham/application/member/MemberProfileService.kt`
  - Inject `CropRepository` and `UploadedMediaRepository`.
  - Add `updateMyProfile(command)`.
  - Keep helper methods for profile image sync, farm upsert, crop loading, crop sync, and validation.
- Modify `backend/application/src/test/kotlin/com/chamchamcham/application/member/MemberProfileServiceTest.kt`
  - Extend constructor setup for new repositories.
  - Add service tests for profile fields, image keep/replace/remove, farm upsert, crop sync, and rejection cases.
- Create `backend/api/src/main/kotlin/com/chamchamcham/api/member/dto/MemberRequests.kt`
  - Define request DTOs and bean validation annotations.
- Modify `backend/api/src/main/kotlin/com/chamchamcham/api/member/controller/MemberController.kt`
  - Add `PUT /api/v1/members/me/profile`.
- Modify `backend/api/src/test/kotlin/com/chamchamcham/api/member/controller/MemberControllerTest.kt`
  - Add MockMvc tests for request mapping and response.

---

### Task 1: Domain Support For Farm And Farm Crop Updates

**Files:**
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/farm/Farm.kt`
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/crop/MemberCropRepository.kt`
- Test: `backend/domain/src/test/kotlin/com/chamchamcham/domain/farm/FarmTest.kt`

**Interfaces:**
- Produces:
  - `Farm.updateProfile(name, roadAddress, jibunAddress, latitude, longitude, pnu, landCategory, areaSqm, areaIsManualEntry, boundaryCoordinates, dataSource): Unit`
  - `MemberCropRepository.deleteByMember_IdAndFarm_Id(memberId: UUID, farmId: UUID): Unit`

- [ ] **Step 1: Write the failing Farm domain test**

Create `backend/domain/src/test/kotlin/com/chamchamcham/domain/farm/FarmTest.kt`:

```kotlin
package com.chamchamcham.domain.farm

import com.chamchamcham.domain.member.Member
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID

class FarmTest {
    @Test
    fun `update profile replaces editable farm fields`() {
        val owner = Member(id = UUID.randomUUID(), email = "owner@example.com", passwordHash = null)
        val farm = Farm(
            id = UUID.randomUUID(),
            owner = owner,
            name = "기존 농장",
            roadAddress = "강원특별자치도 횡성군 기존로 1",
            jibunAddress = "강원특별자치도 횡성군 기존리 1",
            latitude = 37.1,
            longitude = 128.1,
            pnu = "old-pnu",
            landCategory = "전",
            areaSqm = BigDecimal("100.00"),
            areaIsManualEntry = false,
            boundaryCoordinates = mutableListOf(FarmBoundaryCoordinate(37.1, 128.1)),
            dataSource = FarmDataSource(address = "OLD", coordinate = "OLD")
        )

        farm.updateProfile(
            name = "수정 농장",
            roadAddress = "강원특별자치도 평창군 새로 2",
            jibunAddress = null,
            latitude = 37.2,
            longitude = 128.2,
            pnu = "new-pnu",
            landCategory = "답",
            areaSqm = BigDecimal("200.50"),
            areaIsManualEntry = true,
            boundaryCoordinates = listOf(
                FarmBoundaryCoordinate(37.2, 128.2),
                FarmBoundaryCoordinate(37.3, 128.3)
            ),
            dataSource = FarmDataSource(address = "KAKAO", coordinate = "KAKAO", parcel = "PUBLIC_DATA")
        )

        assertThat(farm.name).isEqualTo("수정 농장")
        assertThat(farm.roadAddress).isEqualTo("강원특별자치도 평창군 새로 2")
        assertThat(farm.jibunAddress).isNull()
        assertThat(farm.latitude).isEqualTo(37.2)
        assertThat(farm.longitude).isEqualTo(128.2)
        assertThat(farm.pnu).isEqualTo("new-pnu")
        assertThat(farm.landCategory).isEqualTo("답")
        assertThat(farm.areaSqm).isEqualByComparingTo("200.50")
        assertThat(farm.areaIsManualEntry).isTrue()
        assertThat(farm.boundaryCoordinates).hasSize(2)
        assertThat(farm.dataSource.address).isEqualTo("KAKAO")
        assertThat(farm.dataSource.coordinate).isEqualTo("KAKAO")
        assertThat(farm.dataSource.parcel).isEqualTo("PUBLIC_DATA")
        assertThat(farm.dataSource.landCharacteristic).isNull()
    }
}
```

- [ ] **Step 2: Run domain test to verify current failure**

Run:

```bash
./gradlew :domain:test --tests "com.chamchamcham.domain.farm.FarmTest"
```

Expected before implementation: compile failure because `Farm.updateProfile` does not exist.

- [ ] **Step 3: Implement Farm update support**

Modify `backend/domain/src/main/kotlin/com/chamchamcham/domain/farm/Farm.kt`:

```kotlin
@Column(nullable = false, length = 128)
var name: String,

@Column(name = "road_address", nullable = false, length = 255)
var roadAddress: String,

@Column(name = "jibun_address", length = 255)
var jibunAddress: String? = null,

@Column
var latitude: Double? = null,

@Column
var longitude: Double? = null,

@Column(length = 32)
var pnu: String? = null,

@Column(name = "land_category", length = 64)
var landCategory: String? = null,

@Column(name = "area_sqm", precision = 12, scale = 2)
var areaSqm: BigDecimal? = null,

@Column(name = "area_is_manual_entry", nullable = false)
var areaIsManualEntry: Boolean = false,
```

Replace the class tail with:

```kotlin
    @Embedded
    var dataSource: FarmDataSource = FarmDataSource()
) : BaseTimeEntity() {
    fun updateProfile(
        name: String,
        roadAddress: String,
        jibunAddress: String?,
        latitude: Double?,
        longitude: Double?,
        pnu: String?,
        landCategory: String?,
        areaSqm: BigDecimal?,
        areaIsManualEntry: Boolean,
        boundaryCoordinates: List<FarmBoundaryCoordinate>,
        dataSource: FarmDataSource
    ) {
        this.name = name
        this.roadAddress = roadAddress
        this.jibunAddress = jibunAddress
        this.latitude = latitude
        this.longitude = longitude
        this.pnu = pnu
        this.landCategory = landCategory
        this.areaSqm = areaSqm
        this.areaIsManualEntry = areaIsManualEntry
        this.boundaryCoordinates.clear()
        this.boundaryCoordinates.addAll(boundaryCoordinates)
        this.dataSource = dataSource
    }
}
```

- [ ] **Step 4: Add farm crop delete repository method**

Modify `backend/domain/src/main/kotlin/com/chamchamcham/domain/crop/MemberCropRepository.kt`:

```kotlin
interface MemberCropRepository : JpaRepository<MemberCrop, UUID> {
    fun countByMember_Id(memberId: UUID): Long
    fun findByMember_Id(memberId: UUID): List<MemberCrop>
    fun deleteByMember_IdAndFarm_Id(memberId: UUID, farmId: UUID)
}
```

- [ ] **Step 5: Run domain verification**

Run:

```bash
./gradlew :domain:test --tests "com.chamchamcham.domain.farm.FarmTest"
```

Expected: PASS.

- [ ] **Step 6: Commit Task 1**

```bash
git add backend/domain/src/main/kotlin/com/chamchamcham/domain/farm/Farm.kt \
  backend/domain/src/main/kotlin/com/chamchamcham/domain/crop/MemberCropRepository.kt \
  backend/domain/src/test/kotlin/com/chamchamcham/domain/farm/FarmTest.kt
git commit -m "feat(profile): 농장 프로필 수정 도메인 지원"
```

---

### Task 2: Application Profile Update Flow

**Files:**
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/member/MemberProfileCommand.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/member/MemberProfileService.kt`
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/member/MemberProfileServiceTest.kt`

**Interfaces:**
- Consumes:
  - `Farm.updateProfile(...)`
  - `MemberCropRepository.deleteByMember_IdAndFarm_Id(memberId, farmId)`
- Produces:
  - `MemberProfileCommand.UpdateMyProfile`
  - `MemberProfileService.updateMyProfile(command): MemberProfileResult.MyProfile`

- [ ] **Step 1: Create command types**

Create `backend/application/src/main/kotlin/com/chamchamcham/application/member/MemberProfileCommand.kt`:

```kotlin
package com.chamchamcham.application.member

import com.chamchamcham.domain.member.ManagementType
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

object MemberProfileCommand {
    data class UpdateMyProfile(
        val memberId: UUID,
        val name: String,
        val phone: String,
        val birthDate: LocalDate,
        val nickname: String,
        val experienceLevel: Int,
        val managementType: ManagementType,
        val profileMediaId: UUID?,
        val farms: List<Farm>
    )

    data class Farm(
        val farmId: UUID?,
        val name: String,
        val roadAddress: String,
        val jibunAddress: String?,
        val latitude: Double?,
        val longitude: Double?,
        val pnu: String?,
        val landCategory: String?,
        val areaSqm: BigDecimal?,
        val areaIsManualEntry: Boolean,
        val boundaryCoordinates: List<FarmBoundaryCoordinate>,
        val dataSource: FarmDataSource,
        val cropIds: List<UUID>
    )

    data class FarmBoundaryCoordinate(
        val latitude: Double,
        val longitude: Double
    )

    data class FarmDataSource(
        val address: String?,
        val coordinate: String?,
        val parcel: String?,
        val landCharacteristic: String?
    )
}
```

- [ ] **Step 2: Update test setup with new repository mocks**

Modify imports in `MemberProfileServiceTest.kt`:

```kotlin
import com.chamchamcham.domain.crop.CropRepository
import com.chamchamcham.domain.media.UploadedMediaRepository
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import java.math.BigDecimal
```

Add mocks:

```kotlin
@Mock private lateinit var cropRepository: CropRepository
@Mock private lateinit var uploadedMediaRepository: UploadedMediaRepository
```

Update service construction:

```kotlin
service = MemberProfileService(
    memberRepository = memberRepository,
    farmRepository = farmRepository,
    memberCropRepository = memberCropRepository,
    cropRepository = cropRepository,
    uploadedMediaRepository = uploadedMediaRepository
)
```

- [ ] **Step 3: Write failing application tests**

Add these tests to `MemberProfileServiceTest.kt`:

```kotlin
@Test
fun `update my profile changes basic fields keeps current image updates farm and replaces farm crops`() {
    val currentProfileMedia = requireNotNull(member.profileMedia)
    val command = updateCommand(
        profileMediaId = currentProfileMedia.id,
        farms = listOf(updateFarmCommand(farmId, listOf(cropId, secondCropId)))
    )
    `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))
    `when`(farmRepository.findById(farmId)).thenReturn(Optional.of(farm))
    `when`(cropRepository.findAllById(listOf(cropId, secondCropId))).thenReturn(listOf(crop, secondCrop))
    `when`(memberCropRepository.findByMember_Id(memberId)).thenReturn(
        listOf(memberCrop(farm, crop), memberCrop(farm, secondCrop))
    )
    `when`(farmRepository.findByOwnerId(memberId)).thenReturn(listOf(farm))

    val profile = service.updateMyProfile(command)

    assertEquals("수정 이름", member.name)
    assertEquals("010-2000-0002", member.phone)
    assertEquals(LocalDate.of(1990, 4, 5), member.birthDate)
    assertEquals("수정닉", member.nickname)
    assertEquals(7, member.experienceLevel)
    assertEquals(ManagementType.NON_REGISTERED_FARMER, member.managementType)
    assertEquals(UploadedMediaStatus.ATTACHED, currentProfileMedia.status)
    assertEquals("수정 농장", farm.name)
    assertEquals("강원특별자치도 평창군 새로 2", farm.roadAddress)
    verify(memberCropRepository).deleteByMember_IdAndFarm_Id(memberId, farmId)
    assertThat(capturedMemberCrops().map { it.crop.id }).containsExactly(cropId, secondCropId)
    assertEquals(memberId, profile.memberId)
}

@Test
fun `update my profile replaces profile image and deletes previous image`() {
    val previousMedia = requireNotNull(member.profileMedia)
    val newMediaId = UUID.fromString("00000000-0000-0000-0000-000000000302")
    val newMedia = profileMedia(member, newMediaId, status = UploadedMediaStatus.TEMP)
    `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))
    `when`(uploadedMediaRepository.findById(newMediaId)).thenReturn(Optional.of(newMedia))
    `when`(farmRepository.findById(farmId)).thenReturn(Optional.of(farm))
    `when`(cropRepository.findAllById(listOf(cropId))).thenReturn(listOf(crop))
    `when`(memberCropRepository.findByMember_Id(memberId)).thenReturn(listOf(memberCrop(farm, crop)))
    `when`(farmRepository.findByOwnerId(memberId)).thenReturn(listOf(farm))

    service.updateMyProfile(updateCommand(profileMediaId = newMediaId, farms = listOf(updateFarmCommand(farmId))))

    assertEquals(UploadedMediaStatus.DELETED, previousMedia.status)
    assertEquals(UploadedMediaStatus.ATTACHED, newMedia.status)
    assertEquals(newMedia, member.profileMedia)
}

@Test
fun `update my profile removes profile image when profile media id is null`() {
    val previousMedia = requireNotNull(member.profileMedia)
    `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))
    `when`(farmRepository.findById(farmId)).thenReturn(Optional.of(farm))
    `when`(cropRepository.findAllById(listOf(cropId))).thenReturn(listOf(crop))
    `when`(memberCropRepository.findByMember_Id(memberId)).thenReturn(listOf(memberCrop(farm, crop)))
    `when`(farmRepository.findByOwnerId(memberId)).thenReturn(listOf(farm))

    service.updateMyProfile(updateCommand(profileMediaId = null, farms = listOf(updateFarmCommand(farmId))))

    assertEquals(UploadedMediaStatus.DELETED, previousMedia.status)
    assertEquals(null, member.profileMedia)
}

@Test
fun `update my profile adds new farm and keeps omitted existing farms`() {
    val newFarmId = UUID.fromString("00000000-0000-0000-0000-000000000103")
    val savedFarm = farm(newFarmId, roadAddress = "강원특별자치도 평창군 새로 2")
    `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))
    `when`(farmRepository.save(any(Farm::class.java))).thenReturn(savedFarm)
    `when`(cropRepository.findAllById(listOf(secondCropId))).thenReturn(listOf(secondCrop))
    `when`(memberCropRepository.findByMember_Id(memberId)).thenReturn(listOf(memberCrop(savedFarm, secondCrop)))
    `when`(farmRepository.findByOwnerId(memberId)).thenReturn(listOf(farm, savedFarm))

    service.updateMyProfile(updateCommand(profileMediaId = requireNotNull(member.profileMedia).id, farms = listOf(updateFarmCommand(null, listOf(secondCropId)))))

    verify(farmRepository, never()).delete(any(Farm::class.java))
    verify(memberCropRepository).deleteByMember_IdAndFarm_Id(memberId, newFarmId)
    assertThat(capturedMemberCrops().map { it.crop.id }).containsExactly(secondCropId)
}

@Test
fun `update my profile rejects another member farm`() {
    val otherMember = Member(id = otherMemberId, email = "other@example.com", passwordHash = null)
    val otherFarm = Farm(id = farmId, owner = otherMember, name = "남의 농장", roadAddress = "서울특별시 강남구")
    `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))
    `when`(farmRepository.findById(farmId)).thenReturn(Optional.of(otherFarm))

    val exception = assertThrows(BusinessException::class.java) {
        service.updateMyProfile(updateCommand(profileMediaId = requireNotNull(member.profileMedia).id, farms = listOf(updateFarmCommand(farmId))))
    }

    assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.errorCode)
    verifyNoInteractions(cropRepository)
}

@Test
fun `update my profile rejects missing crop`() {
    `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))
    `when`(farmRepository.findById(farmId)).thenReturn(Optional.of(farm))
    `when`(cropRepository.findAllById(listOf(cropId))).thenReturn(emptyList())

    val exception = assertThrows(BusinessException::class.java) {
        service.updateMyProfile(updateCommand(profileMediaId = requireNotNull(member.profileMedia).id, farms = listOf(updateFarmCommand(farmId))))
    }

    assertEquals(ErrorCode.CROP_NOT_FOUND, exception.errorCode)
}

@Test
fun `update my profile rejects another member profile image`() {
    val otherMediaId = UUID.fromString("00000000-0000-0000-0000-000000000302")
    val otherMember = Member(id = otherMemberId, email = "other@example.com", passwordHash = null)
    val otherMedia = profileMedia(otherMember, otherMediaId, status = UploadedMediaStatus.TEMP)
    `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))
    `when`(uploadedMediaRepository.findById(otherMediaId)).thenReturn(Optional.of(otherMedia))

    val exception = assertThrows(BusinessException::class.java) {
        service.updateMyProfile(updateCommand(profileMediaId = otherMediaId, farms = listOf(updateFarmCommand(farmId))))
    }

    assertEquals(ErrorCode.MEDIA_NOT_OWNED, exception.errorCode)
}
```

Add helper fields and helper functions:

```kotlin
private val otherMemberId = UUID.fromString("00000000-0000-0000-0000-000000000002")

private fun updateCommand(
    profileMediaId: UUID?,
    farms: List<MemberProfileCommand.Farm>
): MemberProfileCommand.UpdateMyProfile =
    MemberProfileCommand.UpdateMyProfile(
        memberId = memberId,
        name = "수정 이름",
        phone = "010-2000-0002",
        birthDate = LocalDate.of(1990, 4, 5),
        nickname = "수정닉",
        experienceLevel = 7,
        managementType = ManagementType.NON_REGISTERED_FARMER,
        profileMediaId = profileMediaId,
        farms = farms
    )

private fun updateFarmCommand(
    farmId: UUID?,
    cropIds: List<UUID> = listOf(cropId)
): MemberProfileCommand.Farm =
    MemberProfileCommand.Farm(
        farmId = farmId,
        name = "수정 농장",
        roadAddress = "강원특별자치도 평창군 새로 2",
        jibunAddress = null,
        latitude = 37.2,
        longitude = 128.2,
        pnu = "new-pnu",
        landCategory = "답",
        areaSqm = BigDecimal("200.50"),
        areaIsManualEntry = true,
        boundaryCoordinates = listOf(MemberProfileCommand.FarmBoundaryCoordinate(37.2, 128.2)),
        dataSource = MemberProfileCommand.FarmDataSource("KAKAO", "KAKAO", "PUBLIC_DATA", null),
        cropIds = cropIds
    )

private fun profileMedia(
    owner: Member,
    id: UUID,
    usageType: UploadedMediaUsageType = UploadedMediaUsageType.PROFILE,
    status: UploadedMediaStatus = UploadedMediaStatus.ATTACHED
): UploadedMedia =
    UploadedMedia(
        id = id,
        owner = owner,
        mediaType = UploadedMediaType.IMAGE,
        usageType = usageType,
        fileUrl = "https://example.test/$id.jpg",
        cloudinaryPublicId = "profile/$id",
        status = status
    )
```

Update existing `profileMedia(owner: Member)` helper to delegate:

```kotlin
private fun profileMedia(owner: Member): UploadedMedia =
    profileMedia(owner, mediaId, status = UploadedMediaStatus.ATTACHED)
```

Add a captor helper:

```kotlin
private fun capturedMemberCrops(): List<MemberCrop> {
    @Suppress("UNCHECKED_CAST")
    val captor = ArgumentCaptor.forClass(Iterable::class.java) as ArgumentCaptor<Iterable<MemberCrop>>
    verify(memberCropRepository).saveAll(captor.capture())
    return captor.value.toList()
}
```

- [ ] **Step 4: Run application test to capture expected blocker**

Run:

```bash
./gradlew :application:test --tests "com.chamchamcham.application.member.MemberProfileServiceTest"
```

Expected in current dev: FAIL before reaching these tests because existing `DevRagSeedServiceTest` test sources do not compile. If that unrelated blocker has been fixed, expected before implementation is compile/test failure because `updateMyProfile` is not implemented.

- [ ] **Step 5: Implement MemberProfileService update flow**

Modify constructor in `MemberProfileService.kt`:

```kotlin
class MemberProfileService(
    private val memberRepository: MemberRepository,
    private val farmRepository: FarmRepository,
    private val memberCropRepository: MemberCropRepository,
    private val cropRepository: CropRepository,
    private val uploadedMediaRepository: UploadedMediaRepository
)
```

Add imports:

```kotlin
import com.chamchamcham.domain.crop.Crop
import com.chamchamcham.domain.crop.CropRepository
import com.chamchamcham.domain.farm.FarmBoundaryCoordinate
import com.chamchamcham.domain.farm.FarmDataSource
import com.chamchamcham.domain.media.UploadedMedia
import com.chamchamcham.domain.media.UploadedMediaRepository
import com.chamchamcham.domain.media.UploadedMediaUsageType
```

Change class transaction annotation:

```kotlin
@Service
@Transactional
class MemberProfileService(...)
```

Add `@Transactional(readOnly = true)` to `getMyProfile` and `getPublicProfile`.

Add this method:

```kotlin
fun updateMyProfile(command: MemberProfileCommand.UpdateMyProfile): MemberProfileResult.MyProfile {
    validateUpdateCommand(command)
    val member = findMember(command.memberId)

    member.completeOnboarding(
        name = command.name,
        phone = command.phone,
        birthDate = command.birthDate,
        nickname = command.nickname,
        experienceLevel = command.experienceLevel,
        managementType = command.managementType
    )
    syncProfileImage(member, command.profileMediaId)

    command.farms.forEach { farmCommand ->
        val farm = upsertFarm(member, farmCommand)
        syncFarmCrops(member, farm, farmCommand.cropIds)
    }

    return getMyProfile(command.memberId)
}
```

Add helper methods:

```kotlin
private fun validateUpdateCommand(command: MemberProfileCommand.UpdateMyProfile) {
    if (command.name.isBlank() || command.phone.isBlank() || command.nickname.isBlank()) {
        throw BusinessException(ErrorCode.INVALID_INPUT)
    }
    if (command.experienceLevel !in 0..100 || command.farms.isEmpty()) {
        throw BusinessException(ErrorCode.INVALID_INPUT)
    }
    val requestedFarmIds = command.farms.mapNotNull { it.farmId }
    if (requestedFarmIds.size != requestedFarmIds.toSet().size) {
        throw BusinessException(ErrorCode.INVALID_INPUT)
    }
    command.farms.forEach { farm ->
        if (farm.name.isBlank() || farm.roadAddress.isBlank() || farm.latitude == null || farm.longitude == null) {
            throw BusinessException(ErrorCode.INVALID_INPUT)
        }
        if (farm.areaSqm != null && farm.areaSqm.signum() <= 0) {
            throw BusinessException(ErrorCode.INVALID_INPUT)
        }
        if (farm.cropIds.isEmpty()) {
            throw BusinessException(ErrorCode.INVALID_INPUT)
        }
    }
}

private fun syncProfileImage(member: Member, profileMediaId: UUID?) {
    val currentMedia = member.profileMedia
    if (profileMediaId == null) {
        currentMedia?.markDeleted()
        member.updateProfileMedia(null)
        return
    }
    if (currentMedia?.id == profileMediaId) {
        return
    }

    val newMedia = uploadedMediaRepository.findById(profileMediaId).orElseThrow {
        BusinessException(ErrorCode.MEDIA_NOT_FOUND)
    }
    validateProfileMedia(member, newMedia)
    currentMedia?.markDeleted()
    newMedia.markAttached()
    member.updateProfileMedia(newMedia)
}

private fun validateProfileMedia(member: Member, media: UploadedMedia) {
    if (media.owner.id != member.id) {
        throw BusinessException(ErrorCode.MEDIA_NOT_OWNED)
    }
    if (media.usageType != UploadedMediaUsageType.PROFILE) {
        throw BusinessException(ErrorCode.MEDIA_USAGE_MISMATCH)
    }
    if (!media.isAttachable()) {
        throw BusinessException(ErrorCode.MEDIA_NOT_ATTACHABLE)
    }
}

private fun upsertFarm(member: Member, command: MemberProfileCommand.Farm): Farm {
    val boundaryCoordinates = command.boundaryCoordinates.map {
        FarmBoundaryCoordinate(latitude = it.latitude, longitude = it.longitude)
    }
    val dataSource = FarmDataSource(
        address = command.dataSource.address,
        coordinate = command.dataSource.coordinate,
        parcel = command.dataSource.parcel,
        landCharacteristic = command.dataSource.landCharacteristic
    )

    if (command.farmId == null) {
        return farmRepository.save(
            Farm(
                owner = member,
                name = command.name,
                roadAddress = command.roadAddress,
                jibunAddress = command.jibunAddress,
                latitude = command.latitude,
                longitude = command.longitude,
                pnu = command.pnu,
                landCategory = command.landCategory,
                areaSqm = command.areaSqm,
                areaIsManualEntry = command.areaIsManualEntry,
                boundaryCoordinates = boundaryCoordinates.toMutableList(),
                dataSource = dataSource
            )
        )
    }

    val farm = farmRepository.findById(command.farmId).orElseThrow {
        BusinessException(ErrorCode.RESOURCE_NOT_FOUND)
    }
    if (farm.owner.id != member.id) {
        throw BusinessException(ErrorCode.RESOURCE_NOT_FOUND)
    }
    farm.updateProfile(
        name = command.name,
        roadAddress = command.roadAddress,
        jibunAddress = command.jibunAddress,
        latitude = command.latitude,
        longitude = command.longitude,
        pnu = command.pnu,
        landCategory = command.landCategory,
        areaSqm = command.areaSqm,
        areaIsManualEntry = command.areaIsManualEntry,
        boundaryCoordinates = boundaryCoordinates,
        dataSource = dataSource
    )
    return farm
}

private fun syncFarmCrops(member: Member, farm: Farm, cropIds: List<UUID>) {
    val distinctCropIds = cropIds.distinct()
    val crops = loadCrops(distinctCropIds)
    val farmId = requireNotNull(farm.id) { "Persisted farm id is required" }
    val memberId = requireNotNull(member.id) { "Persisted member id is required" }

    memberCropRepository.deleteByMember_IdAndFarm_Id(memberId, farmId)
    memberCropRepository.saveAll(
        crops.map { crop ->
            MemberCrop(
                member = member,
                farm = farm,
                crop = crop
            )
        }
    )
}

private fun loadCrops(cropIds: List<UUID>): List<Crop> {
    val cropsById = cropRepository.findAllById(cropIds)
        .associateBy { requireNotNull(it.id) { "Persisted crop id is required" } }
    if (cropsById.size != cropIds.size) {
        throw BusinessException(ErrorCode.CROP_NOT_FOUND)
    }
    return cropIds.map { cropsById.getValue(it) }
}
```

- [ ] **Step 6: Run application main compile**

Run:

```bash
./gradlew :application:compileKotlin
```

Expected: PASS.

- [ ] **Step 7: Run focused application test command**

Run:

```bash
./gradlew :application:test --tests "com.chamchamcham.application.member.MemberProfileServiceTest"
```

Expected in current dev: still FAIL due existing RAG test source compilation blockers. Confirm there are no new compile errors from `MemberProfileServiceTest` in the output. If RAG blockers are gone, expected: PASS.

- [ ] **Step 8: Commit Task 2**

```bash
git add backend/application/src/main/kotlin/com/chamchamcham/application/member/MemberProfileCommand.kt \
  backend/application/src/main/kotlin/com/chamchamcham/application/member/MemberProfileService.kt \
  backend/application/src/test/kotlin/com/chamchamcham/application/member/MemberProfileServiceTest.kt
git commit -m "feat(profile): 마이 프로필 수정 서비스 추가"
```

---

### Task 3: API Request DTO And Controller Endpoint

**Files:**
- Create: `backend/api/src/main/kotlin/com/chamchamcham/api/member/dto/MemberRequests.kt`
- Modify: `backend/api/src/main/kotlin/com/chamchamcham/api/member/controller/MemberController.kt`
- Modify: `backend/api/src/test/kotlin/com/chamchamcham/api/member/controller/MemberControllerTest.kt`

**Interfaces:**
- Consumes:
  - `MemberProfileCommand.UpdateMyProfile`
  - `MemberProfileService.updateMyProfile(command)`
- Produces:
  - `MemberRequests.UpdateMyProfileRequest`
  - `PUT /api/v1/members/me/profile`

- [ ] **Step 1: Create API request DTOs**

Create `backend/api/src/main/kotlin/com/chamchamcham/api/member/dto/MemberRequests.kt`:

```kotlin
package com.chamchamcham.api.member.dto

import com.chamchamcham.domain.member.ManagementType
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

object MemberRequests {
    data class UpdateMyProfileRequest(
        @field:NotBlank(message = "이름을 입력해주세요")
        val name: String,
        @field:NotBlank(message = "전화번호를 입력해주세요")
        val phone: String,
        @field:NotNull(message = "생년월일을 입력해주세요")
        val birthDate: LocalDate?,
        @field:NotBlank(message = "닉네임을 입력해주세요")
        val nickname: String,
        @field:NotNull(message = "경험 수준을 입력해주세요")
        @field:Min(value = 0, message = "경험 수준은 0 이상이어야 합니다")
        @field:Max(value = 100, message = "경험 수준은 100 이하여야 합니다")
        val experienceLevel: Int?,
        @field:NotNull(message = "경영 형태를 입력해주세요")
        val managementType: ManagementType?,
        val profileMediaId: UUID? = null,
        @field:Valid
        @field:NotEmpty(message = "농장 정보를 하나 이상 입력해주세요")
        val farms: List<FarmRequest>
    )

    data class FarmRequest(
        val farmId: UUID? = null,
        @field:NotBlank(message = "농장 이름을 입력해주세요")
        val name: String,
        @field:NotBlank(message = "도로명 주소를 입력해주세요")
        val roadAddress: String,
        val jibunAddress: String? = null,
        @field:NotNull(message = "위도를 입력해주세요")
        val latitude: Double?,
        @field:NotNull(message = "경도를 입력해주세요")
        val longitude: Double?,
        val pnu: String? = null,
        val landCategory: String? = null,
        @field:DecimalMin(value = "0.0", inclusive = false, message = "면적은 0보다 커야 합니다")
        val areaSqm: BigDecimal? = null,
        val areaIsManualEntry: Boolean = false,
        @field:Valid
        val boundaryCoordinates: List<FarmBoundaryCoordinateRequest> = emptyList(),
        @field:Valid
        val dataSource: FarmDataSourceRequest = FarmDataSourceRequest(),
        @field:NotEmpty(message = "작물을 하나 이상 선택해주세요")
        val cropIds: List<UUID>
    )

    data class FarmBoundaryCoordinateRequest(
        @field:NotNull(message = "경계 위도를 입력해주세요")
        val latitude: Double?,
        @field:NotNull(message = "경계 경도를 입력해주세요")
        val longitude: Double?
    )

    data class FarmDataSourceRequest(
        val address: String? = null,
        val coordinate: String? = null,
        val parcel: String? = null,
        val landCharacteristic: String? = null
    )
}
```

- [ ] **Step 2: Add controller test for PUT mapping**

Add imports to `MemberControllerTest.kt`:

```kotlin
import com.chamchamcham.application.member.MemberProfileCommand
import com.chamchamcham.domain.member.ManagementType
import org.mockito.Mockito.verify
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
```

Add test:

```kotlin
@Test
fun `update my profile maps request to command and returns updated profile`() {
    val command = updateMyProfileCommand()
    `when`(memberProfileService.updateMyProfile(command)).thenReturn(myProfileResult())

    mockMvc.perform(
        put("/api/v1/members/me/profile")
            .with(authenticatedMember(memberId.toString()))
            .contentType(MediaType.APPLICATION_JSON)
            .content(updateMyProfileJson())
    )
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.data.memberId", equalTo(memberId.toString())))
        .andExpect(jsonPath("$.data.name", equalTo("이황기")))
        .andExpect(jsonPath("$.data.farms[0].farmId", equalTo(farmId.toString())))

    verify(memberProfileService).updateMyProfile(command)
}
```

Add helper functions:

```kotlin
private fun updateMyProfileJson(): String =
    """
        {
          "name":"이황기",
          "phone":"010-1000-0001",
          "birthDate":"1986-03-12",
          "nickname":"황기농부",
          "experienceLevel":2,
          "managementType":"AGRICULTURAL_INDIVIDUAL",
          "profileMediaId":null,
          "farms":[
            {
              "farmId":"$farmId",
              "name":"횡성 황기밭",
              "roadAddress":"강원특별자치도 횡성군 둔내면 샘물로 12",
              "jibunAddress":"강원특별자치도 횡성군 둔내면 현천리 101",
              "latitude":37.1,
              "longitude":128.1,
              "pnu":"4273031021101010000",
              "landCategory":"전",
              "areaSqm":1234.5,
              "areaIsManualEntry":false,
              "boundaryCoordinates":[{"latitude":37.1,"longitude":128.1}],
              "dataSource":{"address":"KAKAO","coordinate":"KAKAO","parcel":"PUBLIC_DATA","landCharacteristic":null},
              "cropIds":["$cropId"]
            }
          ]
        }
    """.trimIndent()

private fun updateMyProfileCommand(): MemberProfileCommand.UpdateMyProfile =
    MemberProfileCommand.UpdateMyProfile(
        memberId = memberId,
        name = "이황기",
        phone = "010-1000-0001",
        birthDate = LocalDate.of(1986, 3, 12),
        nickname = "황기농부",
        experienceLevel = 2,
        managementType = ManagementType.AGRICULTURAL_INDIVIDUAL,
        profileMediaId = null,
        farms = listOf(
            MemberProfileCommand.Farm(
                farmId = farmId,
                name = "횡성 황기밭",
                roadAddress = "강원특별자치도 횡성군 둔내면 샘물로 12",
                jibunAddress = "강원특별자치도 횡성군 둔내면 현천리 101",
                latitude = 37.1,
                longitude = 128.1,
                pnu = "4273031021101010000",
                landCategory = "전",
                areaSqm = BigDecimal("1234.5"),
                areaIsManualEntry = false,
                boundaryCoordinates = listOf(MemberProfileCommand.FarmBoundaryCoordinate(37.1, 128.1)),
                dataSource = MemberProfileCommand.FarmDataSource("KAKAO", "KAKAO", "PUBLIC_DATA", null),
                cropIds = listOf(cropId)
            )
        )
    )
```

- [ ] **Step 3: Run API test to capture expected failure**

Run:

```bash
./gradlew :api:test --tests "com.chamchamcham.api.member.controller.MemberControllerTest"
```

Expected in current dev: FAIL before reaching these tests because existing dev controller test sources do not compile. If that unrelated blocker has been fixed, expected before implementation is compile/test failure because the endpoint is not implemented.

- [ ] **Step 4: Implement controller endpoint**

Modify imports in `MemberController.kt`:

```kotlin
import com.chamchamcham.api.member.dto.MemberRequests
import com.chamchamcham.application.member.MemberProfileCommand
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
```

Add method:

```kotlin
@PutMapping("/me/profile")
fun updateMyProfile(
    @AuthenticationPrincipal memberId: String?,
    @Valid @RequestBody request: MemberRequests.UpdateMyProfileRequest
): ResponseEntity<ApiResponse<MemberResponses.MyProfileResponse>> {
    val profile = memberProfileService.updateMyProfile(
        MemberProfileCommand.UpdateMyProfile(
            memberId = parseMemberId(memberId),
            name = request.name,
            phone = request.phone,
            birthDate = requireNotNull(request.birthDate),
            nickname = request.nickname,
            experienceLevel = requireNotNull(request.experienceLevel),
            managementType = requireNotNull(request.managementType),
            profileMediaId = request.profileMediaId,
            farms = request.farms.map { farm ->
                MemberProfileCommand.Farm(
                    farmId = farm.farmId,
                    name = farm.name,
                    roadAddress = farm.roadAddress,
                    jibunAddress = farm.jibunAddress,
                    latitude = farm.latitude,
                    longitude = farm.longitude,
                    pnu = farm.pnu,
                    landCategory = farm.landCategory,
                    areaSqm = farm.areaSqm,
                    areaIsManualEntry = farm.areaIsManualEntry,
                    boundaryCoordinates = farm.boundaryCoordinates.map {
                        MemberProfileCommand.FarmBoundaryCoordinate(
                            latitude = requireNotNull(it.latitude),
                            longitude = requireNotNull(it.longitude)
                        )
                    },
                    dataSource = MemberProfileCommand.FarmDataSource(
                        address = farm.dataSource.address,
                        coordinate = farm.dataSource.coordinate,
                        parcel = farm.dataSource.parcel,
                        landCharacteristic = farm.dataSource.landCharacteristic
                    ),
                    cropIds = farm.cropIds
                )
            }
        )
    )
    return ResponseEntity.ok(ApiResponse.ok(MemberResponses.MyProfileResponse.from(profile)))
}
```

- [ ] **Step 5: Run API main compile**

Run:

```bash
./gradlew :api:compileKotlin
```

Expected: PASS.

- [ ] **Step 6: Run focused API test command**

Run:

```bash
./gradlew :api:test --tests "com.chamchamcham.api.member.controller.MemberControllerTest"
```

Expected in current dev: still FAIL due existing dev test source compilation blockers. Confirm there are no new compile errors from `MemberControllerTest` in the output. If dev blockers are gone, expected: PASS.

- [ ] **Step 7: Commit Task 3**

```bash
git add backend/api/src/main/kotlin/com/chamchamcham/api/member/dto/MemberRequests.kt \
  backend/api/src/main/kotlin/com/chamchamcham/api/member/controller/MemberController.kt \
  backend/api/src/test/kotlin/com/chamchamcham/api/member/controller/MemberControllerTest.kt
git commit -m "feat(profile): 마이 프로필 수정 API 추가"
```

---

### Task 4: Final Verification And Cleanup

**Files:**
- Review: all files modified in Tasks 1-3.

**Interfaces:**
- Verifies:
  - `PUT /api/v1/members/me/profile`
  - `MemberProfileService.updateMyProfile`
  - `Farm.updateProfile`

- [ ] **Step 1: Run main compilation across touched modules**

Run:

```bash
./gradlew :domain:compileKotlin :application:compileKotlin :api:compileKotlin
```

Expected: PASS.

- [ ] **Step 2: Run whitespace check**

Run:

```bash
git diff --check
```

Expected: no output and exit code `0`.

- [ ] **Step 3: Run focused test commands and record current blocker if present**

Run:

```bash
./gradlew :domain:test --tests "com.chamchamcham.domain.farm.FarmTest"
./gradlew :application:test --tests "com.chamchamcham.application.member.MemberProfileServiceTest"
./gradlew :api:test --tests "com.chamchamcham.api.member.controller.MemberControllerTest"
```

Expected:

- Domain focused test should PASS.
- Application/API focused test tasks currently fail in this dev branch if existing RAG/dev test source compilation blockers remain.
- If those blockers remain, do not edit unrelated RAG/dev tests in this feature. Report the exact blocker files in final verification.

- [ ] **Step 4: Review YAGNI constraints**

Check with:

```bash
rg -n "PreAuthorize|softDelete|isDeleted|audit|history|cloudinary.*delete|delete\\(.*Farm|deleteById" backend/api/src/main/kotlin/com/chamchamcham/api/member backend/application/src/main/kotlin/com/chamchamcham/application/member backend/domain/src/main/kotlin/com/chamchamcham/domain/farm
```

Expected: no new farm deletion, soft delete, audit/history, or physical Cloudinary deletion code.

- [ ] **Step 5: Commit final cleanup only if files changed**

If Step 4 finds cleanup edits are needed, make them and commit:

```bash
git add backend/domain/src/main/kotlin/com/chamchamcham/domain/farm/Farm.kt \
  backend/domain/src/main/kotlin/com/chamchamcham/domain/crop/MemberCropRepository.kt \
  backend/domain/src/test/kotlin/com/chamchamcham/domain/farm/FarmTest.kt \
  backend/application/src/main/kotlin/com/chamchamcham/application/member/MemberProfileCommand.kt \
  backend/application/src/main/kotlin/com/chamchamcham/application/member/MemberProfileService.kt \
  backend/application/src/test/kotlin/com/chamchamcham/application/member/MemberProfileServiceTest.kt \
  backend/api/src/main/kotlin/com/chamchamcham/api/member/dto/MemberRequests.kt \
  backend/api/src/main/kotlin/com/chamchamcham/api/member/controller/MemberController.kt \
  backend/api/src/test/kotlin/com/chamchamcham/api/member/controller/MemberControllerTest.kt
git commit -m "refactor(profile): 마이 프로필 수정 구현 정리"
```

If no cleanup edits are needed, do not create an empty commit.
