package com.chamchamcham.api.report.controller

import com.chamchamcham.api.exception.GlobalExceptionHandler
import com.chamchamcham.api.report.dto.FarmingCycleReportResponses
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.application.report.FarmingCycleReportQueryService
import com.chamchamcham.application.report.FarmingCycleReportResult
import com.chamchamcham.application.report.FarmingCycleReportSearchCondition
import com.chamchamcham.application.security.TokenProvider
import com.chamchamcham.domain.report.Coverage
import com.chamchamcham.domain.report.AmountByUnit
import com.chamchamcham.domain.report.CategoryAmountByUnit
import com.chamchamcham.domain.report.CategoryMethodStatistics
import com.chamchamcham.domain.report.CommonOnlyStatistics
import com.chamchamcham.domain.report.CountDistribution
import com.chamchamcham.domain.report.CycleReportStatistics
import com.chamchamcham.domain.report.FertilizingStatistics
import com.chamchamcham.domain.report.FarmingCycleReportStatus
import com.chamchamcham.domain.report.FarmingCycleStartBasis
import com.chamchamcham.domain.report.HarvestStatistics
import com.chamchamcham.domain.report.HarvestPartStatistics
import com.chamchamcham.domain.report.GrowthPeriodRange
import com.chamchamcham.domain.report.MaterialCategoryStatistics
import com.chamchamcham.domain.report.PestControlStatistics
import com.chamchamcham.domain.report.PlantingStatistics
import com.chamchamcham.domain.report.PropagationStatistics
import com.chamchamcham.domain.report.TargetCount
import com.chamchamcham.domain.report.WateringStatistics
import com.chamchamcham.domain.report.WeedingStatistics
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@WebMvcTest(FarmingCycleReportController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler::class)
class FarmingCycleReportControllerTest(
    @Autowired private val mockMvc: MockMvc
) {
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val farmId = UUID.fromString("00000000-0000-0000-0000-000000000201")
    private val secondFarmId = UUID.fromString("00000000-0000-0000-0000-000000000202")
    private val cropId = UUID.fromString("00000000-0000-0000-0000-000000000301")
    private val secondCropId = UUID.fromString("00000000-0000-0000-0000-000000000302")
    private val selectedReportId = UUID.fromString("00000000-0000-0000-0000-000000000601")
    private val previousReportId = UUID.fromString("00000000-0000-0000-0000-000000000602")
    private val finalHarvestRecordId = UUID.fromString("00000000-0000-0000-0000-000000000101")

    @MockBean private lateinit var service: FarmingCycleReportQueryService
    @MockBean private lateinit var tokenProvider: TokenProvider

    @Test
    fun `former current path is rejected as an invalid report id`() {
        mockMvc.perform(
            get("/api/v1/farming-reports/current")
                .with(authenticatedMember(memberId.toString()))
                .param("farmId", farmId.toString())
                .param("cropId", cropId.toString()),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code", equalTo("COMMON_001")))

        verifyNoInteractions(service)
    }

    @Test
    fun `list returns completed metadata and cursor without statistics`() {
        `when`(service.listCompleted(searchCondition()))
            .thenReturn(completedPage())

        mockMvc.perform(
            get("/api/v1/farming-reports")
                .with(authenticatedMember(memberId.toString()))
                .param("farmId", farmId.toString())
                .param("cropId", cropId.toString())
                .param("size", "20"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items[0].status", equalTo("COMPLETED")))
            .andExpect(jsonPath("$.data.items[0].statistics").doesNotExist())
            .andExpect(jsonPath("$.data.nextCursor", equalTo("next-cursor")))
    }

    @Test
    fun `detail returns selected full typed statistics without comparison snapshots`() {
        `when`(service.getDetail(memberId, selectedReportId))
            .thenReturn(
                FarmingCycleReportResult.Detail(
                    selected = selectedSnapshot(),
                ),
            )

        mockMvc.perform(
            get("/api/v1/farming-reports/{reportId}", selectedReportId)
                .with(authenticatedMember(memberId.toString())),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.selected.id", equalTo(selectedReportId.toString())))
            .andExpect(jsonPath("$.data.selected.statistics.planting.recordCount", equalTo(1)))
            .andExpect(jsonPath("$.data.selected.statistics.planting.plantingMethodDistribution[0].code", equalTo("SEED")))
            .andExpect(jsonPath("$.data.selected.statistics.planting.plantingMethodDistribution[0].ratePct", equalTo(100.0)))
            .andExpect(jsonPath("$.data.selected.statistics.watering.recordCount", equalTo(2)))
            .andExpect(jsonPath("$.data.selected.statistics.pestControl.totalSprayAmountMl", equalTo(2000.0)))
            .andExpect(jsonPath("$.data.selected.statistics.pestControl.totalSprayAmountLiters").doesNotExist())
            .andExpect(jsonPath("$.data.selected.statistics.harvest.totalAmountKg", equalTo(30.0)))
            .andExpect(jsonPath("$.data.selected.statistics.harvest.growthPeriodDistribution[0].code", equalTo("6")))
            .andExpect(jsonPath("$.data.selected.statistics.harvest.amountCoverage.recordedCount", equalTo(2)))
            .andExpect(jsonPath("$.data.selected.statistics.harvest.amountCoverage.targetCount", equalTo(3)))
            .andExpect(jsonPath("$.data.previous").doesNotExist())
            .andExpect(jsonPath("$.data.comparison").doesNotExist())

        verify(service).getDetail(memberId, selectedReportId)
    }

    @Test
    fun `former current path without query parameters is rejected as invalid input`() {
        mockMvc.perform(
            get("/api/v1/farming-reports/current")
                .with(authenticatedMember(memberId.toString())),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code", equalTo("COMMON_001")))

        verifyNoInteractions(service)
    }

    @Test
    fun `list rejects size above boundary before service call`() {
        mockMvc.perform(
            get("/api/v1/farming-reports")
                .with(authenticatedMember(memberId.toString()))
                .param("farmId", farmId.toString())
                .param("cropId", cropId.toString())
                .param("size", "101"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code", equalTo("COMMON_001")))

        verifyNoInteractions(service)
    }

    @Test
    fun `detail maps report not found to not found`() {
        `when`(service.getDetail(memberId, selectedReportId))
            .thenThrow(BusinessException(ErrorCode.REPORT_NOT_FOUND))

        mockMvc.perform(
            get("/api/v1/farming-reports/{reportId}", selectedReportId)
                .with(authenticatedMember(memberId.toString())),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code", equalTo("REPORT_001")))

        verify(service).getDetail(memberId, selectedReportId)
    }

    @Test
    fun `list passes cursor and requested size to service`() {
        `when`(service.listCompleted(searchCondition(cursor = "cursor-1", size = 5)))
            .thenReturn(FarmingCycleReportResult.Page(emptyList(), null))

        mockMvc.perform(
            get("/api/v1/farming-reports")
                .with(authenticatedMember(memberId.toString()))
                .param("farmId", farmId.toString())
                .param("cropId", cropId.toString())
                .param("cursor", "cursor-1")
                .param("size", "5"),
        )
            .andExpect(status().isOk)

        verify(service).listCompleted(searchCondition(cursor = "cursor-1", size = 5))
    }

    @Test
    fun `list translates an invalid cursor from the query service to invalid input`() {
        val condition = searchCondition(
            farmId = null,
            cropId = null,
            cursor = "not-base64",
        )
        `when`(service.listCompleted(condition))
            .thenThrow(BusinessException(ErrorCode.INVALID_CURSOR))

        mockMvc.perform(
            get("/api/v1/farming-reports")
                .with(authenticatedMember(memberId.toString()))
                .param("cursor", "not-base64"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code", equalTo("COMMON_001")))

        verify(service).listCompleted(condition)
    }

    @Test
    fun `list accepts no filters either filter and both filters`() {
        val conditions = listOf(
            searchCondition(farmId = null, cropId = null),
            searchCondition(farmId = farmId, cropId = null),
            searchCondition(farmId = null, cropId = cropId),
            searchCondition(farmId = farmId, cropId = cropId),
        )
        conditions.forEach { condition ->
            `when`(service.listCompleted(condition))
                .thenReturn(FarmingCycleReportResult.Page(emptyList(), null))
        }

        mockMvc.perform(get("/api/v1/farming-reports").with(authenticatedMember(memberId.toString())))
            .andExpect(status().isOk)
        mockMvc.perform(
            get("/api/v1/farming-reports")
                .with(authenticatedMember(memberId.toString()))
                .param("farmId", farmId.toString()),
        ).andExpect(status().isOk)
        mockMvc.perform(
            get("/api/v1/farming-reports")
                .with(authenticatedMember(memberId.toString()))
                .param("cropId", cropId.toString()),
        ).andExpect(status().isOk)
        mockMvc.perform(
            get("/api/v1/farming-reports")
                .with(authenticatedMember(memberId.toString()))
                .param("farmId", farmId.toString())
                .param("cropId", cropId.toString()),
        ).andExpect(status().isOk)

        conditions.forEach { verify(service).listCompleted(it) }
    }

    @Test
    fun `list binds repeated farm and crop ids as multi filters`() {
        val condition = FarmingCycleReportSearchCondition(
            memberId = memberId,
            farmIds = setOf(farmId, secondFarmId),
            cropIds = setOf(cropId, secondCropId),
            cursor = null,
            size = 20,
        )
        `when`(service.listCompleted(condition)).thenReturn(FarmingCycleReportResult.Page(emptyList(), null))

        mockMvc.perform(
            get("/api/v1/farming-reports")
                .with(authenticatedMember(memberId.toString()))
                .param("farmId", farmId.toString(), secondFarmId.toString())
                .param("cropId", cropId.toString(), secondCropId.toString()),
        ).andExpect(status().isOk)

        verify(service).listCompleted(condition)
    }

    @Test
    fun `snapshot response copies every nested statistic into API owned types`() {
        val statistics = representativeStatistics()

        val response = FarmingCycleReportResponses.SnapshotResponse.from(
            selectedSnapshot(statistics),
        )

        assertThat(response.statistics).isNotSameAs(statistics)
        assertThat(response.statistics).isNotInstanceOf(CycleReportStatistics::class.java)
        assertThat(response.statistics)
            .usingRecursiveComparison()
            .ignoringFields(
                "pestControl.totalSprayAmountMl",
                "pestControl.totalSprayAmountLiters",
            )
            .isEqualTo(statistics)
        assertThat(response.statistics.pestControl.totalSprayAmountMl)
            .isEqualByComparingTo("2000.0000")
    }

    private fun searchCondition(
        farmId: UUID? = this.farmId,
        cropId: UUID? = this.cropId,
        cursor: String? = null,
        size: Int = 20,
    ) = FarmingCycleReportSearchCondition(
        memberId = memberId,
        farmIds = farmId?.let(::setOf).orEmpty(),
        cropIds = cropId?.let(::setOf).orEmpty(),
        cursor = cursor,
        size = size,
    )

    private fun completedPage() = FarmingCycleReportResult.Page(
        items = listOf(
            FarmingCycleReportResult.Metadata(
                id = previousReportId,
                farmId = farmId,
                farmName = "초록농장",
                cropId = cropId,
                cropName = "감초",
                status = FarmingCycleReportStatus.COMPLETED,
                startsAt = LocalDateTime.of(2026, 1, 1, 9, 0),
                endsAt = LocalDateTime.of(2026, 6, 30, 17, 0),
                startBasis = FarmingCycleStartBasis.FIRST_RECORD,
                sourceRevision = 7L,
            ),
        ),
        nextCursor = "next-cursor",
    )

    private fun selectedSnapshot(
        statistics: CycleReportStatistics = representativeStatistics(),
    ) = snapshot(
        id = selectedReportId,
        status = FarmingCycleReportStatus.ACTIVE,
        startsAt = LocalDateTime.of(2026, 7, 1, 9, 0),
        endsAt = null,
        statistics = statistics,
    )

    private fun completedSnapshot() = snapshot(
        id = previousReportId,
        status = FarmingCycleReportStatus.COMPLETED,
        startsAt = LocalDateTime.of(2026, 1, 1, 9, 0),
        endsAt = LocalDateTime.of(2026, 6, 30, 17, 0),
        statistics = representativeStatistics(),
    )

    private fun snapshot(
        id: UUID,
        status: FarmingCycleReportStatus,
        startsAt: LocalDateTime,
        endsAt: LocalDateTime?,
        statistics: CycleReportStatistics,
    ) = FarmingCycleReportResult.Snapshot(
        id = id,
        farmId = farmId,
        farmName = "초록농장",
        cropId = cropId,
        cropName = "감초",
        status = status,
        startsAt = startsAt,
        endsAt = endsAt,
        startBasis = FarmingCycleStartBasis.FIRST_RECORD,
        finalHarvestRecordId = finalHarvestRecordId.takeIf { status == FarmingCycleReportStatus.COMPLETED },
        statisticsSchemaVersion = 1,
        sourceRevision = 7L,
        statistics = statistics,
    )

    private fun representativeStatistics() = CycleReportStatistics(
        planting = PlantingStatistics(
            recordCount = 1,
            firstWorkedOn = LocalDate.of(2026, 1, 2),
            lastWorkedOn = LocalDate.of(2026, 1, 3),
            workedDayCount = 2,
            averageIntervalDays = BigDecimal("1.0"),
            photoAttachedRecordCount = 1,
            photoAttachmentRatePct = BigDecimal("100.0"),
            weatherDistribution = listOf(distribution("SUNNY", "맑음")),
            averageTemperatureC = BigDecimal("18.5"),
            plantingMethodDistribution = listOf(distribution("SEED", "씨앗 심기")),
            propagationMethods = listOf(
                PropagationStatistics(
                    code = "SEED",
                    label = "종자",
                    recordCount = 1,
                    recordRatePct = BigDecimal("100.0"),
                    totalQuantity = BigDecimal("12.0"),
                    quantityUnit = "EA",
                    quantityCoverage = Coverage(1, 1),
                ),
            ),
        ),
        watering = WateringStatistics(
            recordCount = 2,
            workedDayCount = 2,
            weatherDistribution = listOf(distribution("CLOUDY", "흐림")),
            amountDistribution = listOf(distribution("SUFFICIENT", "충분")),
            methodDistribution = listOf(distribution("SPRAYING", "살수")),
        ),
        fertilizing = FertilizingStatistics(
            recordCount = 2,
            totalAmountKg = BigDecimal("8.0"),
            averageAmountKg = BigDecimal("4.0"),
            amountCoverage = Coverage(2, 2),
            materialCategories = listOf(
                MaterialCategoryStatistics(
                    code = "ORGANIC",
                    label = "유기질",
                    recordCount = 2,
                    recordRatePct = BigDecimal("100.0"),
                    amountKg = BigDecimal("8.0"),
                    amountRatePct = BigDecimal("100.0"),
                ),
            ),
            methodDistribution = listOf(distribution("SOIL", "토양")),
            categoryMethods = listOf(
                CategoryMethodStatistics(
                    categoryCode = "ORGANIC",
                    categoryLabel = "유기질",
                    methodCode = "SOIL",
                    methodLabel = "토양",
                    recordCount = 2,
                    recordRatePct = BigDecimal("100.0"),
                ),
            ),
        ),
        pestControl = PestControlStatistics(
            recordCount = 1,
            categoryDistribution = listOf(distribution("PESTICIDE", "농약")),
            pesticideAmounts = listOf(AmountByUnit("ML", BigDecimal("20.0"), Coverage(1, 1))),
            categoryAmounts = listOf(
                CategoryAmountByUnit(
                    categoryCode = "PESTICIDE",
                    categoryLabel = "농약",
                    unit = "ML",
                    recordCount = 1,
                    amount = BigDecimal("20.0"),
                    coverage = Coverage(1, 1),
                ),
            ),
            totalSprayAmountLiters = BigDecimal("2.0"),
            sprayAmountCoverage = Coverage(1, 1),
            targets = listOf(TargetCount("진딧물", 1)),
        ),
        weeding = WeedingStatistics(
            recordCount = 1,
            methodDistribution = listOf(distribution("HAND", "손제초")),
        ),
        pruning = CommonOnlyStatistics(
            recordCount = 1,
            weatherDistribution = listOf(distribution("SUNNY", "맑음")),
        ),
        harvest = HarvestStatistics(
            recordCount = 3,
            totalAmountKg = BigDecimal("30.0"),
            averageAmountKg = BigDecimal("10.0"),
            amountCoverage = Coverage(recordedCount = 2, targetCount = 3),
            firstHarvestedOn = LocalDate.of(2026, 6, 1),
            lastHarvestedOn = LocalDate.of(2026, 6, 30),
            medicinalParts = listOf(
                HarvestPartStatistics(
                    code = "ROOT_BARK",
                    label = "근피",
                    recordCount = 3,
                    recordRatePct = BigDecimal("100.0"),
                    knownAmountKg = BigDecimal("30.0"),
                    amountRatePct = BigDecimal("100.0"),
                    amountCoverage = Coverage(2, 3),
                ),
            ),
            finalGrowthPeriodMonths = 6,
            growthPeriodRangeMonths = GrowthPeriodRange(5, 7),
            growthPeriodDistribution = listOf(distribution("6", "6개월")),
        ),
        etc = CommonOnlyStatistics(
            recordCount = 1,
            photoAttachedRecordCount = 1,
            photoAttachmentRatePct = BigDecimal("100.0"),
        ),
    )

    private fun distribution(code: String, label: String) = CountDistribution(
        code = code,
        label = label,
        count = 1,
        ratePct = BigDecimal("100.0"),
    )

    private fun authenticatedMember(memberId: String): RequestPostProcessor {
        return RequestPostProcessor { request ->
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(
                    memberId,
                    null,
                    listOf(SimpleGrantedAuthority("ROLE_USER")),
                )
            request
        }
    }
}
