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

    @Test
    fun `parseEnvelope extracts resultCode, resultMsg, totalCount and items from an error envelope`() {
        val xml = """
            <response>
              <header>
                <resultCode>03</resultCode>
                <resultMsg>NODATA_ERROR</resultMsg>
              </header>
              <body>
                <items/>
                <totalCount>0</totalCount>
              </body>
            </response>
        """.trimIndent()

        val envelope = parser.parseEnvelope(xml)

        assertThat(envelope.resultCode).isEqualTo("03")
        assertThat(envelope.resultMsg).isEqualTo("NODATA_ERROR")
        assertThat(envelope.totalCount).isEqualTo(0)
        assertThat(envelope.items).isEmpty()
    }

    @Test
    fun `parseEnvelope reads totalCount and items from a success envelope`() {
        val xml = """
            <response>
              <header>
                <resultCode>00</resultCode>
                <resultMsg>NORMAL_SERVICE</resultMsg>
              </header>
              <body>
                <items>
                  <item>
                    <cropNm>감자</cropNm>
                  </item>
                </items>
                <totalCount>1</totalCount>
              </body>
            </response>
        """.trimIndent()

        val envelope = parser.parseEnvelope(xml)

        assertThat(envelope.resultCode).isEqualTo("00")
        assertThat(envelope.totalCount).isEqualTo(1)
        assertThat(envelope.items).hasSize(1)
    }

    @Test
    fun `parseEnvelope leaves totalCount null when the tag is absent`() {
        val xml = "<response><body><items/></body></response>"

        val envelope = parser.parseEnvelope(xml)

        assertThat(envelope.resultCode).isNull()
        assertThat(envelope.resultMsg).isNull()
        assertThat(envelope.totalCount).isNull()
        assertThat(envelope.items).isEmpty()
    }
}
