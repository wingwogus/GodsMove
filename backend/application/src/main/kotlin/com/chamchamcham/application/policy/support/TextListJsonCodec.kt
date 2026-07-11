package com.chamchamcham.application.policy.support

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.stereotype.Component

@Component
class TextListJsonCodec {
    private val objectMapper = jacksonObjectMapper()

    fun encode(values: Collection<String>): String {
        return objectMapper.writeValueAsString(values.map(String::trim).filter(String::isNotEmpty).distinct())
    }

    fun decode(json: String?): Set<String> {
        if (json.isNullOrBlank()) {
            return emptySet()
        }
        return runCatching { objectMapper.readValue<List<String>>(json).toSet() }.getOrElse { exception ->
            throw IllegalArgumentException("Malformed text list JSON", exception)
        }
    }
}
