# Onboarding Step 1 — Figma and API Contract Differences

- 확인일: 2026-07-11
- 목적: 최신 Figma를 시각·사용자 흐름의 기준으로 유지하면서, 현재 배포 API 계약과
  충돌하는 상태를 별도로 관리한다.
- Figma evidence: `onboarding / step 1 - 필수 정보 전체 입력` (`631:11555`)
- Backend source baseline: `origin/dev`를 병합한 commit `b75ba0e75c1640469bd511877a8cc1d3c1861bd4`
- Swagger source: `https://chamchamcham.jaehyuns.com/v3/api-docs`
- Synced Swagger SHA-256: `3cc2a1870dbc6006a9dd3591e7e1c1aee5bb188c4ac836c15d58657babdf2541`

## Confirmed Conflict — Nickname

### Figma state

`631:11555`는 이름, 연락처, 생년월일, 귀농 년차, 자격이 채워진 상태로 보이며
`다음` 버튼을 활성 색 `#343434`로 표시한다. 그러나 닉네임 field는
`닉네임을 입력해주세요.` placeholder가 남아 있고, 닉네임 label에는 required marker가 있다.

### Latest deployed Swagger

동기화한 `docs/swagger/openapi.json`의 `CompleteOnboardingRequest.required`에는
다음이 포함된다.

```text
birthDate, cropIds, experienceLevel, farm, managementType, name, nickname, phone
```

즉 `nickname`은 선택값이 아니다.

### Latest backend source

`backend/api/.../AuthRequests.kt`의 `CompleteOnboardingRequest`도 아래처럼
nickname을 검증한다.

```kotlin
@field:NotBlank(message = "닉네임을 입력해주세요")
val nickname: String
```

빈 문자열 또는 공백 nickname으로 완료 요청을 보내면 backend validation에서 거절된다.

### Decision for implementation planning

Figma의 전체 화면 구조, 컴포넌트, 색상, 문구, 전환 흐름은 최신 디자인 기준으로
반영한다. 다만 이 활성 CTA 상태 하나는 배포 계약을 만족하지 못하므로 그대로
구현하지 않는다.

- `다음` 활성 조건에는 nickname의 공백 제거 후 비어 있지 않음을 포함한다.
- client DTO도 같은 조건을 방어적으로 검증한다.
- Figma에 수정이 가능하다면 `631:11555`의 닉네임을 채우거나 CTA를 비활성으로
  바꾸는 것이 현재 backend와 일치한다.
- 제품이 nickname을 선택값으로 바꾸려는 의도라면 Figma만으로는 부족하다. backend
  validation, Swagger, `BR-USER-002`, client DTO를 함께 변경하는 별도 계약 변경이 필요하다.

## Related Semantic Difference — 귀농 년차

Figma는 `귀농 년차`를 필수 숫자 입력으로 표현한다. 최신 Swagger/backend는
`experienceLevel`이라는 `0...100` 정수만 받으며, 귀농 시점이나 귀농 여부를 별도
필드로 갖지 않는다.

- `귀농 년차`가 현재의 영농 경력과 같은 제품 의미라면 `experienceLevel`로 매핑할 수 있다.
- 두 의미가 다르다면 현재 API 계약으로는 보존할 수 없다.

이 항목은 nickname과 달리 구조적 충돌로 확정하지 않는다. 제품 의미를 확인한 뒤
매핑 또는 backend 변경을 결정한다.

## Confirmed Compatibility — Social Prefill

Swagger의 `MemberProfileResponse`는 name, phone, birthDate, nickname을 응답
속성으로 제공한다(필수 보장은 id만). 따라서 provider/서버가 실제 값을 준 경우
Step 1에 필드별 prefill하는 설계는 현재 API 계약과 양립한다. 값이 없으면 해당 필드만
사용자 입력으로 남겨야 한다.

## Follow-up

이 문서는 현재 배포 계약을 기준으로 한 스냅샷이다. backend 계약을 변경하거나
Swagger hash가 바뀌면, 새 Swagger 동기화 후 이 문서를 다시 대조한다.
