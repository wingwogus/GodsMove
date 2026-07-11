# 공통 PR 템플릿과 정책 API 경로 정리 설계

## 목표

- 사람과 AI가 동일한 형식으로 변경 목적, 영향, 검증 결과를 전달하도록 저장소 공통
  Pull Request 템플릿을 제공한다.
- 정책 관련 API를 복수형 리소스 경로 아래로 모아 일반 조회와 관리자 동기화 경로의
  구조를 일관되게 만든다.
- 현재 정책 카테고리·정렬 PR에 변경을 함께 반영하고 `dev` 대상으로 검증한다.

## 공통 PR 템플릿

저장소 기본 템플릿은 `.github/pull_request_template.md` 한 개만 둔다. 백엔드와 iOS가
같은 템플릿을 사용하며 별도 유형별 템플릿은 만들지 않는다.

템플릿은 다음 섹션을 포함한다.

1. `변경 내용`
2. `변경 이유`
3. `영향 범위`
4. `검증`
5. `배포 및 호환성`
6. `체크리스트`

체크리스트는 테스트 추가·수정, 로컬 검증, API·설정·DB 호환성 확인, 민감 정보 미포함을
확인한다. 적용되지 않는 섹션은 삭제하지 않고 `해당 없음`으로 기록한다. 스크린샷과 관련
이슈는 모든 PR에 필요하지 않으므로 고정 섹션으로 강제하지 않는다.

## AI PR 작성 규칙

루트 `AGENTS.md`에 PR 작성 규칙을 추가한다. 루트 규칙이 모든 작업 영역에 적용되므로
backend와 frontend 가이드에는 같은 내용을 중복하지 않는다.

AI가 PR을 만들 때는 다음 계약을 따른다.

- `.github/pull_request_template.md`의 섹션 순서를 사용한다.
- 각 항목을 실제 변경 기준으로 작성하고 해당 없으면 `해당 없음`을 명시한다.
- 실행한 검증 명령과 결과를 구체적으로 기록한다.
- 실행하지 못한 검증과 남은 위험을 숨기지 않는다.
- API, DB, 설정 호환성과 별도 배포 작업을 명시한다.
- 토큰, 키, 실제 환경변수 등 민감한 값을 PR 본문에 포함하지 않는다.
- 사용자가 Ready PR을 명시하지 않으면 Draft PR을 기본으로 생성한다.

## 정책 API 경로

정책 리소스는 다음 경로를 정식 계약으로 사용한다.

```text
GET  /api/v1/policies/recommendations
GET  /api/v1/policies/{policyProgramId}

POST /api/v1/admin/policies/sync-jobs
GET  /api/v1/admin/policies/sync-jobs/{jobId}
```

`policies`는 복수형 정책 리소스 경계다. 개인화된 추천 목록은 정책 컬렉션의
`recommendations` 하위 컬렉션으로 표현한다. 관리자 동기화 작업은 admin 경계 아래의
정책별 `sync-jobs` 컬렉션으로 표현한다.

다음 기존 경로는 제거한다.

```text
/api/v1/policy-recommendations
/api/v1/policy-programs/{policyProgramId}
/api/v1/admin/policy-sync-jobs
/api/v1/admin/policy-sync-jobs/{jobId}
```

현재 앱 정식 연동 전 개발 단계이므로 기존 경로 alias와 폐기 경고 계층은 추가하지
않는다. 요청·응답 DTO, 인증 방식, 정렬·필터·커서 계약은 변경하지 않는다.

## 구현 범위

- `.github/pull_request_template.md` 추가
- 루트 `AGENTS.md`에 AI PR 작성 규칙 추가
- 정책 및 관리자 정책 동기화 컨트롤러 경로 변경
- API 컨트롤러 테스트와 관리자 보안 테스트의 요청 경로 변경
- ignored `test-ios` 하네스의 API 경로 상수, 단위 테스트, README 변경
- 현재 Draft PR 본문을 새 템플릿 구조에 맞게 갱신

`test-ios`는 저장소 ignore 대상이므로 로컬 검증 하네스만 갱신하고 PR 커밋에는 포함하지
않는다.

## 검증

- `rg`로 backend 실행 코드·테스트와 test-ios 하네스에 제거 대상 기존 경로가 남지
  않았는지 확인한다.
- backend에서 `./gradlew test`를 실행한다.
- test-ios에서 Xcode 프로젝트 목록과 iOS 시뮬레이터 단위 테스트를 실행한다.
- `git diff --check`로 공백 오류를 확인한다.
- PR이 `dev`를 대상으로 열려 있고 mergeable 상태인지 확인한다.
- PR 본문이 새 공통 템플릿의 여섯 섹션을 모두 포함하는지 확인한다.

## 배포 및 호환성

- 기존 정책 경로를 호출하는 클라이언트는 새 경로로 함께 변경해야 한다.
- 경로 외의 API 데이터 계약과 권한 모델에는 변화가 없다.
- DB 스키마 변경, 데이터 마이그레이션, 새 의존성은 없다.
- 배포 전에 추적되지 않는 외부 소비자가 기존 경로를 호출하지 않는지 확인할 책임이
  있다. 현재 저장소에서 확인된 런타임 소비자는 ignored test-ios 하네스뿐이다.
- 과거 설계·계획 문서와 frontend에 보관된 가져오기 형식의 API 명세는 당시 계약을
  기록하는 자료이므로 이번 변경에서 소급 수정하지 않는다.

## 제외 범위

- 기존 경로 alias 또는 deprecation 응답
- API 버전 변경
- 정책 도메인의 요청·응답 모델 변경
- PR 유형별 복수 템플릿과 자동 라벨링
- GitHub Actions를 이용한 PR 본문 형식 강제
- 과거 설계·계획 문서와 frontend 보관 API 명세의 일괄 개정
