package com.godsmove.application.coaching.rag

import com.godsmove.application.exception.ErrorCode
import com.godsmove.application.exception.business.BusinessException
import com.godsmove.domain.crop.CropRepository
import com.godsmove.domain.farm.FarmRepository
import com.godsmove.domain.farming.FarmingRecordRepository
import com.godsmove.domain.member.MemberRepository
import org.springframework.stereotype.Component

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
    fun build(command: CoachingRagCommand): CoachingContext {
        val member = memberRepository.findById(command.memberId).orElseThrow {
            BusinessException(ErrorCode.MEMBER_NOT_FOUND)
        }
        val farm = command.farmId?.let { farmRepository.findById(it).orElse(null) }
        val crop = command.cropId?.let { cropRepository.findById(it).orElse(null) }
        val record = command.recordId?.let { farmingRecordRepository.findById(it).orElse(null) }

        return CoachingContext(
            text = buildString {
                appendLine("사용자 재배 context:")
                appendLine("- 지역: ${member.region ?: "미입력"}")
                appendLine("- 영농 경력: ${member.experienceLevel ?: "미입력"}")
                appendLine("- 경영체 등록: ${member.managementType}")
                farm?.let { appendLine("- 농장: ${it.name} (${it.region} ${it.city})") }
                crop?.let { appendLine("- 작물: ${it.name} / ${it.category} / 기본 단위 ${it.defaultUnit}") }
                record?.let { appendLine("- 기준 영농일지: ${it.workedAt} ${it.workType.name}") }
                command.periodStart?.let { appendLine("- 기간 시작: $it") }
                command.periodEnd?.let { appendLine("- 기간 종료: $it") }
            }.trim()
        )
    }
}
