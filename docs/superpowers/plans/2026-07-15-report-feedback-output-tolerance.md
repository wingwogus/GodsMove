# Report Feedback Output Tolerance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 리포트 코칭의 개행을 공백으로 정규화하고 모든 공개 문구의 길이를 20~65자로 통일한다.

**Architecture:** OpenClaw 구조화 응답을 파싱한 직후 `ReportFeedbackContent`가 공개 문구를 정규화한다. 생성 서비스는 정규화된 객체만 검증하고 반환하며, 검증기는 공통 20~65자 범위만 적용한다.

**Tech Stack:** Kotlin, Spring Boot, JUnit 5, AssertJ

## Global Constraints

- 리포트 코칭의 최대 길이는 65자를 유지한다.
- `summary`와 모든 섹션 `text`의 최소 길이는 20자로 통일한다.
- 개행은 실패 사유가 아니라 단일 공백으로 정규화한다.
- 구조, 항목 수, 근거 참조 검증은 유지한다.
- 기록 피드백은 변경하지 않는다.
- 새 의존성을 추가하지 않는다.

---

### Task 1: 공개 문구 정규화와 길이 통일

**Files:**
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackContent.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackGenerationService.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackOutputValidator.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackPromptBuilder.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackGenerationServiceTest.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackOutputValidatorTest.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackPromptBuilderTest.kt`

**Interfaces:**
- Consumes: `ReportFeedbackContent` returned by Spring AI structured output conversion.
- Produces: `ReportFeedbackContent.normalizedParagraphs(): ReportFeedbackContent` and a common 20~65 character validation contract.

- [ ] **Step 1: Write failing regression tests**

Add a generation-service test that supplies newlines in `summary` and every section `text`, expects one model attempt, and asserts that returned strings contain a single space instead of a newline. Update validator boundary tests so 20 characters pass for all public text and 19 characters fail. Update the prompt assertion to require 20~65 characters for all public text.

- [ ] **Step 2: Run focused tests and verify RED**

Run:

```bash
cd backend
./gradlew :application:test --tests '*ReportFeedbackGenerationServiceTest' --tests '*ReportFeedbackOutputValidatorTest' --tests '*ReportFeedbackPromptBuilderTest'
```

Expected: FAIL because newlines still produce `*_text_paragraph`, 20-character coaching items are rejected, and the prompt still advertises 25/30-character minima.

- [ ] **Step 3: Implement the minimal behavior**

Add `normalizedParagraphs()` to copy `summary` and every item `text` after replacing one or more line breaks plus adjacent whitespace with one space. Invoke it immediately after structured output conversion. Remove paragraph warnings from the validator, replace role-specific minima with one `MIN_TEXT_LENGTH = 20`, and change prompt ranges to 20~65 characters.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run the command from Step 2.

Expected: all selected tests pass.

- [ ] **Step 5: Run backend regression verification**

Run:

```bash
cd backend
./gradlew :application:test :api:test
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit without pushing**

Stage only the design, plan, implementation, and focused test files. Commit with a Korean Conventional Commit subject and Lore trailers. Do not push.
