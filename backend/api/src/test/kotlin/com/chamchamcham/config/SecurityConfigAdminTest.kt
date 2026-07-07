package com.chamchamcham.config

import com.chamchamcham.api.exception.GlobalExceptionHandler
import com.chamchamcham.api.policy.controller.AdminPolicySyncController
import com.chamchamcham.api.security.CustomAccessDeniedHandler
import com.chamchamcham.api.security.CustomAuthenticationEntryPoint
import com.chamchamcham.application.policy.PolicySyncAsyncRunner
import com.chamchamcham.application.policy.PolicySyncResult
import com.chamchamcham.application.policy.PolicySyncService
import com.chamchamcham.application.security.TokenProvider
import com.chamchamcham.domain.policy.PolicySyncJobStatus
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@WebMvcTest(AdminPolicySyncController::class)
@Import(
    SecurityConfig::class,
    CustomAuthenticationEntryPoint::class,
    CustomAccessDeniedHandler::class,
    MDCLoggingFilter::class,
    GlobalExceptionHandler::class
)
class SecurityConfigAdminTest(
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
    fun `unauthenticated admin request returns 401`() {
        mockMvc.perform(post("/api/v1/admin/policy-sync-jobs"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    @WithMockUser(roles = ["USER"])
    fun `authenticated user role admin request returns 403`() {
        mockMvc.perform(post("/api/v1/admin/policy-sync-jobs"))
            .andExpect(status().isForbidden)
    }

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000002", roles = ["ADMIN"])
    fun `authenticated admin role admin request reaches controller`() {
        `when`(policySyncService.createAdminSyncJob(adminMemberId))
            .thenReturn(
                PolicySyncResult.JobSummary(
                    jobId = jobId,
                    status = PolicySyncJobStatus.SUCCEEDED,
                    targetYear = "2026"
                )
            )

        mockMvc.perform(post("/api/v1/admin/policy-sync-jobs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.jobId", equalTo(jobId.toString())))
    }
}
