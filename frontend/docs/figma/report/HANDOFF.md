#Report (리포트) Figma Capture and Fix Handoff

Handoff for continuing the Report tab's Figma-vs-implementation design pass in
a **new** Claude session (this one is stopping early due to token budget).
TalkToFigma connection steps are identical to
[`../record/HANDOFF.md`](../record/HANDOFF.md) Part 1 — reuse that verbatim,
don't re-derive it.

## Where this work lives

- Repository: `/Users/user/Project/ChamChamCham/GodsMove`
- This session ran inside a **git worktree**:
  `/Users/user/Project/ChamChamCham/GodsMove/.claude/worktrees/fix+report-list-design-front`,
  branch `worktree-fix+report-list-design-front`, based on `dev`.
- One small unrelated dev-tooling change (see below) was made directly on
  `dev` (the original checkout at `/Users/user/Project/ChamChamCham/GodsMove`),
  **not** in this worktree, per explicit user instruction. Know that these two
  branches now each have one committed change from this session — check both
  `git log` on `dev` and on this worktree branch when picking up context.

## ⚠️ Do not touch these files without checking with the user first

As of 2026-07-18, the `dev` working tree (main checkout, not this worktree)
has substantial **uncommitted, in-progress work from a separate session**
touching the Report filter/multi-select area and neighboring features:

```
ChamChamCham/ChamChamCham/Features/Community/Presentation/ViewModels/CommunityComposeViewModel.swift
ChamChamCham/ChamChamCham/Features/Community/Presentation/ViewModels/CommunityFeedViewModel.swift
ChamChamCham/ChamChamCham/Features/Community/Presentation/Views/CommunityComposeView.swift
ChamChamCham/ChamChamCham/Features/Community/Presentation/Views/CommunityView.swift
ChamChamCham/ChamChamCham/Features/Community/Presentation/Views/CropPickerView.swift
ChamChamCham/ChamChamCham/Features/MyPage/Presentation/Views/FarmAddView.swift
ChamChamCham/ChamChamCham/Features/Record/Presentation/Views/RecordFilterSheets.swift
ChamChamCham/ChamChamCham/Features/Record/Presentation/Views/RecordListView.swift
ChamChamCham/ChamChamCham/Features/Report/Data/ReportCache.swift
ChamChamCham/ChamChamCham/Features/Report/Data/ReportEndpoint.swift
ChamChamCham/ChamChamCham/Features/Report/Data/ReportRepository.swift
ChamChamCham/ChamChamCham/Features/Report/Domain/FarmingWorkReportModels.swift
ChamChamCham/ChamChamCham/Features/Report/Presentation/Models/ReportListPresentation.swift
ChamChamCham/ChamChamCham/Features/Report/Presentation/ViewModels/ReportListViewModel.swift
ChamChamCham/ChamChamCham/Features/Report/Presentation/Views/ReportFilterSheets.swift
ChamChamCham/ChamChamCham/Features/Report/Presentation/Views/ReportListView.swift
ChamChamCham/ChamChamChamTests/ReportCacheTests.swift
ChamChamCham/ChamChamChamTests/ReportEndpointTests.swift
ChamChamCham/ChamChamChamTests/ReportListPresentationTests.swift
ChamChamCham/ChamChamChamTests/ReportRepositoryTests.swift
ChamChamCham/ChamChamCham/Core/DesignSystem/Components/AppFilterSheetScaffold.swift (new file)
```

This is the user's own explanation for [finding 1](#capture-1) (filter chip
multi-select-count vs single-select) — "필터 칩은 현재 수정 중"
("the filter chip is currently being worked on"). **Do not edit, rebase onto,
or resolve conflicts with this in-progress work without asking the user
first** — re-check `git status`/`git diff` on `dev` before assuming it's still
there or still in the same state.

## What's done (2026-07-18)

### Report List View — capture vs implementation ✅ reviewed, mostly matches

Compared against Figma frame `리포트 / 필터 사용 완료 후` (`1547:25623`). Findings:

- `AppCard(size: .large)`, `AppTabBar` (기록/리포트 segment), `AppChip`
  (solidPastel filter chips) all match the Figma spec pixel-for-pixel — no
  action needed.
- Filter chip label shows selected-value name (single-select); Figma shows a
  selection count implying multi-select. Same class of conflict already
  tracked for Record
  ([record capture](../record/2026-07-13-record-main-record-button-tapped.md)).
  **User decision: skip — already being handled in the in-progress work listed
  above.**
- Top bar missing a notification/bell icon (`AppTopAppBar` in
  `RecordListView.swift` only wires a search icon) — noted but out of strict
  scope (shared file with Record tab), not fixed.

No separate capture doc was written for the List View pass (findings were
resolved/deferred in-conversation); this HANDOFF section is the record of it.

### Chart format & usage spec ✅ captured, 1 bug fixed, 2 bugs documented (not fixed)

[2026-07-18-report-detail-chart-spec.md](2026-07-18-report-detail-chart-spec.md) —
covers 3 Figma sections: the general stacked-bar/half-donut usage spec, the
stacked-bar expand/collapse interaction, and the donut expand/collapse
interaction.

- **Fixed** (user-authorized, committed in this worktree):
  `Color.Chart.turquoise` `0x81DAD8` → `0x81DACB` in
  [Color+App.swift](../../ChamChamCham/ChamChamCham/Core/DesignSystem/Foundation/Color+App.swift).
- **Documented, not fixed** (user said "문서화만 해줘" — document only):
  1. `ReportChartCard`'s legend rows swap label/value typography+color
     (label should be emphasized+dark, value regular+subtle — currently
     backwards). Shared code path, affects both stacked bar and donut legends.
  2. Donut center label should stay visible when the card is expanded (unlike
     the stacked bar, which correctly hides its inline text on expand);
     current code hides it for both because `ReportChartCard`/`ReportChartModel`
     share one `highlightedEntry(isExpanded:)` codepath.
- **Also done** (separate, on `dev` branch, not this worktree — see below):
  added a `#Preview` block to `ReportChartCard.swift` so both chart styles can
  be previewed without running the full app.

### Report Detail — 심기 (Planting) screen ✅ captured, 7 findings documented, none fixed

[2026-07-18-report-detail-planting.md](2026-07-18-report-detail-planting.md) —
full real screen (`1711:24746`), not just the chart section. Confirmed
matches: `AppBadge` crop/farm colors, metric-card grid layout, section-title
typography. Open findings (all documented, **none fixed yet** — this is where
a follow-up session should start deciding fix vs. defer):

1. Back icon: code uses `chevron_backward`, Figma names `arrow_back_ios_new`
   (separate asset already exists in `Assets.xcassets/icon/`).
2. "기록 내역 리스트" row icon: code uses `chevron_forward`, Figma names
   `arrow_forward_ios` (separate asset already exists).
3. WorkType title ("심기") uses `.titleLargeEmphasized` (24px) — Figma wants
   `.headlineMedium` (28px).
4. `ReportMetricCard` label uses `.labelMedium` (Medium weight) — Figma wants
   `.labelMediumEmphasized` (SemiBold).
5. `ReportMetricCard` value color uses `Color.Text.default` — Figma wants
   `Color.Text.subtle`.
6. Detail screen's date-range separator is `-` in Figma vs `~` in code (List
   screen's `~` was already confirmed correct separately) — flagged as an
   open question, not an assumed bug.
7. "기록 내역 리스트" — Figma shows 3 inline record-row previews (thumbnail +
   title + caption) directly on the Detail screen; the app currently only has
   a link to a placeholder `ReportRecordHistoryView` ("준비 중" — feature not
   built). This is a product-scope gap, not a style fix.

## What's committed where

- **This worktree** (`worktree-fix+report-list-design-front`): the
  `Color.Chart.turquoise` fix + all three files under `docs/figma/report/`
  (this HANDOFF + the two capture docs).
- **`dev`** (main checkout): the `ReportChartCard.swift` `#Preview` block
  addition only — a small, independent dev-tooling change the user asked to
  land directly on `dev` rather than in this feature worktree.

## Remaining work / suggested next steps

1. Decide fix vs. defer for the 7 open findings in
   [2026-07-18-report-detail-planting.md](2026-07-18-report-detail-planting.md)
   and the 2 open findings in
   [2026-07-18-report-detail-chart-spec.md](2026-07-18-report-detail-chart-spec.md).
   Items 1–5 in the planting doc look like straightforward, low-risk fixes
   (wrong icon asset name / wrong typography token / wrong color token) —
   confirm with the user, then fix directly (not a Foundation change, so no
   extra sign-off needed per `frontend/AGENTS.md`, unlike the turquoise hex
   which *is* a Foundation value).
2. Capture the remaining Report Detail workType variants one at a time
   (물주기/비료 주기/병해충 관리/잡초 관리/가지·순 정리/수확/기타) — user's
   explicit plan: "화면 하나씩 캡처하고 바로 구현" (capture one screen, then
   implement immediately), except chart UI/UX, which gets documented
   separately before implementing (already mostly done via the chart-spec
   doc). Header/badges/metrics/coaching/history layout is shared across
   workTypes per this capture — likely only the metric fields and chart data
   shape differ per type; confirm against each capture rather than assuming.
3. `ReportCoachingSection.swift` (AI coaching cards) has not been compared
   against Figma yet — the 심기 capture shows 4 example coaching cards
   (잘한 점/이전 리포트과의 비교/개선 필요점/추천 행동) worth checking once a
   real coaching-populated capture is available.
4. Do not touch the filter/multi-select files listed above without checking
   with the user — that work is being done in a separate, concurrent session.

## Resume prompt for a new session

```text
리포트(Report) 탭 Figma 캡처 대조 작업을 이어가자.
프로젝트: /Users/user/Project/ChamChamCham/GodsMove
워크트리: /Users/user/Project/ChamChamCham/GodsMove/.claude/worktrees/fix+report-list-design-front
(EnterWorktree path로 재진입)

먼저 docs/figma/report/HANDOFF.md 를 읽고, 이미 캡처된
2026-07-18-report-detail-chart-spec.md / 2026-07-18-report-detail-planting.md
문서도 읽어줘. TalkToFigma 연결 절차는 docs/figma/record/HANDOFF.md Part 1과 동일해.

지금까지: Report List View는 검토 완료(대부분 일치, 필터 칩 다중선택 건은 다른
세션에서 처리 중이라 스킵). 그래프 포맷 스펙 캡처 완료(turquoise 색상 버그는
수정함, 범례 스타일 버그·도넛 펼침 시 중앙 라벨 숨김 버그는 문서화만 하고
미수정). 심기(Planting) 리포트 상세 화면 전체 캡처 완료, 7개 불일치 발견,
전부 미수정 상태.

⚠️ dev 브랜치(메인 체크아웃)에는 필터/다중선택 관련 다른 세션의 진행 중인
변경사항이 있어 — HANDOFF.md의 "Do not touch these files" 목록을 반드시 먼저
확인하고, 그 파일들은 사용자 확인 없이 건드리지 마.

먼저 HANDOFF.md의 "Remaining work" 섹션에 있는 7+2개 미해결 항목 중 무엇을
지금 고칠지 사용자에게 확인한 뒤, 승인된 것만 고쳐줘. 그 다음 나머지
workType(물주기/비료 주기/병해충 관리/잡초 관리/가지·순 정리/수확/기타) 캡처를
하나씩 이어가면 돼.
```
