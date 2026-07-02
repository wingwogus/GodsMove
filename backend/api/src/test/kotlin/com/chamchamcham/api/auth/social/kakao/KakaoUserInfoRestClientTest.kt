package com.chamchamcham.api.auth.social.kakao

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withException
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.web.client.RestClient
import java.io.IOException
import java.time.LocalDate

class KakaoUserInfoRestClientTest {
    private lateinit var server: MockRestServiceServer
    private lateinit var client: KakaoUserInfoRestClient

    @BeforeEach
    fun setUp() {
        val restClientBuilder = RestClient.builder()
        server = MockRestServiceServer.bindTo(restClientBuilder).build()
        client = KakaoUserInfoRestClient(restClientBuilder.build(), USER_INFO_URI)
    }

    @Test
    fun `fetch maps successful userinfo and sends bearer header`() {
        server.expect(ExpectedCount.once(), requestTo(USER_INFO_URI))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
            .andRespond(
                withStatus(HttpStatus.OK)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(
                        """
                        {
                          "sub": "kakao-sub",
                          "email": "kakao@example.com",
                          "email_verified": true,
                          "name": "카카오회원",
                          "phone_number": "010-1234-5678",
                          "phone_number_verified": true,
                          "birthdate": "1990-05-12"
                        }
                        """.trimIndent()
                    )
            )

        val userInfo = client.fetch("access-token")

        assertEquals("kakao-sub", userInfo.subject)
        assertEquals("kakao@example.com", userInfo.email)
        assertEquals("카카오회원", userInfo.name)
        assertEquals("010-1234-5678", userInfo.phone)
        assertEquals(LocalDate.of(1990, 5, 12), userInfo.birthDate)
        server.verify()
    }

    @Test
    fun `fetch maps unauthorized response to invalid kakao token`() {
        server.expect(requestTo(USER_INFO_URI))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED))

        val exception = assertThrows(BusinessException::class.java) {
            client.fetch("access-token")
        }

        assertEquals(ErrorCode.INVALID_KAKAO_TOKEN, exception.errorCode)
    }

    @Test
    fun `fetch maps forbidden response to invalid kakao token`() {
        server.expect(requestTo(USER_INFO_URI))
            .andRespond(withStatus(HttpStatus.FORBIDDEN))

        val exception = assertThrows(BusinessException::class.java) {
            client.fetch("access-token")
        }

        assertEquals(ErrorCode.INVALID_KAKAO_TOKEN, exception.errorCode)
    }

    @Test
    fun `fetch maps generic client error response to invalid kakao token`() {
        server.expect(requestTo(USER_INFO_URI))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST))

        val exception = assertThrows(BusinessException::class.java) {
            client.fetch("access-token")
        }

        assertEquals(ErrorCode.INVALID_KAKAO_TOKEN, exception.errorCode)
    }

    @Test
    fun `fetch maps provider server failure to unavailable`() {
        server.expect(requestTo(USER_INFO_URI))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

        val exception = assertThrows(BusinessException::class.java) {
            client.fetch("access-token")
        }

        assertEquals(ErrorCode.KAKAO_OIDC_UNAVAILABLE, exception.errorCode)
    }

    @Test
    fun `fetch maps transport exception to unavailable`() {
        server.expect(requestTo(USER_INFO_URI))
            .andRespond(withException(IOException("connection reset")))

        val exception = assertThrows(BusinessException::class.java) {
            client.fetch("access-token")
        }

        assertEquals(ErrorCode.KAKAO_OIDC_UNAVAILABLE, exception.errorCode)
    }

    @Test
    fun `fetch maps null response body to unavailable`() {
        server.expect(requestTo(USER_INFO_URI))
            .andRespond(withStatus(HttpStatus.OK))

        val exception = assertThrows(BusinessException::class.java) {
            client.fetch("access-token")
        }

        assertEquals(ErrorCode.KAKAO_OIDC_UNAVAILABLE, exception.errorCode)
    }

    @Test
    fun `fetch maps missing subject to invalid kakao token`() {
        server.expect(requestTo(USER_INFO_URI))
            .andRespond(
                withStatus(HttpStatus.OK)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""{"email":"kakao@example.com","email_verified":true}""")
            )

        val exception = assertThrows(BusinessException::class.java) {
            client.fetch("access-token")
        }

        assertEquals(ErrorCode.INVALID_KAKAO_TOKEN, exception.errorCode)
    }

    @Test
    fun `fetch maps blank subject to invalid kakao token`() {
        server.expect(requestTo(USER_INFO_URI))
            .andRespond(
                withStatus(HttpStatus.OK)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""{"sub":" "}""")
            )

        val exception = assertThrows(BusinessException::class.java) {
            client.fetch("access-token")
        }

        assertEquals(ErrorCode.INVALID_KAKAO_TOKEN, exception.errorCode)
    }

    @Test
    fun `fetch maps unverified email to null`() {
        server.expect(requestTo(USER_INFO_URI))
            .andRespond(
                withStatus(HttpStatus.OK)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""{"sub":"kakao-sub","email":"kakao@example.com","email_verified":false}""")
            )

        val userInfo = client.fetch("access-token")

        assertNull(userInfo.email)
    }

    @Test
    fun `fetch maps partial and placeholder birthdate to null`() {
        val birthdates = listOf("1990", "0000-05-12", "1990-13-40")
        birthdates.forEach { birthdate ->
            server.expect(requestTo(USER_INFO_URI))
                .andRespond(
                    withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""{"sub":"kakao-sub","birthdate":"$birthdate"}""")
                )
        }

        birthdates.forEach {
            val userInfo = client.fetch("access-token")

            assertNull(userInfo.birthDate)
        }
    }

    companion object {
        private const val USER_INFO_URI = "https://kakao.example.test/userinfo"
    }
}
