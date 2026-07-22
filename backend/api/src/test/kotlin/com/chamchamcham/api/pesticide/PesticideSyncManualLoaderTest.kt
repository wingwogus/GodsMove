package com.chamchamcham.api.pesticide

import com.chamchamcham.application.pesticide.PesticideCatalogService
import com.chamchamcham.application.pesticide.sync.PesticideSyncService
import com.chamchamcham.domain.pesticide.PestRepository
import com.chamchamcham.domain.pesticide.PesticideApplicationRepository
import com.chamchamcham.domain.pesticide.PesticideRepository
import com.chamchamcham.domain.pesticide.PesticideSyncJobStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.ActiveProfiles

/**
 * PSIS 농약등록정보(≈143,912건)를 실 DB에 1회성으로 적재하는 수동 로더.
 *
 * 관리자 토큰/HTTP 없이 실 Postgres에 바로 적재하기 위한 것으로, 일반 테스트 실행(`./gradlew test`)에서는
 * 절대 돌지 않도록 전용 환경변수 `PSIS_PESTICIDE_SYNC_RUN=true`가 있을 때만 활성화된다.
 *
 * 실행 전제(로컬 앱 구동과 동일):
 *  - 로컬 Postgres(5444) + Redis 기동
 *  - env: `PSIS_PESTICIDE_API_KEY`(서비스인증키), `PSIS_PESTICIDE_BASE_URL=http://psis.rda.go.kr/openApi/service.do`
 *
 * 실행 예:
 *  PSIS_PESTICIDE_SYNC_RUN=true \
 *  PSIS_PESTICIDE_API_KEY=<서비스인증키> \
 *  PSIS_PESTICIDE_BASE_URL=http://psis.rda.go.kr/openApi/service.do \
 *  ./gradlew :api:test --tests "com.chamchamcham.api.pesticide.PesticideSyncManualLoaderTest"
 *
 * runExistingJob을 (비동기 러너가 아니라) 직접 호출해 동기로 끝까지 순회하므로, 전량 적재가 끝날 때까지
 * 블로킹된다(수 분~수십 분). 재실행은 dedup되어 안전하다. totalCount에 미달하면 잡이 FAILED가 되어
 * 아래 단언에서 실패로 드러난다.
 *
 * ⚠️ 적재와 조회 검증은 반드시 **같은 실행(=같은 Spring 컨텍스트)** 안에서 끝낸다. local 프로필은
 * `ddl-auto: create`라 컨텍스트가 부팅될 때마다 스키마를 DROP/재생성하므로, 적재가 끝난 뒤 별도
 * 테스트/앱을 local 프로필로 다시 띄우면 방금 넣은 데이터가 통째로 지워진다. 그래서 적재 직후
 * 이 테스트 안에서 [PesticideCatalogService]로 검색·병해충 조회까지 함께 검증한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named = "PSIS_PESTICIDE_SYNC_RUN", matches = "true")
class PesticideSyncManualLoaderTest @Autowired constructor(
    private val pesticideSyncService: PesticideSyncService,
    private val pesticideCatalogService: PesticideCatalogService,
    private val pesticideRepository: PesticideRepository,
    private val pestRepository: PestRepository,
    private val pesticideApplicationRepository: PesticideApplicationRepository,
) {
    @Test
    fun `PSIS 농약등록정보를 실 DB에 전량 적재하고 조회를 검증한다`() {
        val job = pesticideSyncService.createSyncJob(adminMemberId = null)
        pesticideSyncService.runExistingJob(job.jobId)

        val detail = pesticideSyncService.getJob(job.jobId)
        println(
            "[PSIS sync] status=${detail.status} total=${detail.totalCount} " +
                "fetched=${detail.fetchedRowCount} createdApplications=${detail.createdApplicationCount} " +
                "error=${detail.errorMessage}"
        )

        assertThat(detail.status).isEqualTo(PesticideSyncJobStatus.SUCCEEDED)
        assertThat(detail.fetchedRowCount).isGreaterThan(0)

        // 같은 컨텍스트에서 곧바로 조회 검증(별도 부팅 시 ddl-auto:create가 데이터를 지우므로 여기서 검증).
        val pesticideCount = pesticideRepository.count()
        val pestCount = pestRepository.count()
        val applicationCount = pesticideApplicationRepository.count()
        println("[verify] 적재 건수 pesticides=$pesticideCount pests=$pestCount applications=$applicationCount")

        assertThat(pesticideCount).isGreaterThan(0)
        assertThat(pestCount).isGreaterThan(0)
        assertThat(applicationCount).isGreaterThan(0)

        // 검색 API 로직(키워드 = 품목명/상표명 부분일치 + 커서 페이지네이션)
        val page = pesticideCatalogService.search(keyword = "가스가마이신", cursor = null, size = 5)
        println("[verify] search('가스가마이신') -> ${page.items.size}건, nextCursor존재=${page.nextCursor != null}")
        println("[verify] 첫 결과=${page.items.firstOrNull()}")
        assertThat(page.items).isNotEmpty

        // 약제별 병해충 조회 API 로직
        val anyPesticide = pesticideRepository.findAll(PageRequest.of(0, 1)).content.first()
        val pests = pesticideCatalogService.listPestsByPesticide(requireNotNull(anyPesticide.id))
        println("[verify] '${anyPesticide.itemName}/${anyPesticide.brandName}' -> 병해충 ${pests.size}종, 예시=${pests.firstOrNull()}")
        assertThat(pests).isNotEmpty
    }
}
