package com.chamchamcham.application.report

import com.chamchamcham.application.coaching.reportfeedback.lifecycle.ReportFeedbackLifecycleService
import com.chamchamcham.domain.crop.Crop
import com.chamchamcham.domain.crop.CropRepository
import com.chamchamcham.domain.crop.CropUsePartCategory
import com.chamchamcham.domain.farm.Farm
import com.chamchamcham.domain.farm.FarmRepository
import com.chamchamcham.domain.farming.FarmingRecord
import com.chamchamcham.domain.farming.FarmingRecordRepository
import com.chamchamcham.domain.farming.WorkType
import com.chamchamcham.domain.member.Member
import com.chamchamcham.domain.report.FarmingCycleReport
import com.chamchamcham.domain.report.FarmingCycleReportProjection
import com.chamchamcham.domain.report.FarmingCycleReportRepository
import com.chamchamcham.domain.report.FarmingCycleReportStatus
import com.chamchamcham.domain.report.FarmingCycleStartBasis
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDateTime
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class FarmingCycleReportProjectionServiceTest {
    private val memberId = uuid("000000000001")
    private val farmId = uuid("000000000101")
    private val cropId = uuid("000000000201")
    private val activeReportId = uuid("000000000301")
    private val completedReportId = uuid("000000000302")
    private val supersededReportId = uuid("000000000303")
    private val finalHarvestRecordId = uuid("000000000401")
    private val changedFinalHarvestRecordId = uuid("000000000402")
    private val sameTimeFinalHarvestRecordId = uuid("000000000403")
    private val baseTime = LocalDateTime.of(2026, 1, 1, 9, 0)
    private val scope = ReportScope(memberId = memberId, farmId = farmId, cropId = cropId)

    @Mock private lateinit var farmRepository: FarmRepository
    @Mock private lateinit var cropRepository: CropRepository
    @Mock private lateinit var farmingRecordRepository: FarmingRecordRepository
    @Mock private lateinit var sourceLoader: FarmingCycleReportSourceLoader
    @Mock private lateinit var partitioner: FarmingCyclePartitioner
    @Mock private lateinit var reportRepository: FarmingCycleReportRepository
    @Mock private lateinit var reportFeedbackLifecycleService: ReportFeedbackLifecycleService

    private lateinit var service: FarmingCycleReportProjectionService
    private lateinit var member: Member
    private lateinit var farm: Farm
    private lateinit var crop: Crop

    @BeforeEach
    fun setUp() {
        service = FarmingCycleReportProjectionService(
            farmRepository = farmRepository,
            cropRepository = cropRepository,
            farmingRecordRepository = farmingRecordRepository,
            sourceLoader = sourceLoader,
            partitioner = partitioner,
            statisticsCalculator = CycleReportStatisticsCalculator(),
            reportRepository = reportRepository,
            reportFeedbackLifecycleService = reportFeedbackLifecycleService,
        )
        member = Member(id = memberId, email = "$memberId@example.com", passwordHash = null)
        farm = Farm(id = farmId, owner = member, name = "약초농장", roadAddress = "서울시 강남구")
        crop = Crop(id = cropId, externalNo = 1001, name = "황기", usePartCategory = CropUsePartCategory.ROOT_BARK)
    }

    @Test
    fun `rebuild locks owned farm before reading source records`() {
        stubScope()
        val source = listOf(wateringRecord(day = 1))
        `when`(sourceLoader.load(scope)).thenReturn(source)
        `when`(partitioner.partition(source)).thenReturn(listOf(activeSlice(day = 1)))

        service.rebuild(scope)

        inOrder(farmRepository, sourceLoader).apply {
            verify(farmRepository).findOwnedByIdForReportUpdate(farmId, memberId)
            verify(sourceLoader).load(scope)
        }
    }

    @Test
    fun `first record creates active report`() {
        stubScope()
        val source = listOf(wateringRecord(day = 1))
        `when`(sourceLoader.load(scope)).thenReturn(source)
        `when`(partitioner.partition(source)).thenReturn(listOf(activeSlice(day = 1)))

        service.rebuild(scope)

        val saved = capturedSavedReports().single()
        assertThat(saved.status).isEqualTo(FarmingCycleReportStatus.ACTIVE)
        assertThat(saved.statistics.watering.recordCount).isEqualTo(1)
        assertThat(saved.sourceRevision).isEqualTo(1)
    }

    @Test
    fun `latest final harvest converts active report to completed`() {
        val active = persistedActiveReport(activeReportId)
        stubScope(existing = listOf(active))
        `when`(farmingRecordRepository.getReferenceById(finalHarvestRecordId))
            .thenReturn(referenceRecord(finalHarvestRecordId))
        val source = recordsEndingWithFinalHarvest()
        `when`(sourceLoader.load(scope)).thenReturn(source)
        `when`(partitioner.partition(source)).thenReturn(listOf(completedSlice(finalHarvestRecordId)))

        service.rebuild(scope)

        assertThat(active.status).isEqualTo(FarmingCycleReportStatus.COMPLETED)
        assertThat(active.finalHarvestRecord?.id).isEqualTo(finalHarvestRecordId)
        assertThat(active.endsAt).isEqualTo(baseTime.plusDays(10))
        verify(reportRepository).save(active)
        verify(reportFeedbackLifecycleService).enqueue(active)
    }

    @Test
    fun `active report does not enqueue report feedback`() {
        stubScope()
        val source = listOf(wateringRecord(day = 1))
        `when`(sourceLoader.load(scope)).thenReturn(source)
        `when`(partitioner.partition(source)).thenReturn(listOf(activeSlice(day = 1)))

        service.rebuild(scope)

        verifyNoInteractions(reportFeedbackLifecycleService)
    }

    @Test
    fun `records after final preserve active report and create completed report`() {
        val active = persistedActiveReport(activeReportId)
        stubScope(existing = listOf(active))
        `when`(farmingRecordRepository.getReferenceById(finalHarvestRecordId))
            .thenReturn(referenceRecord(finalHarvestRecordId))
        val source = recordsEndingWithFinalHarvest() + wateringRecord(day = 20)
        `when`(sourceLoader.load(scope)).thenReturn(source)
        `when`(partitioner.partition(source)).thenReturn(
            listOf(
                completedSlice(finalHarvestRecordId),
                activeSlice(day = 20, startBasis = FarmingCycleStartBasis.AFTER_PREVIOUS_FINAL_HARVEST),
            ),
        )

        service.rebuild(scope)

        val saved = capturedSavedReports()
        val createdCompleted = saved.single { it !== active }
        assertThat(active.status).isEqualTo(FarmingCycleReportStatus.ACTIVE)
        assertThat(active.startsAt).isEqualTo(baseTime.plusDays(20))
        assertThat(createdCompleted.status).isEqualTo(FarmingCycleReportStatus.COMPLETED)
        assertThat(createdCompleted.finalHarvestRecord?.id).isEqualTo(finalHarvestRecordId)
    }

    @Test
    fun `historical boundary matching uses final harvest id instead of timestamp`() {
        val completed = persistedCompletedReport(
            id = completedReportId,
            finalRecordId = finalHarvestRecordId,
            startsAt = baseTime.plusDays(1),
            endsAt = baseTime.plusDays(10),
        )
        val active = persistedActiveReport(activeReportId, startsAt = baseTime.plusDays(20))
        stubScope(existing = listOf(completed, active))
        `when`(farmingRecordRepository.getReferenceById(sameTimeFinalHarvestRecordId))
            .thenReturn(referenceRecord(sameTimeFinalHarvestRecordId, day = 10))
        val source = listOf(
            wateringRecord(day = 1),
            finalHarvestRecord(sameTimeFinalHarvestRecordId, day = 10),
            wateringRecord(day = 20),
        )
        `when`(sourceLoader.load(scope)).thenReturn(source)
        `when`(partitioner.partition(source)).thenReturn(
            listOf(
                completedSlice(sameTimeFinalHarvestRecordId),
                activeSlice(day = 20, startBasis = FarmingCycleStartBasis.AFTER_PREVIOUS_FINAL_HARVEST),
            ),
        )

        service.rebuild(scope)

        val createdCompleted = capturedSavedReports().single { it !== active }
        assertThat(completed.status).isEqualTo(FarmingCycleReportStatus.SUPERSEDED)
        assertThat(active.status).isEqualTo(FarmingCycleReportStatus.ACTIVE)
        assertThat(createdCompleted.finalHarvestRecord?.id).isEqualTo(sameTimeFinalHarvestRecordId)
    }

    @Test
    fun `changed completed boundary creates new completed report and supersedes old one`() {
        val completed = persistedCompletedReport(
            id = completedReportId,
            finalRecordId = finalHarvestRecordId,
            startsAt = baseTime.plusDays(1),
            endsAt = baseTime.plusDays(10),
        )
        stubScope(existing = listOf(completed))
        `when`(farmingRecordRepository.getReferenceById(changedFinalHarvestRecordId))
            .thenReturn(referenceRecord(changedFinalHarvestRecordId, day = 11))
        val source = listOf(wateringRecord(day = 1), finalHarvestRecord(changedFinalHarvestRecordId, day = 11))
        `when`(sourceLoader.load(scope)).thenReturn(source)
        `when`(partitioner.partition(source)).thenReturn(listOf(completedSlice(changedFinalHarvestRecordId, day = 11)))

        service.rebuild(scope)

        val createdCompleted = capturedSavedReports().single { it !== completed }
        assertThat(createdCompleted.status).isEqualTo(FarmingCycleReportStatus.COMPLETED)
        assertThat(createdCompleted.finalHarvestRecord?.id).isEqualTo(changedFinalHarvestRecordId)
        assertThat(completed.status).isEqualTo(FarmingCycleReportStatus.SUPERSEDED)
    }

    @Test
    fun `removed final boundary merges into active and supersedes completed report`() {
        val completed = persistedCompletedReport(completedReportId, finalHarvestRecordId)
        val active = persistedActiveReport(activeReportId, startsAt = baseTime.plusDays(20))
        stubScope(existing = listOf(completed, active))
        val source = listOf(wateringRecord(day = 1), wateringRecord(day = 20))
        `when`(sourceLoader.load(scope)).thenReturn(source)
        `when`(partitioner.partition(source)).thenReturn(listOf(activeSlice(day = 1, records = source)))

        service.rebuild(scope)

        assertThat(active.status).isEqualTo(FarmingCycleReportStatus.ACTIVE)
        assertThat(active.startsAt).isEqualTo(baseTime.plusDays(1))
        assertThat(completed.status).isEqualTo(FarmingCycleReportStatus.SUPERSEDED)
    }

    @Test
    fun `empty source supersedes all current reports`() {
        val completed = persistedCompletedReport(completedReportId, finalHarvestRecordId)
        val active = persistedActiveReport(activeReportId)
        stubScope(existing = listOf(completed, active))
        `when`(sourceLoader.load(scope)).thenReturn(emptyList())
        `when`(partitioner.partition(emptyList())).thenReturn(emptyList())

        service.rebuild(scope)

        assertThat(completed.status).isEqualTo(FarmingCycleReportStatus.SUPERSEDED)
        assertThat(active.status).isEqualTo(FarmingCycleReportStatus.SUPERSEDED)
        verify(reportRepository).saveAll(listOf(completed, active))
    }

    @Test
    fun `idempotent rebuild leaves source revision unchanged`() {
        val completed = persistedCompletedReport(
            id = completedReportId,
            finalRecordId = finalHarvestRecordId,
            startsAt = baseTime.plusDays(1),
            endsAt = baseTime.plusDays(10),
        )
        val revision = completed.sourceRevision
        stubScope(existing = listOf(completed))
        `when`(farmingRecordRepository.getReferenceById(finalHarvestRecordId))
            .thenReturn(referenceRecord(finalHarvestRecordId))
        val source = recordsEndingWithFinalHarvest()
        `when`(sourceLoader.load(scope)).thenReturn(source)
        `when`(partitioner.partition(source)).thenReturn(listOf(completedSlice(finalHarvestRecordId)))

        service.rebuild(scope)

        assertThat(completed.sourceRevision).isEqualTo(revision)
        verify(reportRepository).save(completed)
    }

    @Test
    fun `rebuildAll deduplicates and sorts scopes before rebuilding`() {
        val scopeA = ReportScope(uuid("000000000010"), uuid("000000000010"), uuid("000000000010"))
        val scopeB = ReportScope(uuid("000000000010"), uuid("000000000011"), uuid("000000000010"))
        val scopeC = ReportScope(uuid("000000000011"), uuid("000000000010"), uuid("000000000010"))
        listOf(scopeA, scopeB, scopeC).forEach { sortedScope ->
            `when`(farmRepository.findOwnedByIdForReportUpdate(sortedScope.farmId, sortedScope.memberId))
                .thenReturn(farm)
            `when`(cropRepository.findById(sortedScope.cropId)).thenReturn(Optional.of(crop))
            `when`(
                reportRepository.findAllCurrent(
                    sortedScope.memberId,
                    sortedScope.farmId,
                    sortedScope.cropId,
                ),
            ).thenReturn(emptyList())
            `when`(sourceLoader.load(sortedScope)).thenReturn(emptyList())
        }
        `when`(partitioner.partition(emptyList())).thenReturn(emptyList())

        service.rebuildAll(listOf(scopeC, scopeB, scopeA, scopeB))

        inOrder(farmRepository).apply {
            verify(farmRepository).findOwnedByIdForReportUpdate(scopeA.farmId, scopeA.memberId)
            verify(farmRepository).findOwnedByIdForReportUpdate(scopeB.farmId, scopeB.memberId)
            verify(farmRepository).findOwnedByIdForReportUpdate(scopeC.farmId, scopeC.memberId)
        }
    }

    private fun stubScope(existing: List<FarmingCycleReport> = emptyList()) {
        `when`(farmRepository.findOwnedByIdForReportUpdate(farmId, memberId)).thenReturn(farm)
        `when`(cropRepository.findById(cropId)).thenReturn(Optional.of(crop))
        `when`(reportRepository.findAllCurrent(memberId, farmId, cropId)).thenReturn(existing)
    }

    private fun activeSlice(
        day: Long,
        startBasis: FarmingCycleStartBasis = FarmingCycleStartBasis.FIRST_RECORD,
        records: List<CycleReportSourceRecord> = listOf(wateringRecord(day = day)),
    ): CycleSlice = CycleSlice(
        status = FarmingCycleReportStatus.ACTIVE,
        startBasis = startBasis,
        records = records,
    )

    private fun completedSlice(
        finalRecordId: UUID,
        day: Long = 10,
    ): CycleSlice = CycleSlice(
        status = FarmingCycleReportStatus.COMPLETED,
        startBasis = FarmingCycleStartBasis.FIRST_RECORD,
        records = listOf(wateringRecord(day = 1), finalHarvestRecord(finalRecordId, day = day)),
    )

    private fun recordsEndingWithFinalHarvest(): List<CycleReportSourceRecord> =
        listOf(wateringRecord(day = 1), finalHarvestRecord(finalHarvestRecordId, day = 10))

    private fun wateringRecord(day: Long): CycleReportSourceRecord =
        CycleReportSourceRecord(
            id = uuid("0000000005%02d".format(day)),
            workedAt = baseTime.plusDays(day),
            workType = WorkType.WATERING,
            weatherCondition = "맑음",
            weatherTemperature = 20,
            hasPhoto = false,
            watering = WateringReportSource(
                amount = CategoryRef("LOW", "적음"),
                method = CategoryRef("HOSE", "호스"),
            ),
        )

    private fun finalHarvestRecord(id: UUID, day: Long): CycleReportSourceRecord =
        CycleReportSourceRecord(
            id = id,
            workedAt = baseTime.plusDays(day),
            workType = WorkType.HARVEST,
            weatherCondition = "맑음",
            weatherTemperature = 20,
            hasPhoto = false,
            harvest = HarvestReportSource(
                amountKg = null,
                medicinalPart = CategoryRef("ROOT_BARK", "뿌리·껍질"),
                growthPeriodMonths = 12,
                isFinalHarvest = true,
            ),
        )

    private fun persistedActiveReport(
        id: UUID,
        startsAt: LocalDateTime = baseTime.plusDays(1),
    ): FarmingCycleReport = persistedReport(
        id = id,
        projection = FarmingCycleReportProjection(
            status = FarmingCycleReportStatus.ACTIVE,
            startsAt = startsAt,
            endsAt = null,
            startBasis = FarmingCycleStartBasis.FIRST_RECORD,
            finalHarvestRecord = null,
            statisticsSchemaVersion = 1,
            statistics = CycleReportStatisticsCalculator().calculate(listOf(wateringRecord(day = 1))),
        ),
    )

    private fun persistedCompletedReport(
        id: UUID,
        finalRecordId: UUID,
        startsAt: LocalDateTime = baseTime.plusDays(1),
        endsAt: LocalDateTime = baseTime.plusDays(10),
    ): FarmingCycleReport = persistedReport(
        id = id,
        projection = FarmingCycleReportProjection(
            status = FarmingCycleReportStatus.COMPLETED,
            startsAt = startsAt,
            endsAt = endsAt,
            startBasis = FarmingCycleStartBasis.FIRST_RECORD,
            finalHarvestRecord = referenceRecord(finalRecordId, day = 10),
            statisticsSchemaVersion = 1,
            statistics = CycleReportStatisticsCalculator().calculate(
                listOf(wateringRecord(day = 1), finalHarvestRecord(finalRecordId, day = 10)),
            ),
        ),
    )

    private fun persistedReport(
        id: UUID,
        projection: FarmingCycleReportProjection,
    ): FarmingCycleReport {
        val report = FarmingCycleReport.create(member, farm, crop, projection)
        FarmingCycleReport::class.java.getDeclaredField("id").apply {
            isAccessible = true
            set(report, id)
        }
        return report
    }

    private fun referenceRecord(id: UUID, day: Long = 10): FarmingRecord =
        FarmingRecord(
            id = id,
            member = member,
            farm = farm,
            crop = crop,
            workType = WorkType.HARVEST,
            workedAt = baseTime.plusDays(day),
            weatherCondition = "맑음",
            weatherTemperature = 20,
            memo = "memo",
            entryMode = "MANUAL",
        )

    private fun capturedSavedReports(): List<FarmingCycleReport> {
        val captor = ArgumentCaptor.forClass(FarmingCycleReport::class.java)
        verify(reportRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture())
        return captor.allValues
    }

    private fun uuid(tail: String): UUID =
        UUID.fromString("00000000-0000-0000-0000-$tail")
}
