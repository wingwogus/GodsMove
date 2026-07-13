package com.chamchamcham.api.pesticide.controller

import com.chamchamcham.api.common.ApiResponse
import com.chamchamcham.api.pesticide.dto.PesticideResponses.PesticideProbeResponse
import com.chamchamcham.api.pesticide.dto.PesticideResponses.PesticideSyncJobDetailResponse
import com.chamchamcham.api.pesticide.dto.PesticideResponses.PesticideSyncJobSummaryResponse
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.application.pesticide.sync.PesticideSyncAsyncRunner
import com.chamchamcham.application.pesticide.sync.PesticideSyncService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * PSIS 데이터를 서버 DB로 동기화하는 관리자 트리거. 143k건 전체를 순회하므로 시간이 걸려
 * RUNNING 잡을 즉시 반환하고 실제 순회는 [PesticideSyncAsyncRunner]가 비동기로 수행한다
 * (PolicySyncJob과 동일한 패턴). 진행 상태는 GET .../{jobId}로 폴링한다.
 */
@RestController
@RequestMapping("/api/v1/admin/pesticide-sync")
class AdminPesticideSyncController(
    private val pesticideSyncService: PesticideSyncService,
    private val pesticideSyncAsyncRunner: PesticideSyncAsyncRunner,
) {
    @PostMapping
    fun createSyncJob(
        @AuthenticationPrincipal principal: Any?,
    ): ResponseEntity<ApiResponse<PesticideSyncJobSummaryResponse>> {
        val result = pesticideSyncService.createSyncJob(parseMemberId(principal))
        pesticideSyncAsyncRunner.run(result.jobId)
        return ResponseEntity.ok(ApiResponse.ok(PesticideSyncJobSummaryResponse.from(result)))
    }

    @GetMapping("/{jobId}")
    fun getJob(
        @PathVariable jobId: UUID,
    ): ResponseEntity<ApiResponse<PesticideSyncJobDetailResponse>> {
        val result = pesticideSyncService.getJob(jobId)
        return ResponseEntity.ok(ApiResponse.ok(PesticideSyncJobDetailResponse.from(result)))
    }

    @PostMapping("/probe")
    fun probe(
        @RequestParam(defaultValue = "10") rows: Int,
    ): ResponseEntity<ApiResponse<PesticideProbeResponse>> {
        if (rows !in 1..50) {
            throw BusinessException(ErrorCode.INVALID_INPUT)
        }
        val result = pesticideSyncService.probe(rows)
        return ResponseEntity.ok(ApiResponse.ok(PesticideProbeResponse.from(result)))
    }

    // pesticide sync job의 createdByMemberId는 nullable이라(관리 트리거는 인증 주체가 없어도 되는
    // 1회성 작업), 정책 동기화 컨트롤러와 달리 principal이 없거나 UUID 형식이 아니어도 401 대신 null로
    // 처리한다.
    private fun parseMemberId(principal: Any?): UUID? {
        val memberId = when (principal) {
            is String -> principal
            is UserDetails -> principal.username
            else -> null
        }
        return memberId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    }
}
