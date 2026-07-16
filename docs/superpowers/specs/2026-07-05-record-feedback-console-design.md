# 기록 피드백 테스트 콘솔 설계 (로컬 전용)

- 날짜: 2026-07-05
- 상태: 승인됨
- 산출물: `local-tools/record-feedback-console.html` — **로컬 전용, 커밋하지 않음**
  (`.gitignore`의 `local-tools/` 등록만 커밋)

## 목적

local 프로필의 `POST /api/v1/dev/coaching/record-feedback`
(permitAll, body = `TodayRecordFeedbackContext`)를 브라우저에서 호출해
(1) 컨텍스트를 빠르게 조정하며 실험하고 (2) 코칭 결과를 한눈에
파악하는 개발용 콘솔. 삭제된 구 `frontend/dev-rag-test.html`의 대체.

## 형태·실행

- 의존성 없는 자기완결 단일 HTML (vanilla JS/CSS).
- 실행: 백엔드 `./gradlew :api:bootRun` +
  `python3 -m http.server 5173 --directory local-tools`.
  `http://localhost:5173`은 local CORS 허용 목록에 이미 포함.
- API base URL은 상단 입력란(기본 `http://localhost:8080`)으로 변경 가능.

## 구성

### 좌측 — 컨텍스트 편집 (하이브리드)

- 프리셋 5종 내장(테스트 픽스처 복사본): 물주기/방제/수확/시비/무주기.
- 폼 필드: 작물명·약용부위(셀렉트)·작업유형(셀렉트 8종)·메모·
  재배일차(주기 없음 토글)·기록일·targetRecord.fields(미니 JSON)·
  사진 유무/수·당일 날씨(평균/최고/최저/강수/습도)·최근 7일(강수/
  고온일수/건조일수)·예보(일 단위 행 추가/삭제, riskFlags 콤마 입력).
- 접이식 "전체 JSON" 에디터: 폼 변경 → JSON 자동 갱신, JSON 수정 →
  [JSON 적용] 버튼으로 폼 반영(파싱 실패 시 인라인 에러).
- 단일 진실 원천은 내부 context 객체 — 폼과 JSON 모두 이를 렌더링.

### 우측 — 결과 대시보드

- 헤더: riskLevel 색 배지(LOW 초록/MEDIUM 주황/HIGH 빨강/UNKNOWN 회색),
  confidence 게이지, audit 배지(PASS/WARN/FAIL)+warnings 원문,
  contextWarnings, 소요시간(ms).
- 본문 순서: summary(크게) → diagnosis → 관찰 카드 → 권고 카드
  (priority 색 테두리, action/reason/caution, citationLabels 칩) →
  다음 행동(due 배지 체크리스트) → 후속질문 → recordQuality(score
  배지+missingOrWeakFields) → limitations → 인용 목록(displayLabel,
  chunkId 툴팁, sourceType).
- 인용 칩 클릭 → 인용 목록 해당 항목 하이라이트.
- 에러: fetch 실패(연결/CORS)와 `ApiResponse.error{code,message}` 구분 표시.

### 응답 계약 (렌더링 기준)

`ApiResponse{success, data, error}` / data =
`{result: StructuredResultResponse, audit{status,warnings},
model{embedding,chat}, contextWarnings}`.
StructuredResultResponse의 observations/recommendations/nextActions는
`citationLabels`("근거 N: 문서명")를 포함 — 칩은 이 값을 그대로 사용.

### 히스토리

- localStorage(`ccc-record-feedback-history`) 최근 20건:
  시각, 작물+작업유형, riskLevel, 소요시간.
- 클릭 시 당시 컨텍스트+응답 복원, [다시 호출]로 재실행.

## 테스트 절차 (이 콘솔의 사용 시나리오)

1. 로컬 시드 재실행(cropName 메타데이터 반영 필수) 후 콘솔 접속.
2. 프리셋 5종 호출 — 작업유형별 코칭 타당성 + audit PASS 확인.
3. 실험: 메모에 병징 추가 → 방제 근거 검색 확인 / 날씨 수치 조작 →
   과습·건조 분기 확인 / 미존재 작물명 → insufficientEvidence 폴백
   + 농민 톤 문구 확인.
4. audit WARN(`sanitized_output`) 발생 케이스 관찰 → 프롬프트 개선 힌트.

## 비범위

- 자동화 테스트(로컬 수동 도구), A/B 비교, raw JSON 뷰어, 인증 흐름,
  프로덕션 사용.
