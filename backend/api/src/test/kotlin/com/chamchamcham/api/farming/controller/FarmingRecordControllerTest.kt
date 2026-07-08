package com.chamchamcham.api.farming.controller

import com.chamchamcham.api.exception.GlobalExceptionHandler
import com.chamchamcham.application.farming.FarmingRecordCommand
import com.chamchamcham.application.farming.FarmingRecordResult
import com.chamchamcham.application.farming.FarmingRecordSearchCondition
import com.chamchamcham.application.farming.FarmingRecordService
import com.chamchamcham.application.security.TokenProvider
import com.chamchamcham.domain.crop.CropUsePartCategory
import com.chamchamcham.domain.farming.GrowthPeriodUnit
import com.chamchamcham.domain.farming.HarvestAmountUnit
import com.chamchamcham.domain.farming.HarvestSource
import com.chamchamcham.domain.farming.WorkType
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@WebMvcTest(FarmingRecordController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler::class)
class FarmingRecordControllerTest(
    @Autowired private val mockMvc: MockMvc
) {
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val recordId = UUID.fromString("00000000-0000-0000-0000-000000000101")
    private val farmId = UUID.fromString("00000000-0000-0000-0000-000000000201")
    private val cropId = UUID.fromString("00000000-0000-0000-0000-000000000301")
    private val mediaId = UUID.fromString("00000000-0000-0000-0000-000000000401")
    private val workedAt = LocalDateTime.of(2026, 6, 1, 9, 0)
    private val createdAt = LocalDateTime.of(2026, 6, 1, 9, 5)

    @MockBean private lateinit var farmingRecordService: FarmingRecordService
    @MockBean private lateinit var tokenProvider: TokenProvider

    @Test
    fun `create record maps request to command and returns record id`() {
        `when`(farmingRecordService.create(createCommand()))
            .thenReturn(FarmingRecordResult.RecordId(id = recordId, workType = WorkType.HARVEST))

        mockMvc.perform(
            post("/api/v1/farming-records")
                .with(authenticatedMember(memberId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(saveRecordJson())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.id", equalTo(recordId.toString())))
            .andExpect(jsonPath("$.data.workType", equalTo("HARVEST")))
    }

    @Test
    fun `create record without auth returns unauthorized`() {
        mockMvc.perform(
            post("/api/v1/farming-records")
                .contentType(MediaType.APPLICATION_JSON)
                .content(saveRecordJson())
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code", equalTo("AUTH_001")))
    }

    @Test
    fun `create record rejects blank weather condition`() {
        mockMvc.perform(
            post("/api/v1/farming-records")
                .with(authenticatedMember(memberId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(saveRecordJson(weatherCondition = ""))
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `create record rejects more than five media ids`() {
        val tooManyMediaIds = List(6) { UUID.randomUUID() }.joinToString(",") { "\"$it\"" }

        mockMvc.perform(
            post("/api/v1/farming-records")
                .with(authenticatedMember(memberId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(saveRecordJson(mediaIdsJson = "[$tooManyMediaIds]"))
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `create record rejects non-positive harvest amount`() {
        mockMvc.perform(
            post("/api/v1/farming-records")
                .with(authenticatedMember(memberId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(saveRecordJson(harvestAmount = "0"))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code", equalTo("COMMON_001")))
    }

    @Test
    fun `create record rejects non-positive growth period`() {
        mockMvc.perform(
            post("/api/v1/farming-records")
                .with(authenticatedMember(memberId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(saveRecordJson(growthPeriod = "0"))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code", equalTo("COMMON_001")))
    }

    @Test
    fun `create record rejects blank fertilizing material name`() {
        val json = """
            {
              "farmId":"$farmId",
              "cropId":"$cropId",
              "workType":"FERTILIZING",
              "workedAt":"2026-06-01T09:00:00",
              "weatherCondition":"맑음",
              "weatherTemperature":28,
              "fertilizing":{
                "materialName":" ",
                "amount":10,
                "amountUnit":"KG"
              }
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/v1/farming-records")
                .with(authenticatedMember(memberId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code", equalTo("COMMON_001")))
    }

    @Test
    fun `list records returns cursor page`() {
        `when`(farmingRecordService.search(anySearchCondition())).thenReturn(pageResult())

        mockMvc.perform(
            get("/api/v1/farming-records")
                .with(authenticatedMember(memberId.toString()))
                .param("size", "20")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items[0].id", equalTo(recordId.toString())))
            .andExpect(jsonPath("$.data.nextCursor", equalTo("cursor-1")))
    }

    @Test
    fun `list records maps filters and cursor parameters`() {
        `when`(
            farmingRecordService.search(
                FarmingRecordSearchCondition(
                    memberId = memberId,
                    cropId = cropId,
                    workType = WorkType.HARVEST,
                    startDate = LocalDate.of(2026, 6, 1),
                    endDate = LocalDate.of(2026, 6, 30),
                    cursor = "cursor-1",
                    size = 10
                )
            )
        ).thenReturn(pageResult())

        mockMvc.perform(
            get("/api/v1/farming-records")
                .with(authenticatedMember(memberId.toString()))
                .param("cropId", cropId.toString())
                .param("workType", "HARVEST")
                .param("startDate", "2026-06-01")
                .param("endDate", "2026-06-30")
                .param("cursor", "cursor-1")
                .param("size", "10")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.nextCursor", equalTo("cursor-1")))
    }

    @Test
    fun `get record detail returns detail`() {
        `when`(farmingRecordService.getDetail(memberId, recordId)).thenReturn(detailResult())

        mockMvc.perform(
            get("/api/v1/farming-records/{recordId}", recordId)
                .with(authenticatedMember(memberId.toString()))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.id", equalTo(recordId.toString())))
            .andExpect(jsonPath("$.data.harvest.harvestAmountUnit", equalTo("KG")))
    }

    @Test
    fun `update record maps request to command`() {
        `when`(farmingRecordService.update(updateCommand()))
            .thenReturn(FarmingRecordResult.RecordId(id = recordId, workType = WorkType.HARVEST))

        mockMvc.perform(
            patch("/api/v1/farming-records/{recordId}", recordId)
                .with(authenticatedMember(memberId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(saveRecordJson())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.id", equalTo(recordId.toString())))
    }

    @Test
    fun `delete record delegates to service`() {
        mockMvc.perform(
            delete("/api/v1/farming-records/{recordId}", recordId)
                .with(authenticatedMember(memberId.toString()))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success", equalTo(true)))
    }

    private fun authenticatedMember(memberId: String): RequestPostProcessor {
        return RequestPostProcessor { request ->
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(
                    memberId,
                    null,
                    listOf(SimpleGrantedAuthority("ROLE_USER"))
                )
            request
        }
    }

    private fun saveRecordJson(
        weatherCondition: String = "맑음",
        mediaIdsJson: String = "[\"$mediaId\"]",
        harvestAmount: String = "10",
        growthPeriod: String = "2"
    ): String {
        return """
            {
              "farmId":"$farmId",
              "cropId":"$cropId",
              "workType":"HARVEST",
              "workedAt":"2026-06-01T09:00:00",
              "weatherCondition":"$weatherCondition",
              "weatherTemperature":28,
              "memo":"수확 완료",
              "harvest":{
                "harvestAmount":$harvestAmount,
                "harvestAmountUnit":"KG",
                "harvestSource":"CULTIVATED",
                "growthPeriod":$growthPeriod,
                "growthPeriodUnit":"YEAR"
              },
              "mediaIds":$mediaIdsJson
            }
        """.trimIndent()
    }

    private fun createCommand(): FarmingRecordCommand.Create =
        FarmingRecordCommand.Create(
            memberId = memberId,
            farmId = farmId,
            cropId = cropId,
            workType = WorkType.HARVEST,
            workedAt = workedAt,
            weatherCondition = "맑음",
            weatherTemperature = 28,
            memo = "수확 완료",
            harvest = FarmingRecordCommand.HarvestDetail(
                harvestAmount = BigDecimal.TEN,
                harvestAmountUnit = HarvestAmountUnit.KG,
                harvestSource = HarvestSource.CULTIVATED,
                growthPeriod = 2,
                growthPeriodUnit = GrowthPeriodUnit.YEAR,
            ),
            mediaIds = listOf(mediaId),
        )

    private fun updateCommand(): FarmingRecordCommand.Update =
        FarmingRecordCommand.Update(
            memberId = memberId,
            recordId = recordId,
            farmId = farmId,
            cropId = cropId,
            workType = WorkType.HARVEST,
            workedAt = workedAt,
            weatherCondition = "맑음",
            weatherTemperature = 28,
            memo = "수확 완료",
            harvest = FarmingRecordCommand.HarvestDetail(
                harvestAmount = BigDecimal.TEN,
                harvestAmountUnit = HarvestAmountUnit.KG,
                harvestSource = HarvestSource.CULTIVATED,
                growthPeriod = 2,
                growthPeriodUnit = GrowthPeriodUnit.YEAR,
            ),
            mediaIds = listOf(mediaId),
        )

    private fun pageResult(): FarmingRecordResult.Page =
        FarmingRecordResult.Page(
            items = listOf(summaryResult()),
            nextCursor = "cursor-1"
        )

    private fun summaryResult(): FarmingRecordResult.Summary =
        FarmingRecordResult.Summary(
            id = recordId,
            cropId = cropId,
            cropName = "마늘",
            workType = WorkType.HARVEST,
            workedAt = workedAt,
            weatherCondition = "맑음",
            weatherTemperature = 28,
            memoPreview = "수확 완료",
            thumbnailUrl = "https://example.test/1.jpg",
        )

    private fun detailResult(): FarmingRecordResult.Detail =
        FarmingRecordResult.Detail(
            id = recordId,
            farmId = farmId,
            farmName = "약초농장",
            cropId = cropId,
            cropName = "마늘",
            workType = WorkType.HARVEST,
            workedAt = workedAt,
            weatherCondition = "맑음",
            weatherTemperature = 28,
            memo = "수확 완료",
            harvest = FarmingRecordResult.HarvestDetail(
                harvestAmount = BigDecimal.TEN,
                harvestAmountUnit = HarvestAmountUnit.KG,
                medicinalPart = CropUsePartCategory.ROOT_BARK,
                harvestSource = HarvestSource.CULTIVATED,
                growthPeriod = 2,
                growthPeriodUnit = GrowthPeriodUnit.YEAR,
            ),
            imageUrls = listOf("https://example.test/1.jpg"),
            createdAt = createdAt,
            updatedAt = createdAt,
        )

    private fun anySearchCondition(): FarmingRecordSearchCondition =
        FarmingRecordSearchCondition(
            memberId = memberId,
            cropId = null,
            workType = null,
            startDate = null,
            endDate = null,
            cursor = null,
            size = 20
        )
}
