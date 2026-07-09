package com.chamchamcham.api.policy.controller

import com.chamchamcham.api.exception.GlobalExceptionHandler
import com.chamchamcham.application.policy.sync.PolicySyncAsyncRunner
import com.chamchamcham.application.policy.sync.PolicySyncResult
import com.chamchamcham.application.policy.sync.PolicySyncService
import com.chamchamcham.application.security.TokenProvider
import com.chamchamcham.domain.policy.PolicySource
import com.chamchamcham.domain.policy.PolicySyncJobStatus
import com.chamchamcham.domain.policy.PolicySyncTriggerType
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime
import java.util.UUID

@WebMvcTest(AdminPolicySyncController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler::class)
class AdminPolicySyncControllerTest(
    @Autowired private val mockMvc: MockMvc
) {
    private val adminMemberId = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val jobId = UUID.fromString("00000000-0000-0000-0000-000000000301")

    @MockBean
    private lateinit var policySyncService: PolicySyncService

    @MockBean
    private lateinit var policySyncAsyncRunner: PolicySyncAsyncRunner

    @MockBean
    private lateinit var tokenProvider: TokenProvider

    @Test
    fun `create admin sync job calls service and starts async runner when running`() {
        `when`(policySyncService.createAdminSyncJob(adminMemberId))
            .thenReturn(
                PolicySyncResult.JobSummary(
                    jobId = jobId,
                    status = PolicySyncJobStatus.RUNNING,
                    targetYear = "2026"
                )
            )

        mockMvc.perform(
            post("/api/v1/admin/policy-sync-jobs")
                .with(authenticatedMember(adminMemberId.toString(), "ROLE_ADMIN"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.jobId", equalTo(jobId.toString())))
            .andExpect(jsonPath("$.data.status", equalTo("RUNNING")))
            .andExpect(jsonPath("$.data.targetYear", equalTo("2026")))

        verify(policySyncService).createAdminSyncJob(adminMemberId)
        verify(policySyncAsyncRunner).run(jobId)
    }

    @Test
    fun `get admin sync job returns counters and status`() {
        val startedAt = LocalDateTime.of(2026, 1, 1, 9, 0)
        val finishedAt = LocalDateTime.of(2026, 1, 1, 9, 5)
        `when`(policySyncService.getJob(jobId))
            .thenReturn(
                PolicySyncResult.JobDetail(
                    jobId = jobId,
                    source = PolicySource.NONGUP_EZ,
                    targetYear = "2026",
                    triggerType = PolicySyncTriggerType.ADMIN,
                    status = PolicySyncJobStatus.SUCCEEDED,
                    totalCount = 10,
                    syncedCount = 9,
                    detailSuccessCount = 8,
                    detailFailureCount = 1,
                    errorMessage = null,
                    startedAt = startedAt,
                    finishedAt = finishedAt
                )
            )

        mockMvc.perform(
            get("/api/v1/admin/policy-sync-jobs/{jobId}", jobId)
                .with(authenticatedMember(adminMemberId.toString(), "ROLE_ADMIN"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.jobId", equalTo(jobId.toString())))
            .andExpect(jsonPath("$.data.source", equalTo("NONGUP_EZ")))
            .andExpect(jsonPath("$.data.targetYear", equalTo("2026")))
            .andExpect(jsonPath("$.data.triggerType", equalTo("ADMIN")))
            .andExpect(jsonPath("$.data.status", equalTo("SUCCEEDED")))
            .andExpect(jsonPath("$.data.totalCount", equalTo(10)))
            .andExpect(jsonPath("$.data.syncedCount", equalTo(9)))
            .andExpect(jsonPath("$.data.detailSuccessCount", equalTo(8)))
            .andExpect(jsonPath("$.data.detailFailureCount", equalTo(1)))
            .andExpect(jsonPath("$.data.errorMessage").doesNotExist())
            .andExpect(jsonPath("$.data.startedAt", equalTo("2026-01-01T09:00:00")))
            .andExpect(jsonPath("$.data.finishedAt", equalTo("2026-01-01T09:05:00")))

        verify(policySyncService).getJob(jobId)
    }

    private fun authenticatedMember(memberId: String, role: String): RequestPostProcessor {
        return RequestPostProcessor { request ->
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(
                    memberId,
                    null,
                    listOf(SimpleGrantedAuthority(role))
                )
            request
        }
    }
}
