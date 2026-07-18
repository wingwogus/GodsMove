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

## ✅ Tier 1 remediation complete (2026-07-18, report detail full remediation plan)

Following item 9 below, a full remediation plan was written
(`/Users/user/.claude/plans/imperative-chasing-planet.md`) synthesizing all 8
capture docs (chart-spec + 7 workTypes) into Tier 1 (frontend-only, fix now),
Tier 2 (backend needed, documented only), Tier 3 (design/product decision,
documented only). **Tier 1 is implemented and committed in this worktree**:

- **1-A chart title parity**: all 6 mismatched chart titles across
  planting/watering/fertilizing/pestControl/weeding/harvest now use Figma's
  exact copy in
  [ReportPresentationModels.swift](../../ChamChamCham/ChamChamCham/Features/Report/Presentation/Models/ReportPresentationModels.swift).
  Pest control's per-unit amount chart keeps a `(unit)` suffix only when 2+
  distinct units exist (Figma mock has a single unit, so it renders without a
  suffix there — no plain title collision if a future report has multiple
  units).
- **1-B chart order**: watering and fertilizing's method/style-distribution
  chart now appends first, matching Figma (pest control was already correct;
  weeding/pruning/planting have ≤1 chart so order doesn't apply).
- **1-C legend swap**: `ReportChartCard.swift` legend rows now render label =
  `.bodyMediumEmphasized` + `Color.Text.default`, value = `.bodyMedium` +
  `Color.Text.subtle` (previously inverted).
- **1-D donut expand label**: `ReportChartModel.highlightedEntry(isExpanded:)`
  now branches on `style` — `.semiDonut` always keeps the center label,
  `.stackedBar` still hides its inline text when expanded.
- Verified with
  `xcodebuild -scheme ChamChamCham -destination 'platform=iOS Simulator,name=iPhone 17' build`
  → `BUILD SUCCEEDED`. `ReportChartCard` has no `#Preview`, so this pass relied
  on the build + a manual diff self-review rather than an in-Simulator visual
  check against real report data (no populated report was available to open in
  this session) — flagging this explicitly per the plan's verification note.
- All 8 capture docs' relevant findings were updated to "✅ Fixed" inline;
  each doc's summary section now separates fixed (Tier 1) items from
  still-open Tier 2/Tier 3 items.
- **Explicitly deferred, not fixed** (per user decision, Tier 2/3 — see the
  plan document for the full list): 심기 "진행한 심기 방식" chart (no backend
  data source), 수확 growth-period-distribution chart (no backend data
  source), 병해충 "총 살포량" L-vs-ml unit (needs backend/DTO contract check),
  물주기 "평균 물 준 양" vs mode computation (user said 보류), 병해충
  pesticide-amount metric title (unit-summation blocks a blind swap), date
  separator `-` vs `~`, inline record-row preview, and 0-value fixed-bucket
  visibility in expanded legends.

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
2. **물주기 (Watering) capture done** —
   [2026-07-18-report-detail-watering.md](2026-07-18-report-detail-watering.md).
   Re-confirmed findings 1–5's fixes hold on this screen too (no regressions).
   2 new findings, both documented only, **not fixed** (user's plan is
   "capture first" — decide fix/defer only after all workTypes are
   captured, then plan the full pass):
   - Watering-specific metric title/semantics mismatch: code's
     "가장 자주 준 물의 양" (mode) vs Figma's "평균 물 준 양" (average) — a
     product decision, not a copy fix (see doc's finding 1).
   - Chart order + titles differ: code renders `[물의 양, 물 주는 방법]`,
     Figma shows `[진행한 물주기 방식, 물 준 양]` — reversed order, different
     titles on both charts (see doc's finding 2).
   - Reconfirmed (not new): date-separator `-` vs `~` question and the
     inline record-row-preview scope gap both recur identically on this
     screen — not workType-specific, systemic to the Detail screen.
3. **비료 주기 (Fertilizing) capture done** —
   [2026-07-18-report-detail-fertilizing.md](2026-07-18-report-detail-fertilizing.md).
   Findings 1–5 reconfirmed (no regressions). Base + fertilizing metric
   titles/values match code exactly this time (no metric-title mismatch,
   unlike 물주기). 2 new findings, both a **repeat of the 물주기 pattern** —
   this is the important signal from this capture:
   - **Chart titles never match Figma's copy**: none of the 3 chart titles
     ("비료 종류별 작업 횟수"/"비료 종류별 사용량"/"비료 주는 방법" in code
     vs "각 비료 사용 횟수"/"각 비료 사용량"/"진행한 비료주기 방식" in
     Figma) match — same class of drift as 물주기's chart-title finding, now
     confirmed on 2 of 2 workTypes captured so far.
   - **Chart order**: Figma consistently puts the method/style-distribution
     chart **first**; code's append order is inconsistent (second for
     watering, last for fertilizing) — confirmed on 2 of 2 workTypes.
   - Reconfirmed (not new): date-separator `-` vs `~`, inline record-row
     preview — both recur a third time.
   - **Working hypothesis for the eventual remediation plan**: the
     chart-title and chart-order issues look systemic to
     `ReportPresentationModels.swift`'s per-workType chart-building code
     (`appendChart`/`appendDistributionChart` call sites), not isolated
     per-workType bugs — keep capturing the rest to see if this holds for
     all 7 workTypes before deciding a single fix vs. per-workType fixes.
4. **병해충 관리 (Pest Control) capture done** —
   [2026-07-18-report-detail-pest-control.md](2026-07-18-report-detail-pest-control.md).
   Findings 1–5 reconfirmed (no regressions). Also confirmed *why* some
   charts render as donut vs stacked bar: it's purely data-count-driven
   (`style = normalized.count <= 3 ? .stackedBar : .semiDonut` at
   `ReportChartModel.swift:75`), not a per-call-site choice — closes a loop,
   not a new finding.
   - **Chart titles: still 100% mismatched** — 3rd workType in a row where
     none of the chart titles match Figma's copy. Strengthens the
     "cross-cutting copy issue" hypothesis from the fertilizing capture.
   - **Important correction to the chart-order hypothesis**: pestControl's
     code *already* orders its 3 charts the same way Figma does
     (distribution-style chart first, target/count chart last) — unlike
     watering/fertilizing. **The chart-order bug is workType-specific, not
     universal** — don't assume every remaining workType needs an order fix;
     check each one.
   - 2 more new findings: pesticide-amount metric title format differs
     ("농약 사용량 (unit)" vs Figma's "총 농약 사용량"), and "총 살포량"'s
     value renders in `L` in code but Figma shows `ml` for the same
     field — flagged as needing a DTO/backend-contract check, not a blind
     presentation fix (may indicate `totalSprayAmountLiters` is mislabeled
     upstream, not just a display bug).
   - Reconfirmed (not new): date-separator `-` vs `~`, inline record-row
     preview — 4th recurrence of both.
5. **잡초 관리 (Weeding) capture done** —
   [2026-07-18-report-detail-weeding.md](2026-07-18-report-detail-weeding.md).
   Findings 1–5 reconfirmed. Simplest workType (1 metric, 1 chart) — both
   match code exactly except the chart title itself.
   - **Chart title mismatch — 4th workType in a row** ("잡초 관리 방법" in
     code vs Figma's "진행한 잡초 관리 방식"). Reinforces the cross-cutting
     copy-drift hypothesis: 4 of 4 workTypes captured so far have at least
     one mismatched chart title.
   - Also the first capture where a chart built via
     `appendDistributionChart` renders as a **4-slice donut** instead of the
     usual 3-segment stacked bar (because `methodDistribution` has 4
     categories here) — confirms the count-driven style rule from the
     pest-control capture applies to distribution charts too, not just
     `appendChart` ones. Not a bug.
   - Reconfirmed (not new): date-separator `-` vs `~`, inline record-row
     preview — 5th recurrence of both.
6. **가지·순 정리 (Pruning) capture done** —
   [2026-07-18-report-detail-pruning.md](2026-07-18-report-detail-pruning.md).
   **Clean match, no new findings.** Code's
   `case .pruning, .etc: break` ([ReportPresentationModels.swift:179-180](../../../ChamChamCham/ChamChamCham/Features/Report/Presentation/Models/ReportPresentationModels.swift))
   adds no metrics/charts beyond the shared base metric, and Figma's frame
   likewise has no "상세 정보" section at all — first workType with nothing
   to compare beyond shared chrome, so no chart-title question even arises
   here. Findings 1–5 reconfirmed (6th time). Reconfirmed (not new):
   date-separator `-` vs `~`, inline record-row preview — 6th recurrence.
   **`.etc` (기타) likely behaves identically** since it shares the same
   `case .pruning, .etc: break` code path — worth a quick capture to
   confirm, but expect the same "nothing to compare" result.
7. **수확 (Harvest) capture done** —
   [2026-07-18-report-detail-harvest.md](2026-07-18-report-detail-harvest.md).
   **This is the last "regular" workType — all 7 workType labels now have
   at least one capture except `.etc`, which shares `.pruning`'s empty code
   path (see item 6) and likely doesn't need its own capture.** Findings 1–5
   reconfirmed (7th time, no regressions). Base + harvest-amount metric
   ("총 수확량") matches code exactly — same clean-match class as
   fertilizing's metric.
   - **Chart title mismatch — now 5 of 5 workTypes-with-charts** ("수확
     부위" in code vs Figma's "수확 부위 종류"). This closes out the
     cross-cutting chart-title hypothesis: every single workType that has
     any chart at all has had a title mismatch. Treat as confirmed
     universal, not per-workType, for the eventual remediation plan.
   - **Bigger finding — a whole chart is missing from code/domain, not just
     mistitled**: Figma's second detail card, "재배 개월에 따른 수확량"
     (harvest count by growth-period bucket, e.g. "24개월 때 수확" → 2번),
     has **no corresponding chart-building call and no supporting field** in
     `HarvestReportStatistics`. `finalGrowthPeriodMonths` is a single scalar
     and `growthPeriodRangeMonths` is just `{minMonths, maxMonths}` — neither
     can produce a per-bucket distribution. This would need a new backend/DTO
     field (something like `growthPeriodDistribution: [ReportCountDistribution]`),
     not a presentation-only fix — flag this one for backend discussion in
     the remediation plan, it's the first "missing feature" finding rather
     than a copy/order drift.
   - Inconclusive (not a confirmed finding): the optional "재배 기간" metric
     didn't appear in this mock — may just be missing mock data.
   - Reconfirmed (not new): date-separator `-` vs `~`, inline record-row
     preview — 7th recurrence of both.
8. **`.etc` (기타) capture explicitly skipped — user confirmed it's the
   same as `.pruning`** ("기타는 스킵. .pruning이랑 똑같애."), matching the
   code-level fact that both share `case .pruning, .etc: break`
   ([ReportPresentationModels.swift:179-180](../../../ChamChamCham/ChamChamCham/Features/Report/Presentation/Models/ReportPresentationModels.swift)).
   **The per-workType capture phase is now done.** No `2026-07-18-report-detail-etc.md`
   exists and none is needed.
9. **Next phase: 리포트 전체 보완 수정 계획 (comprehensive remediation plan)**
   — the user's stated next step once all captures wrapped up. This has NOT
   started yet as of this HANDOFF update. A new session picking this up
   should synthesize all 8 capture docs (chart-spec + 7 workTypes) into one
   plan covering, at minimum: the 5 confirmed chart-title mismatches, the
   workType-specific chart-order issues (물주기/비료 주기 only), the
   harvest growth-period-distribution missing-feature gap (likely needs
   backend/DTO work, not just frontend), the date-separator `-` vs `~`
   open design question, the inline record-row-preview scope gap, the 2
   still-open chart-spec findings (legend styling, donut label-on-expand),
   and the 병해충 관리 metric-title/unit findings. Don't start fixing code
   until this plan exists and the user has reviewed it.
10. `ReportCoachingSection.swift` (AI coaching cards) has not been compared
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
2026-07-18-report-detail-chart-spec.md / 2026-07-18-report-detail-planting.md /
2026-07-18-report-detail-watering.md / 2026-07-18-report-detail-fertilizing.md /
2026-07-18-report-detail-pest-control.md / 2026-07-18-report-detail-weeding.md /
2026-07-18-report-detail-pruning.md / 2026-07-18-report-detail-harvest.md
문서도 읽어줘. TalkToFigma 연결 절차는 docs/figma/record/HANDOFF.md Part 1과 동일해.

지금까지: Report List View는 검토 완료(대부분 일치, 필터 칩 다중선택 건은 이미
dev에 병합돼 해결됨). 그래프 포맷 스펙 캡처 완료(turquoise 색상 버그는 수정함,
범례 스타일 버그·도넛 펼침 시 중앙 라벨 숨김 버그는 아직 미수정). 심기
(Planting) 리포트 상세 화면 전체 캡처 완료, 7개 불일치 중 1~5번(아이콘 애셋명/
WorkType 타이틀 폰트/메트릭 카드 라벨·값 스타일)은 커밋 3aa1457b로 수정 완료.
6번(날짜 구분자 `-` vs `~`)은 디자이너 확인 필요한 열린 질문, 7번(기록 내역
인라인 프리뷰)은 별도 기능 구현이 필요한 제품 스코프 갭으로 둘 다 미수정.

**물주기/비료 주기/병해충 관리/잡초 관리/가지·순 정리/수확 — 6개 workType
전부 캡처 완료.** 1~5번 수정이 전부 그대로 맞음을 재확인했음(회귀 없음). 남은
건 `.etc`(기타)뿐인데, 코드가 `case .pruning, .etc: break`로 가지·순 정리와
완전히 같은 경로라 캡처해도 똑같은 "클린 매치, 발견사항 없음" 결과가 나올
가능성이 높음 — **다음 세션은 먼저 사용자에게 `.etc`도 캡처할지 물어보고,
스킵한다면 캡처 단계는 사실상 끝난 것으로 보고 바로 "리포트 전체 보완 수정
계획" 단계로 넘어가면 됨.**

6개 workType에서 나온 핵심 발견 요약:
- **차트 타이틀 불일치가 차트를 가진 5개 workType 전부(5/5)에서 재현됨** —
  물주기/비료 주기/병해충 관리/잡초 관리/수확 전부. 개별 workType 버그가
  아니라 ReportPresentationModels.swift 전반의 카피 동기화 문제로 확정해도
  될 만큼 근거가 쌓임.
- **차트 순서는 workType마다 다름** — 물주기·비료 주기는 코드-Figma 순서가
  반대, 병해충 관리는 이미 일치, 잡초 관리·가지·순 정리는 차트가 0~1개라
  순서 문제 자체가 없음 — "보편적 버그"로 취급하지 말 것.
- **수확에서 발견한 더 큰 이슈**: Figma의 두 번째 차트("재배 개월에 따른
  수확량", 성장 개월 구간별 수확 횟수)는 코드/도메인 모델에 대응하는 필드
  자체가 없음(`finalGrowthPeriodMonths`는 단일 스칼라, `growthPeriodRangeMonths`
  는 min/max 범위일 뿐 구간별 분포가 아님) — 단순 타이틀/카피 문제가 아니라
  백엔드에 새 필드가 필요할 수 있는 **진짜 기능 누락**. 전체 보완 계획에서
  별도 항목으로 다룰 것.
- 병해충 관리: 농약 사용량 메트릭 타이틀 포맷 불일치, "총 살포량" 값이 코드는
  항상 L 단위인데 Figma는 ml(DTO 단위 자체를 확인해야 할 수도 있음).
- 잡초 관리: distribution 차트도 카테고리 4개 이상이면 스택바 대신 도넛으로
  렌더링됨을 확인(count<=3 규칙, 버그 아님).
- 가지·순 정리: 코드가 `.pruning, .etc: break`로 메트릭/차트를 아예 안
  만드는데 Figma도 정확히 그래서 발견사항 0건.

날짜 구분자(`-` vs `~`)와 기록 내역 인라인 프리뷰는 캡처한 모든 workType에서
예외 없이 재발(7/7) — 개별 workType 이슈가 아니라 Detail 화면 전체의 이슈로
확정.

**사용자 방침(중요)**: 지금까지는 캡처부터 하고 발견된 이슈는 즉시 고치지
않았음(심기의 findings 1-5만 예외적으로 이미 승인받아 수정·커밋함). `.etc`
캡처 여부를 확인한 뒤에는 "리포트 전체 보완 수정 계획"을 세우는 단계로
넘어가면 됨 — 이제부터는 캡처가 아니라 계획 수립이 다음 국면이라는 것을 놓치지
말 것.

이 워크트리엔 Core/Config/Secrets.swift가 없으니(gitignore) 빌드 확인이
필요하면 메인 체크아웃에서 복사해와.
```
