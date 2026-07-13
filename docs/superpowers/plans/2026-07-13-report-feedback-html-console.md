# 리포트 피드백 HTML 콘솔 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 로컬 HTML 콘솔에서 선택한 완료 리포트의 피드백을 자동 조회하고 `PENDING`, `READY`, `FAILED` 상태와 공개 코칭 내용을 확인한다.

**Architecture:** 기존 리포트 통계 탭 아래에 피드백 카드를 추가하고, `renderReport()`가 확정한 선택 스냅샷을 단일 동기화 함수에 전달한다. 리포트 피드백은 기록 피드백과 별도 timer/run ID 상태를 사용하며 리포트가 바뀌면 이전 요청 결과를 폐기한다.

**Tech Stack:** HTML5, CSS, Vanilla JavaScript, Fetch API, Node.js 정적 검증

## Global Constraints

- `frontend/dev-rag-test.html`은 `.gitignore`된 로컬 개발 파일로 유지한다.
- 백엔드, iOS, API DTO, 공개 API 계약은 변경하지 않는다.
- 새 프레임워크나 의존성을 추가하지 않는다.
- 리포트 피드백 재생성 기능을 추가하지 않는다.
- 기록 피드백과 리포트 피드백의 폴링 상태를 공유하지 않는다.
- `application-local.yml`과 `.claude/`는 수정하거나 스테이징하지 않는다.

---

### Task 1: 리포트 피드백 카드와 상태 렌더링

**Files:**
- Modify local-only: `frontend/dev-rag-test.html`
- Reference: `backend/api/src/main/kotlin/com/chamchamcham/api/coaching/reportfeedback/dto/ReportFeedbackResponses.kt`

**Interfaces:**
- Consumes: `snapshot.id`, `snapshot.status`, `ReportFeedbackResponses.StatusResponse`
- Produces: `renderReportFeedback(feedback)`, `renderReportFeedbackUnavailable(status, message)`, `renderReportFeedbackItems(containerId, items, emptyMessage)`

- [ ] **Step 1: Run the structural assertion before implementation**

Run:

```bash
node -e 'const fs=require("fs");const h=fs.readFileSync("frontend/dev-rag-test.html","utf8");for(const token of ["reportFeedbackStatusBadge","loadReportFeedback","reportFeedbackSummary","reportFeedbackStrengths","reportFeedbackImprovements","reportFeedbackNextActions"]){if(!h.includes(token))throw new Error(`missing ${token}`)}'
```

Expected: FAIL with `missing reportFeedbackStatusBadge`.

- [ ] **Step 2: Add the card markup below `reportDashboard`**

Add the following structure inside `reportSnapshotContent` after the statistics dashboard:

```html
<div id="reportFeedbackCard" class="feedback-result">
  <div class="report-title">
    <div class="stack compact">
      <h3>리포트 피드백</h3>
      <p class="subtle">선택한 완료 리포트의 요약과 다음 주기 코칭을 확인합니다.</p>
    </div>
    <span id="reportFeedbackStatusBadge" class="badge">선택 없음</span>
  </div>
  <div class="button-row">
    <button id="loadReportFeedback" class="secondary" type="button" disabled>피드백 새로고침</button>
    <button id="stopReportFeedbackPolling" class="secondary" type="button" disabled>폴링 중지</button>
  </div>
  <div id="reportFeedbackError" class="polling-error" role="alert" hidden></div>
  <div class="metrics" aria-live="polite">
    <div class="metric"><strong id="reportFeedbackInputPrepared">-</strong><span>Input prepared</span></div>
    <div class="metric"><strong id="reportFeedbackFailureCode">-</strong><span>Failure code</span></div>
    <div class="metric"><strong id="reportFeedbackPollAttempts">0</strong><span>Poll attempts / 60</span></div>
  </div>
  <div class="report-section">
    <h3>요약</h3>
    <div id="reportFeedbackSummary" class="good-point">선택한 완료 리포트가 없습니다.</div>
  </div>
  <div class="report-section"><h3>잘한 점</h3><ul id="reportFeedbackStrengths" class="actions"></ul></div>
  <div class="report-section"><h3>개선점</h3><ul id="reportFeedbackImprovements" class="actions"></ul></div>
  <div class="report-section"><h3>다음 주기 행동</h3><ul id="reportFeedbackNextActions" class="actions"></ul></div>
</div>
```

- [ ] **Step 3: Add report feedback rendering functions**

Implement item rendering with text-only DOM assignment, never `innerHTML` for API content:

```javascript
function renderReportFeedbackItems(containerId, items, emptyMessage) {
  const container = $(containerId);
  const values = Array.isArray(items) ? items : [];
  if (values.length === 0) {
    const empty = document.createElement("li");
    empty.className = "empty";
    empty.textContent = emptyMessage;
    container.replaceChildren(empty);
    return;
  }
  container.replaceChildren(...values.map((item, index) => {
    const row = document.createElement("li");
    row.className = "action";
    const number = document.createElement("span");
    number.className = "mini";
    number.textContent = String(index + 1);
    const text = document.createElement("div");
    text.textContent = item?.text || "-";
    row.append(number, text);
    return row;
  }));
}
```

`renderReportFeedback(feedback)` must update the badge, metrics, summary and all three lists. `renderReportFeedbackUnavailable(status, message)` must reset metrics/lists and disable controls for selection-none and active-report states.

- [ ] **Step 4: Re-run the structural assertion**

Run the Step 1 command.

Expected: PASS with exit code 0.

### Task 2: 선택 리포트 자동 조회와 독립 폴링

**Files:**
- Modify local-only: `frontend/dev-rag-test.html`

**Interfaces:**
- Consumes: `snapshotForScope()`, `request(path)`, terminal statuses `READY` and `FAILED`
- Produces: `fetchReportFeedback(reportId)`, `startReportFeedbackPolling(reportId)`, `stopReportFeedbackPolling()`, `syncReportFeedback(snapshot)`

- [ ] **Step 1: Run the polling assertion before implementation**

Run:

```bash
node -e 'const fs=require("fs");const h=fs.readFileSync("frontend/dev-rag-test.html","utf8");for(const token of ["reportFeedbackTimer","reportFeedbackRunId","startReportFeedbackPolling","stopReportFeedbackPolling","syncReportFeedback","/farming-reports/${encodeURIComponent(reportId)}/feedback"]){if(!h.includes(token))throw new Error(`missing ${token}`)}'
```

Expected: FAIL with `missing reportFeedbackTimer`.

- [ ] **Step 2: Add isolated state**

Extend `state` with:

```javascript
reportFeedbackTimer: null,
reportFeedbackRunId: 0,
reportFeedbackReportId: null,
reportFeedbackStatus: null,
reportFeedbackPollAttempts: 0,
```

- [ ] **Step 3: Implement request and polling functions**

Use the current endpoint and a per-run guard:

```javascript
async function fetchReportFeedback(reportId) {
  const response = await request(`/api/v1/farming-reports/${encodeURIComponent(reportId)}/feedback`);
  return response.data;
}

function stopReportFeedbackPolling() {
  state.reportFeedbackRunId += 1;
  if (state.reportFeedbackTimer) {
    window.clearTimeout(state.reportFeedbackTimer);
    state.reportFeedbackTimer = null;
  }
  $("stopReportFeedbackPolling").disabled = true;
}
```

`startReportFeedbackPolling(reportId)` must:

- stop the previous run;
- store `reportId`, reset attempts and clear the card error;
- request immediately;
- ignore responses whose run ID or report ID is stale;
- render every response;
- schedule the next request after 2,000 ms only for `PENDING`;
- stop at `READY`, `FAILED`, an HTTP error, or 60 attempts;
- leave manual refresh enabled for the selected completed report.

- [ ] **Step 4: Connect polling to report selection**

Implement:

```javascript
function syncReportFeedback(snapshot) {
  if (!snapshot) {
    stopReportFeedbackPolling();
    state.reportFeedbackReportId = null;
    renderReportFeedbackUnavailable("선택 없음", "현재·직전·상세 완료 리포트를 선택하세요.");
    return;
  }
  if (snapshot.status !== "COMPLETED") {
    stopReportFeedbackPolling();
    state.reportFeedbackReportId = snapshot.id;
    renderReportFeedbackUnavailable("완료 전", "리포트가 완료되면 피드백이 자동 생성됩니다.");
    return;
  }
  if (state.reportFeedbackReportId !== snapshot.id) {
    startReportFeedbackPolling(snapshot.id);
  }
}
```

Call `syncReportFeedback(null)` in the empty branch of `renderReport()` and `syncReportFeedback(selected)` after rendering a selected snapshot. This keeps current/previous/detail and work-type tab changes on one synchronization path.

- [ ] **Step 5: Wire manual controls**

In `initialize()`:

```javascript
$("loadReportFeedback").addEventListener("click", (event) => run(event.currentTarget, async () => {
  const snapshot = snapshotForScope();
  if (!snapshot || snapshot.status !== "COMPLETED") {
    throw new Error("완료된 리포트를 선택하세요.");
  }
  await startReportFeedbackPolling(snapshot.id);
}));
$("stopReportFeedbackPolling").addEventListener("click", stopReportFeedbackPolling);
```

- [ ] **Step 6: Re-run the polling assertion**

Run the Step 1 command.

Expected: PASS with exit code 0.

### Task 3: 기록 피드백 경로 교정과 정적 검증

**Files:**
- Modify local-only: `frontend/dev-rag-test.html`

**Interfaces:**
- Produces current record endpoints `/feedback` and `/feedback/regenerate`

- [ ] **Step 1: Verify the stale endpoint exists**

Run:

```bash
rg -n '/coaching-feedback' frontend/dev-rag-test.html
```

Expected: two matches in record feedback GET and regeneration calls.

- [ ] **Step 2: Replace only the two stale endpoint suffixes**

Use:

```javascript
`/api/v1/farming-records/${encodeURIComponent(recordId)}/feedback`
`/api/v1/farming-records/${encodeURIComponent(recordId)}/feedback/regenerate`
```

- [ ] **Step 3: Validate JavaScript syntax**

Run:

```bash
node -e 'const fs=require("fs");const h=fs.readFileSync("frontend/dev-rag-test.html","utf8");const scripts=[...h.matchAll(/<script>([\s\S]*?)<\/script>/g)];if(scripts.length!==1)throw new Error(`expected one inline script, got ${scripts.length}`);new Function(scripts[0][1]);'
```

Expected: PASS with exit code 0.

- [ ] **Step 4: Validate unique IDs and endpoint ownership**

Run:

```bash
node -e 'const fs=require("fs");const h=fs.readFileSync("frontend/dev-rag-test.html","utf8");const ids=[...h.matchAll(/\bid="([^"]+)"/g)].map(x=>x[1]);const duplicates=ids.filter((id,i)=>ids.indexOf(id)!==i);if(duplicates.length)throw new Error(`duplicate ids: ${[...new Set(duplicates)].join(", ")}`);'
rg -n '/coaching-feedback' frontend/dev-rag-test.html
rg -n '/api/v1/farming-(records|reports)/.*feedback' frontend/dev-rag-test.html
git check-ignore -v frontend/dev-rag-test.html
```

Expected: JavaScript assertion passes, stale endpoint search has no results, current endpoint search shows record GET/regenerate and report GET, and Git still reports `.gitignore` ownership.

- [ ] **Step 5: Preserve repository cleanliness**

Run:

```bash
git status --short
```

Expected: only the pre-existing `application-local.yml`, `.claude/`, and the tracked plan document before its commit; `frontend/dev-rag-test.html` remains absent because it is ignored.

- [ ] **Step 6: Confirm the local-only handoff**

Record in the final report that `frontend/dev-rag-test.html` was updated and verified locally but intentionally was not staged or committed. Include the exact validation commands and disclose when live backend integration was not exercised.

## Plan self-review

- Spec coverage: card UI, automatic completed-report lookup, independent polling, cancellation, 60-attempt limit, manual controls, state rendering, current record endpoint correction, ignored-file ownership, and verification all have explicit steps.
- Placeholder scan: no incomplete implementation placeholders remain.
- Type consistency: markup IDs match JavaScript selectors; state and function names are consistent across tasks; response fields match `ReportFeedbackResponses.StatusResponse`.
- Scope: no backend, iOS, dependency, regeneration, shared polling abstraction, or Git tracking change is included.
