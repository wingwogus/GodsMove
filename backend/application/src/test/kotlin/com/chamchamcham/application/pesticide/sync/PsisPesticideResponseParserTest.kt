package com.chamchamcham.application.pesticide.sync

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PsisPesticideResponseParserTest {
    private val parser = PsisPesticideResponseParser()

    @Test
    fun `parses repeated item elements into tag-to-text maps`() {
        val xml = """
            <service>
              <totalCount>2</totalCount>
              <list>
                <item>
                  <cropName>감자</cropName>
                  <diseaseWeedName>역병</diseaseWeedName>
                  <pestiKorName>만코제브 수화제</pestiKorName>
                </item>
                <item>
                  <cropName>강낭콩</cropName>
                  <diseaseWeedName>탄저병</diseaseWeedName>
                  <pestiKorName>만코제브 수화제</pestiKorName>
                </item>
              </list>
            </service>
        """.trimIndent()

        val rows = parser.parse(xml)

        assertThat(rows).hasSize(2)
        assertThat(rows[0]).containsEntry("cropName", "감자").containsEntry("diseaseWeedName", "역병")
        assertThat(rows[1]).containsEntry("cropName", "강낭콩").containsEntry("diseaseWeedName", "탄저병")
    }

    @Test
    fun `returns empty list when no item elements exist`() {
        val xml = "<service><list/></service>"

        assertThat(parser.parse(xml)).isEmpty()
    }

    @Test
    fun `does not resolve external doctype entities`() {
        val xml = """<?xml version="1.0"?><!DOCTYPE items [<!ENTITY xxe "boom">]><service/>"""

        org.junit.jupiter.api.Assertions.assertThrows(Exception::class.java) { parser.parse(xml) }
    }

    @Test
    fun `parseEnvelope extracts errorCode, errorMsg and empty items from an error envelope`() {
        val xml = """
            <service>
              <errorCode>ERR_101</errorCode>
              <errorMsg>인증키가 등록되지 않았습니다. 정상적인 인증키를 확인하세요.</errorMsg>
            </service>
        """.trimIndent()

        val envelope = parser.parseEnvelope(xml)

        assertThat(envelope.errorCode).isEqualTo("ERR_101")
        assertThat(envelope.errorMsg).isEqualTo("인증키가 등록되지 않았습니다. 정상적인 인증키를 확인하세요.")
        assertThat(envelope.totalCount).isNull()
        assertThat(envelope.items).isEmpty()
    }

    @Test
    fun `parseEnvelope reads totalCount and items from a success envelope with no errorCode`() {
        val xml = """
            <service>
              <totalCount>143912</totalCount>
              <buildTime>15:51:31[935]</buildTime>
              <list>
                <item>
                  <pestiCode>973</pestiCode>
                  <cropName>벼</cropName>
                  <diseaseWeedName>세균벼알마름병</diseaseWeedName>
                  <useName>살균</useName>
                  <pestiKorName>가스가마이신 액제</pestiKorName>
                  <pestiBrandName>가스가민</pestiBrandName>
                  <compName>(주)동방아그로</compName>
                  <engName>Kasugamycin SL 2.3 %</engName>
                  <indictSymbl>라3</indictSymbl>
                  <dilutUnit>1000배 -</dilutUnit>
                  <useSuittime>수확14일전</useSuittime>
                  <useNum>5회</useNum>
                </item>
              </list>
              <displayCount>10</displayCount>
              <startPoint>1</startPoint>
            </service>
        """.trimIndent()

        val envelope = parser.parseEnvelope(xml)

        assertThat(envelope.errorCode).isNull()
        assertThat(envelope.errorMsg).isNull()
        assertThat(envelope.totalCount).isEqualTo(143912)
        assertThat(envelope.items).hasSize(1)
        assertThat(envelope.items[0]).containsEntry("pestiKorName", "가스가마이신 액제")
    }

    @Test
    fun `parseEnvelope leaves totalCount null when the tag is absent`() {
        val xml = "<service><list/></service>"

        val envelope = parser.parseEnvelope(xml)

        assertThat(envelope.errorCode).isNull()
        assertThat(envelope.errorMsg).isNull()
        assertThat(envelope.totalCount).isNull()
        assertThat(envelope.items).isEmpty()
    }
}
