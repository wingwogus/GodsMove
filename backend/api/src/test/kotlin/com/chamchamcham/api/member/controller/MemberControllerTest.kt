package com.chamchamcham.api.member.controller

import com.chamchamcham.api.exception.GlobalExceptionHandler
import com.chamchamcham.application.crop.CropResult
import com.chamchamcham.application.member.MemberProfileCommand
import com.chamchamcham.application.member.MemberProfileResult
import com.chamchamcham.application.member.MemberProfileService
import com.chamchamcham.application.security.TokenProvider
import com.chamchamcham.domain.crop.CropUsePartCategory
import com.chamchamcham.domain.member.ManagementType
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verify
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.util.UUID

@WebMvcTest(MemberController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler::class)
class MemberControllerTest(
    @Autowired private val mockMvc: MockMvc
) {
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val requesterId = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val farmId = UUID.fromString("00000000-0000-0000-0000-000000000101")
    private val cropId = UUID.fromString("00000000-0000-0000-0000-000000000201")

    @MockBean private lateinit var memberProfileService: MemberProfileService
    @MockBean private lateinit var tokenProvider: TokenProvider

    @Test
    fun `get my profile keeps farm summaries as read model`() {
        given(memberProfileService.getMyProfile(memberId)).willReturn(myProfile())

        mockMvc.perform(get("/api/v1/members/me").with(authenticatedMember(memberId.toString())))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.memberId", equalTo(memberId.toString())))
            .andExpect(jsonPath("$.data.farms[0].farmId", equalTo(farmId.toString())))
            .andExpect(jsonPath("$.data.crops[0].cropName", equalTo("황기")))
    }

    @Test
    fun `delete me delegates authenticated member hard withdrawal`() {
        mockMvc.perform(
            delete("/api/v1/members/me")
                .with(authenticatedMember(memberId.toString()))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success", equalTo(true)))

        verify(memberProfileService).withdraw(memberId)
    }

    @Test
    fun `get farm crops keeps nested crop read model`() {
        given(memberProfileService.getMyFarmCrops(memberId)).willReturn(
            listOf(
                MemberProfileResult.FarmCrops(
                    farmId = farmId,
                    farmName = "횡성 황기밭",
                    crops = listOf(cropSummary())
                )
            )
        )

        mockMvc.perform(get("/api/v1/members/me/farm-crops").with(authenticatedMember(memberId.toString())))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data[0].crops[0].id", equalTo(cropId.toString())))
    }

    @Test
    fun `update profile maps profile fields without farms`() {
        given(memberProfileService.updateMyProfile(updateCommand())).willReturn(myProfile())

        mockMvc.perform(
            put("/api/v1/members/me/profile")
                .with(authenticatedMember(memberId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(profileJson())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.name", equalTo("이황기")))

        verify(memberProfileService).updateMyProfile(updateCommand())
    }

    @Test
    fun `update profile succeeds without name phone birthDate or nickname`() {
        given(memberProfileService.updateMyProfile(updateCommand().copy(name = null, phone = null, birthDate = null, nickname = null)))
            .willReturn(myProfile())

        mockMvc.perform(
            put("/api/v1/members/me/profile")
                .with(authenticatedMember(memberId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    profileJson()
                        .replace("\"name\":\"이황기\",", "")
                        .replace("\"phone\":\"010-1000-0001\",", "")
                        .replace("\"birthDate\":\"1986-03-12\",", "")
                        .replace("\"nickname\":\"황기농부\",", "")
                )
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `update profile rejects experience level above requester age`() {
        mockMvc.perform(
            put("/api/v1/members/me/profile")
                .with(authenticatedMember(memberId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    profileJson()
                        .replace("\"birthDate\":\"1986-03-12\"", "\"birthDate\":\"${LocalDate.now().minusYears(5)}\"")
                        .replace("\"experienceLevel\":2", "\"experienceLevel\":50")
                )
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code", equalTo("COMMON_001")))

        verifyNoInteractions(memberProfileService)
    }

    @Test
    fun `update profile allows experience level up to one hundred when birthDate is absent`() {
        given(
            memberProfileService.updateMyProfile(
                updateCommand().copy(birthDate = null, experienceLevel = 90)
            )
        ).willReturn(myProfile())

        mockMvc.perform(
            put("/api/v1/members/me/profile")
                .with(authenticatedMember(memberId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    profileJson()
                        .replace("\"birthDate\":\"1986-03-12\",", "")
                        .replace("\"experienceLevel\":2", "\"experienceLevel\":90")
                )
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `get public profile keeps safe fields`() {
        given(memberProfileService.getPublicProfile(memberId)).willReturn(
            MemberProfileResult.PublicProfile(
                memberId = memberId,
                nickname = "황기농부",
                experienceLevel = 2,
                managementType = ManagementType.AGRICULTURAL_INDIVIDUAL.name,
                profileImageUrl = null,
                farms = listOf(MemberProfileResult.PublicFarm(farmId, "강원특별자치도 횡성군")),
                crops = listOf(MemberProfileResult.CropProfile(cropId, "황기"))
            )
        )

        mockMvc.perform(
            get("/api/v1/members/{memberId}/profile", memberId)
                .with(authenticatedMember(requesterId.toString()))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.email").doesNotExist())
            .andExpect(jsonPath("$.data.farms[0].roadAddress").doesNotExist())
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

    private fun myProfile(): MemberProfileResult.MyProfile =
        MemberProfileResult.MyProfile(
            memberId = memberId,
            email = "hwanggi@example.com",
            name = "이황기",
            phone = "010-1000-0001",
            birthDate = LocalDate.of(1986, 3, 12),
            nickname = "황기농부",
            experienceLevel = 2,
            managementType = ManagementType.AGRICULTURAL_INDIVIDUAL.name,
            profileImageUrl = null,
            farms = listOf(
                MemberProfileResult.MyFarm(
                    farmId = farmId,
                    name = "횡성 황기밭",
                    roadAddress = "강원특별자치도 횡성군 둔내면 1",
                    jibunAddress = null,
                    displayRegion = "강원특별자치도 횡성군"
                )
            ),
            crops = listOf(MemberProfileResult.CropProfile(cropId, "황기"))
        )

    private fun cropSummary(): CropResult.CropSummary =
        CropResult.CropSummary(
            id = cropId,
            externalNo = 422,
            name = "황기",
            usePartCategory = CropUsePartCategory.ROOT_BARK.name,
            usePartCategoryLabel = CropUsePartCategory.ROOT_BARK.label
        )

    private fun updateCommand(): MemberProfileCommand.UpdateMyProfile =
        MemberProfileCommand.UpdateMyProfile(
            memberId = memberId,
            name = "이황기",
            phone = "010-1000-0001",
            birthDate = LocalDate.of(1986, 3, 12),
            nickname = "황기농부",
            experienceLevel = 2,
            managementType = ManagementType.AGRICULTURAL_INDIVIDUAL,
            profileMediaId = null,
            farms = listOf(
                MemberProfileCommand.Farm(
                    farmId = farmId,
                    name = "횡성 황기밭",
                    roadAddress = "강원특별자치도 횡성군 둔내면 1",
                    jibunAddress = null,
                    latitude = 37.5,
                    longitude = 128.5,
                    pnu = null,
                    landCategory = null,
                    areaSqm = null,
                    areaIsManualEntry = false,
                    boundaryCoordinates = emptyList(),
                    dataSource = MemberProfileCommand.FarmDataSource(null, null, null, null),
                    cropIds = listOf(cropId)
                )
            )
        )

    private fun profileJson(): String =
        """
        {
          "name":"이황기",
          "phone":"010-1000-0001",
          "birthDate":"1986-03-12",
          "nickname":"황기농부",
          "experienceLevel":2,
          "managementType":"AGRICULTURAL_INDIVIDUAL",
          "profileMediaId":null,
          "farms":[
            {
              "farmId":"$farmId",
              "name":"횡성 황기밭",
              "roadAddress":"강원특별자치도 횡성군 둔내면 1",
              "latitude":37.5,
              "longitude":128.5,
              "cropIds":["$cropId"]
            }
          ]
        }
        """.trimIndent()
}
