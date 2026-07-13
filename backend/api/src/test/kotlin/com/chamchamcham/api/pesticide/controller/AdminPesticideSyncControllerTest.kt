package com.chamchamcham.api.pesticide.controller

import com.chamchamcham.api.exception.GlobalExceptionHandler
import com.chamchamcham.application.pesticide.sync.PesticideProbeResult
import com.chamchamcham.application.pesticide.sync.PesticideSyncService
import com.chamchamcham.application.pesticide.sync.PsisPesticideRow
import com.chamchamcham.application.security.TokenProvider
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(AdminPesticideSyncController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler::class)
class AdminPesticideSyncControllerTest(
    @Autowired private val mockMvc: MockMvc
) {
    @MockBean
    private lateinit var pesticideSyncService: PesticideSyncService

    @MockBean
    private lateinit var tokenProvider: TokenProvider

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
                resultCode = "00",
                resultMsg = "NORMAL_SERVICE",
                totalCount = 137877,
                itemCount = 1,
                distinctTagNames = listOf("cropNm", "prdtNm"),
                sampleRawItem = mapOf("cropNm" to "감자", "prdtNm" to "만코제브 수화제"),
                requiredKeyResolution = mapOf("itemName" to true, "cropName" to true, "pestName" to true),
                mapped = mapped,
            )
        )

        mockMvc.perform(post("/api/v1/admin/pesticide-sync/probe"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.resultCode", equalTo("00")))
            .andExpect(jsonPath("$.data.resultMsg", equalTo("NORMAL_SERVICE")))
            .andExpect(jsonPath("$.data.totalCount", equalTo(137877)))
            .andExpect(jsonPath("$.data.itemCount", equalTo(1)))
            .andExpect(jsonPath("$.data.distinctTagNames[0]", equalTo("cropNm")))
            .andExpect(jsonPath("$.data.requiredKeyResolution.itemName", equalTo(true)))
            .andExpect(jsonPath("$.data.mapped.itemName", equalTo("만코제브 수화제")))

        verify(pesticideSyncService).probe(10)
    }

    @Test
    fun `probe rejects rows outside 1 to 100`() {
        mockMvc.perform(post("/api/v1/admin/pesticide-sync/probe").param("rows", "0"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code", equalTo("COMMON_001")))
    }
}
