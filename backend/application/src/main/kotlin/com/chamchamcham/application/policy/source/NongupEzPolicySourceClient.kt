package com.chamchamcham.application.policy.source

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Component
class NongupEzPolicySourceClient(
    private val transport: NongupEzHttpTransport
) {
    private val objectMapper = jacksonObjectMapper()
    private val basicDate = DateTimeFormatter.BASIC_ISO_DATE

    fun detectLatestYear(): String {
        val json = transport.post("/nsm/bizAply/wholeBiz/retrieveListBizSrchCnd", emptyMap())
        val root = objectMapper.readTree(json)
        return root.path("result").path("SRCH_BIZ_YR")
            .mapNotNull { node -> node.path("cd").asText(null) }
            .filter { it != "0000" && it.length == 4 && it.all(Char::isDigit) }
            .maxOrNull()
            ?: error("NongupEZ business year condition is empty")
    }

    fun fetchPrograms(year: String): List<NongupEzPolicyListItem> {
        val pageUnit = 1000
        val items = mutableListOf<NongupEzPolicyListItem>()
        var pageIndex = 1
        var totalCount = Int.MAX_VALUE
        while (items.size < totalCount) {
            val json = transport.post(
                "/nsm/bizAply/wholeBiz/retrieveListBizSrch",
                mapOf("bizYr" to year, "pageIndex" to pageIndex.toString(), "pageUnit" to pageUnit.toString())
            )
            val root = objectMapper.readTree(json)
            totalCount = root.path("result").path("totalCount").asInt(root.path("result").path("list").size())
            val pageItems = root.path("result").path("list").map { item -> toListItem(item, year) }
            if (pageItems.isEmpty()) {
                break
            }
            items += pageItems
            pageIndex += 1
        }
        return items
    }

    fun fetchDetail(externalId: String, year: String): NongupEzPolicyDetail {
        val json = transport.post(
            "/nsm/bizAply/cstBiz/findBizSrchDtl",
            mapOf("afbzCd" to externalId, "bizYr" to year)
        )
        val detail = objectMapper.readTree(json).path("result")
        return NongupEzPolicyDetail(
            externalId = detail.text("afbzCd").ifBlank { externalId },
            sourceYear = detail.text("bizYr").ifBlank { year },
            title = detail.text("afbzNm"),
            purpose = detail.nullableText("bizPrpsCn"),
            summary = detail.nullableText("bizCn"),
            eligibility = detail.nullableText("bizSprtQlfcCn"),
            benefit = detail.nullableText("bizSprtCn"),
            applyStartsOn = detail.localDate("bizAplyBgngYmd"),
            applyEndsOn = detail.localDate("bizAplyEndYmd"),
            applicationMethod = detail.nullableText("bizAplyMthdCn"),
            requiredDocuments = detail.nullableText("bizRqdcCn"),
            selectionCriteria = detail.nullableText("bizSlctnCrtrCn"),
            agencyName = detail.nullableText("bizTkcgInstNm") ?: "농업e지",
            contacts = detail.path("bizPicList").map { contact ->
                NongupEzPolicyContact(
                    agencyName = contact.nullableText("instNm"),
                    departmentName = contact.nullableText("deptNm"),
                    phoneNumber = contact.nullableText("telno")
                )
            },
            attachments = detail.path("bizAtchFileList").map { file ->
                NongupEzPolicyAttachment(
                    fileName = file.nullableText("atchFileNm"),
                    extension = file.nullableText("fileExtnNm"),
                    sizeBytes = file.path("fileSz").takeIf { it.isNumber }?.asLong(),
                    url = file.nullableText("url")
                )
            },
            rawJson = objectMapper.writeValueAsString(detail)
        )
    }

    private fun toListItem(item: JsonNode, year: String): NongupEzPolicyListItem {
        return NongupEzPolicyListItem(
            externalId = item.text("afbzCd"),
            sourceYear = item.text("bizYr").ifBlank { year },
            title = item.text("afbzNm"),
            summary = item.nullableText("bizCn"),
            agencyName = item.nullableText("bizTkcgInstNm") ?: "농업e지",
            applyStartsOn = item.localDate("bizAplyBgngYmd"),
            applyEndsOn = item.localDate("bizAplyEndYmd"),
            rawJson = objectMapper.writeValueAsString(item)
        )
    }

    private fun JsonNode.text(field: String): String =
        nullableText(field) ?: ""

    private fun JsonNode.nullableText(field: String): String? =
        path(field).takeUnless { it.isMissingNode || it.isNull }?.asText()?.trim()?.takeIf(String::isNotEmpty)

    private fun JsonNode.localDate(field: String): LocalDate? =
        nullableText(field)?.takeIf { it.length == 8 && it.all(Char::isDigit) }?.let {
            LocalDate.parse(it, basicDate)
        }
}
