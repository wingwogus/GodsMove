package com.chamchamcham.application.coaching.reportfeedback.generation

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.security.MessageDigest

@Component
class ReportFeedbackContextFingerprint(
    private val objectMapper: ObjectMapper,
) {
    fun calculate(context: ReportFeedbackContext): String {
        return calculate(objectMapper.convertValue(context, SNAPSHOT_TYPE))
    }

    fun calculate(snapshot: Map<String, Any?>): String {
        return calculateCanonical(snapshot)
    }

    private fun calculateCanonical(source: Any): String {
        val tree = objectMapper.valueToTree<JsonNode>(source)
        val canonicalBytes = objectMapper.writer()
            .with(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN)
            .writeValueAsBytes(canonicalize(tree))
        return MessageDigest.getInstance("SHA-256")
            .digest(canonicalBytes)
            .joinToString(separator = "") { byte ->
                (byte.toInt() and 0xff).toString(16).padStart(2, '0')
            }
    }

    private fun canonicalize(node: JsonNode): JsonNode = when {
        node.isObject -> objectMapper.createObjectNode().also { canonical ->
            node.fields().asSequence()
                .sortedBy { it.key }
                .forEach { (name, value) -> canonical.set<JsonNode>(name, canonicalize(value)) }
        }
        node.isArray -> objectMapper.createArrayNode().also { canonical ->
            node.forEach { value -> canonical.add(canonicalize(value)) }
        }
        node.isNumber -> objectMapper.nodeFactory.numberNode(node.decimalValue().canonicalValue())
        else -> node
    }

    private fun BigDecimal.canonicalValue(): BigDecimal =
        stripTrailingZeros().let { normalized ->
            if (normalized.scale() < 0) normalized.setScale(0) else normalized
        }

    private companion object {
        val SNAPSHOT_TYPE = object : TypeReference<Map<String, Any?>>() {}
    }
}
