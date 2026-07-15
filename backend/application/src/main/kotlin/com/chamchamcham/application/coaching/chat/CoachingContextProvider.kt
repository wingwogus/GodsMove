package com.chamchamcham.application.coaching.chat

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.crop.CropRepository
import com.chamchamcham.domain.farm.FarmRepository
import com.chamchamcham.domain.farming.FarmingRecordRepository
import com.chamchamcham.domain.member.MemberRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

data class CoachingContext(
    val text: String
)

@Component
class CoachingContextProvider(
    private val memberRepository: MemberRepository,
    private val farmRepository: FarmRepository,
    private val cropRepository: CropRepository,
    private val farmingRecordRepository: FarmingRecordRepository
) {
    @Transactional(readOnly = true)
    fun build(command: CoachingRagCommand): CoachingContext {
        val member = memberRepository.findById(command.memberId).orElseThrow {
            BusinessException(ErrorCode.MEMBER_NOT_FOUND)
        }
        val farm = command.farmId?.let {
            farmRepository.findByIdAndOwnerId(it, command.memberId)
                ?: throw BusinessException(ErrorCode.RAG_INVALID_REQUEST)
        }
        val crop = command.cropId?.let { cropRepository.findById(it).orElse(null) }
        val record = command.recordId?.let {
            farmingRecordRepository.findByIdAndMember_Id(it, command.memberId)
                ?: throw BusinessException(ErrorCode.RAG_INVALID_REQUEST)
        }

        return CoachingContext(
            text = buildString {
                appendLine("사용자 재배 context:")
                appendLine("- 영농 경력 점수: ${member.experienceLevel?.toString() ?: "미입력"}")
                appendLine("- 경영 형태: ${member.managementType?.name ?: "미입력"}")
                farm?.let { appendLine("- 농장: ${it.name} (${it.roadAddress})") }
                crop?.let { appendLine("- 작물: ${it.name} / ${it.usePartCategory.label}") }
                record?.let { appendLine("- 기준 영농일지: ${it.workedAt} ${it.workType.label}") }
                command.periodStart?.let { appendLine("- 기간 시작: $it") }
                command.periodEnd?.let { appendLine("- 기간 종료: $it") }
            }.trim()
        )
    }
}
