# Figma Capture: Onboarding Step 1 — Input States

- Captured at: 2026-07-11
- Source: TalkToFigma MCP `get_selection`, `read_my_design`, `scan_text_nodes`, `get_nodes_info`
- Shared layout: [Step 1 / 소셜 로그인 데이터 수신](2026-07-11-onboarding-step-1-social-prefill-default.md)
- Purpose: Step 1의 소셜 데이터 미수신, 필수 정보 입력, 전체 정보 입력 상태 차이를 기록한다.

세 프레임의 크기와 기본 레이아웃은 모두 `390 x 1117`로 동일하다. 헤더, 1/4
진행 바, 프로필 사진, 여섯 입력/선택 영역, 하단 CTA 구조는 기본 캡처 문서를 따른다.

## Captured State Matrix

| Figma node | Frame | 입력 상태 | CTA |
| --- | --- | --- | --- |
| `631:11412` | `onboarding / step 1 default - 소셜 로그인 데이터 받아오지 못한 경우` | 모든 직접 입력 필드가 비어 있음. 자격만 개인 농업인 선택 | 비활성 (`#e0e0e0`) |
| `631:11555` | `onboarding / step 1 - 필수 정보 전체 입력` | 이름 `이름 입력 완료`, 연락처 `000-0000-0000`, 생년월일 `yyyy.mm.dd`, 귀농 년차 `2년차`, 개인 농업인 선택. 닉네임은 placeholder 유지 | 활성 (`#343434`) |
| `631:11636` | `onboarding / step 1 - 모든 정보 전체 입력` | 이름·닉네임 입력 완료, 연락처 `000-0000-0000`, 생년월일 `yyyy.mm.dd`, 귀농 년차 `2년차`, 개인 농업인 선택 | 활성 (`#343434`) |

## State Details

### Social data unavailable

Figma node `631:11412`는 provider가 기본 프로필 데이터를 제공하지 못했을 때의
기본 상태다.

- 이름: `이름을 입력해주세요.` placeholder, 흰 field.
- 닉네임: `닉네임을 입력해주세요.` placeholder, 흰 field.
- 연락처: `000-0000-0000` placeholder, 흰 field.
- 생년월일: `yyyy.mm.dd` placeholder, 흰 field.
- 귀농 년차: `귀농 년차를 입력해주세요.` placeholder, 흰 field.
- 자격: 개인 농업인 선택 상태.
- `다음`: 비활성, fill `#e0e0e0`, label `#878787`.

동작 규칙은 단순하다. 소셜 응답에 값이 없거나 null이면 값을 만들어 내지 않고,
사용자가 필수 필드를 직접 입력할 때까지 CTA를 비활성으로 유지한다.

### Required-information-filled

Figma node `631:11555`는 이름, 연락처, 생년월일, 귀농 년차, 자격을 채운 것으로
보이는 활성 상태다. 버튼은 fill `#343434`, label `#ffffff`이다.

그러나 닉네임 input은 `닉네임을 입력해주세요.` placeholder 그대로이고 required
marker도 있다. backend는 nickname을 `@NotBlank`로 검증하므로 이 프레임의 활성
CTA를 실제 validation 조건으로 해석하면 안 된다.

**구현 결정:** 이 화면의 시각적 활성 CTA 스타일만 재사용하고, nickname까지 포함한
모든 필수값이 유효할 때만 활성화한다.

이 결정은 추정이 아니다. 최신 배포 Swagger와 `origin/dev` 병합 후 실제 backend가
nickname을 모두 필수로 검증함을 재확인했다. 자세한 계약 대조는
[Step 1 Figma-계약 충돌](2026-07-11-onboarding-step-1-figma-contract-conflict.md)에 분리했다.

### All-information-filled

Figma node `631:11636`은 이름 `이름 입력 완료`, 닉네임 `닉네임 입력 완료`, 귀농
년차 `2년차`를 명시적으로 보여 주며 활성 CTA를 사용한다. 전체 입력 완료의
참조 상태로 사용한다.

생년월일 텍스트가 `yyyy.mm.dd`로 남아 있으나 placeholder가 아닌 primary text
색 `#1a1a1a`로 표시된다. 이 문자열은 실제 date value가 아니므로 예시 텍스트로만
취급한다. 구현에서는 `Date`가 존재하고 유효한 `yyyy-MM-dd` 요청 값으로 변환될 때만
완료 상태로 판단한다.

## Implementation Guardrails

### Validation source of truth

다음은 Figma 목업의 활성 상태보다 우선한다.

1. `BR-USER-002`의 필수 입력값
2. 실제 backend `CompleteOnboardingRequest`의 `@NotBlank` / `@NotNull`
3. client-side CTA 검증과 DTO 방어 검증

따라서 Step 1의 CTA 활성 조건은 이름, 닉네임, 연락처, 생년월일, 확정된 경력 값,
자격을 모두 만족해야 한다. 공백만 있는 문자열은 값으로 보지 않는다.

### Social prefill state

소셜 데이터 수신 여부는 하나의 화면 상태가 아니라 **필드별** 상태다. 예를 들어
name과 phone만 받았으면 그 두 필드만 prefill하고, birthDate/nickname/경력은 그대로
사용자 입력을 기다린다. 이전에 저장된 동일 member의 초안은 provider 응답보다 우선한다.

### Date and phone formatting

- 날짜 UI 표기는 Figma의 `yyyy.MM.dd`를 사용하되, API body는 기존 계약인 `yyyy-MM-dd`로 전송한다.
- 연락처 입력·prefill 값은 표시 형식을 통일하고, 빈 placeholder `000-0000-0000`를 실제 값으로 취급하지 않는다.

## Design Evidence Limits

- 두 활성 프레임의 날짜 텍스트는 실제 날짜가 아니라 형식 문자열처럼 보인다.
- `필수 정보 전체 입력` 프레임은 nickname이 비어 있는데도 CTA가 활성이다.
- 따라서 이 캡처는 filled/disabled 색상과 레이아웃의 근거로 쓰되, 필수값 검증의
  완전한 제품 규칙으로 사용하지 않는다.

다음에 validation error 또는 date picker opened 상태가 제공되면, 이 문서의
불확실한 부분을 해당 캡처로 대체한다.
