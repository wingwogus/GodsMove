package com.chamchamcham.application.member

import java.util.UUID

data class MemberWithdrawalCommitted(
    val memberId: UUID,
    val cloudinaryPublicIds: List<String>
)
