# 코칭 출력의 영어 전용 언어 검증 설계

## 배경과 문제

기록 코칭과 작업별 리포트 코칭은 농부에게 보여 주는 문장에서 영어와 어려운 농업
용어를 함께 차단한다. 현재 공통 정책은 영어 알파벳뿐 아니라 `토양`, `수분`, `배수`,
`생육`, `정식`, `살포`, `유기질` 같은 단어도 구조화 출력 실패 조건으로 취급한다.

이 방식은 쉬운 표현을 유도하는 수준을 넘어 정상적인 한국어 문장까지 버린다. 특히
공식 기술 문서를 근거로 만드는 물 주기, 거름 주기, 병이나 벌레 관리 코칭은 해당
용어를 포함하기 쉬워 두 번의 생성 시도 후 `STRUCTURED_OUTPUT_INVALID`가 될 가능성이
높다. 쉬운 말의 경계는 문맥에 따라 달라지므로 고정 문자열 목록으로 생성 성공 여부를
결정하기에 적합하지 않다.

반면 `WATERING`, `recordCount`, `kg`처럼 내부 enum, 필드명, 영문 단위가 사용자
문장에 노출되는 것은 명확한 출력 오류다. 이번 변경은 모호한 쉬운 말 판단과 명확한
영어 노출 판단을 분리한다.

## 확정된 결정

- 쉬운 표현은 기록·리포트 코칭의 공통 프롬프트 지침으로만 요구한다.
- 농업 용어 블랙리스트는 출력 실패 조건에서 완전히 제거한다.
- 농부에게 보여 주는 생성 문장에 영어 알파벳 `[A-Za-z]`가 있으면 기존처럼 실패시킨다.
- 친근한 존댓말 `~요` 검증은 명시적인 제품 요구이므로 유지한다.
- JSON 구조, 필수 값, 길이, 항목 수, 중복, 근거 참조 범위와 작업별 근거 규칙은 모두
  유지한다.
- 언어 경고 이름은 실제 의미가 드러나도록 `*_text_language`에서
  `*_text_english`로 바꾼다.
- API 응답, DB 스키마, 재시도 횟수와 실패 코드는 변경하지 않는다.
- 기존에 실패한 리포트 피드백의 재생성 기능은 이번 범위에 포함하지 않는다.

## 영어가 필요한 경우에 대한 경계

농업 문장에도 `pH`, `EC`, `NPK`, 영문 품종 코드, 농자재 상품명처럼 영어가 필요한
경우가 생길 수 있다. 그러나 현재 코칭 계약은 작업명과 단위를 한글로 풀어 쓰며,
사용자 문장에서 영문 고유명사를 보존해야 한다는 요구가 없다. 따라서 확인되지 않은
미래 사례를 위해 허용 목록이나 문맥 분석기를 미리 만들지 않는다.

영어 검사는 실제 사용자 노출 문장에만 적용한다.

```text
RecordFeedback
- goodPoint.text
- nextActions[].text

ReportFeedback
- summary
- strengths[].text
- improvements[].text
- nextActions[].text
```

JSON 필드명, enum wire 값, `basis`, `evidenceRefs`, RAG 문서와 검색 질의는 검사하지
않는다. 이후 영문 상품명이나 품종명을 그대로 보여 줘야 하는 실제 요구가 생기면,
입력 컨텍스트에 존재하는 고유명사만 허용하는 별도 설계로 다룬다.

## 변경 설계

### 공통 정책

`CoachingTextPolicy`는 다음 두 책임만 유지한다.

1. 쉬운 한국어와 친근한 존댓말을 요구하는 공통 프롬프트 지침 제공
2. 사용자 노출 문장에 영어 알파벳이 있는지 검사

`DISALLOWED_TERMS`와 용어 substring 검사는 삭제한다. 기존
`hasDisallowedLanguage`는 영어 검사라는 책임이 드러나는 `containsEnglishLetter`로
바꾼다. 별도 validator 계층, 용어 사전, 후처리 치환기는 추가하지 않는다.

### 기록 코칭

`RecordFeedbackOutputValidator`는 good point와 next action의 `text`에만 공통 영어
검사를 적용한다. 영어가 있으면 다음 고정 warning을 반환한다.

```text
good_point_text_english
next_action_<index>_text_english
```

`RecordFeedbackGenerationService`의 재시도 프롬프트와
`RecordFeedbackGenerationProcessor`의 안전 진단 코드 허용식도 같은 이름을 사용한다.
원문이나 발견된 영어 문자열은 로그에 남기지 않는다.

### 리포트 코칭

`ReportFeedbackOutputValidator`는 summary와 각 section의 `text`에 영어 검사를
적용한다. 영어가 있으면 다음 고정 warning을 반환한다.

```text
summary_text_english
strength_text_english
improvement_text_english
next_action_text_english
```

`ReportFeedbackGenerationService`의 안전 재시도 warning 목록과 정규식도 같은 이름으로
맞춘다. 기존 두 번 재시도와 최종 `STRUCTURED_OUTPUT_INVALID` 동작은 유지한다.

### 프롬프트

공통 프롬프트에는 쉬운 일상말을 사용하고 기술 문서의 어려운 표현을 그대로 복사하지
말라는 지침을 남긴다. 농업 용어를 발견했을 때 반드시 실패하는 계약은 제거하지만,
모델이 쉬운 말로 작성해야 한다는 제품 방향은 바꾸지 않는다.

영어 알파벳, 내부 enum, 영어 필드명과 영어 단위를 사용자 문장에 쓰지 말라는 지침은
유지한다. 알려진 작업명과 단위의 한글 변환도 기존 PromptBuilder 구현을 그대로 쓴다.

## 고려한 대안

### 기존 이름을 유지하고 용어 목록만 삭제

변경량은 가장 작지만 `hasDisallowedLanguage`와 `text_language`가 실제로는 영어만
검사하게 되어 이후 코드를 읽는 사람이 동작을 오해한다. 명확한 이름으로 함께 바꾸는
편이 장기 유지보수 비용이 낮아 기각한다.

### 어려운 용어를 감사 경고로 저장

생성 실패는 피할 수 있지만 새로운 감사 정책과 저장 의미가 필요하다. 현재 제품에서
이 경고를 소비하는 기능이 없으므로 YAGNI에 따라 추가하지 않는다.

### 영문 고유명사 허용 목록 추가

`pH`, 상품명, 품종 코드 같은 사례를 다룰 수 있지만 현재 요구와 실제 실패 사례가
없다. 임의 허용 목록은 누락과 과허용을 함께 만든다. 실제 고유명사 보존 요구가 생길
때 입력 컨텍스트 기반으로 설계한다.

## 테스트 전략

구현은 기존 동작을 테스트로 잠근 뒤 변경한다.

### 공통 정책

- `WATERING`, `10kg`, `pH`가 포함된 문장은 영어 위반이다.
- 영어가 없는 한국어 문장은 허용한다.
- 과거 금지 목록의 모든 농업 용어가 포함된 문장도 영어가 없으면 허용한다.
- 공통 프롬프트의 쉬운 말 지침은 유지된다.

### 기록 코칭

- good point와 next action의 영어는 `*_text_english` warning을 만든다.
- `토양`, `수분`, `배수` 같은 한국어 농업 용어만 있는 text는 언어 warning을 만들지
  않는다.
- 존댓말, 길이, action 수, 근거 범위와 작업별 근거 규칙 테스트는 그대로 통과한다.
- 첫 응답의 영어 위반이 두 번째 응답에서 수정되면 재시도 후 성공한다.
- 안전 진단 로그는 새 warning 이름만 기록하고 원문을 기록하지 않는다.

### 리포트 코칭

- summary와 세 section text의 영어는 각각 `*_text_english` warning을 만든다.
- 한국어 농업 용어는 언어 warning 없이 통과한다.
- 빈 section 허용, 존댓말, 중복, 근거 참조 검증은 그대로 유지한다.
- 영어 warning이 두 번째 구조화 출력 요청에 안전한 고정 코드로 전달된다.

### 검증 명령

```bash
cd backend

./gradlew :application:test \
  --tests '*CoachingTextPolicyTest' \
  --tests '*RecordFeedbackOutputValidatorTest' \
  --tests '*RecordFeedbackGenerationServiceTest' \
  --tests '*RecordFeedbackGenerationProcessorTest' \
  --tests '*ReportFeedbackOutputValidatorTest' \
  --tests '*ReportFeedbackGenerationServiceTest'

./gradlew --no-parallel :domain:test :application:test :api:test --rerun-tasks
```

## 남는 위험과 대응

- 프롬프트만으로 쉬운 표현을 완전히 보장할 수는 없다. 대신 모호한 표현 때문에 전체
  코칭이 실패하는 문제를 줄이는 것을 우선한다.
- 영어가 포함된 실제 상품명이나 품종명이 필요해지면 현재 정책은 이를 거부한다. 실제
  요구가 확인될 때 입력값 기반 예외를 추가한다.
- 기존 FAILED 리포트는 정책 변경만으로 자동 재생성되지 않는다. 현재 리포트 재생성
  API가 없으므로 로컬 검증에서는 DB를 재생성해야 하며, 재생성 API 추가는 이번 범위가
  아니다.
- warning 이름은 내부 재시도와 로그용이며 API의 `failureCode` 계약은 바뀌지 않는다.

## 완료 기준

- 농업 용어 블랙리스트가 생성 성공 여부에 영향을 주지 않는다.
- 기록·리포트 사용자 문장의 영어 알파벳은 기존처럼 검증 실패한다.
- 모든 사용자 문장의 친근한 `~요` 말투 검증이 유지된다.
- 구조, 길이, 항목 수, 근거 참조와 중복 검증 동작이 유지된다.
- warning과 함수 이름이 영어 전용 검사의 실제 책임을 나타낸다.
- API와 DB 계약이 바뀌지 않는다.
- 관련 집중 테스트와 전체 backend 테스트가 통과한다.
