package com.chamchamcham.api.farming

import com.chamchamcham.ApiApplication
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.application.farming.FarmingRecordCommand
import com.chamchamcham.application.farming.FarmingRecordService
import com.chamchamcham.application.report.FarmingCycleReportProjectionService
import com.chamchamcham.domain.coaching.recordfeedback.RecordFeedbackRepository
import com.chamchamcham.domain.crop.Crop
import com.chamchamcham.domain.crop.CropRepository
import com.chamchamcham.domain.crop.CropUsePartCategory
import com.chamchamcham.domain.crop.MemberCrop
import com.chamchamcham.domain.crop.MemberCropRepository
import com.chamchamcham.domain.farm.Farm
import com.chamchamcham.domain.farm.FarmRepository
import com.chamchamcham.domain.farming.FarmingRecordMediaRepository
import com.chamchamcham.domain.farming.FarmingRecordRepository
import com.chamchamcham.domain.farming.IrrigationAmount
import com.chamchamcham.domain.farming.IrrigationMethod
import com.chamchamcham.domain.farming.WateringRecordRepository
import com.chamchamcham.domain.farming.WorkType
import com.chamchamcham.domain.media.UploadedMedia
import com.chamchamcham.domain.media.UploadedMediaRepository
import com.chamchamcham.domain.media.UploadedMediaStatus
import com.chamchamcham.domain.media.UploadedMediaType
import com.chamchamcham.domain.media.UploadedMediaUsageType
import com.chamchamcham.domain.member.Member
import com.chamchamcham.domain.member.MemberRepository
import com.chamchamcham.domain.report.FarmingCycleReportRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDateTime
import java.util.UUID

@SpringBootTest(
    classes = [ApiApplication::class],
    properties = [
        "spring.datasource.url=jdbc:h2:mem:farming-record-media-sync;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;INIT=CREATE DOMAIN IF NOT EXISTS JSONB AS JSON",
    ],
)
@ActiveProfiles("test")
class FarmingRecordMediaSyncIntegrationTest @Autowired constructor(
    private val farmingRecordService: FarmingRecordService,
    private val farmingRecordRepository: FarmingRecordRepository,
    private val wateringRecordRepository: WateringRecordRepository,
    private val farmingRecordMediaRepository: FarmingRecordMediaRepository,
    private val reportRepository: FarmingCycleReportRepository,
    private val uploadedMediaRepository: UploadedMediaRepository,
    private val memberRepository: MemberRepository,
    private val memberCropRepository: MemberCropRepository,
    private val farmRepository: FarmRepository,
    private val cropRepository: CropRepository,
    private val recordFeedbackRepository: RecordFeedbackRepository,
) {
    @MockBean
    private lateinit var projectionService: FarmingCycleReportProjectionService

    private lateinit var member: Member
    private lateinit var farm: Farm
    private lateinit var crop: Crop

    private val memberId: UUID
        get() = requireNotNull(member.id) { "Persisted member id is required" }
    private val farmId: UUID
        get() = requireNotNull(farm.id) { "Persisted farm id is required" }
    private val cropId: UUID
        get() = requireNotNull(crop.id) { "Persisted crop id is required" }

    @BeforeEach
    fun setUp() {
        recordFeedbackRepository.deleteAll()
        reportRepository.deleteAllInBatch()
        farmingRecordMediaRepository.deleteAllInBatch()
        wateringRecordRepository.deleteAllInBatch()
        farmingRecordRepository.deleteAllInBatch()
        uploadedMediaRepository.deleteAllInBatch()
        memberCropRepository.deleteAllInBatch()
        farmRepository.deleteAllInBatch()
        cropRepository.deleteAllInBatch()
        memberRepository.deleteAllInBatch()

        member = memberRepository.save(Member(email = "media-sync-member@example.com", passwordHash = null))
        farm = farmRepository.save(Farm(owner = member, name = "미디어싱크농장", roadAddress = "충청북도 제천시"))
        crop = cropRepository.save(
            Crop(externalNo = 7003, name = "감초", usePartCategory = CropUsePartCategory.ROOT_BARK),
        )
        memberCropRepository.save(MemberCrop(member = member, farm = farm, crop = crop))
    }

    @Test
    fun `resending the same media id on update does not violate the unique constraint`() {
        val media = newMedia()
        val recordId = requireNotNull(
            farmingRecordService.create(wateringCommand(mediaIds = listOf(mediaId(media)))).id
        )

        farmingRecordService.update(
            updateCommand(recordId = recordId, mediaIds = listOf(mediaId(media)))
        )

        val rows = farmingRecordMediaRepository.findByRecord_Id(recordId)
        assertThat(rows).hasSize(1)
        assertThat(rows.single().uploadedMedia.id).isEqualTo(mediaId(media))
        assertThat(refreshed(media).status).isEqualTo(UploadedMediaStatus.ATTACHED)
    }

    @Test
    fun `dropping a media id on update marks it deleted and keeps the retained one`() {
        val kept = newMedia()
        val dropped = newMedia()
        val recordId = requireNotNull(
            farmingRecordService.create(
                wateringCommand(mediaIds = listOf(mediaId(kept), mediaId(dropped)))
            ).id
        )

        farmingRecordService.update(
            updateCommand(recordId = recordId, mediaIds = listOf(mediaId(kept)))
        )

        val rows = farmingRecordMediaRepository.findByRecord_Id(recordId)
        assertThat(rows).hasSize(1)
        assertThat(rows.single().uploadedMedia.id).isEqualTo(mediaId(kept))
        assertThat(refreshed(kept).status).isEqualTo(UploadedMediaStatus.ATTACHED)
        assertThat(refreshed(dropped).status).isEqualTo(UploadedMediaStatus.DELETED)
    }

    @Test
    fun `update rejects duplicate media ids in the same request`() {
        val media = newMedia()
        val recordId = requireNotNull(farmingRecordService.create(wateringCommand(mediaIds = emptyList())).id)

        assertThatThrownBy {
            farmingRecordService.update(
                updateCommand(recordId = recordId, mediaIds = listOf(mediaId(media), mediaId(media)))
            )
        }
            .isInstanceOf(BusinessException::class.java)
            .satisfies({ ex -> assertThat((ex as BusinessException).errorCode).isEqualTo(ErrorCode.INVALID_INPUT) })
    }

    @Test
    fun `update rejects media already attached to a different record`() {
        val otherMedia = newMedia()
        val otherRecordId = requireNotNull(
            farmingRecordService.create(wateringCommand(mediaIds = listOf(mediaId(otherMedia)))).id
        )
        val recordId = requireNotNull(farmingRecordService.create(wateringCommand(mediaIds = emptyList())).id)
        assertThat(otherRecordId).isNotEqualTo(recordId)

        assertThatThrownBy {
            farmingRecordService.update(
                updateCommand(recordId = recordId, mediaIds = listOf(mediaId(otherMedia)))
            )
        }
            .isInstanceOf(BusinessException::class.java)
            .satisfies({ ex -> assertThat((ex as BusinessException).errorCode).isEqualTo(ErrorCode.MEDIA_NOT_ATTACHABLE) })
    }

    private fun newMedia(): UploadedMedia = uploadedMediaRepository.save(
        UploadedMedia(
            owner = member,
            mediaType = UploadedMediaType.IMAGE,
            usageType = UploadedMediaUsageType.FARMING_RECORD,
            fileUrl = "https://example.com/farming-record-${UUID.randomUUID()}.jpg",
            cloudinaryPublicId = "farming-record-test-${UUID.randomUUID()}",
        ),
    )

    private fun mediaId(media: UploadedMedia): UUID = requireNotNull(media.id) { "Persisted media id is required" }

    private fun refreshed(media: UploadedMedia): UploadedMedia =
        uploadedMediaRepository.findById(mediaId(media)).orElseThrow()

    private fun wateringCommand(mediaIds: List<UUID>) = FarmingRecordCommand.Create(
        memberId = memberId,
        farmId = farmId,
        cropId = cropId,
        workType = WorkType.WATERING,
        workedAt = LocalDateTime.of(2026, 4, 1, 8, 30),
        weatherCondition = "맑음",
        weatherTemperature = 18,
        memo = "미디어 싱크 검증용 관수 기록입니다",
        watering = FarmingRecordCommand.WateringDetail(
            irrigationAmount = IrrigationAmount.NORMAL,
            irrigationMethod = IrrigationMethod.DRIP,
        ),
        mediaIds = mediaIds,
    )

    private fun updateCommand(recordId: UUID, mediaIds: List<UUID>) = FarmingRecordCommand.Update(
        memberId = memberId,
        recordId = recordId,
        farmId = farmId,
        cropId = cropId,
        workType = WorkType.WATERING,
        workedAt = LocalDateTime.of(2026, 4, 1, 8, 30),
        weatherCondition = "맑음",
        weatherTemperature = 19,
        memo = "미디어 싱크 검증용 관수 기록 수정입니다",
        watering = FarmingRecordCommand.WateringDetail(
            irrigationAmount = IrrigationAmount.NORMAL,
            irrigationMethod = IrrigationMethod.DRIP,
        ),
        mediaIds = mediaIds,
    )
}
