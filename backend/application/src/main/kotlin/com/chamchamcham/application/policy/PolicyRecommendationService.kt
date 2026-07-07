package com.chamchamcham.application.policy

import com.chamchamcham.application.common.OpaqueCursorCodec
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.member.MemberRepository
import com.chamchamcham.domain.policy.PolicyProgram
import com.chamchamcham.domain.policy.PolicyProgramRepository
import com.chamchamcham.domain.policy.PolicyRecommendation
import com.chamchamcham.domain.policy.PolicyRecommendationQueryRepository
import com.chamchamcham.domain.policy.PolicyRecommendationRepository
import com.chamchamcham.domain.policy.PolicySource
import com.chamchamcham.domain.policy.PolicySyncJob
import com.chamchamcham.domain.policy.PolicySyncJobRepository
import com.chamchamcham.domain.policy.PolicySyncJobStatus
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

@Service
@Transactional
class PolicyRecommendationService(
    private val policySyncJobRepository: PolicySyncJobRepository,
    private val policyProgramRepository: PolicyProgramRepository,
    private val policyRecommendationRepository: PolicyRecommendationRepository,
    private val policyRecommendationQueryRepository: PolicyRecommendationQueryRepository,
    private val memberRepository: MemberRepository,
    private val memberProfileReader: PolicyMemberProfileReader,
    private val scorer: PolicyRecommendationScorer,
    private val textListJsonCodec: TextListJsonCodec,
    private val cursorCodec: OpaqueCursorCodec,
    private val clock: Clock = Clock.systemDefaultZone()
) {
    private val objectMapper = jacksonObjectMapper()

    fun listRecommendations(memberId: UUID, cursor: String?, size: Int): PolicyRecommendationResult.Page {
        validateSize(size)
        val latestJob = policySyncJobRepository.findFirstBySourceAndStatusOrderByTargetYearDescFinishedAtDesc(
            PolicySource.NONGUP_EZ,
            PolicySyncJobStatus.SUCCEEDED
        ) ?: return PolicyRecommendationResult.Page(emptyList(), null)
        val latestJobId = requireNotNull(latestJob.id)

        if (!policyRecommendationRepository.existsByMember_IdAndSourceSyncJob_Id(memberId, latestJobId)) {
            regenerate(memberId, latestJob)
        }

        val decodedCursor = decodeCursor(cursor, latestJobId)
        val result = policyRecommendationQueryRepository.findPage(
            PolicyRecommendationQueryRepository.SearchCondition(
                memberId = memberId,
                sourceSyncJobId = latestJobId,
                cursor = decodedCursor,
                size = size + 1
            )
        )
        val visible = result.rows.take(size)
        val nextCursor = if (result.rows.size > size) visible.lastOrNull()?.let(::encodeCursor) else null
        return PolicyRecommendationResult.Page(visible.map(::toCard), nextCursor)
    }

    fun getProgramDetail(memberId: UUID, policyProgramId: UUID): PolicyRecommendationResult.Detail {
        if (!memberRepository.existsById(memberId)) {
            throw BusinessException(ErrorCode.MEMBER_NOT_FOUND)
        }
        val program = policyProgramRepository.findByIdAndDetailSyncedTrueAndRecommendableTrue(policyProgramId)
            ?: throw BusinessException(ErrorCode.POLICY_PROGRAM_NOT_FOUND, detail = policyProgramId)

        return PolicyRecommendationResult.Detail(
            id = requireNotNull(program.id),
            programTitle = program.title,
            sourceYear = program.sourceYear,
            agencyName = program.agencyName,
            departmentName = program.departmentName,
            applicationPeriodLabel = program.applicationPeriodLabel,
            onlineApplyAvailable = program.onlineApplyAvailable,
            sourceUrl = program.sourceUrl,
            applicationUrl = program.applicationUrl,
            purpose = program.purpose,
            summary = program.summary,
            eligibility = program.eligibilityOriginal,
            benefit = program.benefitOriginal,
            applicationMethod = program.applicationMethod,
            requiredDocuments = program.requiredDocuments,
            selectionCriteria = program.selectionCriteria,
            contacts = parseContacts(program.rawPayload),
            attachments = parseAttachments(program.rawPayload)
        )
    }

    private fun regenerate(memberId: UUID, latestJob: PolicySyncJob) {
        val member = memberRepository.findById(memberId).orElseThrow {
            BusinessException(ErrorCode.MEMBER_NOT_FOUND)
        }
        val profile = memberProfileReader.read(memberId)
        policyRecommendationRepository.deleteByMember_Id(memberId)

        val today = LocalDate.now(clock)
        val latestJobId = requireNotNull(latestJob.id)
        val candidates = policyProgramRepository.findRecommendableCandidates(
            latestJobId,
            latestJob.targetYear,
            today
        )
        val recommendations = candidates.map { program ->
            val score = scorer.score(
                profile = profile,
                policyTargetTags = textListJsonCodec.decode(program.targetTagsJson),
                policyCropTags = textListJsonCodec.decode(program.cropTagsJson),
                policyRegionTags = textListJsonCodec.decode(program.regionTagsJson),
                onlineApplyAvailable = program.onlineApplyAvailable,
                today = today
            )
            PolicyRecommendation(
                member = member,
                policyProgram = program,
                sourceSyncJob = latestJob,
                score = score.score,
                reason = score.reason
            )
        }.filter { it.score > BigDecimal.ZERO }

        policyRecommendationRepository.saveAll(recommendations)
    }

    private fun validateSize(size: Int) {
        if (size !in 1..50) {
            throw BusinessException(ErrorCode.INVALID_INPUT)
        }
    }

    private fun decodeCursor(
        cursor: String?,
        latestJobId: UUID
    ): PolicyRecommendationQueryRepository.Cursor? {
        if (cursor.isNullOrBlank()) {
            return null
        }
        val payload = cursorCodec.decode(cursor, PolicyRecommendationCursorPayload::class.java)
        if (payload.sourceSyncJobId != latestJobId) {
            throw BusinessException(ErrorCode.INVALID_INPUT)
        }
        return PolicyRecommendationQueryRepository.Cursor(
            score = payload.score,
            applyEndsOn = payload.applyEndsOn,
            id = payload.id
        )
    }

    private fun encodeCursor(row: PolicyRecommendation): String =
        cursorCodec.encode(
            PolicyRecommendationCursorPayload(
                sourceSyncJobId = requireNotNull(row.sourceSyncJob.id),
                score = row.score,
                applyEndsOn = row.policyProgram.applyEndsOn,
                id = requireNotNull(row.id)
            )
        )

    private fun toCard(row: PolicyRecommendation): PolicyRecommendationResult.Card {
        val program = row.policyProgram
        return PolicyRecommendationResult.Card(
            recommendationId = requireNotNull(row.id),
            policyProgramId = requireNotNull(program.id),
            programTitle = program.title,
            eligibilitySummary = program.eligibilitySummary,
            benefitSummary = program.benefitSummary,
            applicationPeriodLabel = program.applicationPeriodLabel,
            agencyName = program.agencyName,
            score = row.score,
            reason = row.reason
        )
    }

    private fun parseContacts(rawPayload: String): List<PolicyRecommendationResult.Contact> {
        val root = parseRawPayload(rawPayload) ?: return emptyList()
        return root.arrayField("bizPicList", "contacts").mapNotNull { contact ->
            val agencyName = contact.nullableText("instNm") ?: contact.nullableText("agencyName")
            val departmentName = contact.nullableText("deptNm") ?: contact.nullableText("departmentName")
                ?: contact.nullableText("department")
            val phoneNumber = contact.nullableText("telno") ?: contact.nullableText("phoneNumber")
                ?: contact.nullableText("phone")
            if (agencyName == null && departmentName == null && phoneNumber == null) {
                null
            } else {
                PolicyRecommendationResult.Contact(
                    agencyName = agencyName,
                    departmentName = departmentName,
                    phoneNumber = phoneNumber
                )
            }
        }
    }

    private fun parseAttachments(rawPayload: String): List<PolicyRecommendationResult.Attachment> {
        val root = parseRawPayload(rawPayload) ?: return emptyList()
        return root.arrayField("bizAtchFileList", "attachments").mapNotNull { attachment ->
            val fileName = attachment.nullableText("atchFileNm") ?: attachment.nullableText("fileName")
                ?: attachment.nullableText("name")
            val extension = attachment.nullableText("fileExtnNm") ?: attachment.nullableText("extension")
            val sizeBytes = attachment.path("fileSz").takeIf { it.isNumber }?.asLong()
                ?: attachment.path("sizeBytes").takeIf { it.isNumber }?.asLong()
            val url = attachment.nullableText("url")
            if (fileName == null && extension == null && sizeBytes == null && url == null) {
                null
            } else {
                PolicyRecommendationResult.Attachment(
                    fileName = fileName,
                    extension = extension,
                    sizeBytes = sizeBytes,
                    url = url
                )
            }
        }
    }

    private fun parseRawPayload(rawPayload: String): JsonNode? =
        runCatching {
            val root = objectMapper.readTree(rawPayload)
            root.path("result").takeIf { !it.isMissingNode && !it.isNull } ?: root
        }.getOrNull()

    private fun JsonNode.nullableText(field: String): String? =
        path(field).takeIf { it.isTextual }?.asText()?.trim()?.takeIf { it.isNotEmpty() }

    private fun JsonNode.arrayField(vararg fields: String): Iterable<JsonNode> =
        fields.firstNotNullOfOrNull { field -> path(field).takeIf { it.isArray } } ?: emptyList()
}
