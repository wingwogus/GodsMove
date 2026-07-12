# Community Compose Figma Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the community compose and farming-record picker presentations from the 2026-07-12 Figma captures while using the existing code design system and preserving current repository behavior.

**Architecture:** Keep `CommunityComposeViewModel` as the owner of compose validation, uploads, and submission. Add only the captured `.xsmall`/`.small` selected state to `AppCard`, replace feature-local design-system copies with existing components, and move the picker plus its pure filtering/selection state into a focused file.

**Tech Stack:** Swift 6, SwiftUI, Observation, PhotosUI, Swift Testing, Xcode/iOS Simulator.

## Global Constraints

- iOS 17+ and Swift 6 strict concurrency remain unchanged.
- Do not modify `AppTopAppBar`; continue using its current API.
- Ignore the bottom `TabView`; it is outside this implementation.
- Reuse `Core/DesignSystem` components and `Color+App.swift`, `Font+App.swift`, and `Spacing.swift` semantic tokens.
- Do not add raw equivalents of existing colors, fonts, spacing, buttons, toggles, dividers, search fields, chips, upload slots, or cards.
- Do not implement the Figma status-bar template.
- Do not invent a farming-record API or submit synthetic preview record IDs.
- Title, body, and image limits are 30, 500, and 5 respectively.
- Preserve iPhone SE usability with scrolling and safe-area-aware bottom actions.
- New Swift files must use the repository's Xcode header with `Created by iyungui on 7/12/26`.
- Preserve unrelated user changes and do not modify `AppTopAppBar.swift`.

---

## File Structure

- Modify `ChamChamCham/ChamChamCham/Core/DesignSystem/Components/AppCard.swift` to add the captured selected state and two-line `.small` caption.
- Modify `ChamChamCham/ChamChamCham/Features/Community/Presentation/ViewModels/CommunityComposeViewModel.swift` for the confirmed image limit and testable validation copy.
- Modify `ChamChamCham/ChamChamCham/Features/Community/Presentation/Views/CommunityComposeView.swift` so it contains only the compose surface and reuses design-system controls.
- Create `ChamChamCham/ChamChamCham/Features/Community/Presentation/Views/FarmingRecordPickerView.swift` for the picker, its local preview model, pure picker state, and preview image.
- Modify `ChamChamCham/ChamChamChamTests/DesignSystemCaptureStyleTests.swift` for card-state contract coverage.
- Modify `ChamChamCham/ChamChamChamTests/CommunityComposeViewModelValidationTests.swift` for limits, copy, and attachment cap coverage.
- Create `ChamChamCham/ChamChamChamTests/CommunityComposeFigmaContractTests.swift` for captured compose geometry constants.
- Create `ChamChamCham/ChamChamChamTests/FarmingRecordPickerStateTests.swift` for filtering, selection clearing, and picker geometry constants.

---

### Task 1: Add The Captured AppCard Selected State

**Files:**
- Modify: `ChamChamCham/ChamChamCham/Core/DesignSystem/Components/AppCard.swift`
- Test: `ChamChamCham/ChamChamChamTests/DesignSystemCaptureStyleTests.swift`

**Interfaces:**
- Consumes: Existing `AppCard.Size`, `AppBadge`, and semantic color tokens.
- Produces: `AppCard(..., isSelected: Bool = false, ...)`, `AppCard.usesSelectedStyle(size:isSelected:)`, selected color helpers, and `AppCard.smallCaptionLineLimit`.

- [ ] **Step 1: Write the failing selected-card contract test**

Add this test to `DesignSystemCaptureStyleTests`:

```swift
@Test("card selection matches the captured xsmall and small states")
@MainActor
func selectedCardStyles() {
    typealias Card = AppCard<EmptyView>

    #expect(Card.usesSelectedStyle(size: .xsmall, isSelected: true))
    #expect(Card.usesSelectedStyle(size: .small, isSelected: true))
    #expect(!Card.usesSelectedStyle(size: .medium, isSelected: true))
    #expect(!Card.usesSelectedStyle(size: .large, isSelected: true))
    #expect(!Card.usesSelectedStyle(size: .small, isSelected: false))

    #expect(hex(Card.backgroundColor(size: .small, isSelected: true)) == 0xE4F8E3)
    #expect(hex(Card.borderColor(size: .small, isSelected: true)) == 0x38C284)
    #expect(hex(Card.titleColor(size: .small, isSelected: true)) == 0x1A1A1A)
    #expect(hex(Card.captionColor(size: .small, isSelected: true)) == 0x4F4F4F)
    #expect(hex(Card.badgeBackgroundColor(size: .small, isSelected: true)) == 0xFFFFFF)
    #expect(hex(Card.badgeTextColor(size: .small, isSelected: true)) == 0x27865C)

    #expect(hex(Card.backgroundColor(size: .small, isSelected: false)) == 0xFFFFFF)
    #expect(hex(Card.borderColor(size: .small, isSelected: false)) == 0xE0E0E0)
    #expect(Card.smallCaptionLineLimit == 2)
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run from `frontend/ChamChamCham`:

```bash
xcodebuild -scheme ChamChamCham -destination 'platform=iOS Simulator,name=iPhone 17' test -only-testing:ChamChamChamTests/DesignSystemCaptureStyleTests
```

Expected: compilation fails because the new `AppCard` helpers do not exist.

- [ ] **Step 3: Add the backward-compatible AppCard state API**

Add `isSelected` to the stored properties and both initializers, always defaulting to `false`:

```swift
var isSelected: Bool = false
```

```swift
init(
    size: Size,
    title: String,
    captions: [String] = [],
    badges: [String] = [],
    dateText: String = "mm/dd",
    nickname: String = "닉네임",
    likeText: String = "nn",
    commentText: String = "nn",
    showsPostInfo: Bool = true,
    width: CGFloat? = nil,
    isSelected: Bool = false,
    @ViewBuilder thumbnail: () -> Thumbnail
) {
    self.size = size
    self.title = title
    self.captions = captions
    self.badges = badges
    self.dateText = dateText
    self.nickname = nickname
    self.likeText = likeText
    self.commentText = commentText
    self.showsPostInfo = showsPostInfo
    self.width = width
    self.isSelected = isSelected
    self.thumbnail = thumbnail()
}
```

Mirror the same `isSelected: Bool = false` parameter and assignment in the `Thumbnail == EmptyView` initializer.

- [ ] **Step 4: Implement captured-size-only semantic helpers**

Add these internal helpers to `AppCard` and use them from the view body and text styles:

```swift
static let smallCaptionLineLimit = 2

static func usesSelectedStyle(size: Size, isSelected: Bool) -> Bool {
    guard isSelected else { return false }
    switch size {
    case .xsmall, .small: return true
    case .medium, .large: return false
    }
}

static func backgroundColor(size: Size, isSelected: Bool) -> Color {
    usesSelectedStyle(size: size, isSelected: isSelected)
        ? Color.Object.primarySubtle
        : Color.Object.default
}

static func borderColor(size: Size, isSelected: Bool) -> Color {
    usesSelectedStyle(size: size, isSelected: isSelected)
        ? Color.Border.primary
        : Color.Border.default
}

static func titleColor(size: Size, isSelected: Bool) -> Color {
    usesSelectedStyle(size: size, isSelected: isSelected)
        ? Color.Text.default
        : Color.Text.subtle
}

static func captionColor(size: Size, isSelected: Bool) -> Color {
    usesSelectedStyle(size: size, isSelected: isSelected)
        ? Color.Text.subtle
        : Color.Text.muted
}

static func badgeBackgroundColor(size: Size, isSelected: Bool) -> Color {
    usesSelectedStyle(size: size, isSelected: isSelected)
        ? Color.Object.default
        : Color.Object.muted
}

static func badgeTextColor(size: Size, isSelected: Bool) -> Color {
    usesSelectedStyle(size: size, isSelected: isSelected)
        ? Color.Text.primary
        : Color.Text.subtle
}
```

Update the outer chrome:

```swift
.background(Self.backgroundColor(size: size, isSelected: isSelected))
.overlay {
    RoundedRectangle(cornerRadius: size.cornerRadius)
        .stroke(Self.borderColor(size: size, isSelected: isSelected), lineWidth: 1)
}
```

Use `titleColor` and `captionColor` in `.xsmall` and `.small`. Change only the `.small` caption to:

```swift
.lineLimit(Self.smallCaptionLineLimit)
```

For the `.small` badge row, keep the current `AppBadge` path when unselected and render the selected card's confirmed white/green nested badge treatment inside `AppCard`:

```swift
@ViewBuilder
private func cardBadge(_ label: String) -> some View {
    if Self.usesSelectedStyle(size: size, isSelected: isSelected) {
        Text(label)
            .appTypography(.labelMedium)
            .foregroundStyle(Self.badgeTextColor(size: size, isSelected: isSelected))
            .lineLimit(1)
            .padding(.horizontal, 10)
            .frame(minWidth: 48, minHeight: 32)
            .background(Self.badgeBackgroundColor(size: size, isSelected: isSelected))
            .clipShape(RoundedRectangle(cornerRadius: 8))
    } else {
        AppBadge(label: label, size: .medium, style: .solidPastel, variant: .secondary)
    }
}
```

Make `badgeRow` call `cardBadge(badge)`.

- [ ] **Step 5: Run the focused test and verify GREEN**

Run the Task 1 test command again.

Expected: `DesignSystemCaptureStyleTests` passes.

- [ ] **Step 6: Build to catch generic SwiftUI regressions**

```bash
xcodebuild -scheme ChamChamCham -destination 'platform=iOS Simulator,name=iPhone 17' build
```

Expected: `BUILD SUCCEEDED`.

- [ ] **Step 7: Commit Task 1**

```bash
git add ChamChamCham/ChamChamCham/Core/DesignSystem/Components/AppCard.swift ChamChamCham/ChamChamChamTests/DesignSystemCaptureStyleTests.swift
git commit -m "feat(design-system): 카드 선택 상태 추가"
```

---

### Task 2: Lock Compose Limits And Validation Copy

**Files:**
- Modify: `ChamChamCham/ChamChamCham/Features/Community/Presentation/ViewModels/CommunityComposeViewModel.swift`
- Test: `ChamChamCham/ChamChamChamTests/CommunityComposeViewModelValidationTests.swift`

**Interfaces:**
- Consumes: Existing view-model state and media upload repository.
- Produces: `maxImages == 5` and `inputValidationMessage: String?` for the view.

- [ ] **Step 1: Update the limits test and add validation-copy coverage**

Change the existing limit expectation to 5 and add:

```swift
@Test("compose exposes the captured validation copy with title priority")
func inputValidationMessage() {
    let viewModel = makeViewModel()
    #expect(viewModel.inputValidationMessage == nil)

    viewModel.title = String(repeating: "가", count: 31)
    #expect(viewModel.inputValidationMessage == "제목은 최대 30자까지 입력 가능합니다.")

    viewModel.body = String(repeating: "나", count: 501)
    #expect(viewModel.inputValidationMessage == "제목은 최대 30자까지 입력 가능합니다.")

    viewModel.title = String(repeating: "가", count: 30)
    #expect(viewModel.inputValidationMessage == "내용은 최대 500자까지 입력 가능합니다.")
}

@Test("compose accepts at most five image attachments")
func imageAttachmentLimit() async {
    let viewModel = makeViewModel()

    for byte in UInt8(0)..<UInt8(6) {
        await viewModel.addImage(Data([byte]))
    }

    #expect(viewModel.attachments.count == 5)
    #expect(!viewModel.canAddImage)
}
```

- [ ] **Step 2: Run the focused view-model suite and verify RED**

```bash
xcodebuild -scheme ChamChamCham -destination 'platform=iOS Simulator,name=iPhone 17' test -only-testing:ChamChamChamTests/CommunityComposeViewModelValidationTests
```

Expected: the limit test fails with 10 instead of 5 and compilation fails for `inputValidationMessage`.

- [ ] **Step 3: Implement the confirmed limits and validation copy**

In `CommunityComposeViewModel`:

```swift
static let titleLimit = 30
static let bodyLimit = 500
static let maxImages = 5

var inputValidationMessage: String? {
    if isTitleOverLimit {
        return "제목은 최대 30자까지 입력 가능합니다."
    }
    if isBodyOverLimit {
        return "내용은 최대 500자까지 입력 가능합니다."
    }
    return nil
}
```

Do not truncate title or body input. Keep `canSubmit` dependent on both over-limit flags.

- [ ] **Step 4: Run the focused suite and verify GREEN**

Run the Task 2 test command again.

Expected: `CommunityComposeViewModelValidationTests` passes.

- [ ] **Step 5: Commit Task 2**

```bash
git add ChamChamCham/ChamChamCham/Features/Community/Presentation/ViewModels/CommunityComposeViewModel.swift ChamChamCham/ChamChamChamTests/CommunityComposeViewModelValidationTests.swift
git commit -m "fix(community): 게시물 작성 입력 제한을 피그마와 동기화"
```

---

### Task 3: Rebuild CommunityComposeView With Design-System Components

**Files:**
- Modify: `ChamChamCham/ChamChamCham/Features/Community/Presentation/Views/CommunityComposeView.swift`
- Create: `ChamChamCham/ChamChamChamTests/CommunityComposeFigmaContractTests.swift`

**Interfaces:**
- Consumes: Task 1 `AppCard(isSelected:)`, Task 2 `inputValidationMessage`, and existing `AppChip`, `AppImageUploadSlot`, `AppToggle`, `AppDivider`, `AppButton`, and unchanged `AppTopAppBar`.
- Produces: Figma-backed compose geometry and the five captured/runtime states.

- [ ] **Step 1: Write the failing compose geometry contract test**

Create the test file with the required header and:

```swift
import Testing
@testable import ChamChamCham

@Suite("Community compose Figma contract")
struct CommunityComposeFigmaContractTests {
    @Test("compose uses the captured text-area and attachment geometry")
    func geometry() {
        #expect(CommunityComposeView.Layout.horizontalInset == 20)
        #expect(CommunityComposeView.Layout.textAreaContentInset == 20)
        #expect(CommunityComposeView.Layout.titleHeight == 38)
        #expect(CommunityComposeView.Layout.minimumBodyLines == 9)
        #expect(CommunityComposeView.Layout.maximumBodyLines == 21)
        #expect(CommunityComposeView.Layout.descriptionSpacing == 12)
        #expect(CommunityComposeView.Layout.imageSpacing == 12)
    }
}
```

- [ ] **Step 2: Run the new test and verify RED**

```bash
xcodebuild -scheme ChamChamCham -destination 'platform=iOS Simulator,name=iPhone 17' test -only-testing:ChamChamChamTests/CommunityComposeFigmaContractTests
```

Expected: compilation fails because `CommunityComposeView.Layout` does not exist.

- [ ] **Step 3: Add the captured layout contract**

Inside `CommunityComposeView`, add:

```swift
enum Layout {
    static let horizontalInset: CGFloat = 20
    static let textAreaContentInset: CGFloat = 20
    static let titleHeight: CGFloat = 38
    static let minimumBodyLines = 9
    static let maximumBodyLines = 21
    static let descriptionSpacing: CGFloat = 12
    static let imageSpacing: CGFloat = 12
}
```

- [ ] **Step 4: Replace the combined text area with an auto-growing vertical TextField**

Keep this one-off composition in `CommunityComposeView` and use design tokens only:

```swift
private var composeTextArea: some View {
    VStack(alignment: .leading, spacing: Spacing.md) {
        TextField("제목을 입력해주세요.", text: $viewModel.title)
            .appTypography(.titleMedium)
            .foregroundStyle(viewModel.isTitleOverLimit ? Color.Text.red : Color.Text.default)
            .lineLimit(1)
            .focused($focusedField, equals: .title)
            .frame(height: Layout.titleHeight, alignment: .topLeading)
            .overlay(alignment: .bottom) {
                Rectangle()
                    .fill(Color.Border.default)
                    .frame(height: 1)
            }

        VStack(alignment: .leading, spacing: Layout.descriptionSpacing) {
            TextField(
                "농사와 관련해 이야기하고 싶은 내용을 자유롭게 작성해보세요.",
                text: $viewModel.body,
                axis: .vertical
            )
            .appTypography(.bodyLarge)
            .foregroundStyle(Color.Text.subtle)
            .lineLimit(Layout.minimumBodyLines...Layout.maximumBodyLines)
            .focused($focusedField, equals: .body)

            validationRow
        }
    }
    .padding(Layout.textAreaContentInset)
    .frame(maxWidth: .infinity, alignment: .leading)
    .background(Color.Background.subtle)
    .clipShape(RoundedRectangle(cornerRadius: 12))
    .padding(.horizontal, Layout.horizontalInset)
    .padding(.top, Spacing.md)
    .padding(.bottom, Spacing.lg)
}
```

Change `validationRow` to read `viewModel.inputValidationMessage`, keep the error text on the left, and keep the live `body.count/500` counter on the right. Remove the view-local `validationMessage` property.

- [ ] **Step 5: Replace local component copies in the compose body**

Make these exact substitutions:

```swift
private var sectionDivider: some View {
    AppDivider(size: .small)
}
```

```swift
private var questionToggle: some View {
    HStack {
        Text("질문으로 올리기")
            .appTypography(.bodyMedium)
            .foregroundStyle(Color.Text.default)
        Spacer()
        AppToggle(isOn: $viewModel.isQuestion)
    }
    .padding(.horizontal, Layout.horizontalInset)
    .frame(height: 60)
}
```

Use the selected `AppCard` in the farming-record horizontal list:

```swift
AppCard(
    size: .xsmall,
    title: record.title,
    captions: [record.cropName, record.caption],
    dateText: record.dateText,
    isSelected: selectedFarmingRecord?.id == record.id
) {
    FarmingRecordImage(record: record, height: 84)
}
```

Use `AppButton` in the safe-area submit bar:

```swift
AppButton(
    viewModel.isSubmitting ? nil : "완료",
    variant: .primary,
    size: .medium,
    fullWidth: true,
    action: submit
)
.disabled(!viewModel.canSubmit)
.overlay {
    if viewModel.isSubmitting {
        ProgressView()
            .tint(Color.Text.inverse)
    }
}
```

Do not change `AppButton` itself for the submitting state.

Change the image row to `HStack(spacing: Layout.imageSpacing)` and let every label derive from `viewModel.attachments.count` and `CommunityComposeViewModel.maxImages`.

Delete `FarmingRecordCompactCard`, `AppSwitch`, and `AppSwitchToggleStyle` after their call sites are gone. Do not edit `AppTopAppBar.swift`.

- [ ] **Step 6: Run the contract and validation suites**

```bash
xcodebuild -scheme ChamChamCham -destination 'platform=iOS Simulator,name=iPhone 17' test -only-testing:ChamChamChamTests/CommunityComposeFigmaContractTests -only-testing:ChamChamChamTests/CommunityComposeViewModelValidationTests -only-testing:ChamChamChamTests/DesignSystemCaptureStyleTests
```

Expected: all three suites pass.

- [ ] **Step 7: Build the app**

```bash
xcodebuild -scheme ChamChamCham -destination 'platform=iOS Simulator,name=iPhone 17' build
```

Expected: `BUILD SUCCEEDED` with no `AppTopAppBar.swift` diff.

- [ ] **Step 8: Commit Task 3**

```bash
git add ChamChamCham/ChamChamCham/Features/Community/Presentation/Views/CommunityComposeView.swift ChamChamCham/ChamChamChamTests/CommunityComposeFigmaContractTests.swift
git commit -m "feat(community): 피그마 게시물 작성 화면 반영"
```

---

### Task 4: Extract And Rebuild The Farming-Record Picker

**Files:**
- Create: `ChamChamCham/ChamChamCham/Features/Community/Presentation/Views/FarmingRecordPickerView.swift`
- Modify: `ChamChamCham/ChamChamCham/Features/Community/Presentation/Views/CommunityComposeView.swift`
- Create: `ChamChamCham/ChamChamChamTests/FarmingRecordPickerStateTests.swift`

**Interfaces:**
- Consumes: Task 1 selected `.small` `AppCard`, `AppSearchBar`, `AppChip`, `AppButton`, unchanged `AppTopAppBar`, and compose's `Binding<FarmingRecordPreview?>`.
- Produces: `FarmingRecordPickerState`, `FarmingRecordPickerView`, `FarmingRecordPreview`, and `FarmingRecordImage` in a focused file.

- [ ] **Step 1: Write failing picker-state and geometry tests**

Create the test file with the required header and:

```swift
import Foundation
import Testing
@testable import ChamChamCham

@MainActor
@Suite("Farming record picker state")
struct FarmingRecordPickerStateTests {
    @Test("crop and search filters clear an invisible selection")
    func filteringClearsInvisibleSelection() {
        let records = FarmingRecordPreview.samples
        let state = FarmingRecordPickerState(records: records)

        state.selectRecord(records[0].id)
        #expect(state.selectedRecordID == records[0].id)

        state.selectCrop(records[1].cropName)
        #expect(state.selectedRecordID == nil)
        #expect(state.filteredRecords.allSatisfy { $0.cropName == records[1].cropName })

        state.selectRecord(records[1].id)
        state.searchText = "검색 결과 없음"
        #expect(state.selectedRecordID == nil)
        #expect(state.filteredRecords.isEmpty)
    }

    @Test("picker geometry matches the selected-state capture")
    func geometry() {
        #expect(FarmingRecordPickerView.Layout.horizontalInset == 20)
        #expect(FarmingRecordPickerView.Layout.filterTopInset == 16)
        #expect(FarmingRecordPickerView.Layout.chipAreaHeight == 64)
        #expect(FarmingRecordPickerView.Layout.cardSpacing == 16)
        #expect(FarmingRecordPickerView.Layout.listTopInset == 8)
    }
}
```

- [ ] **Step 2: Run the picker suite and verify RED**

```bash
xcodebuild -scheme ChamChamCham -destination 'platform=iOS Simulator,name=iPhone 17' test -only-testing:ChamChamChamTests/FarmingRecordPickerStateTests
```

Expected: compilation fails because the extracted picker types do not exist.

- [ ] **Step 3: Create the picker file and pure state**

Start the new Swift file with the repository header, import SwiftUI, and move `FarmingRecordPreview`, its four sample values, and `FarmingRecordImage` from `CommunityComposeView.swift` without changing sample IDs or content.

Add this state type:

```swift
@MainActor
@Observable
final class FarmingRecordPickerState {
    var searchText = "" {
        didSet { clearSelectionIfNeeded() }
    }
    private(set) var selectedCropName: String?
    private(set) var selectedRecordID: UUID?
    let records: [FarmingRecordPreview]

    init(records: [FarmingRecordPreview], selectedRecord: FarmingRecordPreview? = nil) {
        self.records = records
        selectedCropName = selectedRecord?.cropName
        selectedRecordID = selectedRecord?.id
    }

    var cropNames: [String] {
        Array(Set(records.map(\.cropName))).sorted()
    }

    var filteredRecords: [FarmingRecordPreview] {
        let keyword = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
        return records.filter { record in
            let cropMatches = selectedCropName == nil || record.cropName == selectedCropName
            let keywordMatches = keyword.isEmpty
                || record.title.localizedCaseInsensitiveContains(keyword)
                || record.caption.localizedCaseInsensitiveContains(keyword)
                || record.cropName.localizedCaseInsensitiveContains(keyword)
            return cropMatches && keywordMatches
        }
    }

    var selectedRecord: FarmingRecordPreview? {
        records.first { $0.id == selectedRecordID }
    }

    func selectCrop(_ cropName: String?) {
        selectedCropName = cropName
        clearSelectionIfNeeded()
    }

    func selectRecord(_ id: UUID) {
        selectedRecordID = id
    }

    private func clearSelectionIfNeeded() {
        guard let selectedRecordID else { return }
        if !filteredRecords.contains(where: { $0.id == selectedRecordID }) {
            self.selectedRecordID = nil
        }
    }
}
```

- [ ] **Step 4: Rebuild the picker with existing design-system components**

Add the captured layout constants:

```swift
struct FarmingRecordPickerView: View {
    enum Layout {
        static let horizontalInset: CGFloat = 20
        static let filterTopInset: CGFloat = 16
        static let chipAreaHeight: CGFloat = 64
        static let cardSpacing: CGFloat = 16
        static let listTopInset: CGFloat = 8
    }
```

The view owns `@State private var state` initialized from `FarmingRecordPreview.samples` and the incoming selected record. In `body`, create `@Bindable var state = state` so `AppSearchBar(text: $state.searchText, placeholder: "어떤 기록을 올릴까요?")` receives a binding.

Use this composition:

```swift
VStack(spacing: 0) {
    AppTopAppBar(
        title: "영농 기록 첨부하기",
        isDetail: true,
        showBorder: false,
        leading: .init("chevron.left") { dismiss() }
    )

    VStack(spacing: 0) {
        AppSearchBar(text: $state.searchText, placeholder: "어떤 기록을 올릴까요?")
            .padding(.horizontal, Layout.horizontalInset)

        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Spacing.sm) {
                AppChip(label: "전체", isSelected: state.selectedCropName == nil, style: .solid) {
                    state.selectCrop(nil)
                }
                ForEach(state.cropNames, id: \.self) { cropName in
                    AppChip(
                        label: cropName,
                        isSelected: state.selectedCropName == cropName,
                        style: .solid
                    ) {
                        state.selectCrop(cropName)
                    }
                }
            }
            .padding(.horizontal, Layout.horizontalInset)
        }
        .frame(height: Layout.chipAreaHeight)
    }
    .padding(.top, Layout.filterTopInset)

    ScrollView {
        LazyVStack(spacing: Layout.cardSpacing) {
            if state.filteredRecords.isEmpty {
                EmptyStateView(message: "조건에 맞는 기록이 없어요.")
                    .padding(.top, Spacing.xl)
            } else {
                ForEach(state.filteredRecords) { record in
                    Button {
                        state.selectRecord(record.id)
                    } label: {
                        AppCard(
                            size: .small,
                            title: record.title,
                            captions: [record.caption],
                            badges: [record.cropName, record.category],
                            dateText: record.dateText,
                            isSelected: state.selectedRecordID == record.id
                        ) {
                            FarmingRecordImage(record: record, height: 96)
                        }
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .padding(.horizontal, Layout.horizontalInset)
        .padding(.top, Layout.listTopInset)
        .padding(.bottom, Spacing.lg)
    }
}
.background(Color.Background.default)
.safeAreaInset(edge: .bottom) {
    selectBar
}
```

Implement `selectBar` with a 1pt `Color.Border.subtle` top border and:

```swift
AppButton(
    "선택",
    variant: .secondary,
    size: .medium,
    fullWidth: true
) {
    selectedRecord = state.selectedRecord
    dismiss()
}
.disabled(state.selectedRecordID == nil)
.padding(.horizontal, Layout.horizontalInset)
.padding(.vertical, 12)
```

Delete the old private picker, picker card, preview model, and preview image definitions from `CommunityComposeView.swift`. Keep its existing `fullScreenCover` call unchanged.

- [ ] **Step 5: Run picker tests and verify GREEN**

Run the Task 4 test command again.

Expected: `FarmingRecordPickerStateTests` passes.

- [ ] **Step 6: Run all community/design focused tests and build**

```bash
xcodebuild -scheme ChamChamCham -destination 'platform=iOS Simulator,name=iPhone 17' test -only-testing:ChamChamChamTests/FarmingRecordPickerStateTests -only-testing:ChamChamChamTests/CommunityComposeFigmaContractTests -only-testing:ChamChamChamTests/CommunityComposeViewModelValidationTests -only-testing:ChamChamChamTests/DesignSystemCaptureStyleTests
```

```bash
xcodebuild -scheme ChamChamCham -destination 'platform=iOS Simulator,name=iPhone 17' build
```

Expected: focused suites pass and build reports `BUILD SUCCEEDED`.

- [ ] **Step 7: Commit Task 4**

```bash
git add ChamChamCham/ChamChamCham/Features/Community/Presentation/Views/CommunityComposeView.swift ChamChamCham/ChamChamCham/Features/Community/Presentation/Views/FarmingRecordPickerView.swift ChamChamCham/ChamChamChamTests/FarmingRecordPickerStateTests.swift
git commit -m "feat(community): 영농 기록 선택 화면 피그마 반영"
```

---

### Task 5: Simulator Visual Verification

**Files:**
- Verify only: all files changed in Tasks 1-4
- Reference: `docs/figma/community/assets/2026-07-12-community-compose-default.png`
- Reference: `docs/figma/community/assets/2026-07-12-community-compose-required-complete.png`
- Reference: `docs/figma/community/assets/2026-07-12-community-compose-all-complete.png`
- Reference: `docs/figma/community/assets/2026-07-12-community-compose-title-over-limit.png`
- Reference: `docs/figma/community/assets/2026-07-12-community-compose-record-picker-selected.png`

**Interfaces:**
- Consumes: Completed implementation and captured PNG references.
- Produces: Build/test evidence plus simulator screenshots on large and small device classes.

- [ ] **Step 1: Confirm the protected file and worktree scope**

```bash
git diff --name-only 551d71d..HEAD
git diff --exit-code 551d71d..HEAD -- ChamChamCham/ChamChamCham/Core/DesignSystem/Components/AppTopAppBar.swift
git status --short
```

Expected: no `AppTopAppBar.swift` diff and no unrelated modified files.

- [ ] **Step 2: Run the full test target**

```bash
xcodebuild -scheme ChamChamCham -destination 'platform=iOS Simulator,name=iPhone 17' test
```

Expected: `TEST SUCCEEDED`.

- [ ] **Step 3: Build for the minimum device class**

```bash
xcodebuild -scheme ChamChamCham -destination 'platform=iOS Simulator,name=iPhone SE (3rd generation)' build
```

Expected: `BUILD SUCCEEDED` without clipping-related compile/runtime failures.

- [ ] **Step 4: Launch and capture iPhone 17 evidence**

Use the iOS debugger workflow to boot `iPhone 17`, install and launch the app, navigate to Community → compose, and capture:

1. Default state.
2. Required values complete.
3. Title over 30 characters.
4. Body over 500 characters.
5. Farming-record picker with a selected card.

Compare component geometry, semantic colors, scroll behavior, and bottom actions with the five reference PNGs. Do not compare or modify the intentionally ignored bottom `TabView`.

- [ ] **Step 5: Launch and capture iPhone SE evidence**

Boot `iPhone SE (3rd generation)`, repeat default compose and selected picker captures, and confirm:

- Crop chips remain horizontally scrollable.
- Title/body remain reachable with the keyboard shown.
- Bottom actions remain visible and safe-area-aware.
- The final picker card can scroll fully above the select button.

- [ ] **Step 6: Fix only evidence-backed mismatches and rerun verification**

For every mismatch, first identify whether the captured value belongs to an existing component or feature-local layout. Modify the existing component only when the mismatch is part of its captured stable state; otherwise keep the adjustment inside the feature screen. Rerun the focused tests, full test target, and both builds after each fix batch.

- [ ] **Step 7: Final completion commit when verification required fixes**

If Step 6 changed files, stage only those verified fixes and commit:

```bash
git add ChamChamCham/ChamChamCham/Core/DesignSystem/Components/AppCard.swift ChamChamCham/ChamChamCham/Features/Community/Presentation/ViewModels/CommunityComposeViewModel.swift ChamChamCham/ChamChamCham/Features/Community/Presentation/Views/CommunityComposeView.swift ChamChamCham/ChamChamCham/Features/Community/Presentation/Views/FarmingRecordPickerView.swift ChamChamCham/ChamChamChamTests/DesignSystemCaptureStyleTests.swift ChamChamCham/ChamChamChamTests/CommunityComposeViewModelValidationTests.swift ChamChamCham/ChamChamChamTests/CommunityComposeFigmaContractTests.swift ChamChamCham/ChamChamChamTests/FarmingRecordPickerStateTests.swift
git commit -m "fix(community): 게시물 작성 피그마 시각 검증 반영"
```

If Step 6 changed nothing, do not create an empty commit.

---

## Completion Criteria

- The five compose states match their captured or user-confirmed specifications.
- The selected record picker matches node `631:9420`.
- `AppCard` owns the selected `.xsmall`/`.small` state; feature-local card copies are absent.
- Existing `AppSearchBar`, `AppChip`, `AppToggle`, `AppDivider`, `AppImageUploadSlot`, and `AppButton` are reused.
- `AppTopAppBar.swift`, foundation token files, and bottom `TabView` remain unchanged.
- Runtime counters report actual state, including `count/5` images.
- Focused tests and the full test target pass.
- iPhone 17 and iPhone SE builds succeed and simulator evidence confirms scrolling and bottom-action usability.
