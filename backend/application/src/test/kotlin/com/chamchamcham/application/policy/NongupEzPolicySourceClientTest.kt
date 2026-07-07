package com.chamchamcham.application.policy

import com.chamchamcham.application.policy.source.NongupEzHttpTransport
import com.chamchamcham.application.policy.source.NongupEzPolicySourceClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets

class NongupEzPolicySourceClientTest {
    @Test
    fun `detectLatestYear ignores all option and selects max numeric year`() {
        val client = NongupEzPolicySourceClient(
            fakeTransport(CONDITIONS_PATH to fixture("conditions.json"))
        )

        val latestYear = client.detectLatestYear()

        assertThat(latestYear).isEqualTo("2026")
    }

    @Test
    fun `fetchPrograms parses list page into source list item`() {
        val client = NongupEzPolicySourceClient(
            fakeTransport(LIST_PATH to fixture("list-page.json"))
        )

        val programs = client.fetchPrograms("2026")

        assertThat(programs).hasSize(1)
        assertThat(programs.first().externalId).isEqualTo("AB000009")
        assertThat(programs.first().title).isEqualTo("친환경농업직불")
        assertThat(programs.first().applyEndsOn?.toString()).isEqualTo("2026-06-30")
    }

    @Test
    fun `fetchDetail parses long text contacts and attachments`() {
        val client = NongupEzPolicySourceClient(
            fakeTransport(DETAIL_PATH to fixture("detail-success.json"))
        )

        val detail = client.fetchDetail("AB000009", "2026")

        assertThat(detail.eligibility).contains("농업경영정보")
        assertThat(detail.contacts.first().departmentName).isEqualTo("친환경농업과")
        assertThat(detail.attachments.first().fileName).contains("시행지침")
    }

    @Test
    fun `fetchDetail tolerates null optional fields`() {
        val client = NongupEzPolicySourceClient(
            fakeTransport(DETAIL_PATH to fixture("detail-missing-optional-fields.json"))
        )

        val detail = client.fetchDetail("AB000010", "2026")

        assertThat(detail.agencyName).isEqualTo("농업e지")
        assertThat(detail.eligibility).isNull()
        assertThat(detail.benefit).isNull()
    }

    private fun fakeTransport(vararg responses: Pair<String, String>): NongupEzHttpTransport {
        val responseMap = responses.toMap()
        return NongupEzHttpTransport { path, _ ->
            responseMap[path]
                ?: error("No fixture response for $path")
        }
    }

    private fun fixture(name: String): String {
        return javaClass.classLoader
            .getResourceAsStream("policy/nongupez/$name")!!
            .readAllBytes()
            .toString(StandardCharsets.UTF_8)
    }

    private companion object {
        private const val CONDITIONS_PATH = "/nsm/bizAply/wholeBiz/retrieveListBizSrchCnd"
        private const val LIST_PATH = "/nsm/bizAply/wholeBiz/retrieveListBizSrch"
        private const val DETAIL_PATH = "/nsm/bizAply/cstBiz/findBizSrchDtl"
    }
}
