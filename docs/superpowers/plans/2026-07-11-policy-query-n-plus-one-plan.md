# 정책 추천 조회 N+1 방지 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 정책 추천 프로필의 `Farm → boundaryCoordinates`, `MemberCrop → Crop` N+1을 제거하고 기존 정책 카드 fetch join을 query-count 회귀 테스트로 고정한다.

**Architecture:** 다른 기능이 사용하는 일반 저장소 메서드는 유지하고 정책 전용 Farm-boundary, MemberCrop-Crop fetch join을 추가한다. 서비스는 회원→농장→회원작물 순으로 조회하며 H2의 Hibernate statistics로 농장·작물 수와 무관하게 SELECT 수가 일정함을 검증한다. PostgreSQL 인덱스는 실제 실행 계획 증거가 없으므로 추가하지 않는다.

**Tech Stack:** Kotlin 2.x, Spring Boot 3.5.4, Spring Data JPA, Hibernate ORM, H2 PostgreSQL mode, JUnit 5, AssertJ, Mockito, Gradle 8.14.3, Java 21

## Global Constraints

- API 응답, 정책 정렬, 카테고리 필터, 커서, stale 판정, 추천 재생성 동작을 변경하지 않는다.
- 새 라이브러리, Testcontainers, Flyway, 전역 Hibernate fetch 설정을 추가하지 않는다.
- `MemberCropRepository.findByMemberId()`의 기존 계약을 변경하지 않는다.
- `FarmRepository.findByOwnerId()`의 기존 계약을 변경하지 않는다.
- 테스트 클래스 범위에서만 `hibernate.generate_statistics=true`를 사용한다.
- 인덱스 DDL, `@Index`, 운영 설정을 추가하지 않는다.
- 정책 프로그램과 회원별 추천이 각각 1만 건에 근접하거나 실제 지연이 관측되기 전에는 PostgreSQL 인덱스를 추가하지 않는다.
- 모든 코드 변경은 테스트를 먼저 작성하고 RED를 확인한 뒤 구현한다.
- 실행 시 현재 브랜치와 분리된 worktree가 필요하면 `superpowers:using-git-worktrees`로 만든다.

---

### Task 1: 정책 전용 MemberCrop-Crop fetch join

> 최종 상태 참고: 이 태스크의 단일 농장 query-count fixture는 Task 4의 다중 농장
> 운영 순서 fixture로 확장되었다. 최종 합격 기준은 Task 4와 Final Checklist를 따른다.

**Files:**
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/crop/MemberCropRepository.kt`
- Create: `backend/domain/src/test/kotlin/com/chamchamcham/domain/crop/MemberCropRepositoryTest.kt`

**Interfaces:**
- Consumes: 기존 `MemberCrop`, `Crop`, `Farm`, `Member` JPA 엔티티와 `MemberCropRepository.findByMemberId(UUID)`.
- Produces: `fun findAllWithCropByMemberId(memberId: UUID): List<MemberCrop>`.
- Guarantee: 반환된 모든 `MemberCrop.crop`의 프로필 필드를 접근해도 작물 수에 비례한
  추가 SELECT가 발생하지 않는다. 다중 농장의 final `farm` 연관은 Task 4의 정책 전용
  농장 선조회로 해소한다.

- [ ] **Step 1: 새 정책 전용 메서드를 요구하는 실패 테스트 작성**

다음 테스트 파일을 새로 만든다. 테스트 코드를 먼저 추가하므로 아직 존재하지 않는
`findAllWithCropByMemberId()` 참조에서 컴파일이 실패해야 한다.

```kotlin
package com.chamchamcham.domain.crop

import com.chamchamcham.domain.farm.Farm
import com.chamchamcham.domain.member.Member
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.SessionFactory
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.test.context.ActiveProfiles
import java.util.UUID

@DataJpaTest(
    properties = ["spring.jpa.properties.hibernate.generate_statistics=true"]
)
@ActiveProfiles("test")
class MemberCropRepositoryTest @Autowired constructor(
    private val entityManager: TestEntityManager,
    private val memberCropRepository: MemberCropRepository
) {
    @Test
    fun `policy crop query fetches every crop in one select`() {
        val member = entityManager.persistFlushFind(
            Member(email = "policy-query@example.com", passwordHash = null)
        )
        val farm = entityManager.persistFlushFind(
            Farm(
                owner = member,
                name = "정책 조회 농장",
                roadAddress = "충청북도 제천시 정책로 1"
            )
        )
        listOf(
            Crop(externalNo = 9001, name = "참당귀", usePartCategory = CropUsePartCategory.ROOT_BARK),
            Crop(externalNo = 9002, name = "황기", usePartCategory = CropUsePartCategory.ROOT_BARK),
            Crop(externalNo = 9003, name = "작약", usePartCategory = CropUsePartCategory.ROOT_BARK)
        ).forEach { crop ->
            val savedCrop = entityManager.persistFlushFind(crop)
            entityManager.persist(
                MemberCrop(member = member, farm = farm, crop = savedCrop)
            )
        }
        entityManager.flush()
        entityManager.clear()

        val statistics = entityManager.entityManager.entityManagerFactory
            .unwrap(SessionFactory::class.java)
            .statistics
        statistics.clear()

        val memberCrops = memberCropRepository.findAllWithCropByMemberId(
            requireNotNull(member.id)
        )
        val afterRepositoryQuery = statistics.prepareStatementCount
        val cropValues = memberCrops.map { it.crop.name to it.crop.usePartCategory }
        val afterCropAccess = statistics.prepareStatementCount

        assertThat(cropValues).containsExactlyInAnyOrder(
            "참당귀" to CropUsePartCategory.ROOT_BARK,
            "황기" to CropUsePartCategory.ROOT_BARK,
            "작약" to CropUsePartCategory.ROOT_BARK
        )
        assertThat(afterRepositoryQuery).isLessThanOrEqualTo(3L)
        assertThat(afterCropAccess).isEqualTo(afterRepositoryQuery)
    }
}

@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan(basePackages = ["com.chamchamcham.domain"])
@EnableJpaAuditing
@EnableJpaRepositories(basePackages = ["com.chamchamcham.domain"])
private class MemberCropRepositoryTestApplication
```

- [ ] **Step 2: focused test를 실행해 RED 확인**

Run from `backend/`:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./gradlew :domain:test \
  --tests 'com.chamchamcham.domain.crop.MemberCropRepositoryTest'
```

Expected: `compileTestKotlin`이 실패하고
`Unresolved reference 'findAllWithCropByMemberId'`가 출력된다.

- [ ] **Step 3: 최소 fetch join 메서드 구현**

`MemberCropRepository.kt`에 `Query`, `Param` import와 다음 메서드만 추가한다.

```kotlin
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

@Query(
    """
    select mc
    from MemberCrop mc
    join fetch mc.crop
    where mc.member.id = :memberId
    """
)
fun findAllWithCropByMemberId(
    @Param("memberId") memberId: UUID
): List<MemberCrop>
```

`findByMemberId()`와 다른 메서드는 수정하지 않는다. `member`, `farm`은 정책 프로필
생성에서 사용하지 않으므로 fetch join하지 않는다.

- [ ] **Step 4: focused test를 실행해 GREEN 확인**

Run:

```bash
./gradlew :domain:test \
  --tests 'com.chamchamcham.domain.crop.MemberCropRepositoryTest'
```

Expected: `BUILD SUCCESSFUL`; 반환된 작물 3개의 필드 접근 후
`prepareStatementCount <= 3` 검증이 통과한다. 이후 `join fetch`를 임시로 `join`으로
바꾸면 서로 다른 작물 조회가 추가되어 테스트가 실패하는지 확인하고 즉시 복원한다.

- [ ] **Step 5: Task 1 변경만 커밋**

```bash
git add \
  backend/domain/src/main/kotlin/com/chamchamcham/domain/crop/MemberCropRepository.kt \
  backend/domain/src/test/kotlin/com/chamchamcham/domain/crop/MemberCropRepositoryTest.kt
git commit \
  -m "fix(policy): 정책 프로필 작물 조회 N+1 방지" \
  -m "정책 추천 전용 MemberCrop-Crop fetch join을 추가하고 서로 다른 작물 수에 비례한 SELECT가 발생하지 않음을 Hibernate statistics로 고정한다." \
  -m "Constraint: 기존 findByMemberId 계약과 전역 Hibernate fetch 설정은 변경하지 않는다." \
  -m "Rejected: member/farm 추가 fetch join | 정책 프로필이 사용하지 않는 연관을 총 1회 수치만 위해 로딩한다." \
  -m "Confidence: high" \
  -m "Scope-risk: narrow" \
  -m "Tested: MemberCropRepositoryTest" \
  -m "Not-tested: 정책 서비스 연결은 다음 태스크에서 검증"
```

---

### Task 2: PolicyRecommendationService를 정책 전용 조회에 연결

**Files:**
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/policy/recommendation/PolicyRecommendationService.kt:75`
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/policy/recommendation/PolicyRecommendationServiceTest.kt:51-90,730-800`

**Interfaces:**
- Consumes: Task 1의 `MemberCropRepository.findAllWithCropByMemberId(UUID)`.
- Produces: 정책 추천 서비스가 기존 `findByMemberId(UUID)`를 호출하지 않고 fetch join 메서드를 사용하는 데이터 흐름.
- Preserves: 카드 내용, 점수, stale 판정, 재생성, 정렬·필터·커서 결과.

- [ ] **Step 1: 서비스 연결 계약 실패 테스트 작성**

`PolicyRecommendationServiceTest`에 다음 테스트를 추가한다. Task 1 메서드는 존재하지만
서비스가 아직 기존 메서드를 사용하므로 Mockito verification에서 실패해야 한다.

```kotlin
@Test
fun `list recommendations loads crops through policy fetch join query`() {
    val program = recommendableProgram()
    val row = recommendation(program)
    givenReusableRecommendations(program)
    `when`(policyRecommendationQueryRepository.findPage(recommendationSearchCondition()))
        .thenReturn(PolicyRecommendationQueryRepository.SearchResult(listOf(row)))

    service.listRecommendations(memberId, cursor = null, size = 20)

    verify(memberCropRepository).findAllWithCropByMemberId(memberId)
    verify(memberCropRepository, never()).findByMemberId(memberId)
}
```

- [ ] **Step 2: focused application test를 실행해 RED 확인**

Run from `backend/`:

```bash
./gradlew :application:test \
  --tests 'com.chamchamcham.application.policy.recommendation.PolicyRecommendationServiceTest'
```

Expected: 새 테스트가 `Wanted but not invoked`로 실패하고 실제 호출은
`findByMemberId(memberId)`로 표시된다.

- [ ] **Step 3: 서비스와 테스트 fixture를 최소 변경**

`PolicyRecommendationService.listRecommendations()`의 회원 작물 조회 한 줄만 바꾼다.

```kotlin
val memberCrops = memberCropRepository.findAllWithCropByMemberId(memberId)
```

`PolicyRecommendationServiceTest.givenPolicyProfileData()`의 stub도 같은 메서드로
변경한다.

```kotlin
`when`(memberCropRepository.findAllWithCropByMemberId(memberId)).thenReturn(
    listOf(
        MemberCrop(
            member = member,
            farm = farm,
            crop = crop
        )
    )
)
```

테스트의 나머지 fixture, scorer 입력, repository verification은 수정하지 않는다.

- [ ] **Step 4: focused application test를 실행해 GREEN 확인**

Run:

```bash
./gradlew :application:test \
  --tests 'com.chamchamcham.application.policy.recommendation.PolicyRecommendationServiceTest'
```

Expected: 전체 `PolicyRecommendationServiceTest` 통과. 새 테스트는 fetch join 메서드
호출 1회와 기존 메서드 미호출을 검증한다.

- [ ] **Step 5: Task 2 변경만 커밋**

```bash
git add \
  backend/application/src/main/kotlin/com/chamchamcham/application/policy/recommendation/PolicyRecommendationService.kt \
  backend/application/src/test/kotlin/com/chamchamcham/application/policy/recommendation/PolicyRecommendationServiceTest.kt
git commit \
  -m "fix(policy): 추천 서비스가 fetch join 작물 조회를 사용" \
  -m "정책 추천 프로필 생성 경로를 전용 MemberCrop-Crop fetch join에 연결하고 기존 조회 메서드가 재사용되지 않도록 서비스 계약 테스트로 고정한다." \
  -m "Constraint: 추천 점수, stale 판정, 카드 및 커서 계약은 변경하지 않는다." \
  -m "Confidence: high" \
  -m "Scope-risk: narrow" \
  -m "Directive: 정책 프로필에 crop 필드가 추가돼도 fetch join query-count 계약을 유지한다." \
  -m "Tested: PolicyRecommendationServiceTest" \
  -m "Not-tested: 전체 백엔드 회귀는 Task 3에서 검증"
```

---

### Task 3: 기존 정책 카드 fetch join query-count 고정과 전체 검증

**Files:**
- Modify: `backend/domain/src/test/kotlin/com/chamchamcham/domain/policy/PolicyRecommendationQueryRepositoryTest.kt:20-30,40-75`
- Verify only: `backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicyRecommendationQueryRepositoryImpl.kt:81-90`

**Interfaces:**
- Consumes: 기존 `PolicyRecommendationQueryRepository.findPage(SearchCondition)`.
- Produces: 결과의 `PolicyRecommendation.policyProgram` 카드 필드 접근까지 SELECT가
  2회 이하이며 카드 수에 비례해 증가하지 않는다는 회귀 계약.
- Preserves: repository production query의 기존 `join fetch r.policyProgram` 구현.

- [ ] **Step 1: Hibernate statistics 설정과 query-count 테스트 추가**

`PolicyRecommendationQueryRepositoryTest`에 다음 import를 추가한다.

```kotlin
import org.hibernate.SessionFactory
```

클래스의 `@DataJpaTest`를 테스트 범위 statistics 설정으로 바꾼다.

```kotlin
@DataJpaTest(
    properties = ["spring.jpa.properties.hibernate.generate_statistics=true"]
)
```

다음 테스트를 추가한다.

```kotlin
@Test
fun `find page avoids policy program selects proportional to card count`() {
    persistRecommendation("정책 A", score = "0.9500", applyEndsOn = null)
    persistRecommendation("정책 B", score = "0.9000", applyEndsOn = null)
    persistRecommendation("정책 C", score = "0.8500", applyEndsOn = null)
    entityManager.flush()
    entityManager.clear()

    val statistics = entityManager.entityManager.entityManagerFactory
        .unwrap(SessionFactory::class.java)
        .statistics
    statistics.clear()

    val result = queryRepository.findPage(condition(size = 10))
    val cardFields = result.rows.map { row ->
        listOf(
            row.policyProgram.title,
            row.policyProgram.eligibilitySummary,
            row.policyProgram.benefitSummary,
            row.policyProgram.agencyName
        )
    }

    assertThat(cardFields).hasSize(3)
    assertThat(statistics.prepareStatementCount).isLessThanOrEqualTo(2L)
}
```

- [ ] **Step 2: 현재 fetch join 기준 GREEN 확인**

Run from `backend/`:

```bash
./gradlew :domain:test \
  --tests 'com.chamchamcham.domain.policy.PolicyRecommendationQueryRepositoryTest'
```

Expected: `BUILD SUCCESSFUL`; 새 테스트의 statement count가 2 이하이다.

- [ ] **Step 3: mutation으로 테스트가 실제 N+1을 탐지하는지 확인**

검증을 위해 `PolicyRecommendationQueryRepositoryImpl`의 다음 한 줄을 임시로 바꾼다.

```kotlin
join r.policyProgram p
```

Step 2 명령을 다시 실행한다.

Expected: `find page avoids policy program selects proportional to card count`가 실패하고
`prepareStatementCount`가 2보다 크다. 이 실패를 확인한 즉시 production query를 다음
원래 코드로 복원한다.

```kotlin
join fetch r.policyProgram p
```

임시 mutation은 커밋하거나 다른 파일에 남기지 않는다.

- [ ] **Step 4: 복원 후 focused GREEN과 diff 확인**

Run:

```bash
./gradlew :domain:test \
  --tests 'com.chamchamcham.domain.policy.PolicyRecommendationQueryRepositoryTest'
git diff -- \
  backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicyRecommendationQueryRepositoryImpl.kt
```

Expected: domain test 통과. production repository diff는 비어 있다.

- [ ] **Step 5: YAGNI 범위와 전체 백엔드 회귀 검증**

Run:

```bash
rg -n 'create index|@Index|indexes =|default_batch_fetch_size|generate_statistics' \
  backend/domain/src/main backend/api/src/main backend/application/src/main
git diff --check
./gradlew test
```

Expected:

- 새 policy/member-crop 인덱스 또는 전역 batch/statistics 설정 검색 결과가 없다.
- `git diff --check`가 출력 없이 종료한다.
- 전체 backend test가 `BUILD SUCCESSFUL`로 종료한다.

- [ ] **Step 6: Task 3 테스트 변경만 커밋**

```bash
git add \
  backend/domain/src/test/kotlin/com/chamchamcham/domain/policy/PolicyRecommendationQueryRepositoryTest.kt
git commit \
  -m "test(policy): 추천 카드 fetch join 쿼리 수 고정" \
  -m "정책 추천 페이지 결과의 PolicyProgram 수에 비례한 SELECT가 발생하지 않음을 Hibernate statistics와 mutation 검증으로 고정한다." \
  -m "Constraint: production query와 운영 Hibernate 설정은 변경하지 않는다." \
  -m "Rejected: PostgreSQL 인덱스 선제 추가 | 실제 실행 계획과 지연 증거가 없다." \
  -m "Confidence: high" \
  -m "Scope-risk: narrow" \
  -m "Directive: join fetch 제거 시 query-count 회귀 테스트가 실패해야 한다." \
  -m "Tested: PolicyRecommendationQueryRepositoryTest, full backend test" \
  -m "Not-tested: 운영 PostgreSQL EXPLAIN ANALYZE는 1만 건 또는 실제 지연 관측 시 수행"
```

---

### Task 4: 최종 리뷰 다중 농장 N+1 수정

**Evidence:** 경계 좌표가 있는 서로 다른 농장 3개에서 cold MemberCrop 조회는 5회,
기존 농장 조회를 먼저 실행해도 EAGER boundary secondary SELECT 때문에 5회였다.
Farm-boundary fetch join을 먼저 실행하면 회원 조회 포함 전체 3회로 고정됐다.

**Files:**
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/farm/FarmRepository.kt`
- Modify: `backend/domain/src/test/kotlin/com/chamchamcham/domain/crop/MemberCropRepositoryTest.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/policy/recommendation/PolicyRecommendationService.kt`
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/policy/recommendation/PolicyRecommendationServiceTest.kt`

**Final contract:**
- 정책 경로는 `findAllWithBoundaryCoordinatesByOwnerId()`를
  `findAllWithCropByMemberId()`보다 먼저 호출한다.
- 3개 농장·경계·작물 프로필 조회가 회원 조회 포함 statement 3회 이하이며 이후 필드
  접근으로 statement가 증가하지 않는다.
- farm boundary fetch 제거 mutation은 6회로 실패하고, 호출 순서 교환 mutation은
  `VerificationInOrderFailure`로 실패한다.
- 기존 `findByOwnerId()`와 `findByMemberId()` 계약은 유지한다.

**Verification:** focused domain/application tests, 두 mutation 복원 후 전체
`./gradlew test`, `git diff --check`가 통과한다.

---

## Final Verification Checklist

- [ ] `MemberCropRepository.findByMemberId()`는 그대로 존재한다.
- [ ] `findAllWithCropByMemberId()`만 `crop`을 fetch join한다.
- [ ] `FarmRepository.findByOwnerId()`는 그대로 존재한다.
- [ ] 정책 전용 farm 쿼리는 boundary를 left fetch join하고 `distinct`를 사용한다.
- [ ] `PolicyRecommendationService`는 농장→회원작물 순으로 정책 전용 메서드만 호출한다.
- [ ] 서로 다른 농장·경계·작물 3개 접근의 statement count가 3 이하이고 farm fetch
  mutation에서 6회로 초과한다.
- [ ] 정책 카드 필드 접근의 statement count가 2 이하이고 mutation에서 초과한다.
- [ ] production 설정에 Hibernate statistics 또는 batch fetch가 추가되지 않았다.
- [ ] index DDL과 `@Index`가 추가되지 않았다.
- [ ] `./gradlew test`가 통과한다.
- [ ] `git status --short`에 의도하지 않은 tracked 변경이 없다.
