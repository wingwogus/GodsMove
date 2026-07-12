# RAG 기록 피드백·리포트 테스트 콘솔 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 로컬 백엔드의 기록 피드백 자동 생성과 주기 리포트 통계를 한 HTML에서 호출·표시·검증한다.

**Architecture:** 이전 RAG 테스트 콘솔을 `frontend/dev-rag-test.html`로 복원하되 현재 브랜치에 없는 seed 기능은 제거한다. 외부 의존성 없는 HTML/CSS/JavaScript에서 인증, API 호출, 피드백 폴링, 리포트 작업별 렌더링과 상태 미리보기를 담당한다.

**Tech Stack:** HTML5, CSS, vanilla JavaScript, browser Fetch API, Playwright CLI

## Global Constraints

- 외부 프론트엔드 라이브러리와 새 빌드 의존성을 추가하지 않는다.
- 실제 API 모드와 상태 미리보기 모드를 명확히 구분한다.
- 리포트 코칭은 아직 구현되지 않았으므로 가짜 코칭 결과를 표시하지 않는다.
- READY 사용자 결과에는 `text`, `due`, `category`만 표시한다.
- 로컬 서버는 `http://127.0.0.1:5173`을 사용한다.
- 토큰은 `sessionStorage`에만 저장하고 원본 응답에서 마스킹한다.

---

### Task 1: 단일 HTML 테스트 콘솔 복원 및 확장

**Files:**
- Create: `frontend/dev-rag-test.html`

**Interfaces:**
- Consumes: 인증 API, 영농기록 API, 기록 피드백 API, 주기 리포트 API
- Produces: `request`, `renderFeedback`, `pollFeedback`, `renderReportSnapshot`, `renderRawResponse`

- [ ] **Step 1: 이전 콘솔 구조를 복원하고 현재 API 탭을 정의한다**

`6d120cdd:frontend/dev-rag-test.html`의 연결·인증·원본 응답 구조를 가져오고
다음 DOM ID를 가진 작업면으로 교체한다.

```html
<nav aria-label="테스트 영역">
  <button data-view="feedback" aria-selected="true">기록 피드백</button>
  <button data-view="report" aria-selected="false">리포트 통계</button>
</nav>
<section id="feedbackView"></section>
<section id="reportView" hidden></section>
<pre id="rawOutput">{}</pre>
```

- [ ] **Step 2: 기록 저장과 피드백 상태 전이를 구현한다**

```javascript
async function loadFeedback(recordId) {
  const response = await request(`/api/v1/farming-records/${recordId}/coaching-feedback`);
  renderFeedback(response.data);
  return response.data;
}

async function pollFeedback(recordId) {
  stopPolling();
  let attempts = 0;
  state.pollTimer = window.setInterval(async () => {
    const feedback = await loadFeedback(recordId);
    attempts += 1;
    if (["READY", "FAILED", "STALE"].includes(feedback.status) || attempts >= 60) stopPolling();
  }, 2000);
}
```

기록 생성 성공 시 `response.data.id`를 record ID 입력에 반영하고 즉시
`loadFeedback` 후 `pollFeedback`을 실행한다. 재생성 버튼은 FAILED에서만
활성화한다.

- [ ] **Step 3: 상태 미리보기와 공개 결과 렌더링을 구현한다**

```javascript
function renderFeedback(feedback) {
  statusBadge.textContent = feedback.status;
  regenerateButton.disabled = feedback.status !== "FAILED";
  goodPoint.textContent = feedback.feedback?.goodPoint?.text || "아직 생성된 피드백이 없습니다.";
  actions.replaceChildren(...(feedback.feedback?.nextActions || []).map(renderAction));
}
```

미리보기 fixture는 PENDING, READY, FAILED, STALE 네 개만 두고 READY fixture는
잘한 점 1개와 다음 행동 2개를 가진다.

- [ ] **Step 4: 리포트 현재·목록·상세와 작업별 통계를 구현한다**

```javascript
async function loadCurrentReport() {
  const query = new URLSearchParams({ farmId: value("farmId"), cropId: value("cropId") });
  const response = await request(`/api/v1/farming-reports/current?${query}`);
  state.report = response.data;
  renderReportSnapshot(response.data.current, response.data.previous);
}
```

작업 유형은 `planting`, `watering`, `fertilizing`, `pestControl`, `weeding`,
`pruning`, `harvest`, `etc` 순서로 고정한다. 공통 통계는 각 작업 객체에서
직접 읽고, 나머지 배열·합계는 키/값 행으로 렌더링한다.

- [ ] **Step 5: 정적 검증을 실행한다**

Run:

```bash
rg -n "dev/rag/seed|basis|evidenceRefs|modelName|auditStatus" frontend/dev-rag-test.html
```

Expected: seed 호출은 없고, 내부 필드는 사용자 피드백 렌더링 코드에 없다.

- [ ] **Step 6: 커밋한다**

```bash
git add frontend/dev-rag-test.html
git commit -m "feat(rag): 기록 피드백과 리포트 테스트 콘솔 추가"
```

---

### Task 2: 브라우저 기능·반응형 검증

**Files:**
- Modify: `frontend/dev-rag-test.html` only when verification finds a defect

**Interfaces:**
- Consumes: Task 1 HTML
- Produces: 데스크톱·모바일에서 검증된 테스트 콘솔

- [ ] **Step 1: 로컬 서버를 실행한다**

```bash
python3 -m http.server 5173 --bind 127.0.0.1 --directory frontend
```

Expected: `http://127.0.0.1:5173/dev-rag-test.html` returns 200.

- [ ] **Step 2: Playwright로 데스크톱 흐름을 검증한다**

`1440x900`에서 기록 피드백 탭, READY 미리보기, 리포트 탭을 차례로 열고
snapshot과 screenshot을 확인한다. 버튼·텍스트·원본 인스펙터가 겹치지 않아야 한다.

- [ ] **Step 3: Playwright로 모바일 흐름을 검증한다**

`390x844`에서 같은 흐름을 실행한다. 가로 스크롤이 없어야 하고 모든 버튼 문구가
잘리지 않아야 한다.

- [ ] **Step 4: 최종 정적 검증을 실행한다**

```bash
git diff --check
git status --short
```

Expected: whitespace 오류 없음, 검증 산출물은 git 변경 목록에 없음.

- [ ] **Step 5: 검증 수정이 있으면 커밋한다**

```bash
git add frontend/dev-rag-test.html
git commit -m "fix(rag): 테스트 콘솔 반응형 동작 보정"
```
