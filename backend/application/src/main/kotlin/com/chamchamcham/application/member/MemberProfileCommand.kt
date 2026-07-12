package com.chamchamcham.application.member

import com.chamchamcham.domain.member.ManagementType
import java.time.LocalDate
import java.util.UUID

object MemberProfileCommand {
    data class UpdateMyProfile(
        val memberId: UUID,
        val name: String,
        val phone: String,
        val birthDate: LocalDate,
        val nickname: String,
        val experienceLevel: Int,
        val managementType: ManagementType,
        val profileMediaId: UUID?
    )
}
