package com.chamchamcham.api.pesticide.controller

import com.chamchamcham.api.common.ApiResponse
import com.chamchamcham.application.pesticide.sync.PesticideSyncResult
import com.chamchamcham.application.pesticide.sync.PesticideSyncService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * PSIS 데이터를 서버 DB로 1회성 동기화하는 관리자 트리거. 137k건 전체를 순회하므로 시간이 걸릴 수
 * 있어 요청-응답을 동기로 유지한다(진행 상황 폴링이 필요해지면 PolicySyncJob처럼 비동기+상태추적으로
 * 확장 - YAGNI).
 */
@RestController
@RequestMapping("/api/v1/admin/pesticide-sync")
class AdminPesticideSyncController(
    private val pesticideSyncService: PesticideSyncService
) {
    @PostMapping
    fun sync(): ResponseEntity<ApiResponse<PesticideSyncResult>> {
        val result = pesticideSyncService.sync()
        return ResponseEntity.ok(ApiResponse.ok(result))
    }
}
