package com.chamchamcham.api.policy.controller

import com.chamchamcham.api.common.ApiResponse
import com.chamchamcham.api.policy.dto.sync.PolicySyncJobDetailResponse
import com.chamchamcham.api.policy.dto.sync.PolicySyncJobSummaryResponse
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.application.policy.sync.PolicySyncAsyncRunner
import com.chamchamcham.application.policy.sync.PolicySyncService
import com.chamchamcham.domain.policy.PolicySyncJobStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/admin/policies/sync-jobs")
class AdminPolicySyncController(
    private val policySyncService: PolicySyncService,
    private val policySyncAsyncRunner: PolicySyncAsyncRunner
) {
    @PostMapping
    fun createJob(
        @AuthenticationPrincipal principal: Any?
    ): ResponseEntity<ApiResponse<PolicySyncJobSummaryResponse>> {
        val result = policySyncService.createAdminSyncJob(parseMemberId(principal))
        if (result.status == PolicySyncJobStatus.RUNNING) {
            policySyncAsyncRunner.run(result.jobId)
        }
        return ResponseEntity.ok(ApiResponse.ok(PolicySyncJobSummaryResponse.from(result)))
    }

    @GetMapping("/{jobId}")
    fun getJob(
        @PathVariable jobId: UUID
    ): ResponseEntity<ApiResponse<PolicySyncJobDetailResponse>> {
        val result = policySyncService.getJob(jobId)
        return ResponseEntity.ok(ApiResponse.ok(PolicySyncJobDetailResponse.from(result)))
    }

    private fun parseMemberId(principal: Any?): UUID {
        val memberId = when (principal) {
            is String -> principal
            is UserDetails -> principal.username
            else -> null
        }
        if (memberId.isNullOrBlank()) {
            throw BusinessException(ErrorCode.UNAUTHORIZED)
        }
        return runCatching { UUID.fromString(memberId) }
            .getOrElse { throw BusinessException(ErrorCode.UNAUTHORIZED) }
    }
}
