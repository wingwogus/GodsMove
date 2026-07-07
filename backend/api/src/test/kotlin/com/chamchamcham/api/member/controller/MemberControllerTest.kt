package com.chamchamcham.api.member.controller

import com.chamchamcham.api.exception.GlobalExceptionHandler
import com.chamchamcham.application.member.MemberProfileResult
import com.chamchamcham.application.member.MemberProfileService
import com.chamchamcham.application.security.TokenProvider
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
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
    fun `get my profile maps authenticated member id and returns private profile`() {
        `when`(memberProfileService.getMyProfile(memberId)).thenReturn(myProfileResult())

        mockMvc.perform(
            get("/api/v1/members/me")
                .with(authenticatedMember(memberId.toString()))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.memberId", equalTo(memberId.toString())))
            .andExpect(jsonPath("$.data.email", equalTo("hwanggi@example.com")))
            .andExpect(jsonPath("$.data.name", equalTo("이황기")))
            .andExpect(jsonPath("$.data.phone", equalTo("010-1000-0001")))
            .andExpect(jsonPath("$.data.birthDate", equalTo("1986-03-12")))
            .andExpect(jsonPath("$.data.farms[0].roadAddress", equalTo("강원특별자치도 횡성군 둔내면 샘물로 12")))
            .andExpect(jsonPath("$.data.crops[0].cropName", equalTo("황기")))
    }

    @Test
    fun `get public profile maps path member id and returns safe profile`() {
        `when`(memberProfileService.getPublicProfile(memberId)).thenReturn(publicProfileResult())

        mockMvc.perform(
            get("/api/v1/members/{memberId}/profile", memberId)
                .with(authenticatedMember(requesterId.toString()))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.memberId", equalTo(memberId.toString())))
            .andExpect(jsonPath("$.data.nickname", equalTo("황기농부")))
            .andExpect(jsonPath("$.data.farms[0].displayRegion", equalTo("강원특별자치도 횡성군")))
            .andExpect(jsonPath("$.data.crops[0].cropId", equalTo(cropId.toString())))
    }

    @Test
    fun `get public profile response does not expose private fields`() {
        `when`(memberProfileService.getPublicProfile(memberId)).thenReturn(publicProfileResult())

        mockMvc.perform(
            get("/api/v1/members/{memberId}/profile", memberId)
                .with(authenticatedMember(requesterId.toString()))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.email").doesNotExist())
            .andExpect(jsonPath("$.data.name").doesNotExist())
            .andExpect(jsonPath("$.data.phone").doesNotExist())
            .andExpect(jsonPath("$.data.birthDate").doesNotExist())
            .andExpect(jsonPath("$.data.farms[0].roadAddress").doesNotExist())
            .andExpect(jsonPath("$.data.farms[0].jibunAddress").doesNotExist())
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

    private fun myProfileResult(): MemberProfileResult.MyProfile =
        MemberProfileResult.MyProfile(
            memberId = memberId,
            email = "hwanggi@example.com",
            name = "이황기",
            phone = "010-1000-0001",
            birthDate = LocalDate.of(1986, 3, 12),
            nickname = "황기농부",
            experienceLevel = 2,
            managementType = "AGRICULTURAL_INDIVIDUAL",
            profileImageUrl = "https://example.test/profile.jpg",
            farms = listOf(
                MemberProfileResult.MyFarm(
                    farmId = farmId,
                    name = "횡성 황기밭",
                    roadAddress = "강원특별자치도 횡성군 둔내면 샘물로 12",
                    jibunAddress = "강원특별자치도 횡성군 둔내면 현천리 101",
                    displayRegion = "강원특별자치도 횡성군"
                )
            ),
            crops = listOf(MemberProfileResult.CropProfile(cropId, "황기"))
        )

    private fun publicProfileResult(): MemberProfileResult.PublicProfile =
        MemberProfileResult.PublicProfile(
            memberId = memberId,
            nickname = "황기농부",
            experienceLevel = 2,
            managementType = "AGRICULTURAL_INDIVIDUAL",
            profileImageUrl = "https://example.test/profile.jpg",
            farms = listOf(MemberProfileResult.PublicFarm(farmId, "강원특별자치도 횡성군")),
            crops = listOf(MemberProfileResult.CropProfile(cropId, "황기"))
        )
}
