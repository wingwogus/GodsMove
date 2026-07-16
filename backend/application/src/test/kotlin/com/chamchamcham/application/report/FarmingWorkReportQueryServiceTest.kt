package com.chamchamcham.application.report

import com.chamchamcham.application.common.OpaqueCursorCodec
import com.chamchamcham.application.coaching.reportfeedback.lifecycle.ReportFeedbackDetailResult
import com.chamchamcham.application.coaching.reportfeedback.lifecycle.ReportFeedbackItemResult
import com.chamchamcham.application.coaching.reportfeedback.lifecycle.ReportFeedbackListResult
import com.chamchamcham.application.coaching.reportfeedback.lifecycle.ReportFeedbackQueryService
import com.chamchamcham.application.coaching.reportfeedback.lifecycle.ReportFeedbackResultContent
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.common.BaseTimeEntity
import com.chamchamcham.domain.coaching.reportfeedback.ReportFeedbackStatus
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
import com.chamchamcham.domain.report.CycleReportStatistics
import com.chamchamcham.domain.report.FarmingCycleReport
import com.chamchamcham.domain.report.FarmingCycleReportProjection
import com.chamchamcham.domain.report.FarmingCycleReportRepository
import com.chamchamcham.domain.report.FarmingCycleReportStatus
import com.chamchamcham.domain.report.FarmingCycleStartBasis
import com.chamchamcham.domain.report.WateringStatistics
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
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
    @Mock private lateinit var reportRepository: FarmingCycleReportRepository
    @Mock private lateinit var feedbackQueryService: ReportFeedbackQueryService

    private lateinit var service: FarmingWorkReportQueryService

    @BeforeEach
    fun setUp() {
        service = FarmingWorkReportQueryService(
            queryRepository = queryRepository,
            sourceRepository = sourceRepository,
            partitioner = FarmingCyclePartitioner(),
            cursorCodec = cursorCodec,
            reportRepository = reportRepository,
            feedbackQueryService = feedbackQueryService,
        )
    }

    @Test
    fun `detail returns one typed statistics branch and ready feedback for an owned completed work`() {
        val report = report(
            status = FarmingCycleReportStatus.COMPLETED,
            statistics = CycleReportStatistics(
                watering = WateringStatistics(
                    recordCount = 3,
                    firstWorkedOn = LocalDate.of(2026, 1, 2),
                    lastWorkedOn = LocalDate.of(2026, 1, 4),
                    workedDayCount = 3,
                ),
            ),
        )
        val feedbackContent = ReportFeedbackResultContent(
            summary = "물 주기 작업을 안정적으로 이어갔어요.",
            comparisons = listOf(ReportFeedbackItemResult("직전 재배보다 물 주기 기록이 한 번 늘었어요.")),
            strengths = listOf(ReportFeedbackItemResult("흙 상태를 꾸준히 살폈어요.")),
            improvements = emptyList(),
            nextActions = listOf(ReportFeedbackItemResult("내일 흙을 다시 확인하세요.")),
        )
        `when`(reportRepository.findByIdAndMember_Id(reportId, memberId)).thenReturn(report)
        `when`(feedbackQueryService.get(memberId, reportId)).thenReturn(
            feedbacks(
                feedback(
                    workType = WorkType.WATERING,
                    status = ReportFeedbackStatus.READY,
                    content = feedbackContent,
                ),
            ),
        )

        val detail = service.getDetail(memberId, reportId, WorkType.WATERING)

        assertThat(detail.reportId).isEqualTo(reportId)
        assertThat(detail.status).isEqualTo(FarmingCycleReportStatus.COMPLETED)
        assertThat(detail.workType).isEqualTo(WorkType.WATERING)
        assertThat(detail.workTypeLabel).isEqualTo("물 주기")
        assertThat(detail.farmId).isEqualTo(farmId)
        assertThat(detail.farmName).isEqualTo("약초농장")
        assertThat(detail.cropId).isEqualTo(cropId)
        assertThat(detail.cropName).isEqualTo("황기")
        assertThat(detail.startsAt).isEqualTo(day(1))
        assertThat(detail.endsAt).isEqualTo(day(5))
        assertThat(detail.statistics.common.recordCount).isEqualTo(3)
        assertThat(detail.statistics.watering?.recordCount).isEqualTo(3)
        assertThat(detail.statistics.planting).isNull()
        assertThat(detail.statistics.fertilizing).isNull()
        assertThat(detail.statistics.pestControl).isNull()
        assertThat(detail.statistics.weeding).isNull()
        assertThat(detail.statistics.harvest).isNull()
        val detailFeedback = requireNotNull(detail.feedback)
        assertThat(detailFeedback.status).isEqualTo(ReportFeedbackStatus.READY)
        assertThat(detailFeedback.content).isSameAs(feedbackContent)
        assertThat(detailFeedback.content?.comparisons?.map { it.text })
            .containsExactly("직전 재배보다 물 주기 기록이 한 번 늘었어요.")
        verify(feedbackQueryService).get(memberId, reportId)
    }

    @Test
    fun `detail hides a report outside the member scope`() {
        `when`(reportRepository.findByIdAndMember_Id(reportId, memberId)).thenReturn(null)

        assertDetailError(WorkType.WATERING, ErrorCode.REPORT_NOT_FOUND)

        verifyNoInteractions(feedbackQueryService)
    }

    @Test
    fun `detail returns active statistics without requesting coaching`() {
        `when`(reportRepository.findByIdAndMember_Id(reportId, memberId)).thenReturn(
            report(
                status = FarmingCycleReportStatus.ACTIVE,
                statistics = CycleReportStatistics(
                    watering = WateringStatistics(recordCount = 2, workedDayCount = 2),
                ),
            ),
        )

        val detail = service.getDetail(memberId, reportId, WorkType.WATERING)

        assertThat(detail.status).isEqualTo(FarmingCycleReportStatus.ACTIVE)
        assertThat(detail.endsAt).isNull()
        assertThat(detail.statistics.common.recordCount).isEqualTo(2)
        assertThat(detail.statistics.watering?.workedDayCount).isEqualTo(2)
        assertThat(detail.feedback).isNull()
        verifyNoInteractions(feedbackQueryService)
    }

    @Test
    fun `detail hides superseded reports`() {
        `when`(reportRepository.findByIdAndMember_Id(reportId, memberId)).thenReturn(
            report(FarmingCycleReportStatus.SUPERSEDED),
        )

        assertDetailError(WorkType.WATERING, ErrorCode.REPORT_NOT_FOUND)

        verifyNoInteractions(feedbackQueryService)
    }

    @Test
    fun `detail reports an absent work only after owned completed report validation`() {
        `when`(reportRepository.findByIdAndMember_Id(reportId, memberId)).thenReturn(
            report(
                status = FarmingCycleReportStatus.COMPLETED,
                statistics = CycleReportStatistics.empty(),
            ),
        )

        assertDetailError(WorkType.WATERING, ErrorCode.WORK_REPORT_NOT_FOUND)

        verifyNoInteractions(feedbackQueryService)
    }

    @Test
    fun `detail exposes common only statistics for pruning`() {
        val report = report(
            status = FarmingCycleReportStatus.COMPLETED,
            statistics = CycleReportStatistics(
                pruning = com.chamchamcham.domain.report.CommonOnlyStatistics(
                    recordCount = 2,
                    workedDayCount = 2,
                ),
            ),
        )
        `when`(reportRepository.findByIdAndMember_Id(reportId, memberId)).thenReturn(report)
        `when`(feedbackQueryService.get(memberId, reportId)).thenReturn(feedbacks())

        val detail = service.getDetail(memberId, reportId, WorkType.PRUNING)

        assertThat(detail.workTypeLabel).isEqualTo("가지 정리")
        assertThat(detail.statistics.common.recordCount).isEqualTo(2)
        assertThat(detail.statistics.planting).isNull()
        assertThat(detail.statistics.watering).isNull()
        assertThat(detail.statistics.fertilizing).isNull()
        assertThat(detail.statistics.pestControl).isNull()
        assertThat(detail.statistics.weeding).isNull()
        assertThat(detail.statistics.harvest).isNull()
    }

    @Test
    fun `detail keeps pending failed and transiently absent feedback content null`() {
        val report = report(
            status = FarmingCycleReportStatus.COMPLETED,
            statistics = CycleReportStatistics(
                watering = WateringStatistics(recordCount = 1),
            ),
        )
        `when`(reportRepository.findByIdAndMember_Id(reportId, memberId)).thenReturn(report)

        listOf(ReportFeedbackStatus.PENDING, ReportFeedbackStatus.FAILED).forEach { status ->
            `when`(feedbackQueryService.get(memberId, reportId)).thenReturn(
                feedbacks(feedback(WorkType.WATERING, status, content = null)),
            )

            val detail = service.getDetail(memberId, reportId, WorkType.WATERING)

            val detailFeedback = requireNotNull(detail.feedback)
            assertThat(detailFeedback.status).isEqualTo(status)
            assertThat(detailFeedback.content).isNull()
        }

        `when`(feedbackQueryService.get(memberId, reportId)).thenReturn(feedbacks())

        val transientlyAbsent = service.getDetail(memberId, reportId, WorkType.WATERING)

        val transientFeedback = requireNotNull(transientlyAbsent.feedback)
        assertThat(transientFeedback.status).isEqualTo(ReportFeedbackStatus.PENDING)
        assertThat(transientFeedback.content).isNull()
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
        `when`(queryRepository.searchWorkItems(expectedCondition)).thenReturn(
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
        assertThat(page.items).allMatch { it.status == FarmingCycleReportStatus.COMPLETED }
        assertThat(page.items.first().thumbnailUrl).isEqualTo("https://img/watering.jpg")
        assertThat(page.items.last().thumbnailUrl).isEqualTo("https://img/harvest.jpg")
        val decoded = cursorCodec.decode(
            requireNotNull(page.nextCursor),
            FarmingWorkReportCursorPayload::class.java,
        )
        assertThat(decoded.version).isEqualTo(FarmingWorkReportCursorPayload.CURRENT_VERSION)
        assertThat(decoded.lastWorkedOn).isEqualTo(page.items.last().lastWorkedOn)
        assertThat(decoded.status).isEqualTo(FarmingCycleReportStatus.COMPLETED)
        assertThat(decoded.sortAt).isEqualTo(page.items.last().endsAt)
        assertThat(decoded.reportId).isEqualTo(page.items.last().reportId)
        assertThat(decoded.workType).isEqualTo(page.items.last().workType)
        verify(queryRepository).searchWorkItems(expectedCondition)
        verify(sourceRepository).load(memberId, setOf(farmId), setOf(cropId))
    }

    @Test
    fun `list resolves active cycle thumbnail without a final harvest`() {
        val expectedCondition = FarmingCycleReportQueryRepository.WorkItemSearchCondition(
            memberId = memberId,
            farmId = farmId,
            cropId = cropId,
            workType = WorkType.WATERING,
            cursor = null,
            size = 2,
        )
        val activeItem = workItem(
            workType = WorkType.WATERING,
            recordCount = 2,
            status = FarmingCycleReportStatus.ACTIVE,
            endsAt = null,
            finalHarvestRecordId = null,
        )
        `when`(queryRepository.searchWorkItems(expectedCondition)).thenReturn(
            FarmingCycleReportQueryRepository.WorkItemSearchResult(listOf(activeItem)),
        )
        val picturedWatering = record("403", WorkType.WATERING, day = 3)
        `when`(sourceRepository.load(memberId, setOf(farmId), setOf(cropId))).thenReturn(
            FarmingWorkReportSourceSnapshot(
                records = listOf(picturedWatering),
                finalHarvestRecordIds = emptySet(),
                firstImageUrlByRecordId = mapOf(
                    requireNotNull(picturedWatering.id) to "https://img/active-watering.jpg",
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

        assertThat(page.items.single().status).isEqualTo(FarmingCycleReportStatus.ACTIVE)
        assertThat(page.items.single().endsAt).isNull()
        assertThat(page.items.single().thumbnailUrl).isEqualTo("https://img/active-watering.jpg")
        assertThat(page.nextCursor).isNull()
        verify(queryRepository).searchWorkItems(expectedCondition)
    }

    @Test
    fun `list decodes item cursor and returns null thumbnail without a pictured matching record`() {
        val cursorPayload = FarmingWorkReportCursorPayload(
            version = FarmingWorkReportCursorPayload.CURRENT_VERSION,
            lastWorkedOn = day(29).toLocalDate(),
            status = FarmingCycleReportStatus.ACTIVE,
            sortAt = day(30),
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
                lastWorkedOn = cursorPayload.lastWorkedOn,
                status = cursorPayload.status,
                sortAt = cursorPayload.sortAt,
                reportId = cursorPayload.reportId,
                workType = cursorPayload.workType,
            ),
            size = 2,
        )
        val pestControl = workItem(WorkType.PEST_CONTROL, recordCount = 1)
        `when`(queryRepository.searchWorkItems(expectedCondition)).thenReturn(
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
        verify(queryRepository).searchWorkItems(expectedCondition)
        verify(sourceRepository).load(memberId, setOf(farmId), setOf(cropId))
    }

    @Test
    fun `list rejects a cursor created before last worked ordering`() {
        val legacyCursor = cursorCodec.encode(
            LegacyFarmingWorkReportCursorPayload(
                status = FarmingCycleReportStatus.ACTIVE,
                sortAt = day(30),
                reportId = id("399"),
                workType = WorkType.WATERING,
            ),
        )

        assertThatThrownBy {
            service.list(
                FarmingWorkReportSearchCondition(
                    memberId = memberId,
                    farmId = null,
                    cropId = null,
                    workType = null,
                    cursor = legacyCursor,
                    size = 1,
                ),
            )
        }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.INVALID_CURSOR)
        verifyNoInteractions(queryRepository, sourceRepository)
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
        `when`(queryRepository.searchWorkItems(expectedCondition)).thenReturn(
            FarmingCycleReportQueryRepository.WorkItemSearchResult(listOf(firstItem, secondItem)),
        )

        val firstPictured = record("401", WorkType.WATERING, day = 1)
        val secondPictured = record("411", WorkType.WATERING, day = 1, farm = otherFarm, crop = otherCrop)
        val temptingCrossProduct = record("421", WorkType.WATERING, day = 2, farm = farm, crop = otherCrop)
        val firstFinal = record("406", WorkType.HARVEST, day = 3)
        val secondFinal = record("416", WorkType.HARVEST, day = 3, farm = otherFarm, crop = otherCrop)
        val farmIds = setOf(requireNotNull(farm.id), requireNotNull(otherFarm.id))
        val cropIds = setOf(requireNotNull(crop.id), requireNotNull(otherCrop.id))
        `when`(sourceRepository.load(memberId, farmIds, cropIds)).thenReturn(
            FarmingWorkReportSourceSnapshot(
                records = listOf(
                    secondFinal,
                    temptingCrossProduct,
                    firstPictured,
                    secondPictured,
                    firstFinal,
                ),
                finalHarvestRecordIds = setOf(
                    requireNotNull(firstFinal.id),
                    requireNotNull(secondFinal.id),
                ),
                firstImageUrlByRecordId = mapOf(
                    requireNotNull(firstPictured.id) to "https://img/first-scope.jpg",
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
            .containsExactly("https://img/first-scope.jpg", "https://img/second-scope.jpg")
        assertThat(page.nextCursor).isNull()
        verify(queryRepository).searchWorkItems(expectedCondition)
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
        `when`(queryRepository.searchWorkItems(expectedCondition)).thenReturn(
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
        verify(queryRepository).searchWorkItems(expectedCondition)
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
        `when`(queryRepository.searchWorkItems(expectedCondition)).thenReturn(
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
        verify(queryRepository).searchWorkItems(expectedCondition)
        verify(sourceRepository).load(memberId, emptySet(), emptySet())
        verifyNoMoreInteractions(sourceRepository)
    }

    private fun workItem(
        workType: WorkType,
        recordCount: Int,
        reportId: UUID = this.reportId,
        farm: Farm = this.farm,
        crop: Crop = this.crop,
        status: FarmingCycleReportStatus = FarmingCycleReportStatus.COMPLETED,
        endsAt: LocalDateTime? = day(5),
        finalHarvestRecordId: UUID? = finalHarvestId,
    ): FarmingCycleReportQueryRepository.WorkItem =
        FarmingCycleReportQueryRepository.WorkItem(
            reportId = reportId,
            status = status,
            farmId = requireNotNull(farm.id),
            farmName = farm.name,
            cropId = requireNotNull(crop.id),
            cropName = crop.name,
            startsAt = day(1),
            endsAt = endsAt,
            finalHarvestRecordId = finalHarvestRecordId,
            workType = workType,
            recordCount = recordCount,
            lastWorkedOn = LocalDate.of(2026, 1, 5),
        )

    private fun assertDetailError(workType: WorkType, expected: ErrorCode) {
        assertThatThrownBy { service.getDetail(memberId, reportId, workType) }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(expected)
    }

    private fun report(
        status: FarmingCycleReportStatus,
        statistics: CycleReportStatistics = CycleReportStatistics(
            watering = WateringStatistics(recordCount = 1),
        ),
    ): FarmingCycleReport {
        val sourceStatus = if (status == FarmingCycleReportStatus.ACTIVE) {
            FarmingCycleReportStatus.ACTIVE
        } else {
            FarmingCycleReportStatus.COMPLETED
        }
        return FarmingCycleReport.create(
            member = member,
            farm = farm,
            crop = crop,
            projection = FarmingCycleReportProjection(
                status = sourceStatus,
                startsAt = day(1),
                endsAt = if (sourceStatus == FarmingCycleReportStatus.COMPLETED) day(5) else null,
                startBasis = FarmingCycleStartBasis.FIRST_RECORD,
                finalHarvestRecord = if (sourceStatus == FarmingCycleReportStatus.COMPLETED) {
                    record("406", WorkType.HARVEST, day = 5)
                } else {
                    null
                },
                statisticsSchemaVersion = 1,
                statistics = statistics,
            ),
        ).also { report ->
            org.springframework.test.util.ReflectionTestUtils.setField(report, "id", reportId)
            if (status == FarmingCycleReportStatus.SUPERSEDED) {
                report.supersede()
            }
        }
    }

    private fun feedbacks(vararg feedbacks: ReportFeedbackDetailResult) =
        ReportFeedbackListResult(reportId = reportId, feedbacks = feedbacks.toList())

    private fun feedback(
        workType: WorkType,
        status: ReportFeedbackStatus,
        content: ReportFeedbackResultContent?,
    ) = ReportFeedbackDetailResult(
        feedbackId = id("701"),
        workType = workType,
        status = status,
        inputPrepared = status != ReportFeedbackStatus.PENDING,
        failureCode = if (status == ReportFeedbackStatus.FAILED) "STRUCTURED_OUTPUT_INVALID" else null,
        content = content,
        createdAt = day(6),
        updatedAt = day(6),
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

    private data class LegacyFarmingWorkReportCursorPayload(
        val status: FarmingCycleReportStatus,
        val sortAt: LocalDateTime,
        val reportId: UUID,
        val workType: WorkType,
    )
}
