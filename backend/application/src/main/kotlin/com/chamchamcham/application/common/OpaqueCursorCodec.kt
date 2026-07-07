package com.chamchamcham.application.common

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.util.Base64

@Component
class OpaqueCursorCodec {
    private val objectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    fun encode(payload: Any): String {
        val json = objectMapper.writeValueAsString(payload)
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(json.toByteArray(StandardCharsets.UTF_8))
    }

    fun <T : Any> decode(cursor: String, payloadType: Class<T>): T {
        return try {
            val json = String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8)
            objectMapper.readValue(json, payloadType)
        } catch (exception: Exception) {
            throw BusinessException(ErrorCode.INVALID_CURSOR)
        }
    }
}
