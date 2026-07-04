# 영농일지 기록 피드백 RAG — 단기 품질 개선 설계

- 날짜: 2026-07-04
- 브랜치: `feat/rag-pgvector-service`
- 상태: 승인됨 (page 메타데이터 포함)

## 배경

영농일지 작성 직후 코칭을 주는 record 파이프라인
(`application/coaching/rag/record/`)을 분석한 결과, 응답 스키마 골격은
좋으나 내용 품질을 보장하는 장치가 꺼져 있거나 미완성이다. 이 설계는
현재 브랜치에서 처리 가능한 코드 수정 6건을 다룬다.

핵심 근거 (분석에서 직접 확인):

- `TodayRecordFeedbackService.retrieveDocuments()`의 SearchRequest에
  similarityThreshold 미설정 → 기본 0.0으로 무관 문서도 topK만큼 항상
  반환. 설정값 `rag.retrieval.lowSimilarityThreshold=0.55`는 정의만
  있고 사용처가 없는 dead config.
- TECH_DOCUMENT 청크 메타데이터에 작물 정보가 없어 작물 필터 불가.
- 검색 쿼리 플래너가 `targetRecord.memo`를 사용하지 않아 메모에 적힌
  실제 문제(병징 등)가 검색에 반영되지 않음.
- 근거 문서가 promptBuilder(user 프롬프트)와
  `RetrievalAugmentationAdvisor` 두 경로로 이중 주입됨.
- `CoachingStructuredOutputValidator`가 `riskLevel == UNKNOWN`이면
  무인용 권고를 경고 없이 통과시키고, audit FAIL이어도 결과가 그대로
  사용자에게 반환됨.
- 프롬프트에 minTemperatureC, lastWorkedOnByType,
  member(experienceLevel/managementType)가 출력되지 않음.
- 구조화 출력 1회 실패가 곧 피드백 전체 실패(재시도 없음).
- pdftotext 출력의 페이지 구분자(`\f`)를 청킹 정규화가 지워버려
  인용의 page가 항상 null.

## 범위

- 대상: `application/coaching/rag/record/`, `rag/common/`, 로컬 전용
  시드 코드(`rag/seed/`, `api/dev/` — 아래 정책 참조)
- 비범위 (중기 과제로 이월): 피드백 영속화·조회 API, 프로덕션 인덱싱
  경로, OCR 파이프라인, 서버 측 컨텍스트 어셈블러, 구 chat 파이프라인
  (`rag/chat/`) 수정, 외부 API(농사로/NCPMS/PSIS) 편입, 응답 계약(DTO)
  구조 변경

## 로컬 전용 코드 정책

seed·dev 코드는 커밋하지 않는다. 브랜치 이력에서도 제거되었고
(2026-07-05 filter-branch, 백업: `backup/feat-rag-pgvector-service-20260705`),
아래 경로는 `.gitignore`로 추적이 차단된다. 파일은 로컬에만 존재하며
local 프로필에서만 동작한다.

- `backend/api/src/main(·test)/kotlin/com/chamchamcham/api/dev/`
- `backend/application/src/main(·test)/kotlin/com/chamchamcham/application/coaching/rag/seed/`
- `/data/`, `/outputs/`, `backend/docs/db/crop-seed.sql`

파급 규칙:

- 커밋되는 코드·테스트는 위 경로의 클래스를 참조하지 않는다
  (컴파일·CI가 로컬 전용 파일 없이 성공해야 한다).
- `SecurityConfig`의 dev 경로 permitAll 목록은 이미
  `environment.acceptsProfiles("local")` 런타임 게이트가 있으므로
  추적 코드에 유지한다 (경로 문자열은 컴파일 의존성이 없다).
- 본 설계의 시드 측 변경(5-a)은 로컬 전용 작업으로, 커밋 계획에서
  제외된다. 커밋되는 본체는 시드가 만드는 **메타데이터 계약**에만
  의존한다.

### 벡터스토어 메타데이터 계약 (색인기 ↔ 검색기)

색인기(로컬 시더든 향후 프로덕션 인덱서든)는 TECH_DOCUMENT 청크에
다음 메타데이터 키를 기록해야 한다. 검색·인용 코드는 이 계약만 본다.

| 키 | 타입 | 의미 |
|---|---|---|
| `sourceType` | String | `TECH_DOCUMENT` 고정 |
| `documentTitle` | String | 사람이 읽는 문서 제목 |
| `cropName` | String | 단일 작물 문서는 작물명, 다작물/일반은 `GENERAL` |
| `page` | Int | 청크 시작 페이지 (1-base), 미상 시 생략 가능 |
| `publisher` | String | 발행 기관 (예: 농촌진흥청) |
| `year` | Int | 발행 연도 |

## 변경 설계

### 1. 유사도 임계값 적용

- `TodayRecordFeedbackService.retrieveDocuments()`의 SearchRequest에
  `.similarityThreshold(ragProperties.retrieval.lowSimilarityThreshold)`
  추가.
- 효과: 저관련 청크 차단, `insufficientEvidence` 폴백이 실제로 동작.
- 테스트: VectorStore 페이크가 받은 SearchRequest의 threshold 값 검증.

### 2. 근거 이중 주입 제거

- `RetrievalAugmentationAdvisor` 및 `DocumentRetriever` 사용 제거.
  chatClient 호출은 `system(prompt.system).user(prompt.user)`만 유지.
- 근거는 promptBuilder가 `[id]` 라벨과 함께 이미 user 프롬프트에
  포함하므로 기능 손실 없음. 토큰 사용량 절감.
- 테스트: 기존 서비스 테스트에서 advisor 관련 검증 제거·조정.

### 3. 메모 기반 검색 쿼리 추가

- `RecordFeedbackRetrievalQueryPlanner.plan()`에서 memo가 공백이 아니면
  `"{작물명} {memo 앞 120자}"` 쿼리를 crop_work_type 쿼리 바로 다음
  (2순위)에 삽입. reason: `memo_text`.
- `take(5)` 상한은 유지 — 2순위 삽입으로 컷에서 살아남는다.
- memo가 blank면 추가하지 않는다.
- 테스트: 메모 유/무, 120자 초과 절단, 쿼리 순서 검증.

### 4. 검증기 허점 봉합 + audit 실효화 (sanitize)

- `CoachingStructuredOutputValidator`: 무인용 권고/행동 경고 조건에서
  `result.riskLevel != CoachingRiskLevel.UNKNOWN` 조건 제거 —
  riskLevel과 무관하게 항상 경고.
- `TodayRecordFeedbackService`: audit이 FAIL이면 결과를 그대로
  반환하지 않고 다음 규칙으로 정화(sanitize) 후 재검증한다.
  - 무인용(citationIds 비어 있음) recommendation/nextAction 제거.
  - 허용 목록 밖 citationId를 참조하는 항목: 해당 citationId만
    제거하되, 제거 후 인용이 비면 항목도 제거. `citations` 목록에서
    미허용 chunkId 항목 제거.
  - `limitations`에 "일부 조언이 근거 검증을 통과하지 못해
    제외되었습니다." 추가.
  - 정화 후 audit을 다시 계산해 반환한다. invalid_confidence처럼
    정화로 해소되지 않는 FAIL은 그대로 FAIL로 반환한다(응답 자체는
    유지 — 프론트 처리 계약은 비범위).
- 정화 로직은 `rag/common`의 별도 컴포넌트
  (`CoachingStructuredResultSanitizer`)로 두어 record/chat 양쪽에서
  재사용 가능하게 한다 (chat 적용은 이번 범위 밖).
- 테스트: UNKNOWN + 무인용 권고 → 경고 발생, FAIL → 항목 제거 후
  WARN/PASS 강등, 정화 불가 FAIL 유지.

### 5. 색인 메타데이터 보강 + 작물 필터

#### 5-a. 시드 측 (로컬 전용 — 커밋하지 않음)

`DevRagSeedService`에서 위 메타데이터 계약을 채운다.

manifest 연동:

- seedRoot의 `manifest.csv`(존재 시)를 읽어 `id`(= 파일명에서 확장자
  제거 = sourceId) 기준으로 행을 매칭한다.
- manifest에 `crop_names` 열을 추가한다. 단일 작물 전용 문서만 작물명
  기입, 다작물/일반 문서는 빈 값 → 메타데이터 `cropName=GENERAL`.
- 청크 메타데이터에 `cropName`, `publisher`, `year`, `page` 기록.
  `documentTitle`은 manifest의 `title`이 있으면 그것을 사용(현재
  파일명 유래 문자열 대체).
- manifest가 없거나 행이 매칭되지 않으면 기존 파일명 유래 값 +
  `cropName=GENERAL`로 동작(하위 호환).
- manifest 파싱 실패(형식 오류)는 시드 요청 실패로 처리해 조용한
  메타데이터 누락을 방지한다.

page 추적:

- pdftotext 출력은 페이지 경계를 `\f`로 구분한다. `chunkText()`의
  정규화가 `\f`를 공백으로 치환해 정보가 소실되므로, 청킹 전에
  `\f` 기준으로 페이지를 분리하고 (페이지 번호, 단락) 단위로 청킹한다.
- 청크의 `page`는 청크에 포함된 첫 단락의 페이지 번호(1-base).
  청크가 페이지 경계를 넘으면 시작 페이지를 기록한다.
- `PdfTextExtractor` 인터페이스는 변경하지 않는다.

이 작업의 테스트(`DevRagSeedServiceTest`)도 로컬 전용이며 CI에서
실행되지 않는다. 로컬에서 `./gradlew test`로 검증한다.

#### 5-b. 검색 측 (커밋 대상)

- `TodayRecordFeedbackService.retrieveDocuments()`의 filterExpression을
  `sourceType == 'TECH_DOCUMENT' && cropName in ['{작물명}', 'GENERAL']`
  로 확장. 작물명은 `context.crop.name.trim()`.
- 로컬 시드는 seedName 기준 전체 삭제 후 재적재이므로 구 메타데이터
  청크와의 혼재는 재시드로 해소된다.
- 서비스의 evidence 매핑(`toRecordFeedbackEvidence`)은 이미
  `metadata["page"]`를 읽으므로 색인만 되면 인용 page가 채워진다.

테스트(커밋 대상): 필터 표현식 검증, cropName 메타데이터 유/무
문서에 대한 evidence 매핑.

### 6. 프롬프트 누락 정보 + 소소한 수정

- `RecordFeedbackPromptBuilder.formatContext()`에 추가 출력:
  - 회원: 영농 경력(experienceLevel), 경영 형태(managementType) —
    null이면 "미상".
  - 당일 날씨에 최저기온(minTemperatureC).
  - 작업 통계에 마지막 작업일(lastWorkedOnByType).
- `formatForecast()`에 최저기온 추가 (서리·저온 리스크 신호).
- `RecordFeedbackRetrievalQueryPlanner.weatherRiskQuery()` 분기 수정:
  - 과습 검사(강수량 ≥ 30.0)를 건조·고온 검사보다 먼저 평가.
  - `rainfall <= 5.0` 단독 조건이 "당일 강수 0mm인 평범한 날"에도
    건조 쿼리를 만드는 문제 제거 — 건조 판단은 dryDays/hotDays/maxTemp
    기준으로만 하고, 강수량 부족은 recent7Days.rainfallMm가 존재할
    때만 신호로 사용한다.
- `TodayRecordFeedbackService`: 구조화 출력(`entity()`) 실패 시 1회
  재시도 후 실패면 기존과 동일하게
  `RAG_STRUCTURED_OUTPUT_INVALID`.
- `CoachingStructuredResult.insufficientEvidence` 호출부의 사용자
  노출 문구를 농민 톤으로 교체: summary/diagnosis "아직 이 작물에
  대한 참고 자료가 부족해 오늘 기록만으로는 조언을 드리기 어려워요.
  기록은 정상적으로 저장됐어요.", limitations는 내부 톤 유지.
- 테스트: 프롬프트 출력 스냅샷 갱신, 분기 경계값, 재시도 1회 검증.

## 오류 처리

- 재시도 후에도 실패 시 기존 `BusinessException(RAG_STRUCTURED_OUTPUT_INVALID)`
  유지 — 에러 메시지 리소스 정비는 응답 계약 작업(비범위)과 함께.

## 테스트 전략

- TDD로 진행. 컴포넌트 단위는 `application/src/test`의 기존 테스트
  파일에 추가·수정, 서비스 흐름(threshold 전달, sanitize, 재시도,
  필터 표현식)은 `TodayRecordFeedbackServiceTest`에.
- 완료 기준: `backend`에서 `./gradlew test` 통과 (로컬 전용 테스트
  포함 상태에서). CI 관점 검증은 로컬 전용 파일을 제외한 컴파일
  성공 여부로 갈음한다.
- 실 VectorStore smoke(`TodayRecordFeedbackVectorStoreSmokeTest`)는
  cropName 필터 추가에 맞춰 시드 재적재 후 수동 확인(문서화만).

## 커밋 계획

항목별 focused commit (Conventional Commits). 5-a(시드 측)는 로컬
전용이라 커밋 없음.

1. `fix(rag): 검색 유사도 임계값 적용`
2. `refactor(rag): 근거 이중 주입 제거`
3. `feat(rag): 메모 기반 검색 쿼리 추가`
4. `fix(rag): 무인용 권고 검증 및 감사 실패 정화 추가`
5. `feat(rag): 작물 필터 및 근거 메타데이터 계약 적용`
6. `fix(rag): 프롬프트 누락 컨텍스트 반영 및 출력 재시도`

## 리스크

- threshold 0.55는 bge-m3 임베딩 기준 실측되지 않은 값 — 시드 코퍼스
  기준 smoke로 과차단 여부 확인 후 필요 시 설정으로 조정(코드 변경
  없이 yml로 가능).
- 작물 필터는 현재 코퍼스가 사실상 다작물 문서(GENERAL) 위주라 즉시
  효과는 제한적. 단일 작물 문서가 늘어날수록 효과가 커지는 기반 작업.
- 메모 쿼리는 memo에 잡음(무관한 텍스트)이 많을 경우 검색 품질을
  떨어뜨릴 수 있음 — threshold(1번)가 방어선.
