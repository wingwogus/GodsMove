package com.chamchamcham.api.farm.controller

import com.chamchamcham.api.exception.GlobalExceptionHandler
import com.chamchamcham.application.crop.CropResult
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.application.farm.FarmCommand
import com.chamchamcham.application.farm.FarmResult
import com.chamchamcham.application.farm.FarmService
import com.chamchamcham.application.security.TokenProvider
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.willThrow
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.util.UUID

@WebMvcTest(FarmController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler::class)
class FarmControllerTest(
    @Autowired private val mockMvc: MockMvc
) {
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val farmId = UUID.fromString("00000000-0000-0000-0000-000000000101")
    private val cropId = UUID.fromString("00000000-0000-0000-0000-000000000201")

    @MockBean private lateinit var farmService: FarmService
    @MockBean private lateinit var tokenProvider: TokenProvider

    @Test
    fun `lists authenticated member farms with editable fields and crops`() {
        given(farmService.list(memberId)).willReturn(listOf(farmDetail()))

        mockMvc.perform(get("/api/v1/farms").with(authenticatedMember(memberId.toString())))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.farms[0].farmId", equalTo(farmId.toString())))
            .andExpect(jsonPath("$.data.farms[0].roadAddress", equalTo("강원특별자치도 횡성군 둔내면 1")))
            .andExpect(jsonPath("$.data.farms[0].boundaryCoordinates[0].latitude", equalTo(37.5)))
            .andExpect(jsonPath("$.data.farms[0].crops[0].id", equalTo(cropId.toString())))
    }

    @Test
    fun `creates farm with crop links and returns 201`() {
        given(farmService.create(expectedCreateCommand())).willReturn(farmDetail())

        mockMvc.perform(
            post("/api/v1/farms")
                .with(authenticatedMember(memberId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validFarmJson())
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.farmId", equalTo(farmId.toString())))
            .andExpect(jsonPath("$.data.crops[0].name", equalTo("황기")))

    }

    @Test
    fun `replaces farm with full draft`() {
        given(farmService.replace(expectedReplaceCommand())).willReturn(farmDetail())

        mockMvc.perform(
            put("/api/v1/farms/{farmId}", farmId)
                .with(authenticatedMember(memberId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validFarmJson())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.farmId", equalTo(farmId.toString())))

    }

    @Test
    fun `deletes unreferenced farm with no content`() {
        mockMvc.perform(
            delete("/api/v1/farms/{farmId}", farmId)
                .with(authenticatedMember(memberId.toString()))
        ).andExpect(status().isNoContent)

        verify(farmService).delete(FarmCommand.Delete(memberId = memberId, farmId = farmId))
    }

    @Test
    fun `rejects invalid farm payload`() {
        mockMvc.perform(
            post("/api/v1/farms")
                .with(authenticatedMember(memberId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validFarmJson().replaceFirst("\"latitude\":37.5", "\"latitude\":91.0"))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code", equalTo("COMMON_001")))
            .andExpect(jsonPath("$.error.detail.field", equalTo("latitude")))
    }

    @Test
    fun `rejects duplicate crop ids`() {
        mockMvc.perform(
            post("/api/v1/farms")
                .with(authenticatedMember(memberId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validFarmJson().replace("[\"$cropId\"]", "[\"$cropId\",\"$cropId\"]"))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code", equalTo("COMMON_001")))
            .andExpect(jsonPath("$.error.detail.field", equalTo("cropIds")))
    }

    @Test
    fun `rejects more than five crop ids`() {
        val extraCropIds = List(5) { UUID.randomUUID() }
        val cropIds = (listOf(cropId) + extraCropIds).joinToString(",") { "\"$it\"" }

        mockMvc.perform(
            post("/api/v1/farms")
                .with(authenticatedMember(memberId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validFarmJson().replace("[\"$cropId\"]", "[$cropIds]"))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code", equalTo("COMMON_001")))
            .andExpect(jsonPath("$.error.detail.field", equalTo("cropIds")))
    }

    @Test
    fun `maps farm in use to conflict`() {
        willThrow(BusinessException(ErrorCode.FARM_IN_USE))
            .given(farmService)
            .delete(FarmCommand.Delete(memberId = memberId, farmId = farmId))

        mockMvc.perform(
            delete("/api/v1/farms/{farmId}", farmId)
                .with(authenticatedMember(memberId.toString()))
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code", equalTo("FARM_002")))
    }

    @Test
    fun `requires authenticated member`() {
        mockMvc.perform(get("/api/v1/farms"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code", equalTo("AUTH_001")))
    }

    private fun authenticatedMember(memberId: String): RequestPostProcessor =
        RequestPostProcessor { request ->
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(
                    memberId,
                    null,
                    listOf(SimpleGrantedAuthority("ROLE_USER"))
                )
            request
        }

    private fun farmDetail(): FarmResult.Detail =
        FarmResult.Detail(
            farmId = farmId,
            name = "횡성 황기밭",
            roadAddress = "강원특별자치도 횡성군 둔내면 1",
            jibunAddress = "강원특별자치도 횡성군 둔내면 현천리 101",
            latitude = 37.5,
            longitude = 128.1,
            pnu = "4273031021101010000",
            landCategory = "전",
            areaSqm = BigDecimal("1200.5"),
            areaIsManualEntry = false,
            boundaryCoordinates = listOf(FarmResult.BoundaryCoordinate(37.5, 128.1)),
            dataSource = FarmResult.DataSource("JUSO", "VWORLD", "VWORLD", "VWORLD"),
            crops = listOf(
                CropResult.CropSummary(
                    id = cropId,
                    externalNo = 422,
                    name = "황기",
                    usePartCategory = "ROOT_BARK",
                    usePartCategoryLabel = "뿌리·껍질"
                )
            )
        )

    private fun expectedCreateCommand(): FarmCommand.Create =
        FarmCommand.Create(
            memberId = memberId,
            draft = expectedDraft(),
            cropIds = listOf(cropId)
        )

    private fun expectedReplaceCommand(): FarmCommand.Replace =
        FarmCommand.Replace(
            memberId = memberId,
            farmId = farmId,
            draft = expectedDraft(),
            cropIds = listOf(cropId)
        )

    private fun expectedDraft(): FarmCommand.Draft =
        FarmCommand.Draft(
            name = "횡성 황기밭",
            roadAddress = "강원특별자치도 횡성군 둔내면 1",
            jibunAddress = "강원특별자치도 횡성군 둔내면 현천리 101",
            latitude = 37.5,
            longitude = 128.1,
            pnu = "4273031021101010000",
            landCategory = "전",
            areaSqm = BigDecimal("1200.5"),
            areaIsManualEntry = false,
            boundaryCoordinates = listOf(FarmCommand.BoundaryCoordinate(37.5, 128.1)),
            dataSource = FarmCommand.DataSource("JUSO", "VWORLD", "VWORLD", "VWORLD")
        )

    private fun validFarmJson(): String =
        """
        {
          "name":"횡성 황기밭",
          "roadAddress":"강원특별자치도 횡성군 둔내면 1",
          "jibunAddress":"강원특별자치도 횡성군 둔내면 현천리 101",
          "latitude":37.5,
          "longitude":128.1,
          "pnu":"4273031021101010000",
          "landCategory":"전",
          "areaSqm":1200.5,
          "areaIsManualEntry":false,
          "boundaryCoordinates":[{"latitude":37.5,"longitude":128.1}],
          "dataSource":{"address":"JUSO","coordinate":"VWORLD","parcel":"VWORLD","landCharacteristic":"VWORLD"},
          "cropIds":["$cropId"]
        }
        """.trimIndent()
}
