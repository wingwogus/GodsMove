package com.chamchamcham.api.farming.controller

import com.chamchamcham.api.exception.GlobalExceptionHandler
import com.chamchamcham.application.farming.FieldValueType
import com.chamchamcham.application.farming.WorkTypeCatalogService
import com.chamchamcham.application.farming.WorkTypeResult
import com.chamchamcham.application.security.TokenProvider
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(WorkTypeController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler::class)
class WorkTypeControllerTest(
    @Autowired private val mockMvc: MockMvc
) {
    @MockBean
    private lateinit var workTypeCatalogService: WorkTypeCatalogService

    @MockBean
    private lateinit var tokenProvider: TokenProvider

    @Test
    fun `list work types returns full catalog with representative work types`() {
        `when`(workTypeCatalogService.listWorkTypes()).thenReturn(
            listOf(
                WorkTypeResult.WorkTypeSummary(
                    code = "PLANTING",
                    label = "파종/정식",
                    detailRequired = false,
                    fields = listOf(
                        WorkTypeResult.FieldSummary(
                            name = "seedSource",
                            type = FieldValueType.ENUM,
                            required = false,
                            options = listOf(
                                WorkTypeResult.EnumOptionSummary("SELF_COLLECTED", "자가채종"),
                                WorkTypeResult.EnumOptionSummary("PURCHASED", "구매")
                            )
                        )
                    )
                ),
                WorkTypeResult.WorkTypeSummary(
                    code = "WATERING",
                    label = "관수",
                    detailRequired = false,
                    fields = listOf(
                        WorkTypeResult.FieldSummary(
                            name = "irrigationAmount",
                            type = FieldValueType.ENUM,
                            required = false,
                            options = emptyList()
                        )
                    )
                ),
                WorkTypeResult.WorkTypeSummary(
                    code = "FERTILIZING",
                    label = "시비",
                    detailRequired = true,
                    fields = listOf(
                        WorkTypeResult.FieldSummary(
                            name = "materialName",
                            type = FieldValueType.STRING,
                            required = true,
                            options = emptyList()
                        )
                    )
                ),
                WorkTypeResult.WorkTypeSummary(
                    code = "PRUNING",
                    label = "전정",
                    detailRequired = false,
                    fields = emptyList()
                )
            )
        )

        mockMvc.perform(get("/api/v1/work-types"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data", hasSize<Any>(4)))
            .andExpect(jsonPath("$.data[0].code", equalTo("PLANTING")))
            .andExpect(jsonPath("$.data[0].label", equalTo("파종/정식")))
            .andExpect(jsonPath("$.data[0].detailRequired", equalTo(false)))
            .andExpect(jsonPath("$.data[0].fields[0].name", equalTo("seedSource")))
            .andExpect(jsonPath("$.data[0].fields[0].type", equalTo("ENUM")))
            .andExpect(jsonPath("$.data[0].fields[0].options[1].code", equalTo("PURCHASED")))
            .andExpect(jsonPath("$.data[0].fields[0].options[1].label", equalTo("구매")))
            .andExpect(jsonPath("$.data[1].code", equalTo("WATERING")))
            .andExpect(jsonPath("$.data[1].fields[0].required", equalTo(false)))
            .andExpect(jsonPath("$.data[2].code", equalTo("FERTILIZING")))
            .andExpect(jsonPath("$.data[2].detailRequired", equalTo(true)))
            .andExpect(jsonPath("$.data[2].fields[0].required", equalTo(true)))
            .andExpect(jsonPath("$.data[3].code", equalTo("PRUNING")))
            .andExpect(jsonPath("$.data[3].detailRequired", equalTo(false)))
            .andExpect(jsonPath("$.data[3].fields", hasSize<Any>(0)))
    }
}
