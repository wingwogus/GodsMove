package com.chamchamcham.application.report

import com.chamchamcham.application.common.OpaqueCursorCodec
import com.chamchamcham.domain.common.BaseTimeEntity
import com.chamchamcham.domain.crop.Crop
import com.chamchamcham.domain.crop.CropUsePartCategory
import com.chamchamcham.domain.farm.Farm
import com.chamchamcham.domain.farming.EntryMode
import com.chamchamcham.domain.farming.FarmingRecord
import com.chamchamcham.domain.farming.FarmingWorkReportSourceRepository
import com.chamchamcham.domain.farming.FarmingWorkReportSourceSnapshot
import com.chamchamcham.domain.farming.WorkType
import com.chamchamcham.domain.member.Member
import com.chamchamcham.domain.report.FarmingCycleReportQueryRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class FarmingWorkReportQueryServiceTest {
    private val memberId = id("001")
    private val farmId = id("101")
    private val cropId = id("201")
    private val reportId = id("301")
    private val finalHarvestId = id("406")
    private val baseTime = LocalDateTime.of(2026, 1, 1, 9, 0)
    private val cursorCodec = OpaqueCursorCodec()

    private val member = Member(id = memberId, email = "member@example.com", passwordHash = null)
    private val farm = Farm(id = farmId, owner = member, name = "약초농장", roadAddress = "서울시 강남구")
    private val crop = Crop(
        id = cropId,
        externalNo = 422,
        name = "황기",
        usePartCategory = CropUsePartCategory.ROOT_BARK,
    )

    @Mock private lateinit var queryRepository: FarmingCycleReportQueryRepository
    @Mock private lateinit var sourceRepository: FarmingWorkReportSourceRepository

    private lateinit var service: FarmingWorkReportQueryService

    @BeforeEach
    fun setUp() {
        service = FarmingWorkReportQueryService(
            queryRepository = queryRepository,
            sourceRepository = sourceRepository,
            partitioner = FarmingCyclePartitioner(),
            cursorCodec = cursorCodec,
        )
    }

    @Test
    fun `list uses lookahead and exact cycle thumbnails with latest pictured fallback`() {
        val expectedCondition = FarmingCycleReportQueryRepository.WorkItemSearchCondition(
            memberId = memberId,
            farmId = farmId,
            cropId = cropId,
            workType = null,
            cursor = null,
            size = 3,
        )
        val watering = workItem(WorkType.WATERING, recordCount = 2)
        val harvest = workItem(WorkType.HARVEST, recordCount = 1)
        val lookahead = workItem(WorkType.ETC, recordCount = 1)
        `when`(queryRepository.searchCompletedWorkItems(expectedCondition)).thenReturn(
            FarmingCycleReportQueryRepository.WorkItemSearchResult(listOf(watering, harvest, lookahead)),
        )

        val previousWatering = record("401", WorkType.WATERING, day = 1)
        val previousFinal = record("402", WorkType.HARVEST, day = 2)
        val picturedWatering = record("403", WorkType.WATERING, day = 3, createdMinute = 1)
        val latestWateringWithoutPhoto = record("404", WorkType.WATERING, day = 3, createdMinute = 2)
        val otherWorkType = record("405", WorkType.PRUNING, day = 4)
        val targetFinal = record("406", WorkType.HARVEST, day = 5)
        val adjacentWatering = record("407", WorkType.WATERING, day = 6)
        val adjacentFinal = record("408", WorkType.HARVEST, day = 7)
        `when`(sourceRepository.load(memberId, setOf(farmId), setOf(cropId))).thenReturn(
            FarmingWorkReportSourceSnapshot(
                records = listOf(
                    adjacentFinal,
                    latestWateringWithoutPhoto,
                    targetFinal,
                    previousFinal,
                    adjacentWatering,
                    picturedWatering,
                    previousWatering,
                    otherWorkType,
                ),
                finalHarvestRecordIds = setOf(
                    requireNotNull(previousFinal.id),
                    requireNotNull(targetFinal.id),
                    requireNotNull(adjacentFinal.id),
                ),
                firstImageUrlByRecordId = mapOf(
                    requireNotNull(previousWatering.id) to "https://img/previous-cycle.jpg",
                    requireNotNull(picturedWatering.id) to "https://img/watering.jpg",
                    requireNotNull(otherWorkType.id) to "https://img/other-work-type.jpg",
                    requireNotNull(targetFinal.id) to "https://img/harvest.jpg",
                    requireNotNull(adjacentWatering.id) to "https://img/adjacent-cycle.jpg",
                ),
            ),
        )

        val page = service.list(
            FarmingWorkReportSearchCondition(
                memberId = memberId,
                farmId = farmId,
                cropId = cropId,
                workType = null,
                cursor = null,
                size = 2,
            ),
        )

        assertThat(page.items.map { it.workType }).containsExactly(WorkType.WATERING, WorkType.HARVEST)
        assertThat(page.items.first().thumbnailUrl).isEqualTo("https://img/watering.jpg")
        assertThat(page.items.last().thumbnailUrl).isEqualTo("https://img/harvest.jpg")
        val decoded = cursorCodec.decode(
            requireNotNull(page.nextCursor),
            FarmingWorkReportCursorPayload::class.java,
        )
        assertThat(decoded.reportId).isEqualTo(page.items.last().reportId)
        assertThat(decoded.workType).isEqualTo(page.items.last().workType)
        verify(queryRepository).searchCompletedWorkItems(expectedCondition)
        verify(sourceRepository).load(memberId, setOf(farmId), setOf(cropId))
    }

    @Test
    fun `list decodes item cursor and returns null thumbnail without a pictured matching record`() {
        val cursorPayload = FarmingWorkReportCursorPayload(
            endsAt = day(30),
            reportId = id("399"),
            workType = WorkType.WATERING,
        )
        val cursor = cursorCodec.encode(cursorPayload)
        val expectedCondition = FarmingCycleReportQueryRepository.WorkItemSearchCondition(
            memberId = memberId,
            farmId = null,
            cropId = null,
            workType = WorkType.PEST_CONTROL,
            cursor = FarmingCycleReportQueryRepository.WorkItemCursor(
                endsAt = cursorPayload.endsAt,
                reportId = cursorPayload.reportId,
                workType = cursorPayload.workType,
            ),
            size = 2,
        )
        val pestControl = workItem(WorkType.PEST_CONTROL, recordCount = 1)
        `when`(queryRepository.searchCompletedWorkItems(expectedCondition)).thenReturn(
            FarmingCycleReportQueryRepository.WorkItemSearchResult(listOf(pestControl)),
        )
        val matchingRecord = record("405", WorkType.PEST_CONTROL, day = 4)
        val targetFinal = record("406", WorkType.HARVEST, day = 5)
        `when`(sourceRepository.load(memberId, setOf(farmId), setOf(cropId))).thenReturn(
            FarmingWorkReportSourceSnapshot(
                records = listOf(matchingRecord, targetFinal),
                finalHarvestRecordIds = setOf(requireNotNull(targetFinal.id)),
                firstImageUrlByRecordId = emptyMap(),
            ),
        )

        val page = service.list(
            FarmingWorkReportSearchCondition(
                memberId = memberId,
                farmId = null,
                cropId = null,
                workType = WorkType.PEST_CONTROL,
                cursor = cursor,
                size = 1,
            ),
        )

        assertThat(page.items.single().thumbnailUrl).isNull()
        assertThat(page.nextCursor).isNull()
        verify(queryRepository).searchCompletedWorkItems(expectedCondition)
        verify(sourceRepository).load(memberId, setOf(farmId), setOf(cropId))
    }

    @Test
    fun `list isolates thumbnails by exact farm and crop scope from cross product records`() {
        val otherFarm = Farm(
            id = id("102"),
            owner = member,
            name = "다른 농장",
            roadAddress = "서울시 서초구",
        )
        val otherCrop = Crop(
            id = id("202"),
            externalNo = 423,
            name = "감초",
            usePartCategory = CropUsePartCategory.ROOT_BARK,
        )
        val firstFinalId = id("406")
        val secondFinalId = id("416")
        val firstItem = workItem(
            workType = WorkType.WATERING,
            recordCount = 1,
            reportId = id("301"),
            farm = farm,
            crop = crop,
            finalHarvestRecordId = firstFinalId,
        )
        val secondItem = workItem(
            workType = WorkType.WATERING,
            recordCount = 1,
            reportId = id("302"),
            farm = otherFarm,
            crop = otherCrop,
            finalHarvestRecordId = secondFinalId,
        )
        val expectedCondition = FarmingCycleReportQueryRepository.WorkItemSearchCondition(
            memberId = memberId,
            farmId = null,
            cropId = null,
            workType = WorkType.WATERING,
            cursor = null,
            size = 3,
        )
        `when`(queryRepository.searchCompletedWorkItems(expectedCondition)).thenReturn(
            FarmingCycleReportQueryRepository.WorkItemSearchResult(listOf(firstItem, secondItem)),
        )

        val firstUnpictured = record("401", WorkType.WATERING, day = 1)
        val firstFinal = record("406", WorkType.HARVEST, day = 2)
        val secondPictured = record("411", WorkType.WATERING, day = 1, farm = otherFarm, crop = otherCrop)
        val secondFinal = record("416", WorkType.HARVEST, day = 2, farm = otherFarm, crop = otherCrop)
        val temptingCrossProduct = record("421", WorkType.WATERING, day = 3, farm = farm, crop = otherCrop)
        val crossProductFinal = record("426", WorkType.HARVEST, day = 4, farm = farm, crop = otherCrop)
        val farmIds = setOf(requireNotNull(farm.id), requireNotNull(otherFarm.id))
        val cropIds = setOf(requireNotNull(crop.id), requireNotNull(otherCrop.id))
        `when`(sourceRepository.load(memberId, farmIds, cropIds)).thenReturn(
            FarmingWorkReportSourceSnapshot(
                records = listOf(
                    temptingCrossProduct,
                    secondFinal,
                    firstUnpictured,
                    crossProductFinal,
                    secondPictured,
                    firstFinal,
                ),
                finalHarvestRecordIds = setOf(
                    requireNotNull(firstFinal.id),
                    requireNotNull(secondFinal.id),
                    requireNotNull(crossProductFinal.id),
                ),
                firstImageUrlByRecordId = mapOf(
                    requireNotNull(secondPictured.id) to "https://img/second-scope.jpg",
                    requireNotNull(temptingCrossProduct.id) to "https://img/cross-product-newer.jpg",
                ),
            ),
        )

        val page = service.list(
            FarmingWorkReportSearchCondition(
                memberId = memberId,
                farmId = null,
                cropId = null,
                workType = WorkType.WATERING,
                cursor = null,
                size = 2,
            ),
        )

        assertThat(page.items.map { it.thumbnailUrl })
            .containsExactly(null, "https://img/second-scope.jpg")
        assertThat(page.nextCursor).isNull()
        verify(queryRepository).searchCompletedWorkItems(expectedCondition)
        verify(sourceRepository).load(memberId, farmIds, cropIds)
    }

    @Test
    fun `list resolves tied pictured records by created time then record id`() {
        val expectedCondition = FarmingCycleReportQueryRepository.WorkItemSearchCondition(
            memberId = memberId,
            farmId = farmId,
            cropId = cropId,
            workType = WorkType.WATERING,
            cursor = null,
            size = 2,
        )
        val item = workItem(WorkType.WATERING, recordCount = 3)
        `when`(queryRepository.searchCompletedWorkItems(expectedCondition)).thenReturn(
            FarmingCycleReportQueryRepository.WorkItemSearchResult(listOf(item)),
        )
        val higherIdButOlderCreated = record("499", WorkType.WATERING, day = 3, createdMinute = 1)
        val lowerTiedId = record("451", WorkType.WATERING, day = 3, createdMinute = 2)
        val higherTiedId = record("452", WorkType.WATERING, day = 3, createdMinute = 2)
        val targetFinal = record("406", WorkType.HARVEST, day = 5)
        `when`(sourceRepository.load(memberId, setOf(farmId), setOf(cropId))).thenReturn(
            FarmingWorkReportSourceSnapshot(
                records = listOf(targetFinal, higherTiedId, higherIdButOlderCreated, lowerTiedId),
                finalHarvestRecordIds = setOf(requireNotNull(targetFinal.id)),
                firstImageUrlByRecordId = mapOf(
                    requireNotNull(higherIdButOlderCreated.id) to "https://img/higher-id-older-created.jpg",
                    requireNotNull(lowerTiedId.id) to "https://img/lower-tied-id.jpg",
                    requireNotNull(higherTiedId.id) to "https://img/higher-tied-id.jpg",
                ),
            ),
        )

        val page = service.list(
            FarmingWorkReportSearchCondition(
                memberId = memberId,
                farmId = farmId,
                cropId = cropId,
                workType = WorkType.WATERING,
                cursor = null,
                size = 1,
            ),
        )

        assertThat(page.items.single().thumbnailUrl).isEqualTo("https://img/higher-tied-id.jpg")
        verify(queryRepository).searchCompletedWorkItems(expectedCondition)
        verify(sourceRepository).load(memberId, setOf(farmId), setOf(cropId))
    }

    @Test
    fun `empty projection still performs one fixed source load with empty scopes`() {
        val expectedCondition = FarmingCycleReportQueryRepository.WorkItemSearchCondition(
            memberId = memberId,
            farmId = null,
            cropId = null,
            workType = null,
            cursor = null,
            size = 11,
        )
        `when`(queryRepository.searchCompletedWorkItems(expectedCondition)).thenReturn(
            FarmingCycleReportQueryRepository.WorkItemSearchResult(emptyList()),
        )
        `when`(sourceRepository.load(memberId, emptySet(), emptySet())).thenReturn(
            FarmingWorkReportSourceSnapshot(
                records = emptyList(),
                finalHarvestRecordIds = emptySet(),
                firstImageUrlByRecordId = emptyMap(),
            ),
        )

        val page = service.list(
            FarmingWorkReportSearchCondition(
                memberId = memberId,
                farmId = null,
                cropId = null,
                workType = null,
                cursor = null,
                size = 10,
            ),
        )

        assertThat(page.items).isEmpty()
        assertThat(page.nextCursor).isNull()
        verify(queryRepository).searchCompletedWorkItems(expectedCondition)
        verify(sourceRepository).load(memberId, emptySet(), emptySet())
        verifyNoMoreInteractions(sourceRepository)
    }

    private fun workItem(
        workType: WorkType,
        recordCount: Int,
        reportId: UUID = this.reportId,
        farm: Farm = this.farm,
        crop: Crop = this.crop,
        finalHarvestRecordId: UUID = finalHarvestId,
    ): FarmingCycleReportQueryRepository.WorkItem =
        FarmingCycleReportQueryRepository.WorkItem(
            reportId = reportId,
            farmId = requireNotNull(farm.id),
            farmName = farm.name,
            cropId = requireNotNull(crop.id),
            cropName = crop.name,
            startsAt = day(1),
            endsAt = day(5),
            finalHarvestRecordId = finalHarvestRecordId,
            workType = workType,
            recordCount = recordCount,
            lastWorkedOn = LocalDate.of(2026, 1, 5),
        )

    private fun record(
        suffix: String,
        workType: WorkType,
        day: Long,
        createdMinute: Long = 0,
        farm: Farm = this.farm,
        crop: Crop = this.crop,
    ): FarmingRecord =
        FarmingRecord(
            id = id(suffix),
            member = member,
            farm = farm,
            crop = crop,
            workType = workType,
            workedAt = day(day),
            weatherCondition = "맑음",
            weatherTemperature = 20,
            memo = "memo",
            entryMode = EntryMode.MANUAL,
        ).also { entity -> setCreatedAt(entity, day(day).plusMinutes(createdMinute)) }

    private fun setCreatedAt(entity: BaseTimeEntity, createdAt: LocalDateTime) {
        BaseTimeEntity::class.java.getDeclaredField("createdAt").apply {
            isAccessible = true
            set(entity, createdAt)
        }
    }

    private fun day(day: Long): LocalDateTime = baseTime.plusDays(day)

    private fun id(suffix: String): UUID =
        UUID.fromString("00000000-0000-0000-0000-000000000$suffix")
}
