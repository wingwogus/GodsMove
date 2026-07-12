package com.chamchamcham.application.pesticide.sync

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PsisPesticideResponseParserTest {
    private val parser = PsisPesticideResponseParser()

    @Test
    fun `parses repeated item elements into tag-to-text maps`() {
        val xml = """
            <response>
              <body>
                <items>
                  <item>
                    <cropNm>감자</cropNm>
                    <aplyPestNm>역병</aplyPestNm>
                    <prdtNm>만코제브 수화제</prdtNm>
                  </item>
                  <item>
                    <cropNm>강낭콩</cropNm>
                    <aplyPestNm>탄저병</aplyPestNm>
                    <prdtNm>만코제브 수화제</prdtNm>
                  </item>
                </items>
              </body>
            </response>
        """.trimIndent()

        val rows = parser.parse(xml)

        assertThat(rows).hasSize(2)
        assertThat(rows[0]).containsEntry("cropNm", "감자").containsEntry("aplyPestNm", "역병")
        assertThat(rows[1]).containsEntry("cropNm", "강낭콩").containsEntry("aplyPestNm", "탄저병")
    }

    @Test
    fun `returns empty list when no item elements exist`() {
        val xml = "<response><body><items/></body></response>"

        assertThat(parser.parse(xml)).isEmpty()
    }

    @Test
    fun `does not resolve external doctype entities`() {
        val xml = """<?xml version="1.0"?><!DOCTYPE items [<!ENTITY xxe "boom">]><response/>"""

        org.junit.jupiter.api.Assertions.assertThrows(Exception::class.java) { parser.parse(xml) }
    }
}
