package com.chamchamcham.api.report

import com.chamchamcham.ApiApplication
import com.chamchamcham.application.farming.FarmingRecordCommand
import com.chamchamcham.application.farming.FarmingRecordService
import com.chamchamcham.application.report.FarmingCycleReportProjectionService
import com.chamchamcham.application.report.ReportScope
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
import com.chamchamcham.domain.media.UploadedMediaType
import com.chamchamcham.domain.media.UploadedMediaUsageType
import com.chamchamcham.domain.member.Member
import com.chamchamcham.domain.member.MemberRepository
import com.chamchamcham.domain.report.FarmingCycleReportRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDateTime
import java.util.UUID

@SpringBootTest(
    classes = [ApiApplication::class],
    properties = [
        "spring.datasource.url=jdbc:h2:mem:farming-record-projection-rollback;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;INIT=CREATE DOMAIN IF NOT EXISTS JSONB AS JSON",
    ],
)
@ActiveProfiles("test")
class FarmingRecordProjectionRollbackIntegrationTest @Autowired constructor(
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
) {
    @MockBean
    private lateinit var projectionService: FarmingCycleReportProjectionService

    private lateinit var member: Member
    private lateinit var farm: Farm
    private lateinit var crop: Crop
    private lateinit var media: UploadedMedia

    private val memberId: UUID
        get() = requireNotNull(member.id) { "Persisted member id is required" }
    private val farmId: UUID
        get() = requireNotNull(farm.id) { "Persisted farm id is required" }
    private val cropId: UUID
        get() = requireNotNull(crop.id) { "Persisted crop id is required" }
    private val mediaId: UUID
        get() = requireNotNull(media.id) { "Persisted media id is required" }

    @BeforeEach
    fun setUp() {
        reportRepository.deleteAllInBatch()
        farmingRecordMediaRepository.deleteAllInBatch()
        wateringRecordRepository.deleteAllInBatch()
        farmingRecordRepository.deleteAllInBatch()
        uploadedMediaRepository.deleteAllInBatch()
        memberCropRepository.deleteAllInBatch()
        farmRepository.deleteAllInBatch()
        cropRepository.deleteAllInBatch()
        memberRepository.deleteAllInBatch()

        member = memberRepository.save(Member(email = "rollback-member@example.com", passwordHash = null))
        farm = farmRepository.save(Farm(owner = member, name = "롤백검증농장", roadAddress = "충청북도 제천시"))
        crop = cropRepository.save(
            Crop(externalNo = 7002, name = "감초", usePartCategory = CropUsePartCategory.ROOT_BARK),
        )
        memberCropRepository.save(MemberCrop(member = member, farm = farm, crop = crop))
        media = uploadedMediaRepository.save(
            UploadedMedia(
                owner = member,
                mediaType = UploadedMediaType.IMAGE,
                usageType = UploadedMediaUsageType.FARMING_RECORD,
                fileUrl = "https://example.com/farming-record.jpg",
                cloudinaryPublicId = "farming-record-test",
            ),
        )
    }

    @Test
    fun `projection failure rolls back record detail and media rows`() {
        doThrow(IllegalStateException("projection failed"))
            .`when`(projectionService)
            .rebuild(ReportScope(memberId, farmId, cropId))

        assertThatThrownBy {
            farmingRecordService.create(wateringCommand(withMedia = true))
        }.isInstanceOf(IllegalStateException::class.java)

        assertThat(farmingRecordRepository.findAll()).isEmpty()
        assertThat(wateringRecordRepository.findAll()).isEmpty()
        assertThat(farmingRecordMediaRepository.findAll()).isEmpty()
    }

    private fun wateringCommand(withMedia: Boolean) = FarmingRecordCommand.Create(
        memberId = memberId,
        farmId = farmId,
        cropId = cropId,
        workType = WorkType.WATERING,
        workedAt = LocalDateTime.of(2026, 4, 1, 8, 30),
        weatherCondition = "맑음",
        weatherTemperature = 18,
        memo = "롤백 관수",
        watering = FarmingRecordCommand.WateringDetail(
            irrigationAmount = IrrigationAmount.NORMAL,
            irrigationMethod = IrrigationMethod.MANUAL,
        ),
        mediaIds = if (withMedia) listOf(mediaId) else emptyList(),
    )
}
