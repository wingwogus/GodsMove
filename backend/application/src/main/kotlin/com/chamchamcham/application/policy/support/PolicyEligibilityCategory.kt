package com.chamchamcham.application.policy.support

enum class PolicyEligibilityCategory(val label: String) {
    YOUNG_FARMER("청년 농업인"),
    RETURNING_FARMER("귀농·귀촌인"),
    FEMALE_FARMER("여성 농업인"),
    SENIOR_RETIRED_FARMER("고령·은퇴 농업인"),
    SPECIAL_TARGET("특수 대상자"),
    DAMAGED_FARM("피해 농가"),
    CERTIFIED_FARMER("인증 보유 농업인"),
    LIVESTOCK_FARM("축산 농가"),
    EXPORT_BUSINESS("수출 농가·기업"),
    FOOD_AGRI_COMPANY("농식품 기업"),
    AGRI_CORPORATION("농업법인·단체"),
    REGISTERED_FARMER("경영체 등록 농업인"),
    FARMER("농업인"),
    UNKNOWN("상세 자격 확인")
}
