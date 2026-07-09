package com.chamchamcham.application.common

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class OpaqueCursorCodecTest {
    private val codec = OpaqueCursorCodec()

    @Test
    fun `encode and decode cursor payload`() {
        val payload = TestCursorPayload(
            sort = "LIKE",
            score = 8,
            createdAt = LocalDateTime.of(2026, 6, 12, 9, 0),
            id = UUID.fromString("00000000-0000-0000-0000-000000000101")
        )

        val encoded = codec.encode(payload)
        val decoded = codec.decode(encoded, TestCursorPayload::class.java)

        assertThat(encoded).doesNotContain("{")
        assertThat(encoded).doesNotContain("=")
        assertThat(encoded).doesNotContain("+")
        assertThat(encoded).doesNotContain("/")
        assertThat(decoded).isEqualTo(payload)
    }

    @Test
    fun `decode rejects malformed cursor`() {
        val exception = assertThrows(BusinessException::class.java) {
            codec.decode("not-a-valid-cursor", TestCursorPayload::class.java)
        }

        assertThat(exception.errorCode).isEqualTo(ErrorCode.INVALID_CURSOR)
    }

    data class TestCursorPayload(
        val sort: String,
        val score: Long?,
        val createdAt: LocalDateTime,
        val id: UUID
    )
}
