package com.chamchamcham.api.report.controller

import com.chamchamcham.api.exception.GlobalExceptionHandler
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.application.report.FarmingCycleReportQueryService
import com.chamchamcham.application.report.FarmingCycleReportResult
import com.chamchamcham.application.report.FarmingCycleReportSearchCondition
import com.chamchamcham.application.security.TokenProvider
import com.chamchamcham.domain.report.Coverage
import com.chamchamcham.domain.report.CycleReportStatistics
import com.chamchamcham.domain.report.FarmingCycleReportStatus
import com.chamchamcham.domain.report.FarmingCycleStartBasis
import com.chamchamcham.domain.report.HarvestStatistics
import com.chamchamcham.domain.report.PlantingStatistics
import com.chamchamcham.domain.report.WateringStatistics
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
    private val cropId = UUID.fromString("00000000-0000-0000-0000-000000000301")
    private val selectedReportId = UUID.fromString("00000000-0000-0000-0000-000000000601")
    private val previousReportId = UUID.fromString("00000000-0000-0000-0000-000000000602")
    private val finalHarvestRecordId = UUID.fromString("00000000-0000-0000-0000-000000000101")

    @MockBean private lateinit var service: FarmingCycleReportQueryService
    @MockBean private lateinit var tokenProvider: TokenProvider

    @Test
    fun `current returns nullable current and latest completed previous`() {
        `when`(service.getCurrent(memberId, farmId, cropId))
            .thenReturn(
                FarmingCycleReportResult.Current(
                    current = null,
                    previous = completedSnapshot(),
                ),
            )

        mockMvc.perform(
            get("/api/v1/farming-reports/current")
                .with(authenticatedMember(memberId.toString()))
                .param("farmId", farmId.toString())
                .param("cropId", cropId.toString()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.current").doesNotExist())
            .andExpect(jsonPath("$.data.previous.id", equalTo(previousReportId.toString())))
            .andExpect(jsonPath("$.data.previous.statistics.watering.recordCount", equalTo(2)))

        verify(service).getCurrent(memberId, farmId, cropId)
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
    fun `detail returns selected and previous full typed statistics`() {
        `when`(service.getDetail(memberId, selectedReportId))
            .thenReturn(
                FarmingCycleReportResult.Detail(
                    selected = selectedSnapshot(),
                    previous = completedSnapshot(),
                ),
            )

        mockMvc.perform(
            get("/api/v1/farming-reports/{reportId}", selectedReportId)
                .with(authenticatedMember(memberId.toString())),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.selected.id", equalTo(selectedReportId.toString())))
            .andExpect(jsonPath("$.data.selected.statistics.planting.recordCount", equalTo(1)))
            .andExpect(jsonPath("$.data.selected.statistics.watering.recordCount", equalTo(2)))
            .andExpect(jsonPath("$.data.selected.statistics.harvest.totalAmountKg", equalTo(30.0)))
            .andExpect(jsonPath("$.data.selected.statistics.harvest.amountCoverage.recordedCount", equalTo(2)))
            .andExpect(jsonPath("$.data.selected.statistics.harvest.amountCoverage.targetCount", equalTo(3)))
            .andExpect(jsonPath("$.data.previous.id", equalTo(previousReportId.toString())))

        verify(service).getDetail(memberId, selectedReportId)
    }

    @Test
    fun `current without principal returns unauthorized without service call`() {
        mockMvc.perform(
            get("/api/v1/farming-reports/current")
                .param("farmId", farmId.toString())
                .param("cropId", cropId.toString()),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code", equalTo("AUTH_001")))

        verifyNoInteractions(service)
    }

    @Test
    fun `current with malformed principal returns unauthorized without service call`() {
        mockMvc.perform(
            get("/api/v1/farming-reports/current")
                .with(authenticatedMember("not-a-uuid"))
                .param("farmId", farmId.toString())
                .param("cropId", cropId.toString()),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code", equalTo("AUTH_001")))

        verifyNoInteractions(service)
    }

    @Test
    fun `current rejects missing farm id before service call`() {
        mockMvc.perform(
            get("/api/v1/farming-reports/current")
                .with(authenticatedMember(memberId.toString()))
                .param("cropId", cropId.toString()),
        )
            .andExpect(status().isBadRequest)

        verifyNoInteractions(service)
    }

    @Test
    fun `current rejects missing crop id before service call`() {
        mockMvc.perform(
            get("/api/v1/farming-reports/current")
                .with(authenticatedMember(memberId.toString()))
                .param("farmId", farmId.toString()),
        )
            .andExpect(status().isBadRequest)

        verifyNoInteractions(service)
    }

    @Test
    fun `list maps invalid input business error to bad request`() {
        val condition = searchCondition(size = 101)
        `when`(service.listCompleted(condition))
            .thenThrow(BusinessException(ErrorCode.INVALID_INPUT))

        mockMvc.perform(
            get("/api/v1/farming-reports")
                .with(authenticatedMember(memberId.toString()))
                .param("farmId", farmId.toString())
                .param("cropId", cropId.toString())
                .param("size", "101"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code", equalTo("COMMON_001")))

        verify(service).listCompleted(condition)
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

    private fun searchCondition(
        cursor: String? = null,
        size: Int = 20,
    ) = FarmingCycleReportSearchCondition(
        memberId = memberId,
        farmId = farmId,
        cropId = cropId,
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

    private fun selectedSnapshot() = snapshot(
        id = selectedReportId,
        status = FarmingCycleReportStatus.ACTIVE,
        startsAt = LocalDateTime.of(2026, 7, 1, 9, 0),
        endsAt = null,
        statistics = representativeStatistics(),
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
        ),
        watering = WateringStatistics(
            recordCount = 2,
            workedDayCount = 2,
        ),
        harvest = HarvestStatistics(
            recordCount = 3,
            totalAmountKg = BigDecimal("30.0"),
            amountCoverage = Coverage(recordedCount = 2, targetCount = 3),
        ),
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
