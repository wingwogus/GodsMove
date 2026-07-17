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

## ✅ RESOLVED (2026-07-18, later same day) — filter multi-select work merged

The warning below was accurate when first written but is now **stale**. The
separate session's filter/multi-select work merged into `dev` via
`05f3a87e fix(report): 영농 활동 필터 복수선택 지원 및 바텀시트 디자인 통일`
(plus `8189be74 refactor(design-system): 필터 바텀시트 스캐폴드를
AppFilterSheetScaffold로 승격` and `9835f6fa fix(record): 기록 탭 상단 필터 칩
selected 상태 미반영 수정`). The files listed originally are no longer
uncommitted WIP — check `git log` on `dev` before assuming otherwise, but as
of this update there's nothing blocking there. Original warning kept below for
history.

<details>
<summary>Original warning (now resolved)</summary>

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

</details>

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

- **This worktree** (`worktree-fix+report-list-design-front`):
  - `Color.Chart.turquoise` fix (`dfd3d344`)
  - all three original files under `docs/figma/report/` (`cf99480a`)
  - `3aa1457b fix(report): 심기 리포트 상세 화면 아이콘/타이포/컬러 피그마 스펙
    정합` — fixes findings 1–5 from
    [2026-07-18-report-detail-planting.md](2026-07-18-report-detail-planting.md):
    `ReportDetailView.swift` (back icon → `arrow_back_ios_new`, history-row
    icon → `arrow_forward_ios`, WorkType title → `.headlineMedium`) and
    `ReportMetricCard.swift` (label → `.labelMediumEmphasized`, value color →
    `Color.Text.subtle`). Build verified with
    `xcodebuild -scheme ChamChamCham -destination 'platform=iOS Simulator,name=iPhone 17' build`
    → `BUILD SUCCEEDED` (note: this worktree didn't have a local
    `Core/Config/Secrets.swift` — gitignored — copied from the main checkout
    to unblock the build; not committed).
- **`dev`** (main checkout): the `ReportChartCard.swift` `#Preview` block
  addition (`a2019a69`), plus (unrelated to this handoff, landed later the
  same day) the filter/multi-select work — see the resolved-warning section
  above.

## Remaining work / suggested next steps

1. ~~Decide fix vs. defer for the 7 open findings in the planting doc~~ —
   **items 1–5 fixed** (see commit `3aa1457b` above). Still open:
   - **Finding 6** (date-range separator: Detail screen's Figma shows `-`,
     code uses `~`) — open question, needs designer confirmation on whether
     List (`~`) and Detail (`-`) are intentionally different before touching.
   - **Finding 7** (inline record-row preview in "기록 내역 리스트") — real
     product-scope gap, not a style fix; `ReportRecordHistoryView` is a
     placeholder. Needs a real decision/API, not a quick patch.
   - The 2 open findings in
     [2026-07-18-report-detail-chart-spec.md](2026-07-18-report-detail-chart-spec.md)
     (legend label/value styling swapped; donut center label should stay
     visible when expanded) are still unfixed — confirm with the user before
     touching `ReportChartCard.swift`/`ReportChartModel`, since both chart
     styles share one `highlightedEntry(isExpanded:)` codepath.
2. Capture the remaining Report Detail workType variants one at a time
   (물주기/비료 주기/병해충 관리/잡초 관리/가지·순 정리/수확/기타) — user's
   explicit plan: "화면 하나씩 캡처하고 바로 구현" (capture one screen, then
   implement immediately), except chart UI/UX, which gets documented
   separately before implementing (already mostly done via the chart-spec
   doc). Header/badges/metrics/coaching/history layout is shared across
   workTypes per this capture — likely only the metric fields and chart data
   shape differ per type; confirm against each capture rather than assuming.
   Since findings 1–5 were structural (icons/typography/color on the shared
   detail-screen chrome), they should already apply to every workType capture
   going forward — no need to re-flag them per workType.
3. `ReportCoachingSection.swift` (AI coaching cards) has not been compared
   against Figma yet — the 심기 capture shows 4 example coaching cards
   (잘한 점/이전 리포트과의 비교/개선 필요점/추천 행동) worth checking once a
   real coaching-populated capture is available.

## Resume prompt for a new session

```text
리포트(Report) 탭 Figma 캡처 대조 작업을 이어가자.
프로젝트: /Users/user/Project/ChamChamCham/GodsMove
워크트리: /Users/user/Project/ChamChamCham/GodsMove/.claude/worktrees/fix+report-list-design-front
(EnterWorktree path로 재진입)

먼저 docs/figma/report/HANDOFF.md 를 읽고, 이미 캡처된
2026-07-18-report-detail-chart-spec.md / 2026-07-18-report-detail-planting.md
문서도 읽어줘. TalkToFigma 연결 절차는 docs/figma/record/HANDOFF.md Part 1과 동일해.

지금까지: Report List View는 검토 완료(대부분 일치, 필터 칩 다중선택 건은 이미
dev에 병합돼 해결됨). 그래프 포맷 스펙 캡처 완료(turquoise 색상 버그는 수정함,
범례 스타일 버그·도넛 펼침 시 중앙 라벨 숨김 버그는 아직 미수정). 심기
(Planting) 리포트 상세 화면 전체 캡처 완료, 7개 불일치 중 1~5번(아이콘 애셋명/
WorkType 타이틀 폰트/메트릭 카드 라벨·값 스타일)은 커밋 3aa1457b로 수정 완료.
6번(날짜 구분자 `-` vs `~`)은 디자이너 확인 필요한 열린 질문, 7번(기록 내역
인라인 프리뷰)은 별도 기능 구현이 필요한 제품 스코프 갭으로 둘 다 미수정.

다음 단계: 차트 스펙의 2개 미해결 항목(범례 스타일, 도넛 라벨) 수정 여부를
사용자에게 확인하거나, 바로 나머지 workType(물주기/비료 주기/병해충 관리/잡초
관리/가지·순 정리/수확/기타) 캡처를 하나씩 이어가면 돼. 이 워크트리엔
Core/Config/Secrets.swift가 없으니(gitignore) 빌드 확인이 필요하면 메인
체크아웃에서 복사해와.
```
