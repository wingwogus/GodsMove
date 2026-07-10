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
                    detailRequired = true,
                    fields = listOf(
                        WorkTypeResult.FieldSummary(
                            name = "propagationMethod",
                            type = FieldValueType.ENUM,
                            required = true,
                            options = listOf(
                                WorkTypeResult.EnumOptionSummary("SEED", "종자"),
                                WorkTypeResult.EnumOptionSummary("CUTTING", "삽목")
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
                    code = "HARVEST",
                    label = "수확",
                    detailRequired = true,
                    fields = listOf(
                        WorkTypeResult.FieldSummary(
                            name = "isFinalHarvest",
                            type = FieldValueType.BOOLEAN,
                            required = true,
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
                            name = "materialCategory",
                            type = FieldValueType.ENUM,
                            required = true,
                            options = listOf(WorkTypeResult.EnumOptionSummary("COMPOUND_FERTILIZER", "복합비료"))
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
            .andExpect(jsonPath("$.data", hasSize<Any>(5)))
            .andExpect(jsonPath("$.data[0].code", equalTo("PLANTING")))
            .andExpect(jsonPath("$.data[0].label", equalTo("파종/정식")))
            .andExpect(jsonPath("$.data[0].detailRequired", equalTo(true)))
            .andExpect(jsonPath("$.data[0].fields[0].name", equalTo("propagationMethod")))
            .andExpect(jsonPath("$.data[0].fields[0].type", equalTo("ENUM")))
            .andExpect(jsonPath("$.data[0].fields[0].options[1].code", equalTo("CUTTING")))
            .andExpect(jsonPath("$.data[0].fields[0].options[1].label", equalTo("삽목")))
            .andExpect(jsonPath("$.data[1].code", equalTo("WATERING")))
            .andExpect(jsonPath("$.data[1].fields[0].required", equalTo(false)))
            .andExpect(jsonPath("$.data[2].code", equalTo("HARVEST")))
            .andExpect(jsonPath("$.data[2].detailRequired", equalTo(true)))
            .andExpect(jsonPath("$.data[2].fields[0].required", equalTo(true)))
            .andExpect(jsonPath("$.data[2].fields[0].type", equalTo("BOOLEAN")))
            .andExpect(jsonPath("$.data[3].code", equalTo("FERTILIZING")))
            .andExpect(jsonPath("$.data[3].detailRequired", equalTo(true)))
            .andExpect(jsonPath("$.data[3].fields[0].name", equalTo("materialCategory")))
            .andExpect(jsonPath("$.data[3].fields[0].options[0].label", equalTo("복합비료")))
            .andExpect(jsonPath("$.data[4].code", equalTo("PRUNING")))
            .andExpect(jsonPath("$.data[4].detailRequired", equalTo(false)))
            .andExpect(jsonPath("$.data[4].fields", hasSize<Any>(0)))
    }
}
