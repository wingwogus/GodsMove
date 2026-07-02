package com.godsmove.api.naver

import com.godsmove.application.exception.ErrorCode
import com.godsmove.application.exception.business.BusinessException
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
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.web.client.RestClient
import java.time.LocalDate

class NaverProfileRestClientTest {
    private lateinit var server: MockRestServiceServer
    private lateinit var client: NaverProfileRestClient

    @BeforeEach
    fun setUp() {
        val restClientBuilder = RestClient.builder()
        server = MockRestServiceServer.bindTo(restClientBuilder).build()
        client = NaverProfileRestClient(restClientBuilder.build(), PROFILE_URI)
    }

    @Test
    fun `fetch maps successful profile and sends bearer header`() {
        server.expect(ExpectedCount.once(), requestTo(PROFILE_URI))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
            .andRespond(
                withStatus(HttpStatus.OK)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(
                        """
                        {
                          "resultcode": "00",
                          "message": "success",
                          "response": {
                            "id": "naver-sub",
                            "email": "naver@example.com",
                            "name": "네이버회원",
                            "mobile": "010-1234-5678",
                            "birthyear": "1990",
                            "birthday": "05-12"
                          }
                        }
                        """.trimIndent()
                    )
            )

        val profile = client.fetch("access-token")

        assertEquals("naver-sub", profile.subject)
        assertEquals("naver@example.com", profile.email)
        assertEquals("네이버회원", profile.name)
        assertEquals("010-1234-5678", profile.phone)
        assertEquals(LocalDate.of(1990, 5, 12), profile.birthDate)
        server.verify()
    }

    @Test
    fun `fetch maps provider server failure to unavailable`() {
        server.expect(requestTo(PROFILE_URI))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

        val exception = assertThrows(BusinessException::class.java) {
            client.fetch("access-token")
        }

        assertEquals(ErrorCode.NAVER_PROFILE_UNAVAILABLE, exception.errorCode)
    }

    @Test
    fun `fetch maps unauthorized response to invalid naver token`() {
        server.expect(requestTo(PROFILE_URI))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED))

        val exception = assertThrows(BusinessException::class.java) {
            client.fetch("access-token")
        }

        assertEquals(ErrorCode.INVALID_NAVER_TOKEN, exception.errorCode)
    }

    @Test
    fun `fetch maps forbidden response to invalid naver token`() {
        server.expect(requestTo(PROFILE_URI))
            .andRespond(withStatus(HttpStatus.FORBIDDEN))

        val exception = assertThrows(BusinessException::class.java) {
            client.fetch("access-token")
        }

        assertEquals(ErrorCode.INVALID_NAVER_TOKEN, exception.errorCode)
    }

    @Test
    fun `fetch maps provider failure resultcode to invalid token`() {
        server.expect(requestTo(PROFILE_URI))
            .andRespond(
                withStatus(HttpStatus.OK)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""{"resultcode":"024","message":"Authentication failed"}""")
            )

        val exception = assertThrows(BusinessException::class.java) {
            client.fetch("access-token")
        }

        assertEquals(ErrorCode.INVALID_NAVER_TOKEN, exception.errorCode)
    }

    @Test
    fun `fetch maps missing profile body to unavailable`() {
        server.expect(requestTo(PROFILE_URI))
            .andRespond(
                withStatus(HttpStatus.OK)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""{"resultcode":"00","message":"success"}""")
            )

        val exception = assertThrows(BusinessException::class.java) {
            client.fetch("access-token")
        }

        assertEquals(ErrorCode.NAVER_PROFILE_UNAVAILABLE, exception.errorCode)
    }

    @Test
    fun `fetch maps blank profile id to invalid token`() {
        server.expect(requestTo(PROFILE_URI))
            .andRespond(
                withStatus(HttpStatus.OK)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""{"resultcode":"00","message":"success","response":{"id":" "}}""")
            )

        val exception = assertThrows(BusinessException::class.java) {
            client.fetch("access-token")
        }

        assertEquals(ErrorCode.INVALID_NAVER_TOKEN, exception.errorCode)
    }

    @Test
    fun `fetch maps invalid birth data to null`() {
        server.expect(requestTo(PROFILE_URI))
            .andRespond(
                withStatus(HttpStatus.OK)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(
                        """
                        {
                          "resultcode": "00",
                          "message": "success",
                          "response": {
                            "id": "naver-sub",
                            "email": "naver@example.com",
                            "birthyear": "1990",
                            "birthday": "13-40"
                          }
                        }
                        """.trimIndent()
                    )
            )

        val profile = client.fetch("access-token")

        assertNull(profile.birthDate)
    }

    companion object {
        private const val PROFILE_URI = "https://naver.example.test/profile"
    }
}
