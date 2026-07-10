package com.chamchamcham.api.farming.controller

import com.chamchamcham.api.exception.GlobalExceptionHandler
import com.chamchamcham.application.farming.FarmingRecordCommand
import com.chamchamcham.application.farming.FarmingRecordResult
import com.chamchamcham.application.farming.FarmingRecordSearchCondition
import com.chamchamcham.application.farming.FarmingRecordService
import com.chamchamcham.application.security.TokenProvider
import com.chamchamcham.domain.crop.CropUsePartCategory
import com.chamchamcham.domain.farming.GrowthPeriodUnit
import com.chamchamcham.domain.farming.HarvestSource
import com.chamchamcham.domain.farming.WorkType
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verifyNoInteractions
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
    fun `create record rejects blank memo`() {
        mockMvc.perform(
            post("/api/v1/farming-records")
                .with(authenticatedMember(memberId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(saveRecordJson(memo = ""))
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
    fun `create record rejects missing fertilizing material category before service call`() {
        val json = """
            {
              "farmId":"$farmId",
              "cropId":"$cropId",
              "workType":"FERTILIZING",
              "workedAt":"2026-06-01T09:00:00",
              "weatherCondition":"맑음",
              "weatherTemperature":28,
              "memo":"오늘은 날씨가 좋아 하루 종일 시비 작업을 진행했고 별다른 문제 없이 마무리했습니다",
              "fertilizing":{
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

        verifyNoInteractions(farmingRecordService)
    }

    @Test
    fun `create record rejects harvest without medicinal part`() {
        val json = """
            {
              "farmId":"$farmId",
              "cropId":"$cropId",
              "workType":"HARVEST",
              "workedAt":"2026-06-01T09:00:00",
              "weatherCondition":"맑음",
              "weatherTemperature":28,
              "memo":"오늘은 날씨가 좋아 하루 종일 수확 작업을 진행했고 별다른 문제 없이 마무리했습니다",
              "harvest":{
                "harvestAmount":10,
                "harvestSource":"CULTIVATED",
                "growthPeriod":2,
                "growthPeriodUnit":"YEAR",
                "isFinalHarvest":false
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
    }

    @Test
    fun `create record rejects harvest without final harvest flag before service call`() {
        val json = """
            {
              "farmId":"$farmId",
              "cropId":"$cropId",
              "workType":"HARVEST",
              "workedAt":"2026-06-01T09:00:00",
              "weatherCondition":"맑음",
              "weatherTemperature":28,
              "memo":"오늘은 날씨가 좋아 하루 종일 수확 작업을 진행했고 별다른 문제 없이 마무리했습니다",
              "harvest":{
                "harvestAmount":10,
                "medicinalPart":"ROOT_BARK",
                "harvestSource":"CULTIVATED",
                "growthPeriod":2,
                "growthPeriodUnit":"YEAR"
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

        verifyNoInteractions(farmingRecordService)
    }

    @Test
    fun `create record accepts explicit false final harvest flag`() {
        val command = FarmingRecordCommand.Create(
            memberId = memberId,
            farmId = farmId,
            cropId = cropId,
            workType = WorkType.HARVEST,
            workedAt = workedAt,
            weatherCondition = "맑음",
            weatherTemperature = 28,
            memo = "오늘은 날씨가 좋아 하루 종일 수확 작업을 진행했고 별다른 문제 없이 마무리했습니다",
            harvest = FarmingRecordCommand.HarvestDetail(
                harvestAmount = BigDecimal.TEN,
                medicinalPart = CropUsePartCategory.ROOT_BARK,
                harvestSource = HarvestSource.CULTIVATED,
                growthPeriod = 2,
                growthPeriodUnit = GrowthPeriodUnit.YEAR,
                isFinalHarvest = false,
            ),
            mediaIds = emptyList(),
        )
        `when`(farmingRecordService.create(command))
            .thenReturn(FarmingRecordResult.RecordId(id = recordId, workType = WorkType.HARVEST))

        val json = """
            {
              "farmId":"$farmId",
              "cropId":"$cropId",
              "workType":"HARVEST",
              "workedAt":"2026-06-01T09:00:00",
              "weatherCondition":"맑음",
              "weatherTemperature":28,
              "memo":"오늘은 날씨가 좋아 하루 종일 수확 작업을 진행했고 별다른 문제 없이 마무리했습니다",
              "harvest":{
                "harvestAmount":10,
                "medicinalPart":"ROOT_BARK",
                "harvestSource":"CULTIVATED",
                "growthPeriod":2,
                "growthPeriodUnit":"YEAR",
                "isFinalHarvest":false
              }
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/v1/farming-records")
                .with(authenticatedMember(memberId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `create record accepts unknown harvest amount`() {
        val command = FarmingRecordCommand.Create(
            memberId = memberId,
            farmId = farmId,
            cropId = cropId,
            workType = WorkType.HARVEST,
            workedAt = workedAt,
            weatherCondition = "맑음",
            weatherTemperature = 28,
            memo = "오늘은 날씨가 좋아 하루 종일 수확 작업을 진행했고 별다른 문제 없이 마무리했습니다",
            harvest = FarmingRecordCommand.HarvestDetail(
                harvestAmount = null,
                amountUnknown = true,
                medicinalPart = CropUsePartCategory.ROOT_BARK,
                harvestSource = HarvestSource.CULTIVATED,
                growthPeriod = 2,
                growthPeriodUnit = GrowthPeriodUnit.YEAR,
                isFinalHarvest = false,
            ),
        )
        `when`(farmingRecordService.create(command)).thenReturn(FarmingRecordResult.RecordId(id = recordId, workType = WorkType.HARVEST))

        val json = """
            {
              "farmId":"$farmId",
              "cropId":"$cropId",
              "workType":"HARVEST",
              "workedAt":"2026-06-01T09:00:00",
              "weatherCondition":"맑음",
              "weatherTemperature":28,
              "memo":"오늘은 날씨가 좋아 하루 종일 수확 작업을 진행했고 별다른 문제 없이 마무리했습니다",
              "harvest":{
                "harvestAmountUnknown":true,
                "medicinalPart":"ROOT_BARK",
                "harvestSource":"CULTIVATED",
                "growthPeriod":2,
                "growthPeriodUnit":"YEAR",
                "isFinalHarvest":false
              }
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/v1/farming-records")
                .with(authenticatedMember(memberId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `create record rejects planting without propagation method`() {
        val json = """
            {
              "farmId":"$farmId",
              "cropId":"$cropId",
              "workType":"PLANTING",
              "workedAt":"2026-06-01T09:00:00",
              "weatherCondition":"맑음",
              "weatherTemperature":28,
              "memo":"오늘은 날씨가 좋아 하루 종일 파종 작업을 진행했고 별다른 문제 없이 마무리했습니다",
              "planting":{
                "seedAmount":10,
                "seedAmountUnit":"KG"
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
    }

    @Test
    fun `create record rejects memo shorter than thirty characters`() {
        mockMvc.perform(
            post("/api/v1/farming-records")
                .with(authenticatedMember(memberId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(saveRecordJson(memo = "너무 짧은 메모"))
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `create record rejects memo longer than five hundred characters`() {
        mockMvc.perform(
            post("/api/v1/farming-records")
                .with(authenticatedMember(memberId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(saveRecordJson(memo = "메".repeat(501)))
        )
            .andExpect(status().isBadRequest)
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
                    keyword = null,
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
    fun `list records maps keyword parameter`() {
        `when`(
            farmingRecordService.search(
                FarmingRecordSearchCondition(
                    memberId = memberId,
                    cropId = null,
                    workType = null,
                    startDate = null,
                    endDate = null,
                    keyword = "수확",
                    cursor = null,
                    size = 20
                )
            )
        ).thenReturn(pageResult())

        mockMvc.perform(
            get("/api/v1/farming-records")
                .with(authenticatedMember(memberId.toString()))
                .param("keyword", "수확")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items[0].id", equalTo(recordId.toString())))
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
            .andExpect(jsonPath("$.data.harvest.harvestAmount", equalTo(10)))
            .andExpect(jsonPath("$.data.harvest.amountUnknown", equalTo(false)))
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
        growthPeriod: String = "2",
        memo: String = "오늘은 날씨가 좋아 하루 종일 수확 작업을 진행했고 별다른 문제 없이 마무리했습니다"
    ): String {
        return """
            {
              "farmId":"$farmId",
              "cropId":"$cropId",
              "workType":"HARVEST",
              "workedAt":"2026-06-01T09:00:00",
              "weatherCondition":"$weatherCondition",
              "weatherTemperature":28,
              "memo":"$memo",
              "harvest":{
                "harvestAmount":$harvestAmount,
                "medicinalPart":"ROOT_BARK",
                "harvestSource":"CULTIVATED",
                "growthPeriod":$growthPeriod,
                "growthPeriodUnit":"YEAR",
                "isFinalHarvest":false
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
            memo = "오늘은 날씨가 좋아 하루 종일 수확 작업을 진행했고 별다른 문제 없이 마무리했습니다",
            harvest = FarmingRecordCommand.HarvestDetail(
                harvestAmount = BigDecimal.TEN,
                medicinalPart = CropUsePartCategory.ROOT_BARK,
                harvestSource = HarvestSource.CULTIVATED,
                growthPeriod = 2,
                growthPeriodUnit = GrowthPeriodUnit.YEAR,
                isFinalHarvest = false,
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
            memo = "오늘은 날씨가 좋아 하루 종일 수확 작업을 진행했고 별다른 문제 없이 마무리했습니다",
            harvest = FarmingRecordCommand.HarvestDetail(
                harvestAmount = BigDecimal.TEN,
                medicinalPart = CropUsePartCategory.ROOT_BARK,
                harvestSource = HarvestSource.CULTIVATED,
                growthPeriod = 2,
                growthPeriodUnit = GrowthPeriodUnit.YEAR,
                isFinalHarvest = false,
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
                medicinalPart = CropUsePartCategory.ROOT_BARK,
                harvestSource = HarvestSource.CULTIVATED,
                growthPeriod = 2,
                growthPeriodUnit = GrowthPeriodUnit.YEAR,
                isFinalHarvest = false,
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
            keyword = null,
            cursor = null,
            size = 20
        )
}
