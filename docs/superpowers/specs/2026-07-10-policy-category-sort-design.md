# 정책 추천 카테고리·정렬 설계

## 목표

추천 정책 목록의 지원 카테고리를 제품에서 사용하는 10개 분류로 정리하고,
기존 추천순 외에 정책 시작일 기준 최신순을 제공한다.

## 확정 API 계약

`GET /api/v1/policy-recommendations`에 선택 쿼리 파라미터를 추가한다.

- `sort=RECOMMENDED|LATEST`
- 생략 시 `RECOMMENDED`
- 알 수 없는 값은 `INVALID_INPUT`으로 응답한다.
- `benefitCategory`는 카테고리 enum key를 받는다.

카테고리는 아래 key와 label을 정식 계약으로 사용한다.

| Key | Label |
| --- | --- |
| `GRANT` | `지원금` |
| `FINANCE` | `융자·금융` |
| `FACILITY_EQUIPMENT` | `시설·장비` |
| `EDUCATION` | `교육` |
| `WELFARE` | `복지` |
| `CERTIFICATION` | `인증` |
| `MARKET` | `판로` |
| `STARTUP` | `창업` |
| `ENVIRONMENT_INFRA` | `환경·인프라` |
| `ETC` | `기타` |

기존 분류 key와 label은 새 계약으로 교체한다. 현재 앱에 정책 API 연동이 없고
기능이 아직 개발 단계이므로 구 key alias는 추가하지 않는다.

## 분류 규칙

정책 상세의 지원 내용에서 다음 우선순위로 하나의 카테고리를 결정한다.

1. 지원금: 직불금, 지원금, 보조금, 장려금, 수당, 바우처
2. 융자·금융: 융자, 정책자금, 대출, 금리, 이자
3. 시설·장비: 시설, 장비, 농기계, 설치, 개보수
4. 교육: 교육, 컨설팅, 상담, 연수
5. 복지: 보험, 보험료, 연금, 건강, 복지
6. 인증: 인증, 검정, 품질, 무병묘, 저탄소
7. 판로: 박람회, 수출, 판로, 홍보, 브랜드
8. 창업: 창업, 사업화, 벤처, R&D
9. 환경·인프라: 수질, 용수, 저수지, 가뭄, 환경, 인프라
10. 기타: 어느 규칙에도 해당하지 않음

기존 우선순위를 유지해 한 정책이 여러 키워드를 포함해도 결과가 결정적이게 한다.
`데이터` 단독 키워드는 창업으로 분류하지 않아 일반 데이터 관련 정책의 오분류를 줄인다.

## 정렬과 커서

추천순은 기존 계약을 유지한다.

```text
score desc, applyEndsOn asc nulls last, id asc
```

최신순은 다음 계약을 사용한다.

```text
applyStartsOn desc nulls last, id asc
```

시작일이 없는 정책은 제외하지 않고 날짜가 있는 정책 뒤에 둔다. 모든 정렬은 `id`를
최종 tie-breaker로 사용해 커서 페이지 사이의 중복과 누락을 막는다.

커서 payload에는 `sort`를 포함한다. 추천순 커서는 `score`, `applyEndsOn`, `id`를,
최신순 커서는 `applyStartsOn`, `id`를 사용한다. 요청의 source, sourceYear,
benefitCategory, sort와 커서의 값이 하나라도 다르면 `INVALID_INPUT`으로 거부한다.

## 구현 경계

- API: `sort` 파라미터를 파싱해 서비스로 전달한다.
- Application: 정렬과 필터를 커서에 결합하고, 다음 커서를 현재 정렬에 맞게 만든다.
- Domain query repository: 기존 JPQL에 정렬별 cursor predicate와 order by를 선택한다.
- Sync/card text: 새 카테고리 label과 키워드 규칙을 적용한다.
- DB 스키마 변경과 새 의존성은 없다.
- 카테고리 필터는 저장된 `benefitSummary` label을 exact-match하므로, 배포 후 새 규칙으로
  저장 label을 갱신하는 성공한 정책 sync를 최소 한 번 실행해야 카테고리 필터 롤아웃이 완료된다.
- 커서 payload에 `sort`가 추가되므로 `sort`가 없는 기존 커서는 롤아웃 시 의도적으로
  `INVALID_INPUT` 처리한다. 현재는 앱 API 연동 전 개발 단계이므로 구 커서 호환 계층은 추가하지 않는다.

## 오류 처리

- 알 수 없는 `sort` 또는 `benefitCategory`: `INVALID_INPUT`
- 다른 필터나 정렬에서 발급된 커서: `INVALID_INPUT`
- 손상된 커서: 기존처럼 `INVALID_INPUT`
- 시작일 누락: 오류가 아니며 최신순의 마지막 그룹으로 처리

## 테스트

- 카테고리 enum key/label 10개와 대표 키워드 매핑 단위 테스트
- `RECOMMENDED` 기존 정렬 회귀 테스트
- `LATEST` 날짜 내림차순, 동률 id, null 마지막, 다음 페이지 커서 저장소 테스트
- 서비스에서 sort/filter 전달, 정렬별 커서 encode/decode, 다른 sort 커서 거부 테스트
- 컨트롤러 기본 추천순, 명시적 최신순, 잘못된 sort 응답 테스트
- 관련 모듈 테스트 후 전체 backend 테스트 실행

## 제외 범위

- 프론트엔드 정책 화면 구현
- 점수 계산식 변경
- 정책 시작일 누락 데이터 보정
- 카테고리 조회 전용 API 추가
- DB migration 도구 도입
