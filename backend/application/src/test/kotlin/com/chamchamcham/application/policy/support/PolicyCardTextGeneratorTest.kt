package com.chamchamcham.application.policy.support

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PolicyCardTextGeneratorTest {
    private val generator = PolicyCardTextGenerator()

    @Test
    fun `benefit category exposes stable enum key and card label`() {
        val category = generator.benefitCategory("정책자금 융자 및 금리 지원")

        assertThat(category).isEqualTo(PolicyBenefitCategory.FINANCE)
        assertThat(category.label).isEqualTo("융자/금융")
        assertThat(generator.benefitSummary("정책자금 융자 및 금리 지원")).isEqualTo("융자/금융")
    }

    @Test
    fun `eligibility category exposes stable enum key and card label`() {
        val category = generator.eligibilityCategory("청년후계농업경영인")

        assertThat(category).isEqualTo(PolicyEligibilityCategory.YOUNG_FARMER)
        assertThat(category.label).isEqualTo("청년 농업인")
        assertThat(generator.eligibilitySummary("청년후계농업경영인")).isEqualTo("청년 농업인")
    }

    @Test
    fun `benefit category key parser rejects unknown values`() {
        assertThat(PolicyBenefitCategory.fromKey("FINANCE")).isEqualTo(PolicyBenefitCategory.FINANCE)
        assertThat(PolicyBenefitCategory.fromKey("bad")).isNull()
    }

    @Test
    fun `eligibility summary maps source text to target category labels`() {
        val cases = mapOf(
            "만 40세 미만 청년후계농" to "청년 농업인",
            "귀농 예정자 및 귀촌 희망자" to "귀농·귀촌인",
            "여성농업인 행복바우처 지원 대상" to "여성 농업인",
            "65세 이상 고령농 농지이양 대상" to "고령·은퇴 농업인",
            "차상위 취약계층 농가" to "특수 대상자",
            "재해 피해 농가 복구 지원" to "피해 농가",
            "친환경인증을 받은 농업인" to "인증 보유 농업인",
            "축산법에 따라 허가받은 한우 농가" to "축산 농가",
            "수출농가 및 수출업체" to "수출 농가·기업",
            "농식품 벤처기업" to "농식품 기업",
            "농업법인 및 영농조합법인" to "농업법인·단체",
            "농업경영정보를 등록한 농업인" to "경영체 등록 농업인",
            "농업인 및 임업인" to "농업인"
        )

        cases.forEach { (source, expected) ->
            val summary = generator.eligibilitySummary(source)

            assertThat(summary).isEqualTo(expected)
            assertThat(summary.length).isLessThanOrEqualTo(19)
        }
    }

    @Test
    fun `eligibility summary uses fallback for blank or uncategorized source text`() {
        assertThat(generator.eligibilitySummary("   ")).isEqualTo("상세 자격 확인")
        assertThat(generator.eligibilitySummary("세부 공고문 참조")).isEqualTo("상세 자격 확인")
    }

    @Test
    fun `benefit summary maps source text to support category labels`() {
        val cases = mapOf(
            "친환경농업 직불금 지급" to "직불/수당",
            "정책자금 융자 및 금리 지원" to "융자/금융",
            "농기계 장비 설치 및 개보수 지원" to "시설/장비",
            "농업인 교육과 컨설팅 지원" to "교육/컨설팅",
            "농작물재해보험 보험료 지원" to "보험/복지",
            "GAP 인증 및 품질 검정 지원" to "인증/품질",
            "수출 박람회 참가와 판로 홍보 지원" to "판로/마케팅",
            "창업 사업화 R&D 지원" to "창업/사업화",
            "농업용수 수질 개선과 가뭄 대응" to "환경/인프라",
            "세부 지원 내용은 공고문 참조" to "기타"
        )

        cases.forEach { (source, expected) ->
            val summary = generator.benefitSummary(source)

            assertThat(summary).isEqualTo(expected)
            assertThat(summary.length).isLessThanOrEqualTo(19)
        }
    }

    @Test
    fun `benefit summary uses fallback for blank source text`() {
        val summary = generator.benefitSummary("   ")

        assertThat(summary).isEqualTo("기타")
        assertThat(summary.length).isLessThanOrEqualTo(19)
    }

    @Test
    fun `period label is nineteen characters or fewer`() {
        val label = generator.periodLabel(LocalDate.of(2026, 3, 25), LocalDate.of(2026, 6, 30), null)

        assertThat(label).isEqualTo("2026.03.25~06.30")
        assertThat(label.length).isLessThanOrEqualTo(19)
    }

    @Test
    fun `period label preserves agency inquiry notice`() {
        val label = generator.periodLabel(null, null, "접수기관문의")

        assertThat(label).isEqualTo("접수기관문의")
    }
}
