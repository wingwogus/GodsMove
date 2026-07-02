package com.chamchamcham.api.auth.social.kakao

import com.chamchamcham.application.auth.social.KakaoUserInfo
import com.chamchamcham.application.auth.social.KakaoUserInfoClient
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.fasterxml.jackson.annotation.JsonProperty
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
class KakaoUserInfoRestClient internal constructor(
    private val restClient: RestClient,
    private val userInfoUri: String
) : KakaoUserInfoClient {
    @Autowired
    constructor(
        restClientBuilder: RestClient.Builder,
        @Value("\${auth.kakao.oidc.user-info-uri:https://kapi.kakao.com/v1/oidc/userinfo}")
        userInfoUri: String,
        @Value("\${auth.kakao.oidc.connect-timeout-millis:2000}")
        connectTimeoutMillis: Int,
        @Value("\${auth.kakao.oidc.read-timeout-millis:2000}")
        readTimeoutMillis: Int
    ) : this(
        restClientBuilder
            .requestFactory(createRequestFactory(connectTimeoutMillis, readTimeoutMillis))
            .build(),
        userInfoUri
    )

    override fun fetch(accessToken: String): KakaoUserInfo {
        val body = try {
            restClient.get()
                .uri(userInfoUri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .retrieve()
                .body(KakaoUserInfoResponse::class.java)
        } catch (exception: RestClientResponseException) {
            throw BusinessException(exception.toErrorCode())
        } catch (exception: RestClientException) {
            throw BusinessException(ErrorCode.KAKAO_OIDC_UNAVAILABLE)
        }

        val response = body ?: throw BusinessException(ErrorCode.KAKAO_OIDC_UNAVAILABLE)
        val subject = response.subject?.takeIf { it.isNotBlank() }
            ?: throw BusinessException(ErrorCode.INVALID_KAKAO_TOKEN)

        return KakaoUserInfo(
            subject = subject,
            email = response.email.takeIf { response.emailVerified == true },
            name = response.name,
            phone = response.phoneNumber,
            birthDate = parseBirthDate(response.birthdate)
        )
    }

    private fun parseBirthDate(birthdate: String?): LocalDate? {
        val value = birthdate?.takeIf { it.matches(COMPLETE_BIRTHDATE_REGEX) } ?: return null
        if (value.startsWith("0000-")) {
            return null
        }

        return try {
            LocalDate.parse(value)
        } catch (exception: DateTimeParseException) {
            null
        }
    }

    private data class KakaoUserInfoResponse(
        @JsonProperty("sub")
        val subject: String? = null,
        val name: String? = null,
        val email: String? = null,
        @JsonProperty("email_verified")
        val emailVerified: Boolean? = null,
        val birthdate: String? = null,
        @JsonProperty("phone_number")
        val phoneNumber: String? = null,
        @JsonProperty("phone_number_verified")
        val phoneNumberVerified: Boolean? = null
    )

    companion object {
        private val COMPLETE_BIRTHDATE_REGEX = Regex("""\d{4}-\d{2}-\d{2}""")

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
                statusCode == HttpStatus.UNAUTHORIZED -> ErrorCode.INVALID_KAKAO_TOKEN
                statusCode == HttpStatus.FORBIDDEN -> ErrorCode.INVALID_KAKAO_TOKEN
                statusCode.is4xxClientError -> ErrorCode.INVALID_KAKAO_TOKEN
                statusCode.is5xxServerError -> ErrorCode.KAKAO_OIDC_UNAVAILABLE
                else -> ErrorCode.KAKAO_OIDC_UNAVAILABLE
            }
    }
}
