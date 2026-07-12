package com.chamchamcham.application.policy.support

import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class PolicyCardTextGenerator {
    fun eligibilitySummary(text: String?): String = eligibilityCategory(text).label

    fun eligibilityCategory(text: String?): PolicyEligibilityCategory {
        val source = text.orEmpty()
        return when {
            source.containsAny("청년", "청년농", "청년후계", "만 40세") -> PolicyEligibilityCategory.YOUNG_FARMER
            source.containsAny("귀농", "귀촌") -> PolicyEligibilityCategory.RETURNING_FARMER
            source.containsAny("여성농업인", "결혼이민여성") -> PolicyEligibilityCategory.FEMALE_FARMER
            source.containsAny("60세", "65세", "고령", "은퇴", "농지이양") -> PolicyEligibilityCategory.SENIOR_RETIRED_FARMER
            source.containsAny("임산부", "학생", "취약계층", "기초생활", "차상위") -> PolicyEligibilityCategory.SPECIAL_TARGET
            source.containsAny("재해", "피해", "재난", "전염병", "질병") -> PolicyEligibilityCategory.DAMAGED_FARM
            source.containsAny("친환경인증", "친환경 인증", "GAP", "HACCP", "인증농가", "인증을 받은") -> PolicyEligibilityCategory.CERTIFIED_FARMER
            source.containsAny("축산", "가축", "한우", "젖소", "돼지", "양돈", "낙농", "송아지", "암소") -> PolicyEligibilityCategory.LIVESTOCK_FARM
            source.containsAny("수출농가", "수출업체", "수출기업", "해외", "박람회") -> PolicyEligibilityCategory.EXPORT_BUSINESS
            source.containsAny("농식품 기업", "농식품기업", "식품기업", "외식기업", "벤처", "창업기업") -> PolicyEligibilityCategory.FOOD_AGRI_COMPANY
            source.containsAny("농업법인", "영농조합", "농업회사법인", "생산자단체", "농협", "협동조합") -> PolicyEligibilityCategory.AGRI_CORPORATION
            source.containsAny("농업경영체", "농업경영정보", "경영체 등록") -> PolicyEligibilityCategory.REGISTERED_FARMER
            source.containsAny("농업인", "임업인", "농가") -> PolicyEligibilityCategory.FARMER
            else -> PolicyEligibilityCategory.UNKNOWN
        }
    }

    fun benefitSummary(text: String?): String = benefitCategory(text).label

    fun benefitCategory(text: String?): PolicyBenefitCategory {
        val source = text.orEmpty()
        return when {
            source.containsAny("직불금", "지원금", "보조금", "장려금", "수당", "바우처") -> PolicyBenefitCategory.GRANT
            source.containsAny("융자", "정책자금", "대출", "금리", "이자") -> PolicyBenefitCategory.FINANCE
            source.containsAny("시설", "장비", "농기계", "설치", "개보수") -> PolicyBenefitCategory.FACILITY_EQUIPMENT
            source.containsAny("교육", "컨설팅", "상담", "연수") -> PolicyBenefitCategory.EDUCATION
            source.containsAny("보험", "보험료", "연금", "건강", "복지") -> PolicyBenefitCategory.WELFARE
            source.containsAny("인증", "검정", "품질", "무병묘", "저탄소") -> PolicyBenefitCategory.CERTIFICATION
            source.containsAny("박람회", "수출", "판로", "홍보", "브랜드") -> PolicyBenefitCategory.MARKET
            source.containsAny("창업", "사업화", "벤처", "R&D") -> PolicyBenefitCategory.STARTUP
            source.containsAny("수질", "용수", "저수지", "가뭄", "환경", "인프라") -> PolicyBenefitCategory.ENVIRONMENT_INFRA
            else -> PolicyBenefitCategory.ETC
        }
    }

    fun periodLabel(start: LocalDate?, end: LocalDate?, notice: String?): String {
        val sourceNotice = notice?.trim()?.takeIf(String::isNotEmpty)
        val label = when {
            start != null && end != null -> "${start.formatFull()}~${end.formatMonthDay()}"
            sourceNotice != null -> sourceNotice
            end != null -> "${end.formatFull()}까지"
            else -> "접수기관문의"
        }
        return compact(label, "접수기관문의")
    }

    private fun compact(text: String, fallback: String): String {
        val normalized = text.replace(Regex("\\s+"), " ").trim()
        if (normalized.isBlank()) {
            return fallback
        }
        return normalized.take(MAX_CARD_CHARS)
    }

    private fun String.containsAny(vararg keywords: String): Boolean =
        keywords.any { keyword -> contains(keyword, ignoreCase = true) }

    private fun LocalDate.formatFull(): String = "%04d.%02d.%02d".format(year, monthValue, dayOfMonth)

    private fun LocalDate.formatMonthDay(): String = "%02d.%02d".format(monthValue, dayOfMonth)

    private companion object {
        const val MAX_CARD_CHARS = 19
    }
}
