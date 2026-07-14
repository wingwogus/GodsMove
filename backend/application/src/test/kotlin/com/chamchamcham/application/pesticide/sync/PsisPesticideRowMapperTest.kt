package com.chamchamcham.application.pesticide.sync

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PsisPesticideRowMapperTest {
    private val mapper = PsisPesticideRowMapper()

    @Test
    fun `maps a raw row using primary candidate tag names`() {
        val raw = mapOf(
            "prdtNm" to "만코제브 수화제",
            "trdmrkNm" to "가가방",
            "cropNm" to "감자",
            "aplyPestNm" to "역병",
            "dltnMag" to "500배",
        )

        val row = mapper.map(raw)

        requireNotNull(row)
        assertEquals("만코제브 수화제", row.itemName)
        assertEquals("가가방", row.brandName)
        assertEquals("감자", row.cropName)
        assertEquals("역병", row.pestName)
        assertEquals("500배", row.dilutionRate)
    }

    @Test
    fun `falls back to item name when brand name is missing`() {
        val raw = mapOf("prdtNm" to "만코제브 수화제", "cropNm" to "감자", "aplyPestNm" to "역병")

        val row = mapper.map(raw)

        requireNotNull(row)
        assertEquals("만코제브 수화제", row.brandName)
    }

    @Test
    fun `returns null when a required field is missing`() {
        val raw = mapOf("prdtNm" to "만코제브 수화제", "cropNm" to "감자")

        assertNull(mapper.map(raw))
    }
}
