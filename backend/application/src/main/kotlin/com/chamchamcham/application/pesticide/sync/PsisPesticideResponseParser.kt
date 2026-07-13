package com.chamchamcham.application.pesticide.sync

import org.springframework.stereotype.Component
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 데이터포털/PSIS류 XML 응답에서 반복되는 <item> 엘리먼트를 태그명 -> 텍스트값 맵으로 평탄화한다.
 * 실제 태그명(예: cropNm, aplyPestNm 등)은 API 키 발급 후 실응답으로 확정하고
 * [PsisPesticideRowMapper]에서만 매핑하면 되도록, 이 파서는 특정 필드명을 알 필요가 없게 만든다.
 */
@Component
class PsisPesticideResponseParser {
    fun parse(xml: String): List<Map<String, String>> = parseEnvelope(xml).items

    fun parseEnvelope(xml: String): PsisPesticideEnvelope {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setXIncludeAware(false)
            setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
        }
        val document = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))

        val items = (0 until document.getElementsByTagName("item").length).map { index ->
            val itemElement = document.getElementsByTagName("item").item(index) as Element
            val children = itemElement.childNodes
            (0 until children.length)
                .mapNotNull { children.item(it) as? Element }
                .associate { it.tagName to it.textContent.trim() }
        }

        return PsisPesticideEnvelope(
            resultCode = firstTagText(document, "resultCode"),
            resultMsg = firstTagText(document, "resultMsg"),
            totalCount = firstTagText(document, "totalCount")?.toIntOrNull(),
            items = items,
        )
    }

    private fun firstTagText(document: org.w3c.dom.Document, tagName: String): String? {
        val nodes = document.getElementsByTagName(tagName)
        if (nodes.length == 0) {
            return null
        }
        return (nodes.item(0) as Element).textContent.trim()
    }
}
