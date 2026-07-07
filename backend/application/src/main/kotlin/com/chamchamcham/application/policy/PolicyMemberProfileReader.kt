package com.chamchamcham.application.policy

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.crop.MemberCropRepository
import com.chamchamcham.domain.farm.FarmRepository
import com.chamchamcham.domain.member.MemberRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class PolicyMemberProfileReader(
    private val memberRepository: MemberRepository,
    private val memberCropRepository: MemberCropRepository,
    private val farmRepository: FarmRepository,
    private val regionMatcher: PolicyRegionMatcher
) {
    fun read(memberId: UUID): PolicyMemberProfile {
        val member = memberRepository.findById(memberId).orElseThrow {
            BusinessException(ErrorCode.MEMBER_NOT_FOUND)
        }
        val memberCrops = memberCropRepository.findByMember_Id(memberId)
        val farms = farmRepository.findByOwner_Id(memberId)

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
}
