package com.chamchamcham.api.farm.controller

import com.chamchamcham.api.common.ApiResponse
import com.chamchamcham.api.farm.dto.FarmRequests
import com.chamchamcham.api.farm.dto.FarmResponses
import com.chamchamcham.api.farm.dto.toCommandDraft
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.application.farm.FarmCommand
import com.chamchamcham.application.farm.FarmService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/farms")
class FarmController(
    private val farmService: FarmService
) {
    @GetMapping
    fun list(
        @AuthenticationPrincipal memberId: String?
    ): ResponseEntity<ApiResponse<List<FarmResponses.FarmResponse>>> =
        ResponseEntity.ok(
            ApiResponse.ok(
                farmService.list(parseMemberId(memberId)).map(FarmResponses.FarmResponse::from)
            )
        )

    @PostMapping
    fun create(
        @AuthenticationPrincipal memberId: String?,
        @Valid @RequestBody request: FarmRequests.SaveFarmRequest
    ): ResponseEntity<ApiResponse<FarmResponses.FarmResponse>> {
        val result = farmService.create(
            FarmCommand.Create(
                memberId = parseMemberId(memberId),
                draft = request.toCommandDraft(),
                cropIds = request.cropIds
            )
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(FarmResponses.FarmResponse.from(result)))
    }

    @PutMapping("/{farmId}")
    fun replace(
        @AuthenticationPrincipal memberId: String?,
        @PathVariable farmId: UUID,
        @Valid @RequestBody request: FarmRequests.SaveFarmRequest
    ): ResponseEntity<ApiResponse<FarmResponses.FarmResponse>> {
        val result = farmService.replace(
            FarmCommand.Replace(
                memberId = parseMemberId(memberId),
                farmId = farmId,
                draft = request.toCommandDraft(),
                cropIds = request.cropIds
            )
        )
        return ResponseEntity.ok(ApiResponse.ok(FarmResponses.FarmResponse.from(result)))
    }

    @DeleteMapping("/{farmId}")
    fun delete(
        @AuthenticationPrincipal memberId: String?,
        @PathVariable farmId: UUID
    ): ResponseEntity<Void> {
        farmService.delete(FarmCommand.Delete(memberId = parseMemberId(memberId), farmId = farmId))
        return ResponseEntity.noContent().build()
    }

    private fun parseMemberId(memberId: String?): UUID {
        if (memberId.isNullOrBlank()) {
            throw BusinessException(ErrorCode.UNAUTHORIZED)
        }
        return try {
            UUID.fromString(memberId)
        } catch (exception: IllegalArgumentException) {
            throw BusinessException(ErrorCode.UNAUTHORIZED)
        }
    }
}
