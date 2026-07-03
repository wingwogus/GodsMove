package com.chamchamcham.api.crop.controller

import com.chamchamcham.api.exception.GlobalExceptionHandler
import com.chamchamcham.application.crop.CropCatalogService
import com.chamchamcham.application.crop.CropResult
import com.chamchamcham.application.security.TokenProvider
import com.chamchamcham.domain.crop.CropUsePartCategory
import org.hamcrest.Matchers.equalTo
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
import java.util.UUID

@WebMvcTest(CropController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler::class)
class CropControllerTest(
    @Autowired private val mockMvc: MockMvc
) {
    @MockBean
    private lateinit var cropCatalogService: CropCatalogService

    @MockBean
    private lateinit var tokenProvider: TokenProvider

    @Test
    fun `list crops returns crop catalog`() {
        val cropId = UUID.fromString("00000000-0000-0000-0000-000000000101")
        `when`(cropCatalogService.listCrops())
            .thenReturn(
                listOf(
                    CropResult.CropSummary(
                        id = cropId,
                        externalNo = 1001,
                        name = "토마토",
                        usePartCategory = "FRUIT",
                        usePartCategoryLabel = "열매"
                    )
                )
            )

        mockMvc.perform(get("/api/v1/crops"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].id", equalTo(cropId.toString())))
            .andExpect(jsonPath("$.data[0].externalNo", equalTo(1001)))
            .andExpect(jsonPath("$.data[0].name", equalTo("토마토")))
            .andExpect(jsonPath("$.data[0].usePartCategory", equalTo("FRUIT")))
            .andExpect(jsonPath("$.data[0].usePartCategoryLabel", equalTo("열매")))
    }

    @Test
    fun `list categories returns crop use part categories`() {
        `when`(cropCatalogService.listCategories())
            .thenReturn(
                listOf(
                    CropResult.CategorySummary(
                        code = "FRUIT",
                        label = "열매"
                    )
                )
            )

        mockMvc.perform(get("/api/v1/crops/categories"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].code", equalTo("FRUIT")))
            .andExpect(jsonPath("$.data[0].label", equalTo("열매")))
    }

    @Test
    fun `list crops by category returns filtered crop catalog`() {
        val cropId = UUID.fromString("00000000-0000-0000-0000-000000000102")
        `when`(cropCatalogService.listCropsByCategory(CropUsePartCategory.ROOT_BARK))
            .thenReturn(
                listOf(
                    CropResult.CropSummary(
                        id = cropId,
                        externalNo = 422,
                        name = "참당귀",
                        usePartCategory = "ROOT_BARK",
                        usePartCategoryLabel = "뿌리·껍질"
                    )
                )
            )

        mockMvc.perform(get("/api/v1/crops/categories/ROOT_BARK/crops"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].id", equalTo(cropId.toString())))
            .andExpect(jsonPath("$.data[0].usePartCategory", equalTo("ROOT_BARK")))
            .andExpect(jsonPath("$.data[0].usePartCategoryLabel", equalTo("뿌리·껍질")))
    }
}
