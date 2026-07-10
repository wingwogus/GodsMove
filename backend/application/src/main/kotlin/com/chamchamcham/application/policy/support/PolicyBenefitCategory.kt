package com.chamchamcham.application.policy.support

enum class PolicyBenefitCategory(val label: String) {
    GRANT("지원금"),
    FINANCE("융자·금융"),
    FACILITY_EQUIPMENT("시설·장비"),
    EDUCATION("교육"),
    WELFARE("복지"),
    CERTIFICATION("인증"),
    MARKET("판로"),
    STARTUP("창업"),
    ENVIRONMENT_INFRA("환경·인프라"),
    ETC("기타");

    companion object {
        fun fromKey(key: String): PolicyBenefitCategory? = entries.firstOrNull { it.name == key }
    }
}
