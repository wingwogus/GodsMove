# 정책 추천 조회 N+1 방지 설계

## 배경

정책 추천 목록은 `PolicyRecommendationQueryRepositoryImpl`에서
`PolicyRecommendation.policyProgram`을 fetch join하므로 카드 변환 단계의 N+1은
발생하지 않는다. 반면 추천 프로필을 만들 때는
`MemberCropRepository.findByMemberId()`로 `MemberCrop`을 조회한 뒤 LAZY 연관인
`MemberCrop.crop`을 반복 접근한다. 회원이 서로 다른 작물을 여러 개 보유하면 작물
수에 비례한 추가 SELECT가 발생할 수 있다.

현재 운영 PostgreSQL의 실행 계획과 지연 자료는 없다. 정책 프로그램과 회원별 추천
규모 목표도 각각 1만 건 이하이므로, 이번 변경에서 추측 기반 복합 인덱스를 미리
추가하지 않는다.

## 목표

- 정책 추천 프로필 생성 시 `MemberCrop`과 `Crop`을 같은 SELECT로 조회해 작물 수에
  비례한 추가 SELECT를 제거한다.
- 정책 추천 프로필 생성 시 `Farm`과 EAGER `boundaryCoordinates`를 같은 SELECT로
  먼저 조회해 농장 수에 비례한 secondary SELECT를 제거한다.
- 정책 추천 카드 조회 시 기존 `PolicyRecommendation`과 `PolicyProgram` fetch join
  계약을 회귀 테스트로 고정한다.
- 조회 결과, 정렬, 커서, stale 판정, 추천 재생성 동작은 변경하지 않는다.
- 새 테스트 라이브러리나 전역 Hibernate fetch 설정을 추가하지 않는다.
- PostgreSQL 인덱스는 실행 계획에서 병목이 확인된 뒤 별도 변경으로 다룬다.

## 비목표

- 정책 후보 전체 조회 또는 stale 판정 알고리즘 재설계
- 캐시, 배치 fetch, DTO projection 도입
- 정책 동기화 INSERT/UPDATE 성능 개선
- 선제적인 복합 인덱스 생성
- Flyway 또는 Testcontainers 도입
- API, 응답 DTO, 정렬·필터·커서 계약 변경

## 설계 결정

### 정책 전용 회원 작물 조회

기존 `MemberCropRepository.findByMemberId()`는 다른 기능이 사용하므로 fetch 전략을
바꾸지 않는다. 대신 정책 추천 프로필 전용 메서드를 추가한다.

```kotlin
@Query(
    """
    select mc
    from MemberCrop mc
    join fetch mc.crop
    where mc.member.id = :memberId
    """
)
fun findAllWithCropByMemberId(@Param("memberId") memberId: UUID): List<MemberCrop>
```

`PolicyRecommendationService.listRecommendations()`만 이 메서드를 사용한다.
추천 프로필 생성에서 `crop.name`과 `crop.usePartCategory`를 읽어도 영속성 컨텍스트가
추가 SELECT를 수행하지 않는다. 이 경로에서 사용하지 않는 `member`와 `farm`은 fetch
join하지 않는다. 대신 아래 정책 전용 농장 조회를 먼저 실행해 final인 `farm` 연관이
영속성 컨텍스트에서 해소되도록 한다.

### 정책 전용 농장·경계 조회와 호출 순서

`Farm.boundaryCoordinates`는 EAGER이므로 기존 `findByOwnerId()`는 다중 농장에서
농장별 secondary SELECT를 발생시킨다. 기존 계약은 유지하고 정책 전용 메서드를
추가한다.

```kotlin
@Query(
    """
    select distinct f
    from Farm f
    left join fetch f.boundaryCoordinates
    where f.owner.id = :ownerId
    """
)
fun findAllWithBoundaryCoordinatesByOwnerId(
    @Param("ownerId") ownerId: UUID
): List<Farm>
```

서비스는 회원을 조회한 다음 이 농장 쿼리를 실행하고, 그 뒤 회원 작물 쿼리를
실행한다. 농장과 경계가 먼저 영속성 컨텍스트에 들어가므로 final인
`MemberCrop.farm` 때문에 농장별 SELECT가 다시 발생하지 않는다. 순서만 바꾸고 기존
농장 쿼리를 유지하면 EAGER 경계 조회가 남으므로 두 변경은 함께 적용한다.

### 기존 정책 카드 조회 유지

`PolicyRecommendationQueryRepositoryImpl.findPage()`의
`join fetch r.policyProgram`은 유지한다. 구현을 재설계하지 않고 Hibernate query-count
테스트를 추가해 카드 수에 비례한 추가 SELECT가 없음을 고정한다. 여기서도 final인
`member` 연관의 고정 SELECT 1회는 허용한다.

## 데이터 흐름

1. 서비스가 최신 성공 동기화 작업과 정책 후보를 조회한다.
2. 회원 정보를 조회한다.
3. 정책 전용 농장 쿼리가 `Farm`과 경계 좌표를 한 번에 조회한다.
4. 정책 전용 회원 작물 쿼리가 `MemberCrop`과 `Crop`을 한 번에 조회한다.
5. 서비스가 로딩된 농장·작물 정보로 추천 프로필과 stale 여부를 계산한다.
6. 기존 추천 페이지 쿼리가 `PolicyRecommendation`과 `PolicyProgram`을 한 번에 조회한다.
7. 서비스가 기존 정렬·커서 규칙으로 카드와 다음 커서를 반환한다.

추천이 stale한 경우의 삭제와 저장 횟수는 이번 query-count 합격 기준에 포함하지
않는다. 단, stale 여부와 관계없이 회원 작물 조회에서 작물 수에 비례한 SELECT가
발생해서는 안 된다.

## 테스트 설계

### 회원 작물 N+1 회귀 테스트

도메인 JPA 테스트에서 경계 좌표가 있는 서로 다른 농장 3개와 작물 3개를 가진 회원을
저장한다. 영속성 컨텍스트와 Hibernate statistics를 초기화한 뒤 운영 순서대로 회원,
정책 전용 농장, 정책 전용 회원 작물을 조회한다. 농장·경계·작물 필드를 실제로 읽은
뒤에도 statement count가 3 이하인지 검증한다. 농장 boundary fetch를 제거하면 6회로
증가하는지 mutation으로 확인한다.

테스트 클래스에만 다음 속성을 사용한다.

```text
spring.jpa.properties.hibernate.generate_statistics=true
```

전역 애플리케이션 설정은 변경하지 않는다.

### 정책 카드 N+1 회귀 테스트

기존 정책 추천 저장소 테스트에서 영속성 컨텍스트와 statistics를 초기화한다.
`findPage()` 결과의 `policyProgram` 카드 필드를 읽고 statement count가 2 이하인지
검증한다. 본 쿼리와 final인 `member`의 고정 조회 1회만 허용하며, fetch join을 제거하면
정책 프로그램 수만큼 조회가 늘어 상한을 초과하는지 mutation으로 확인한다. 정렬과
커서 테스트는 그대로 유지한다.

### 서비스 계약 테스트

`PolicyRecommendationServiceTest`는 서비스가 일반 `findByMemberId()` 대신
`findAllWithCropByMemberId()`를, 일반 `findByOwnerId()` 대신
`findAllWithBoundaryCoordinatesByOwnerId()`를 호출하는지 검증한다. 또한 농장 조회가
회원 작물 조회보다 먼저 실행되는 순서를 고정한다. 결과 카드, stale 판정, 추천 재생성
관련 기존 테스트는 변경된 mock 메서드에 맞춰 유지한다.

## PostgreSQL 측정과 인덱스 게이트

이번 변경은 인덱스 DDL을 추가하지 않는다. 정책 프로그램과 회원별 추천이 각각 1만
건에 가까워졌거나 실제 지연이 관측되면 대표 회원과 연도로 다음 쿼리 경로에
`EXPLAIN (ANALYZE, BUFFERS)`를 실행한다.

- 최신 성공 동기화 작업 조회
- 추천 가능 정책 후보 조회
- 추천순 첫 페이지와 다음 페이지
- 최신순 첫 페이지와 다음 페이지
- 카테고리 필터가 있는 추천 목록

실행 계획에서 다른 회원이나 다른 기준연도의 대량 행 제거, 과도한 buffer read,
반복적인 대규모 sort가 확인된 경우에만 해당 경로를 위한 최소 인덱스를 별도 설계한다.
작은 테이블에서 PostgreSQL이 합리적으로 sequential scan을 선택했다는 이유만으로
인덱스를 추가하지 않는다.

## 오류와 호환성

- fetch join은 조회 결과의 의미를 바꾸지 않으며 기존 API 오류 계약에 영향을 주지
  않는다.
- 회원에게 작물이 없으면 빈 목록을 반환하는 기존 동작을 유지한다.
- `Crop`은 필수 연관관계이므로 inner fetch join으로 행이 유실되지 않는다.
- 경계 좌표가 없는 농장도 유지해야 하므로 boundary는 left fetch join하며, collection
  join 중복은 `distinct`로 제거한다.
- 통계 수집은 테스트 범위에서만 활성화하므로 운영 성능에 영향을 주지 않는다.

## 완료 기준

- 서로 다른 농장·경계·작물 3개에 접근해도 회원+농장+회원작물 SELECT가 3회 이하이며,
  farm boundary fetch 제거 mutation에서 이 상한을 초과한다.
- 정책 추천 페이지의 `PolicyProgram` 필드 접근까지 SELECT가 2회 이하이며, fetch join
  제거 mutation에서 이 상한을 초과한다.
- 정책 추천 서비스가 두 정책 전용 fetch join 메서드를 농장→회원작물 순으로 사용한다.
- 기존 정책 추천 정렬·필터·커서 테스트와 전체 백엔드 테스트가 통과한다.
- 새 의존성, 전역 fetch 설정, 인덱스 DDL이 추가되지 않는다.
