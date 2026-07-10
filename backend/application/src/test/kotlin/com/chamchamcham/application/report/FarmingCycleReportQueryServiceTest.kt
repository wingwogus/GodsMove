package com.chamchamcham.application.report

import com.chamchamcham.application.common.OpaqueCursorCodec
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.crop.Crop
import com.chamchamcham.domain.crop.CropRepository
import com.chamchamcham.domain.crop.CropUsePartCategory
import com.chamchamcham.domain.farm.Farm
import com.chamchamcham.domain.farm.FarmRepository
import com.chamchamcham.domain.farming.FarmingRecord
import com.chamchamcham.domain.farming.WorkType
import com.chamchamcham.domain.member.Member
import com.chamchamcham.domain.report.CycleReportStatistics
import com.chamchamcham.domain.report.FarmingCycleReport
import com.chamchamcham.domain.report.FarmingCycleReportProjection
import com.chamchamcham.domain.report.FarmingCycleReportQueryRepository
import com.chamchamcham.domain.report.FarmingCycleReportRepository
import com.chamchamcham.domain.report.FarmingCycleReportStatus
import com.chamchamcham.domain.report.FarmingCycleStartBasis
import com.chamchamcham.domain.report.WateringStatistics
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class FarmingCycleReportQueryServiceTest {
    private val memberId = uuid("000000000001")
    private val farmId = uuid("000000000101")
    private val cropId = uuid("000000000201")
    private val reportId = uuid("000000000301")
    private val activeReportId = uuid("000000000302")
    private val previousReportId = uuid("000000000303")
    private val finalHarvestRecordId = uuid("000000000401")
    private val previousFinalHarvestRecordId = uuid("000000000402")
    private val baseTime = LocalDateTime.of(2026, 1, 1, 9, 0)
    private val cursorCodec = OpaqueCursorCodec()

    @Mock private lateinit var farmRepository: FarmRepository
    @Mock private lateinit var cropRepository: CropRepository
    @Mock private lateinit var reportRepository: FarmingCycleReportRepository
    @Mock private lateinit var queryRepository: FarmingCycleReportQueryRepository

    private lateinit var service: FarmingCycleReportQueryService
    private lateinit var member: Member
    private lateinit var farm: Farm
    private lateinit var crop: Crop
    private lateinit var active: FarmingCycleReport
    private lateinit var selectedCompleted: FarmingCycleReport
    private lateinit var previous: FarmingCycleReport
    private lateinit var superseded: FarmingCycleReport
    private lateinit var typedStatistics: CycleReportStatistics

    @BeforeEach
    fun setUp() {
        service = FarmingCycleReportQueryService(
            farmRepository = farmRepository,
            cropRepository = cropRepository,
            reportRepository = reportRepository,
            queryRepository = queryRepository,
            cursorCodec = cursorCodec,
        )
        member = Member(id = memberId, email = "member@example.com", passwordHash = null)
        farm = Farm(id = farmId, owner = member, name = "약초농장", roadAddress = "서울시 강남구")
        crop = Crop(id = cropId, externalNo = 422, name = "황기", usePartCategory = CropUsePartCategory.ROOT_BARK)
        typedStatistics = CycleReportStatistics(watering = WateringStatistics(recordCount = 1))
        active = activeReport(id = activeReportId)
        selectedCompleted = completedReport(id = reportId, finalRecordId = finalHarvestRecordId, endedAt = day(30))
        previous = completedReport(id = previousReportId, finalRecordId = previousFinalHarvestRecordId, endedAt = day(20))
        superseded = completedReport(id = uuid("000000000304"), finalRecordId = uuid("000000000403"), endedAt = day(10))
        superseded.supersede()
    }

    @Test
    fun `current returns active and latest completed`() {
        stubScope()
        `when`(
            reportRepository.findByMember_IdAndFarm_IdAndCrop_IdAndStatus(
                memberId,
                farmId,
                cropId,
                FarmingCycleReportStatus.ACTIVE,
            ),
        ).thenReturn(active)
        `when`(queryRepository.findLatestCompleted(memberId, farmId, cropId)).thenReturn(previous)

        val result = service.getCurrent(memberId, farmId, cropId)

        assertThat(result.current?.id).isEqualTo(active.id)
        assertThat(result.previous?.id).isEqualTo(previous.id)
    }

    @Test
    fun `current without active returns null and latest completed as previous`() {
        stubScope()
        `when`(
            reportRepository.findByMember_IdAndFarm_IdAndCrop_IdAndStatus(
                memberId,
                farmId,
                cropId,
                FarmingCycleReportStatus.ACTIVE,
            ),
        ).thenReturn(null)
        `when`(queryRepository.findLatestCompleted(memberId, farmId, cropId)).thenReturn(previous)

        val result = service.getCurrent(memberId, farmId, cropId)

        assertThat(result.current).isNull()
        assertThat(result.previous?.id).isEqualTo(previous.id)
    }

    @Test
    fun `detail returns selected report and immediately previous completed`() {
        `when`(reportRepository.findByIdAndMember_Id(reportId, memberId)).thenReturn(selectedCompleted)
        `when`(
            queryRepository.findPreviousCompleted(
                memberId,
                farmId,
                cropId,
                requireNotNull(selectedCompleted.endsAt),
                requireNotNull(selectedCompleted.finalHarvestRecord?.id),
            ),
        ).thenReturn(previous)

        val result = service.getDetail(memberId, reportId)

        assertThat(result.selected.id).isEqualTo(reportId)
        assertThat(result.selected.statistics).isSameAs(typedStatistics)
        assertThat(result.previous?.id).isEqualTo(previous.id)
    }

    @Test
    fun `active detail previous uses latest completed`() {
        `when`(reportRepository.findByIdAndMember_Id(activeReportId, memberId)).thenReturn(active)
        `when`(queryRepository.findLatestCompleted(memberId, farmId, cropId)).thenReturn(previous)

        val result = service.getDetail(memberId, activeReportId)

        assertThat(result.selected.id).isEqualTo(activeReportId)
        assertThat(result.previous?.id).isEqualTo(previous.id)
        verify(queryRepository).findLatestCompleted(memberId, farmId, cropId)
        verifyNoMoreInteractions(queryRepository)
    }

    @Test
    fun `superseded detail is not exposed`() {
        `when`(reportRepository.findByIdAndMember_Id(reportId, memberId)).thenReturn(superseded)

        val exception = assertThrows(BusinessException::class.java) {
            service.getDetail(memberId, reportId)
        }

        assertEquals(ErrorCode.REPORT_NOT_FOUND, exception.errorCode)
        verifyNoInteractions(queryRepository)
    }

    @Test
    fun `detail hides reports outside member scope`() {
        `when`(reportRepository.findByIdAndMember_Id(reportId, memberId)).thenReturn(null)

        val exception = assertThrows(BusinessException::class.java) {
            service.getDetail(memberId, reportId)
        }

        assertEquals(ErrorCode.REPORT_NOT_FOUND, exception.errorCode)
        verifyNoInteractions(queryRepository)
    }

    @Test
    fun `list completed uses lookahead and cursor from last visible boundary`() {
        stubScope()
        val newest = completedReport(id = uuid("000000000305"), finalRecordId = uuid("000000000405"), endedAt = day(40))
        `when`(
            queryRepository.searchCompleted(
                FarmingCycleReportQueryRepository.SearchCondition(
                    memberId = memberId,
                    farmId = farmId,
                    cropId = cropId,
                    cursor = null,
                    size = 3,
                ),
            ),
        ).thenReturn(FarmingCycleReportQueryRepository.SearchResult(listOf(newest, selectedCompleted, previous)))

        val result = service.listCompleted(searchCondition(size = 2))

        assertThat(result.items.map { it.id }).containsExactly(newest.id, selectedCompleted.id)
        assertThat(result.items.first()).hasNoNullFieldsOrPropertiesExcept()
        assertThat(result.nextCursor).isNotBlank()
        val decoded = cursorCodec.decode(result.nextCursor!!, FarmingCycleReportCursorPayload::class.java)
        assertEquals(selectedCompleted.endsAt, decoded.endsAt)
        assertEquals(selectedCompleted.finalHarvestRecord?.id, decoded.finalHarvestRecordId)
    }

    @Test
    fun `blank cursor is treated as absent`() {
        stubScope()
        `when`(
            queryRepository.searchCompleted(
                FarmingCycleReportQueryRepository.SearchCondition(
                    memberId = memberId,
                    farmId = farmId,
                    cropId = cropId,
                    cursor = null,
                    size = 11,
                ),
            ),
        ).thenReturn(FarmingCycleReportQueryRepository.SearchResult(emptyList()))

        service.listCompleted(searchCondition(cursor = "   ", size = 10))

        verify(queryRepository).searchCompleted(
            FarmingCycleReportQueryRepository.SearchCondition(
                memberId = memberId,
                farmId = farmId,
                cropId = cropId,
                cursor = null,
                size = 11,
            ),
        )
    }

    @Test
    fun `invalid cursor is rejected`() {
        stubScope()

        val exception = assertThrows(BusinessException::class.java) {
            service.listCompleted(searchCondition(cursor = "not-a-valid-cursor"))
        }

        assertEquals(ErrorCode.INVALID_CURSOR, exception.errorCode)
        verifyNoInteractions(queryRepository)
    }

    @Test
    fun `invalid size is rejected before repository access`() {
        listOf(0, 101).forEach { size ->
            val exception = assertThrows(BusinessException::class.java) {
                service.listCompleted(searchCondition(size = size))
            }

            assertEquals(ErrorCode.INVALID_INPUT, exception.errorCode)
        }
        verifyNoInteractions(queryRepository)
    }

    @Test
    fun `current rejects unowned farm before report lookup`() {
        `when`(farmRepository.findByIdAndOwnerId(farmId, memberId)).thenReturn(null)

        val exception = assertThrows(BusinessException::class.java) {
            service.getCurrent(memberId, farmId, cropId)
        }

        assertEquals(ErrorCode.FARM_NOT_FOUND, exception.errorCode)
        verifyNoInteractions(reportRepository, queryRepository)
    }

    @Test
    fun `list rejects unowned farm before query lookup`() {
        `when`(farmRepository.findByIdAndOwnerId(farmId, memberId)).thenReturn(null)

        val exception = assertThrows(BusinessException::class.java) {
            service.listCompleted(searchCondition())
        }

        assertEquals(ErrorCode.FARM_NOT_FOUND, exception.errorCode)
        verifyNoInteractions(queryRepository)
    }

    @Test
    fun `current rejects missing crop`() {
        `when`(farmRepository.findByIdAndOwnerId(farmId, memberId)).thenReturn(farm)
        `when`(cropRepository.existsById(cropId)).thenReturn(false)

        val exception = assertThrows(BusinessException::class.java) {
            service.getCurrent(memberId, farmId, cropId)
        }

        assertEquals(ErrorCode.CROP_NOT_FOUND, exception.errorCode)
        verifyNoInteractions(reportRepository, queryRepository)
    }

    private fun stubScope() {
        `when`(farmRepository.findByIdAndOwnerId(farmId, memberId)).thenReturn(farm)
        `when`(cropRepository.existsById(cropId)).thenReturn(true)
    }

    private fun searchCondition(
        cursor: String? = null,
        size: Int = 20,
    ): FarmingCycleReportSearchCondition =
        FarmingCycleReportSearchCondition(
            memberId = memberId,
            farmId = farmId,
            cropId = cropId,
            cursor = cursor,
            size = size,
        )

    private fun activeReport(id: UUID): FarmingCycleReport =
        report(
            id = id,
            projection = FarmingCycleReportProjection(
                status = FarmingCycleReportStatus.ACTIVE,
                startsAt = day(40),
                endsAt = null,
                startBasis = FarmingCycleStartBasis.AFTER_PREVIOUS_FINAL_HARVEST,
                finalHarvestRecord = null,
                statisticsSchemaVersion = 1,
                statistics = typedStatistics,
            ),
        )

    private fun completedReport(
        id: UUID,
        finalRecordId: UUID,
        endedAt: LocalDateTime,
    ): FarmingCycleReport =
        report(
            id = id,
            projection = FarmingCycleReportProjection(
                status = FarmingCycleReportStatus.COMPLETED,
                startsAt = endedAt.minusDays(10),
                endsAt = endedAt,
                startBasis = FarmingCycleStartBasis.FIRST_RECORD,
                finalHarvestRecord = finalHarvestRecord(finalRecordId, endedAt),
                statisticsSchemaVersion = 1,
                statistics = typedStatistics,
            ),
        )

    private fun report(id: UUID, projection: FarmingCycleReportProjection): FarmingCycleReport {
        val report = FarmingCycleReport.create(member, farm, crop, projection)
        FarmingCycleReport::class.java.getDeclaredField("id").apply {
            isAccessible = true
            set(report, id)
        }
        return report
    }

    private fun finalHarvestRecord(id: UUID, workedAt: LocalDateTime): FarmingRecord =
        FarmingRecord(
            id = id,
            member = member,
            farm = farm,
            crop = crop,
            workType = WorkType.HARVEST,
            workedAt = workedAt,
            weatherCondition = "맑음",
            weatherTemperature = 20,
            memo = "최종 수확",
            entryMode = "MANUAL",
        )

    private fun day(day: Long): LocalDateTime = baseTime.plusDays(day)

    private fun uuid(tail: String): UUID =
        UUID.fromString("00000000-0000-0000-0000-$tail")
}
