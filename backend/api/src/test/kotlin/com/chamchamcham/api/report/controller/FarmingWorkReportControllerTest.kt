package com.chamchamcham.api.report.controller

import com.chamchamcham.api.exception.GlobalExceptionHandler
import com.chamchamcham.application.coaching.reportfeedback.lifecycle.ReportFeedbackItemResult
import com.chamchamcham.application.coaching.reportfeedback.lifecycle.ReportFeedbackResultContent
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.application.report.FarmingWorkReportQueryService
import com.chamchamcham.application.report.FarmingWorkReportResult
import com.chamchamcham.application.report.FarmingWorkReportSearchCondition
import com.chamchamcham.application.security.TokenProvider
import com.chamchamcham.domain.coaching.reportfeedback.ReportFeedbackStatus
import com.chamchamcham.domain.farming.WorkType
import com.chamchamcham.domain.report.CommonOnlyStatistics
import com.chamchamcham.domain.report.FarmingCycleReportStatus
import com.chamchamcham.domain.report.WateringStatistics
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.nullValue
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
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@WebMvcTest(FarmingWorkReportController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler::class)
class FarmingWorkReportControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    private val memberId = id("001")
    private val farmId = id("101")
    private val cropId = id("201")
    private val reportId = id("301")
    private val finalHarvestRecordId = id("401")
    private val startsAt = LocalDateTime.of(2026, 3, 1, 9, 0)
    private val endsAt = LocalDateTime.of(2026, 8, 1, 9, 0)

    @MockBean private lateinit var service: FarmingWorkReportQueryService
    @MockBean private lateinit var tokenProvider: TokenProvider

    @Test
    fun `list binds optional filters cursor and size and returns work card shape`() {
        val condition = FarmingWorkReportSearchCondition(
            memberId = memberId,
            farmId = farmId,
            cropId = cropId,
            workType = WorkType.WATERING,
            cursor = "cursor-1",
            size = 2,
        )
        `when`(service.list(condition)).thenReturn(
            FarmingWorkReportResult.Page(
                items = listOf(workItem()),
                nextCursor = "cursor-2",
            ),
        )

        mockMvc.perform(
            get("/api/v1/farming-reports/work-items")
                .with(authenticatedMember(memberId.toString()))
                .param("farmId", farmId.toString())
                .param("cropId", cropId.toString())
                .param("workType", "WATERING")
                .param("cursor", "cursor-1")
                .param("size", "2"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items[0].reportId", equalTo(reportId.toString())))
            .andExpect(jsonPath("$.data.items[0].status", equalTo("COMPLETED")))
            .andExpect(jsonPath("$.data.items[0].workType", equalTo("WATERING")))
            .andExpect(jsonPath("$.data.items[0].workTypeLabel", equalTo("물 주기")))
            .andExpect(jsonPath("$.data.items[0].farmName", equalTo("약초농장")))
            .andExpect(jsonPath("$.data.items[0].cropName", equalTo("황기")))
            .andExpect(jsonPath("$.data.items[0].recordCount", equalTo(3)))
            .andExpect(jsonPath("$.data.items[0].thumbnailUrl", nullValue()))
            .andExpect(jsonPath("$.data.items[0].finalHarvestRecordId").doesNotExist())
            .andExpect(jsonPath("$.data.nextCursor", equalTo("cursor-2")))

        verify(service).list(condition)
    }

    @Test
    fun `list returns active work card with nullable end date`() {
        val condition = FarmingWorkReportSearchCondition(
            memberId = memberId,
            farmId = null,
            cropId = null,
            workType = null,
            cursor = null,
            size = 20,
        )
        `when`(service.list(condition)).thenReturn(
            FarmingWorkReportResult.Page(
                items = listOf(
                    workItem(
                        status = FarmingCycleReportStatus.ACTIVE,
                        endsAt = null,
                        finalHarvestRecordId = null,
                    ),
                ),
                nextCursor = null,
            ),
        )

        mockMvc.perform(
            get("/api/v1/farming-reports/work-items")
                .with(authenticatedMember(memberId.toString())),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items[0].status", equalTo("ACTIVE")))
            .andExpect(jsonPath("$.data.items[0].endsAt", nullValue()))
            .andExpect(jsonPath("$.data.items[0].thumbnailUrl", nullValue()))
    }

    @Test
    fun `list accepts omitted filters with default size`() {
        val condition = FarmingWorkReportSearchCondition(
            memberId = memberId,
            farmId = null,
            cropId = null,
            workType = null,
            cursor = null,
            size = 20,
        )
        `when`(service.list(condition)).thenReturn(FarmingWorkReportResult.Page(emptyList(), null))

        mockMvc.perform(
            get("/api/v1/farming-reports/work-items")
                .with(authenticatedMember(memberId.toString())),
        ).andExpect(status().isOk)

        verify(service).list(condition)
    }

    @Test
    fun `list rejects sizes outside one through one hundred`() {
        listOf("0", "101").forEach { size ->
            mockMvc.perform(
                get("/api/v1/farming-reports/work-items")
                    .with(authenticatedMember(memberId.toString()))
                    .param("size", size),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code", equalTo("COMMON_001")))
        }

        verifyNoInteractions(service)
    }

    @Test
    fun `list rejects an unknown work type at the API boundary`() {
        mockMvc.perform(
            get("/api/v1/farming-reports/work-items")
                .with(authenticatedMember(memberId.toString()))
                .param("workType", "IRRIGATION"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code", equalTo("COMMON_001")))

        verifyNoInteractions(service)
    }

    @Test
    fun `list translates an invalid cursor from the query service to invalid input`() {
        val condition = FarmingWorkReportSearchCondition(
            memberId = memberId,
            farmId = null,
            cropId = null,
            workType = null,
            cursor = "not-base64",
            size = 20,
        )
        `when`(service.list(condition))
            .thenThrow(BusinessException(ErrorCode.INVALID_CURSOR))

        mockMvc.perform(
            get("/api/v1/farming-reports/work-items")
                .with(authenticatedMember(memberId.toString()))
                .param("cursor", "not-base64"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code", equalTo("COMMON_001")))

        verify(service).list(condition)
    }

    @Test
    fun `detail exposes common and only the selected typed statistics branch`() {
        `when`(service.getDetail(memberId, reportId, WorkType.WATERING)).thenReturn(
            detail(
                statistics = FarmingWorkReportResult.WorkStatistics(
                    common = common(recordCount = 3),
                    watering = WateringStatistics(recordCount = 3, workedDayCount = 2),
                ),
                feedback = readyFeedback(),
            ),
        )

        mockMvc.perform(
            get(
                "/api/v1/farming-reports/{reportId}/work-types/{workType}",
                reportId,
                "WATERING",
            ).with(authenticatedMember(memberId.toString())),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status", equalTo("COMPLETED")))
            .andExpect(jsonPath("$.data.workType", equalTo("WATERING")))
            .andExpect(jsonPath("$.data.workTypeLabel", equalTo("물 주기")))
            .andExpect(jsonPath("$.data.statistics.common.recordCount", equalTo(3)))
            .andExpect(jsonPath("$.data.statistics.watering.recordCount", equalTo(3)))
            .andExpect(jsonPath("$.data.statistics.planting").doesNotExist())
            .andExpect(jsonPath("$.data.statistics.fertilizing").doesNotExist())
            .andExpect(jsonPath("$.data.statistics.pestControl").doesNotExist())
            .andExpect(jsonPath("$.data.statistics.weeding").doesNotExist())
            .andExpect(jsonPath("$.data.statistics.harvest").doesNotExist())
            .andExpect(jsonPath("$.data.feedback.status", equalTo("READY")))
            .andExpect(jsonPath("$.data.feedback.content.summary", equalTo("물 주기를 잘 이어갔어요.")))
            .andExpect(jsonPath("$.data.feedback.content.comparisons").isArray)
            .andExpect(
                jsonPath(
                    "$.data.feedback.content.comparisons[0].text",
                    equalTo("직전 재배보다 물 주기 기록이 한 번 늘었어요."),
                ),
            )
            .andExpect(jsonPath("$.data.feedback.content.strengths[0].text", equalTo("흙 상태를 살폈어요.")))

        verify(service).getDetail(memberId, reportId, WorkType.WATERING)
    }

    @Test
    fun `detail returns active statistics with null feedback`() {
        `when`(service.getDetail(memberId, reportId, WorkType.WATERING)).thenReturn(
            detail(
                status = FarmingCycleReportStatus.ACTIVE,
                endsAt = null,
                feedback = null,
            ),
        )

        mockMvc.perform(
            get(
                "/api/v1/farming-reports/{reportId}/work-types/{workType}",
                reportId,
                "WATERING",
            ).with(authenticatedMember(memberId.toString())),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status", equalTo("ACTIVE")))
            .andExpect(jsonPath("$.data.endsAt", nullValue()))
            .andExpect(jsonPath("$.data.statistics.common.recordCount", equalTo(3)))
            .andExpect(jsonPath("$.data.feedback", nullValue()))
    }

    @Test
    fun `detail exposes common only work without a typed branch`() {
        `when`(service.getDetail(memberId, reportId, WorkType.PRUNING)).thenReturn(
            detail(
                workType = WorkType.PRUNING,
                workTypeLabel = "가지 정리",
                statistics = FarmingWorkReportResult.WorkStatistics(common = common(recordCount = 2)),
                feedback = FarmingWorkReportResult.FeedbackStatus(ReportFeedbackStatus.PENDING, null),
            ),
        )

        mockMvc.perform(
            get(
                "/api/v1/farming-reports/{reportId}/work-types/{workType}",
                reportId,
                "PRUNING",
            ).with(authenticatedMember(memberId.toString())),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.workTypeLabel", equalTo("가지 정리")))
            .andExpect(jsonPath("$.data.statistics.common.recordCount", equalTo(2)))
            .andExpect(jsonPath("$.data.statistics.planting").doesNotExist())
            .andExpect(jsonPath("$.data.statistics.watering").doesNotExist())
            .andExpect(jsonPath("$.data.statistics.fertilizing").doesNotExist())
            .andExpect(jsonPath("$.data.statistics.pestControl").doesNotExist())
            .andExpect(jsonPath("$.data.statistics.weeding").doesNotExist())
            .andExpect(jsonPath("$.data.statistics.harvest").doesNotExist())
    }

    @Test
    fun `detail keeps feedback content null for pending and failed statuses`() {
        listOf(ReportFeedbackStatus.PENDING, ReportFeedbackStatus.FAILED).forEach { feedbackStatus ->
            `when`(service.getDetail(memberId, reportId, WorkType.WATERING)).thenReturn(
                detail(
                    feedback = FarmingWorkReportResult.FeedbackStatus(feedbackStatus, null),
                ),
            )

            mockMvc.perform(
                get(
                    "/api/v1/farming-reports/{reportId}/work-types/{workType}",
                    reportId,
                    "WATERING",
                ).with(authenticatedMember(memberId.toString())),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.feedback.status", equalTo(feedbackStatus.name)))
                .andExpect(jsonPath("$.data.feedback.content", nullValue()))
        }
    }

    @Test
    fun `detail rejects an unknown path work type before service call`() {
        mockMvc.perform(
            get(
                "/api/v1/farming-reports/{reportId}/work-types/{workType}",
                reportId,
                "IRRIGATION",
            ).with(authenticatedMember(memberId.toString())),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code", equalTo("COMMON_001")))

        verifyNoInteractions(service)
    }

    @Test
    fun `detail maps missing work to report scoped not found error`() {
        `when`(service.getDetail(memberId, reportId, WorkType.WATERING))
            .thenThrow(BusinessException(ErrorCode.WORK_REPORT_NOT_FOUND))

        mockMvc.perform(
            get(
                "/api/v1/farming-reports/{reportId}/work-types/{workType}",
                reportId,
                "WATERING",
            ).with(authenticatedMember(memberId.toString())),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code", equalTo("REPORT_002")))
    }

    private fun workItem(
        status: FarmingCycleReportStatus = FarmingCycleReportStatus.COMPLETED,
        endsAt: LocalDateTime? = this.endsAt,
        finalHarvestRecordId: UUID? = this.finalHarvestRecordId,
    ) = FarmingWorkReportResult.Item(
        reportId = reportId,
        status = status,
        farmId = farmId,
        farmName = "약초농장",
        cropId = cropId,
        cropName = "황기",
        startsAt = startsAt,
        endsAt = endsAt,
        finalHarvestRecordId = finalHarvestRecordId,
        workType = WorkType.WATERING,
        workTypeLabel = "물 주기",
        recordCount = 3,
        lastWorkedOn = LocalDate.of(2026, 7, 30),
        thumbnailUrl = null,
    )

    private fun detail(
        workType: WorkType = WorkType.WATERING,
        workTypeLabel: String = "물 주기",
        statistics: FarmingWorkReportResult.WorkStatistics = FarmingWorkReportResult.WorkStatistics(
            common = common(recordCount = 3),
            watering = WateringStatistics(recordCount = 3),
        ),
        status: FarmingCycleReportStatus = FarmingCycleReportStatus.COMPLETED,
        endsAt: LocalDateTime? = this.endsAt,
        feedback: FarmingWorkReportResult.FeedbackStatus?,
    ) = FarmingWorkReportResult.Detail(
        reportId = reportId,
        status = status,
        workType = workType,
        workTypeLabel = workTypeLabel,
        farmId = farmId,
        farmName = "약초농장",
        cropId = cropId,
        cropName = "황기",
        startsAt = startsAt,
        endsAt = endsAt,
        statistics = statistics,
        feedback = feedback,
    )

    private fun readyFeedback() = FarmingWorkReportResult.FeedbackStatus(
        status = ReportFeedbackStatus.READY,
        content = ReportFeedbackResultContent(
            summary = "물 주기를 잘 이어갔어요.",
            comparisons = listOf(ReportFeedbackItemResult("직전 재배보다 물 주기 기록이 한 번 늘었어요.")),
            strengths = listOf(ReportFeedbackItemResult("흙 상태를 살폈어요.")),
            improvements = emptyList(),
            nextActions = listOf(ReportFeedbackItemResult("내일 흙을 확인하세요.")),
        ),
    )

    private fun common(recordCount: Int) = CommonOnlyStatistics(
        recordCount = recordCount,
        workedDayCount = recordCount,
    )

    private fun authenticatedMember(id: String): RequestPostProcessor = RequestPostProcessor { request ->
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
            id,
            null,
            listOf(SimpleGrantedAuthority("ROLE_USER")),
        )
        request
    }

    private fun id(suffix: String): UUID =
        UUID.fromString("00000000-0000-0000-0000-000000000$suffix")
}
