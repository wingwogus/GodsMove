package com.chamchamcham.application.policy.recommendation

import com.chamchamcham.application.common.OpaqueCursorCodec
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.application.policy.support.PolicyBenefitCategory
import com.chamchamcham.application.policy.support.TextListJsonCodec
import com.chamchamcham.domain.common.BaseTimeEntity
import com.chamchamcham.domain.crop.MemberCrop
import com.chamchamcham.domain.crop.MemberCropRepository
import com.chamchamcham.domain.farm.Farm
import com.chamchamcham.domain.farm.FarmRepository
import com.chamchamcham.domain.member.Member
import com.chamchamcham.domain.member.MemberRepository
import com.chamchamcham.domain.policy.PolicyProgram
import com.chamchamcham.domain.policy.PolicyProgramRepository
import com.chamchamcham.domain.policy.PolicyRecommendation
import com.chamchamcham.domain.policy.PolicyRecommendationQueryRepository
import com.chamchamcham.domain.policy.PolicyRecommendationRepository
import com.chamchamcham.domain.policy.PolicyRecommendationSort
import com.chamchamcham.domain.policy.PolicySource
import com.chamchamcham.domain.policy.PolicySyncJobRepository
import com.chamchamcham.domain.policy.PolicySyncJobStatus
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Service
@Transactional
class PolicyRecommendationService(
    private val policySyncJobRepository: PolicySyncJobRepository,
    private val policyProgramRepository: PolicyProgramRepository,
    private val policyRecommendationRepository: PolicyRecommendationRepository,
    private val policyRecommendationQueryRepository: PolicyRecommendationQueryRepository,
    private val memberRepository: MemberRepository,
    private val memberCropRepository: MemberCropRepository,
    private val farmRepository: FarmRepository,
    private val regionMatcher: PolicyRegionMatcher,
    private val scorer: PolicyRecommendationScorer,
    private val textListJsonCodec: TextListJsonCodec,
    private val cursorCodec: OpaqueCursorCodec,
    private val clock: Clock = Clock.systemDefaultZone()
) {
    private val objectMapper = jacksonObjectMapper()

    fun listRecommendations(
        memberId: UUID,
        cursor: String?,
        size: Int,
        benefitCategory: PolicyBenefitCategory? = null,
        sort: PolicyRecommendationSort = PolicyRecommendationSort.RECOMMENDED
    ): PolicyRecommendationResult.Page {
        validateSize(size)
        val latestJob = policySyncJobRepository.findFirstBySourceAndStatusOrderByTargetYearDescFinishedAtDesc(
            PolicySource.NONGUP_EZ,
            PolicySyncJobStatus.SUCCEEDED
        ) ?: return PolicyRecommendationResult.Page(emptyList(), null)
        val source = PolicySource.NONGUP_EZ
        val sourceYear = latestJob.targetYear
        val decodedCursor = decodeCursor(cursor, source, sourceYear, benefitCategory, sort)
        val today = LocalDate.now(clock)
        val candidates = policyProgramRepository.findRecommendableCandidates(
            source,
            sourceYear,
            today
        )
        val member = memberRepository.findById(memberId).orElseThrow {
            BusinessException(ErrorCode.MEMBER_NOT_FOUND)
        }
        val farms = farmRepository.findAllWithBoundaryCoordinatesByOwnerId(memberId)
        val memberCrops = memberCropRepository.findAllWithCropByMemberId(memberId)

        if (recommendationsAreStale(memberId, source, sourceYear, candidates, member, memberCrops, farms)) {
            regenerate(memberId, member, source, sourceYear, candidates, today, memberCrops, farms)
        }

        val result = policyRecommendationQueryRepository.findPage(
            PolicyRecommendationQueryRepository.SearchCondition(
                memberId = memberId,
                source = source,
                sourceYear = sourceYear,
                benefitSummary = benefitCategory?.label,
                sort = sort,
                cursor = decodedCursor,
                size = size + 1
            )
        )
        val visible = result.rows.take(size)
        val nextCursor = if (result.rows.size > size) {
            visible.lastOrNull()?.let { row -> encodeCursor(row, source, sourceYear, benefitCategory, sort) }
        } else {
            null
        }
        return PolicyRecommendationResult.Page(visible.map(::toCard), nextCursor)
    }

    @Transactional(readOnly = true)
    fun searchRecommendations(
        memberId: UUID,
        keyword: String?,
        cursor: String?,
        size: Int,
    ): PolicyRecommendationResult.Page {
        validateSize(size)
        val payload = decodeSearchCursor(cursor)
        val rows = policyRecommendationQueryRepository.searchByMember(
            PolicyRecommendationQueryRepository.MemberSearchCondition(
                memberId = memberId,
                keyword = keyword,
                cursorCreatedAt = payload?.createdAt,
                cursorId = payload?.id,
                size = size + 1
            )
        )
        val visible = rows.take(size)
        val nextCursor = if (rows.size > size) {
            visible.lastOrNull()?.let { row ->
                cursorCodec.encode(PolicyRecommendationSearchCursorPayload(row.createdAt, requireNotNull(row.id)))
            }
        } else {
            null
        }
        return PolicyRecommendationResult.Page(visible.map(::toCard), nextCursor)
    }

    @Transactional(readOnly = true)
    fun countSearchRecommendations(memberId: UUID, keyword: String?): Long {
        return policyRecommendationQueryRepository.countByMember(
            PolicyRecommendationQueryRepository.MemberSearchCondition(
                memberId = memberId,
                keyword = keyword,
                cursorCreatedAt = null,
                cursorId = null,
                size = COUNT_QUERY_SIZE
            )
        )
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

    private fun recommendationsAreStale(
        memberId: UUID,
        source: PolicySource,
        sourceYear: String,
        candidates: List<PolicyProgram>,
        member: Member,
        memberCrops: List<MemberCrop>,
        farms: List<Farm>
    ): Boolean {
        val candidatePolicyIds = candidates.map { requireNotNull(it.id) }.toSet()
        val recommendationPolicyIds = policyRecommendationRepository
            .findPolicyProgramIdsByMemberIdAndPolicyProgramSourceAndSourceYear(memberId, source, sourceYear)
            .toSet()
        val newestRecommendationCreatedAt = policyRecommendationRepository
            .findNewestCreatedAtByMemberIdAndPolicyProgramSourceAndSourceYear(memberId, source, sourceYear)
            ?: return true
        if (candidatePolicyIds.isEmpty()) {
            return recommendationPolicyIds.isNotEmpty()
        }
        return recommendationPolicyIds.isEmpty() ||
            recommendationPolicyIds.any { it !in candidatePolicyIds } ||
            candidates.any { it.updatedAfter(newestRecommendationCreatedAt) } ||
            member.updatedAfter(newestRecommendationCreatedAt) ||
            memberCrops.any { it.updatedAfter(newestRecommendationCreatedAt) } ||
            farms.any { it.updatedAfter(newestRecommendationCreatedAt) }
    }

    private fun regenerate(
        memberId: UUID,
        member: Member,
        source: PolicySource,
        sourceYear: String,
        candidates: List<PolicyProgram>,
        today: LocalDate,
        memberCrops: List<MemberCrop>,
        farms: List<Farm>
    ) {
        val profile = buildPolicyMemberProfile(member, memberCrops, farms)
        policyRecommendationRepository.deleteByMemberIdAndPolicyProgramSourceAndSourceYear(memberId, source, sourceYear)

        val recommendations = candidates.mapNotNull { program ->
            val score = scorer.score(
                profile = profile,
                policyTargetTags = textListJsonCodec.decode(program.targetTagsJson),
                policyCropTags = textListJsonCodec.decode(program.cropTagsJson),
                policyRegionTags = textListJsonCodec.decode(program.regionTagsJson),
                onlineApplyAvailable = program.onlineApplyAvailable,
                today = today
            )
            if (!score.eligible) {
                return@mapNotNull null
            }
            PolicyRecommendation(
                member = member,
                policyProgram = program,
                score = score.score,
                reason = score.reason
            )
        }

        if (recommendations.isNotEmpty()) {
            policyRecommendationRepository.saveAll(recommendations)
        }
    }

    private fun buildPolicyMemberProfile(
        member: Member,
        memberCrops: List<MemberCrop>,
        farms: List<Farm>
    ): PolicyMemberProfile {
        return PolicyMemberProfile(
            birthDate = member.birthDate,
            experienceLevel = member.experienceLevel,
            managementType = member.managementType,
            cropNames = memberCrops.map { it.crop.name }.toSet(),
            cropUsePartCategories = memberCrops.map { it.crop.usePartCategory.name }.toSet(),
            farmRegionTokens = regionMatcher.extractRegionTokens(
                farms.flatMap { listOf(it.roadAddress, it.jibunAddress) }
            )
        )
    }

    private fun validateSize(size: Int) {
        if (size !in 1..50) {
            throw BusinessException(ErrorCode.INVALID_INPUT)
        }
    }

    private fun decodeCursor(
        cursor: String?,
        source: PolicySource,
        sourceYear: String,
        benefitCategory: PolicyBenefitCategory?,
        sort: PolicyRecommendationSort
    ): PolicyRecommendationQueryRepository.Cursor? {
        if (cursor.isNullOrBlank()) {
            return null
        }
        val payload = try {
            cursorCodec.decode(cursor, PolicyRecommendationCursorPayload::class.java)
        } catch (exception: BusinessException) {
            if (exception.errorCode == ErrorCode.INVALID_CURSOR) {
                throw BusinessException(ErrorCode.INVALID_INPUT)
            }
            throw exception
        }
        if (payload.source != source ||
            payload.sourceYear != sourceYear ||
            payload.benefitCategory != benefitCategory?.name ||
            payload.sort != sort
        ) {
            throw BusinessException(ErrorCode.INVALID_INPUT)
        }
        return when (sort) {
            PolicyRecommendationSort.RECOMMENDED -> PolicyRecommendationQueryRepository.Cursor(
                score = payload.score ?: throw BusinessException(ErrorCode.INVALID_INPUT),
                applyStartsOn = null,
                applyEndsOn = payload.applyEndsOn,
                id = payload.id
            )

            PolicyRecommendationSort.LATEST -> PolicyRecommendationQueryRepository.Cursor(
                score = null,
                applyStartsOn = payload.applyStartsOn,
                applyEndsOn = null,
                id = payload.id
            )
        }
    }

    private fun decodeSearchCursor(cursor: String?): PolicyRecommendationSearchCursorPayload? {
        if (cursor.isNullOrBlank()) {
            return null
        }
        return try {
            cursorCodec.decode(cursor, PolicyRecommendationSearchCursorPayload::class.java)
        } catch (exception: BusinessException) {
            if (exception.errorCode == ErrorCode.INVALID_CURSOR) {
                throw BusinessException(ErrorCode.INVALID_INPUT)
            }
            throw exception
        }
    }

    private fun encodeCursor(
        row: PolicyRecommendation,
        source: PolicySource,
        sourceYear: String,
        benefitCategory: PolicyBenefitCategory?,
        sort: PolicyRecommendationSort
    ): String =
        cursorCodec.encode(
            PolicyRecommendationCursorPayload(
                source = source,
                sourceYear = sourceYear,
                benefitCategory = benefitCategory?.name,
                sort = sort,
                score = row.score.takeIf { sort == PolicyRecommendationSort.RECOMMENDED },
                applyStartsOn = row.policyProgram.applyStartsOn.takeIf { sort == PolicyRecommendationSort.LATEST },
                applyEndsOn = row.policyProgram.applyEndsOn.takeIf { sort == PolicyRecommendationSort.RECOMMENDED },
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
            reason = row.reason,
            createdAt = row.createdAt
        )
    }

    private fun parseContacts(rawPayload: String): List<PolicyRecommendationResult.Contact> {
        val root = parseRawPayload(rawPayload) ?: return emptyList()
        return root.arrayField("bizPicList", "contacts").mapNotNull { contact ->
            val agencyName = contact.nullableText("bizTkcgInstCdNm") ?: contact.nullableText("instNm")
                ?: contact.nullableText("agencyName")
            val departmentName = contact.nullableText("bizTkcgDeptInstCdNm") ?: contact.nullableText("deptNm")
                ?: contact.nullableText("departmentName")
                ?: contact.nullableText("department")
            val phoneNumber = contact.nullableText("bizPicTelno") ?: contact.nullableText("telno")
                ?: contact.nullableText("phoneNumber")
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
            val fileName = attachment.nullableText("originalName") ?: attachment.nullableText("atchFileNm")
                ?: attachment.nullableText("fileName")
                ?: attachment.nullableText("name")
            val extension = attachment.nullableText("extension") ?: attachment.nullableText("fileExtnNm")
            val sizeBytes = attachment.path("size").takeIf { it.isNumber }?.asLong()
                ?: attachment.path("fileSz").takeIf { it.isNumber }?.asLong()
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

    private fun BaseTimeEntity.updatedAfter(threshold: LocalDateTime): Boolean =
        runCatching { updatedAt.isAfter(threshold) }.getOrDefault(false)

    private companion object {
        const val COUNT_QUERY_SIZE = 1
    }
}
