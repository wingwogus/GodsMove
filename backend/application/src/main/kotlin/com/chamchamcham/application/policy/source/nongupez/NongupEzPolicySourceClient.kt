package com.chamchamcham.application.policy.source.nongupez

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
        val json = transport.post(CONDITIONS_PATH, emptyMap())
        val root = objectMapper.readTree(json)
        root.throwIfBusinessError(CONDITIONS_PATH)
        val years = root.path("dsBizSrchCndList")
            .filter { node -> node.path("srchCndKywd").asText() == "SRCH_BIZ_YR" }
            .mapNotNull { node -> node.path("srchCndDtlCd").asText(null) }
            .ifEmpty {
                root.path("result").path("SRCH_BIZ_YR")
                    .mapNotNull { node -> node.path("cd").asText(null) }
            }
        return years
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
                LIST_PATH,
                mapOf(
                    "srchCnd" to mapOf(
                        "srchBizYr" to year,
                        "sortCnd" to "M"
                    ),
                    "paging" to mapOf(
                        "curPage" to pageIndex,
                        "pageSize" to pageUnit
                    )
                )
            )
            val root = objectMapper.readTree(json)
            root.throwIfBusinessError(LIST_PATH)
            val pageNodes = root.path("bizList")
            totalCount = root.path("paging").path("totalPage")
                .asInt(root.path("paging").path("pageCount").asInt(pageNodes.size()))
            val pageItems = pageNodes.map { item -> toListItem(item, year) }
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
            DETAIL_PATH,
            mapOf("afbzCd" to externalId, "bizYr" to year)
        )
        val root = objectMapper.readTree(json)
        root.throwIfBusinessError(DETAIL_PATH)
        val detail = root.path("bizDtl").takeIfPresent() ?: root.path("result")
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
            contacts = root.path("bizPicList").map { contact ->
                NongupEzPolicyContact(
                    agencyName = contact.nullableText("bizTkcgInstCdNm") ?: contact.nullableText("instNm"),
                    departmentName = contact.nullableText("bizTkcgDeptInstCdNm") ?: contact.nullableText("deptNm"),
                    phoneNumber = contact.nullableText("bizPicTelno") ?: contact.nullableText("telno")
                )
            },
            attachments = root.path("bizAtchFileList").map { file ->
                NongupEzPolicyAttachment(
                    fileName = file.nullableText("originalName") ?: file.nullableText("atchFileNm"),
                    extension = file.nullableText("extension") ?: file.nullableText("fileExtnNm"),
                    sizeBytes = file.path("size").takeIf { it.isNumber }?.asLong()
                        ?: file.path("fileSz").takeIf { it.isNumber }?.asLong(),
                    url = file.nullableText("url")
                )
            },
            rawJson = objectMapper.writeValueAsString(root)
        )
    }

    private fun toListItem(item: JsonNode, year: String): NongupEzPolicyListItem {
        return NongupEzPolicyListItem(
            externalId = item.text("afbzCd"),
            sourceYear = item.text("bizYr").ifBlank { year },
            title = item.text("afbzNm"),
            summary = item.nullableText("bizCn"),
            agencyName = item.nullableText("bizAtrbDtlNm") ?: item.nullableText("bizTkcgInstNm") ?: "농업e지",
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

    private fun JsonNode.takeIfPresent(): JsonNode? =
        takeIf { !it.isMissingNode && !it.isNull }

    private fun JsonNode.throwIfBusinessError(path: String) {
        val message = path("message").takeIfPresent() ?: return
        if (message.path("status").asText().equals("ERROR", ignoreCase = true)) {
            val code = message.path("code").asText("")
            val content = message.path("content").asText("")
            error("NongupEZ business error for $path: $code $content".trim())
        }
    }

    private companion object {
        private const val CONDITIONS_PATH = "/nsm/bizAply/wholeBiz/retrieveListBizSrchCnd"
        private const val LIST_PATH = "/nsm/bizAply/wholeBiz/retrieveListBizSrch"
        private const val DETAIL_PATH = "/nsm/bizAply/cstBiz/findBizSrchDtl"
    }
}
