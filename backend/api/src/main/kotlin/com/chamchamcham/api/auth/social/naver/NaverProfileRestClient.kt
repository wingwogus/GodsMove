package com.chamchamcham.api.auth.social.naver

import com.chamchamcham.application.auth.social.NaverProfile
import com.chamchamcham.application.auth.social.NaverProfileClient
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import java.time.LocalDate
import java.time.format.DateTimeParseException

@Component
class NaverProfileRestClient internal constructor(
    private val restClient: RestClient,
    private val profileUri: String
) : NaverProfileClient {
    @Autowired
    constructor(
        restClientBuilder: RestClient.Builder,
        @Value("\${auth.naver.profile-uri:https://openapi.naver.com/v1/nid/me}")
        profileUri: String,
        @Value("\${auth.naver.connect-timeout-millis:2000}")
        connectTimeoutMillis: Int,
        @Value("\${auth.naver.read-timeout-millis:2000}")
        readTimeoutMillis: Int
    ) : this(
        restClientBuilder
            .requestFactory(createRequestFactory(connectTimeoutMillis, readTimeoutMillis))
            .build(),
        profileUri
    )

    override fun fetch(accessToken: String): NaverProfile {
        val body = try {
            restClient.get()
                .uri(profileUri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .retrieve()
                .body(NaverProfileResponse::class.java)
        } catch (exception: RestClientResponseException) {
            throw BusinessException(exception.toErrorCode())
        } catch (exception: RestClientException) {
            throw BusinessException(ErrorCode.NAVER_PROFILE_UNAVAILABLE)
        }

        val response = body ?: throw BusinessException(ErrorCode.NAVER_PROFILE_UNAVAILABLE)
        if (response.resultcode != SUCCESS_RESULT_CODE) {
            throw BusinessException(ErrorCode.INVALID_NAVER_TOKEN)
        }

        val profile = response.response ?: throw BusinessException(ErrorCode.NAVER_PROFILE_UNAVAILABLE)
        val subject = profile.id?.takeIf { it.isNotBlank() }
            ?: throw BusinessException(ErrorCode.INVALID_NAVER_TOKEN)

        return NaverProfile(
            subject = subject,
            email = profile.email,
            name = profile.name,
            phone = profile.mobile,
            birthDate = parseBirthDate(profile.birthyear, profile.birthday)
        )
    }

    private fun parseBirthDate(birthyear: String?, birthday: String?): LocalDate? {
        val year = birthyear?.takeIf { it.isNotBlank() } ?: return null
        val monthDay = birthday?.takeIf { it.isNotBlank() } ?: return null

        return try {
            LocalDate.parse("$year-$monthDay")
        } catch (exception: DateTimeParseException) {
            null
        }
    }

    private data class NaverProfileResponse(
        val resultcode: String?,
        val message: String?,
        val response: NaverProfilePayload?
    )

    private data class NaverProfilePayload(
        val id: String?,
        val email: String?,
        val name: String?,
        val mobile: String?,
        val birthyear: String?,
        val birthday: String?
    )

    companion object {
        private const val SUCCESS_RESULT_CODE = "00"

        private fun createRequestFactory(
            connectTimeoutMillis: Int,
            readTimeoutMillis: Int
        ): SimpleClientHttpRequestFactory =
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(connectTimeoutMillis)
                setReadTimeout(readTimeoutMillis)
            }

        private fun RestClientResponseException.toErrorCode(): ErrorCode =
            when {
                statusCode == HttpStatus.UNAUTHORIZED -> ErrorCode.INVALID_NAVER_TOKEN
                statusCode == HttpStatus.FORBIDDEN -> ErrorCode.INVALID_NAVER_TOKEN
                statusCode.is4xxClientError -> ErrorCode.INVALID_NAVER_TOKEN
                statusCode.is5xxServerError -> ErrorCode.NAVER_PROFILE_UNAVAILABLE
                else -> ErrorCode.NAVER_PROFILE_UNAVAILABLE
            }
    }
}
