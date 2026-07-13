package com.chamchamcham.api.pesticide.controller

import com.chamchamcham.api.exception.GlobalExceptionHandler
import com.chamchamcham.application.pesticide.sync.PesticideProbeResult
import com.chamchamcham.application.pesticide.sync.PesticideSyncAsyncRunner
import com.chamchamcham.application.pesticide.sync.PesticideSyncResult
import com.chamchamcham.application.pesticide.sync.PesticideSyncService
import com.chamchamcham.application.pesticide.sync.PsisPesticideRow
import com.chamchamcham.application.security.TokenProvider
import com.chamchamcham.domain.pesticide.PesticideSyncJobStatus
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

@WebMvcTest(AdminPesticideSyncController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler::class)
class AdminPesticideSyncControllerTest(
    @Autowired private val mockMvc: MockMvc
) {
    private val adminMemberId = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val jobId = UUID.fromString("00000000-0000-0000-0000-000000000301")

    @MockBean
    private lateinit var pesticideSyncService: PesticideSyncService

    @MockBean
    private lateinit var pesticideSyncAsyncRunner: PesticideSyncAsyncRunner

    @MockBean
    private lateinit var tokenProvider: TokenProvider

    @Test
    fun `create sync job calls service and starts async runner`() {
        `when`(pesticideSyncService.createSyncJob(adminMemberId))
            .thenReturn(PesticideSyncResult.JobSummary(jobId = jobId, status = PesticideSyncJobStatus.RUNNING))

        mockMvc.perform(
            post("/api/v1/admin/pesticide-sync")
                .with(authenticatedMember(adminMemberId.toString(), "ROLE_ADMIN"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.jobId", equalTo(jobId.toString())))
            .andExpect(jsonPath("$.data.status", equalTo("RUNNING")))

        verify(pesticideSyncService).createSyncJob(adminMemberId)
        verify(pesticideSyncAsyncRunner).run(jobId)
    }

    @Test
    fun `get sync job returns counters and status`() {
        val startedAt = LocalDateTime.of(2026, 1, 1, 9, 0)
        val finishedAt = LocalDateTime.of(2026, 1, 1, 9, 5)
        `when`(pesticideSyncService.getJob(jobId)).thenReturn(
            PesticideSyncResult.JobDetail(
                jobId = jobId,
                status = PesticideSyncJobStatus.SUCCEEDED,
                totalCount = 143912,
                fetchedRowCount = 143912,
                createdApplicationCount = 140000,
                errorMessage = null,
                startedAt = startedAt,
                finishedAt = finishedAt,
            )
        )

        mockMvc.perform(get("/api/v1/admin/pesticide-sync/{jobId}", jobId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.jobId", equalTo(jobId.toString())))
            .andExpect(jsonPath("$.data.status", equalTo("SUCCEEDED")))
            .andExpect(jsonPath("$.data.totalCount", equalTo(143912)))
            .andExpect(jsonPath("$.data.fetchedRowCount", equalTo(143912)))
            .andExpect(jsonPath("$.data.createdApplicationCount", equalTo(140000)))
            .andExpect(jsonPath("$.data.errorMessage").doesNotExist())
            .andExpect(jsonPath("$.data.startedAt", equalTo("2026-01-01T09:00:00")))
            .andExpect(jsonPath("$.data.finishedAt", equalTo("2026-01-01T09:05:00")))

        verify(pesticideSyncService).getJob(jobId)
    }

    @Test
    fun `probe returns the mocked service result`() {
        val mapped = PsisPesticideRow(
            itemName = "만코제브 수화제",
            brandName = "가가방",
            cropName = "감자",
            pestName = "역병",
            activeIngredient = null,
            formulation = null,
            usageCategory = null,
            humanToxicity = null,
            fishToxicity = null,
            manufacturer = null,
            dilutionRate = "500배",
            usageAmount = null,
            usageTiming = null,
            maxUsageCount = null,
        )
        `when`(pesticideSyncService.probe(10)).thenReturn(
            PesticideProbeResult(
                errorCode = null,
                errorMsg = null,
                totalCount = 143912,
                itemCount = 1,
                distinctTagNames = listOf("cropName", "pestiKorName"),
                sampleRawItem = mapOf("cropName" to "감자", "pestiKorName" to "만코제브 수화제"),
                requiredKeyResolution = mapOf("itemName" to true, "cropName" to true, "pestName" to true),
                mapped = mapped,
            )
        )

        mockMvc.perform(post("/api/v1/admin/pesticide-sync/probe"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.errorCode").doesNotExist())
            .andExpect(jsonPath("$.data.errorMsg").doesNotExist())
            .andExpect(jsonPath("$.data.totalCount", equalTo(143912)))
            .andExpect(jsonPath("$.data.itemCount", equalTo(1)))
            .andExpect(jsonPath("$.data.distinctTagNames[0]", equalTo("cropName")))
            .andExpect(jsonPath("$.data.requiredKeyResolution.itemName", equalTo(true)))
            .andExpect(jsonPath("$.data.mapped.itemName", equalTo("만코제브 수화제")))

        verify(pesticideSyncService).probe(10)
    }

    @Test
    fun `probe rejects rows outside 1 to 50`() {
        mockMvc.perform(post("/api/v1/admin/pesticide-sync/probe").param("rows", "0"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code", equalTo("COMMON_001")))
    }

    @Test
    fun `probe rejects rows above 50`() {
        mockMvc.perform(post("/api/v1/admin/pesticide-sync/probe").param("rows", "51"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code", equalTo("COMMON_001")))
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
