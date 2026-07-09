package com.chamchamcham.application.policy.support

enum class PolicyBenefitCategory(val label: String) {
    DIRECT_PAYMENT("직불/수당"),
    FINANCE("융자/금융"),
    FACILITY_EQUIPMENT("시설/장비"),
    EDUCATION_CONSULTING("교육/컨설팅"),
    INSURANCE_WELFARE("보험/복지"),
    CERTIFICATION_QUALITY("인증/품질"),
    MARKETING("판로/마케팅"),
    STARTUP_BUSINESS("창업/사업화"),
    ENVIRONMENT_INFRA("환경/인프라"),
    ETC("기타");

    companion object {
        fun fromKey(key: String): PolicyBenefitCategory? =
            entries.firstOrNull { it.name == key }
    }
}
