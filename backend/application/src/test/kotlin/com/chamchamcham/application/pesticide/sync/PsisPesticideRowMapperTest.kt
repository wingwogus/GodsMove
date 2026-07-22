package com.chamchamcham.application.pesticide.sync

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PsisPesticideRowMapperTest {
    private val mapper = PsisPesticideRowMapper()

    @Test
    fun `maps a raw row using the real PSIS tag names`() {
        val raw = mapOf(
            "pestiKorName" to "가스가마이신 액제",
            "pestiBrandName" to "가스가민",
            "cropName" to "벼",
            "diseaseWeedName" to "세균벼알마름병",
            "useName" to "살균",
            "engName" to "Kasugamycin SL 2.3 %",
            "indictSymbl" to "라3",
            "compName" to "(주)동방아그로",
            "dilutUnit" to "1000배 -",
            "useSuittime" to "수확14일전",
            "useNum" to "5회",
        )

        val row = mapper.map(raw)

        requireNotNull(row)
        assertEquals("가스가마이신 액제", row.itemName)
        assertEquals("가스가민", row.brandName)
        assertEquals("벼", row.cropName)
        assertEquals("세균벼알마름병", row.pestName)
        assertEquals("살균", row.usageCategory)
        assertEquals("Kasugamycin SL 2.3 %", row.activeIngredient)
        assertEquals("라3", row.humanToxicity)
        assertEquals("(주)동방아그로", row.manufacturer)
        assertEquals("1000배 -", row.dilutionRate)
        assertEquals("수확14일전", row.usageTiming)
        assertEquals("5회", row.maxUsageCount)
    }

    @Test
    fun `formulation fishToxicity and usageAmount are always null since PSIS has no matching tag`() {
        val raw = mapOf(
            "pestiKorName" to "가스가마이신 액제",
            "cropName" to "벼",
            "diseaseWeedName" to "세균벼알마름병",
        )

        val row = mapper.map(raw)

        requireNotNull(row)
        assertNull(row.formulation)
        assertNull(row.fishToxicity)
        assertNull(row.usageAmount)
    }

    @Test
    fun `falls back to item name when brand name is missing`() {
        val raw = mapOf("pestiKorName" to "가스가마이신 액제", "cropName" to "벼", "diseaseWeedName" to "세균벼알마름병")

        val row = mapper.map(raw)

        requireNotNull(row)
        assertEquals("가스가마이신 액제", row.brandName)
    }

    @Test
    fun `returns null when a required field is missing`() {
        val raw = mapOf("pestiKorName" to "가스가마이신 액제", "cropName" to "벼")

        assertNull(mapper.map(raw))
    }

    @Test
    fun `diagnoseRequired resolves all required fields for a well-formed row`() {
        val raw = mapOf(
            "pestiKorName" to "가스가마이신 액제",
            "cropName" to "벼",
            "diseaseWeedName" to "세균벼알마름병",
        )

        val diagnosis = mapper.diagnoseRequired(raw)

        assertEquals(mapOf("itemName" to true, "cropName" to true, "pestName" to true), diagnosis)
    }

    @Test
    fun `diagnoseRequired marks a field false when its tag is absent`() {
        val raw = mapOf(
            "pestiKorName" to "가스가마이신 액제",
            "cropName" to "벼",
        )

        val diagnosis = mapper.diagnoseRequired(raw)

        assertEquals(mapOf("itemName" to true, "cropName" to true, "pestName" to false), diagnosis)
    }
}
