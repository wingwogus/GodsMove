package com.chamchamcham.domain.policy

import com.chamchamcham.domain.member.ManagementType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PolicyProgramTest {
    @Test
    fun `applying identical policy fields reports no change`() {
        val program = PolicyProgram(
            title = "친환경농업 직불 지원",
            body = "친환경농업 지원",
            region = "전국",
            targetManagementType = null
        )

        val listChanged = program.applyListFields(
            source = PolicySource.NONGUP_EZ,
            externalId = "AB000009",
            sourceYear = "2026",
            title = "친환경농업 직불 지원",
            summary = "친환경농업 지원",
            region = "전국",
            sourceUrl = "https://example.test/policy",
            agencyName = "농림축산식품부"
        )
        val detailChanged = program.applyDetailFields(
            body = "본문",
            purpose = "목적",
            eligibilityOriginal = "농업인",
            eligibilitySummary = "농업인",
            benefitOriginal = "직불금 지원",
            benefitSummary = "직불/수당",
            applyStartsOn = LocalDate.of(2026, 1, 1),
            applyEndsOn = LocalDate.of(2026, 12, 31),
            applicationPeriodLabel = "2026.01.01~12.31",
            applicationPeriodNotice = null,
            applicationMethod = "방문 신청",
            requiredDocuments = "신청서",
            selectionCriteria = "자격 확인",
            departmentName = "친환경농업과",
            onlineApplyAvailable = false,
            applicationUrl = null,
            targetTagsJson = """["REGISTERED_FARMER"]""",
            cropTagsJson = "[]",
            regionTagsJson = """["전국"]""",
            rawPayload = """{"afbzCd":"AB000009"}""",
            recommendable = true
        )

        assertThat(listChanged).isTrue()
        assertThat(detailChanged).isTrue()

        val secondListChanged = program.applyListFields(
            source = PolicySource.NONGUP_EZ,
            externalId = "AB000009",
            sourceYear = "2026",
            title = "친환경농업 직불 지원",
            summary = "친환경농업 지원",
            region = "전국",
            sourceUrl = "https://example.test/policy",
            agencyName = "농림축산식품부"
        )
        val secondDetailChanged = program.applyDetailFields(
            body = "본문",
            purpose = "목적",
            eligibilityOriginal = "농업인",
            eligibilitySummary = "농업인",
            benefitOriginal = "직불금 지원",
            benefitSummary = "직불/수당",
            applyStartsOn = LocalDate.of(2026, 1, 1),
            applyEndsOn = LocalDate.of(2026, 12, 31),
            applicationPeriodLabel = "2026.01.01~12.31",
            applicationPeriodNotice = null,
            applicationMethod = "방문 신청",
            requiredDocuments = "신청서",
            selectionCriteria = "자격 확인",
            departmentName = "친환경농업과",
            onlineApplyAvailable = false,
            applicationUrl = null,
            targetTagsJson = """["REGISTERED_FARMER"]""",
            cropTagsJson = "[]",
            regionTagsJson = """["전국"]""",
            rawPayload = """{"afbzCd":"AB000009"}""",
            recommendable = true
        )

        assertThat(secondListChanged).isFalse()
        assertThat(secondDetailChanged).isFalse()
    }

    @Test
    fun `detail success makes open detailed policy recommendable`() {
        val program = PolicyProgram(
            title = "친환경농업직불",
            body = "목록 내용",
            region = "전국",
            targetManagementType = ManagementType.AGRICULTURAL_INDIVIDUAL
        )

        program.applyListFields(
            source = PolicySource.NONGUP_EZ,
            externalId = "AB000009",
            sourceYear = "2026",
            title = "친환경농업직불",
            summary = "친환경농업 실천 농업인 지원",
            region = "전국",
            sourceUrl = "https://www.nongupez.go.kr/nsm/bizAply/wholeBiz/wholeBizDtls?afbzCd=AB000009&bizYr=2026",
            agencyName = "농림축산식품부"
        )
        program.applyDetailFields(
            body = "상세 내용",
            purpose = "친환경농업 확산",
            eligibilityOriginal = "친환경인증을 받은 농업경영정보 등록 농업인",
            eligibilitySummary = "친환경 인증 농업인",
            benefitOriginal = "인증단계별 직불금 지급",
            benefitSummary = "인증단계별 직불금",
            applyStartsOn = LocalDate.of(2026, 3, 25),
            applyEndsOn = LocalDate.of(2026, 6, 30),
            applicationPeriodLabel = "2026.03.25~06.30",
            applicationPeriodNotice = null,
            applicationMethod = "방문 신청",
            requiredDocuments = "신청서",
            selectionCriteria = "자격 검토",
            departmentName = "친환경농업과",
            onlineApplyAvailable = false,
            applicationUrl = null,
            targetTagsJson = "[\"REGISTERED_FARMER\"]",
            cropTagsJson = "[\"ECO_FRIENDLY\"]",
            regionTagsJson = "[\"전국\"]",
            rawPayload = """
                {
                  "afbzCd":"AB000009",
                  "contacts":[{"department":"친환경농업과","phone":"044-000-0000"}],
                  "attachments":[{"name":"신청서.hwp"}],
                  "sourceTags":["친환경","직불"]
                }
            """.trimIndent(),
            recommendable = true
        )

        assertThat(program.detailSynced).isTrue()
        assertThat(program.recommendable).isTrue()
        assertThat(program.isOpenOn(LocalDate.of(2026, 6, 1))).isTrue()
        assertThat(program.isOpenOn(LocalDate.of(2026, 7, 1))).isFalse()
        assertThat(program.targetManagementType).isEqualTo(ManagementType.AGRICULTURAL_INDIVIDUAL)
        assertThat(program.rawPayload).contains("contacts", "attachments", "sourceTags")
    }

    @Test
    fun `detail failure keeps list fields but blocks recommendation`() {
        val program = PolicyProgram(
            title = "청년농 지원",
            body = "목록 내용",
            region = "전국",
            targetManagementType = null
        )

        program.markDetailSyncFailed(rawPayload = "{\"listOnly\":true}")

        assertThat(program.detailSynced).isFalse()
        assertThat(program.recommendable).isFalse()
        assertThat(program.rawPayload).isEqualTo("{\"listOnly\":true}")
    }
}
