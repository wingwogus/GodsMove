package com.chamchamcham.api.pesticide

import com.chamchamcham.application.pesticide.PesticideCatalogService
import com.chamchamcham.domain.pesticide.PestRepository
import com.chamchamcham.domain.pesticide.PesticideApplicationRepository
import com.chamchamcham.domain.pesticide.PesticideRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.ActiveProfiles

/**
 * 적재된 실 DB에 대해 "기록 흐름 조회 API"가 쓰는 로직([PesticideCatalogService])이 정상 동작하는지
 * 검증하는 수동 하네스. `PesticideSyncManualLoaderTest`로 적재를 끝낸 뒤 실행한다.
 *
 * 검색/병해충 조회 엔드포인트(`GET /api/v1/pesticides`, `.../{id}/pests`)는 인증이 필요하지만,
 * 컨트롤러는 이 서비스에 위임할 뿐이므로 서비스를 실 데이터에 대해 직접 호출하면 토큰 없이 동일 로직을
 * 검증할 수 있다(HTTP/인증 계약은 별도 컨트롤러/시큐리티 테스트가 이미 커버).
 *
 * 일반 `./gradlew test`에서는 스킵되고 `PSIS_PESTICIDE_VERIFY=true`일 때만 활성화된다(로컬 Postgres 필요).
 * 실행:
 *  PSIS_PESTICIDE_VERIFY=true ./gradlew :api:test --tests "com.chamchamcham.api.pesticide.PesticideCatalogVerificationTest"
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named = "PSIS_PESTICIDE_VERIFY", matches = "true")
class PesticideCatalogVerificationTest @Autowired constructor(
    private val pesticideCatalogService: PesticideCatalogService,
    private val pesticideRepository: PesticideRepository,
    private val pestRepository: PestRepository,
    private val pesticideApplicationRepository: PesticideApplicationRepository,
) {
    @Test
    fun `적재된 데이터로 농약 검색과 병해충 조회가 동작한다`() {
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
