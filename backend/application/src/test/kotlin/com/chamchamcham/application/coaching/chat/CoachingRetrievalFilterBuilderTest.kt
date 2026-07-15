package com.chamchamcham.application.coaching.chat

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CoachingRetrievalFilterBuilderTest {
    private val builder = CoachingRetrievalFilterBuilder()

    @Test
    fun `build restricts generic chat retrieval to technical documents`() {
        assertThat(builder.build()).isEqualTo("sourceType == 'TECH_DOCUMENT'")
    }
}
