//
//  RecordDetailView.swift
//  ChamChamCham
//
//  Created by iyungui on 7/14/26.
//

import SwiftUI

/// 영농기록 결과 상세(읽기) — Figma `심기 작업 결과 - 씨앗` (node `1498:21864`). Structure is shared across all
/// eight workTypes; only the 작업 정보 rows differ (assembled in `RecordDetailLabels`).
///
/// Scope (2026-07-14): read + delete. The ⋮ menu uses native confirmation dialogs (no custom UI). 수정(edit) is
/// deferred — the deployed detail response returns no media ids, so an edit can't preserve existing photos
/// (conflict C-19). The "참참참의 코칭" (AI) section is now wired to per-record feedback
/// (`GET /api/v1/farming-records/{id}/feedback`, backend commit `b943ba9e`), polled via the view model;
/// regeneration (STALE/FAILED) is out of scope for now.
struct RecordDetailView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var viewModel: RecordDetailViewModel
    @State private var showActions = false
    @State private var showDeleteConfirm = false
    private let onMutated: () -> Void
    private let horizontalInset: CGFloat = 20

    /// `onMutated` fires after a successful delete (later: edit) so the list can refresh.
    init(recordId: UUID, repository: any RecordRepository, onMutated: @escaping () -> Void = {}) {
        self.onMutated = onMutated
        _viewModel = State(initialValue: RecordDetailViewModel(recordId: recordId, repository: repository))
    }

    var body: some View {
        VStack(spacing: 0) {
            AppTopAppBar(
                title: "",
                isDetail: true,
                showBorder: false,
                leading: .init(.asset("arrow_back_ios_new")) { dismiss() },
                trailing: [.init(.asset("more_vert")) { showActions = true }]
            )

            switch viewModel.state {
            case .loading:
                loadingState
            case let .failed(message):
                failedState(message)
            case let .loaded(detail):
                loadedContent(detail)
            }
        }
        .navigationBarBackButtonHidden(true)
        .toolbar(.hidden, for: .navigationBar)
        .task { await viewModel.onAppear() }
        .task { await viewModel.loadCoaching() }
        .confirmationDialog("기록 관리", isPresented: $showActions, titleVisibility: .hidden) {
            Button("삭제", role: .destructive) { showDeleteConfirm = true }
            Button("취소", role: .cancel) {}
        }
        .confirmationDialog("이 기록을 삭제할까요?", isPresented: $showDeleteConfirm, titleVisibility: .visible) {
            Button("삭제", role: .destructive) {
                Task {
                    if await viewModel.delete() {
                        onMutated()
                        dismiss()
                    }
                }
            }
            Button("취소", role: .cancel) {}
        } message: {
            Text("삭제한 기록은 되돌릴 수 없어요.")
        }
        .alert(
            "삭제하지 못했어요",
            isPresented: Binding(
                get: { viewModel.deleteError != nil },
                set: { if !$0 { viewModel.deleteError = nil } }
            )
        ) {
            Button("확인", role: .cancel) {}
        } message: {
            Text(viewModel.deleteError ?? "")
        }
        .overlay {
            if viewModel.isDeleting {
                ZStack {
                    Color.black.opacity(0.08).ignoresSafeArea()
                    ProgressView()
                }
            }
        }
    }

    // MARK: - States

    private var loadingState: some View {
        ProgressView()
            .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func failedState(_ message: String) -> some View {
        VStack(spacing: Spacing.md) {
            AppIconView(source: .asset("error"), size: 40)
                .foregroundStyle(Color.Icon.disabled)
            Text(message)
                .appTypography(.bodyMedium)
                .foregroundStyle(Color.Text.muted)
                .multilineTextAlignment(.center)
            AppButton("다시 시도", variant: .secondary, size: .medium) {
                Task { await viewModel.load() }
            }
        }
        .padding(.horizontal, horizontalInset)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    // MARK: - Loaded

    private func loadedContent(_ detail: RecordDetail) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                headerSection(detail)
                divider
                infoSection(detail)
                if !detail.imageUrls.isEmpty {
                    imageSection(detail.imageUrls)
                }
                divider
                coachingSection
            }
            .padding(.bottom, Spacing.xl)
        }
    }

    // MARK: 제목 + 날짜·날씨 칩 + 메모

    /// Figma gap from `top-app-bar` bottom to this section, and between the title row and the memo
    /// body, is a literal `20` — no `Spacing` token matches (between `md` 16 and `lg` 24).
    private let headerGap: CGFloat = 20

    private func headerSection(_ detail: RecordDetail) -> some View {
        VStack(alignment: .leading, spacing: headerGap) {
            HStack(alignment: .center, spacing: Spacing.sm) {
                Text(detail.workType.label)
                    .appTypography(.headlineMediumEmphasized)
                    .foregroundStyle(Color.Text.default)
                dateWeatherChip(detail)
            }
            if !detail.memo.isEmpty {
                Text(detail.memo)
                    .appTypography(.bodyLarge)
                    .foregroundStyle(Color.Text.subtle)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(.horizontal, horizontalInset)
        .padding(.top, headerGap)
    }

    private func dateWeatherChip(_ detail: RecordDetail) -> some View {
        HStack(spacing: Spacing.sm) {
            Text(Self.dateFormatter.string(from: detail.workedAt))
            Rectangle()
                .fill(Color.Border.strong)
                .frame(width: 1, height: 14)
            Text(weatherText(detail))
        }
        .appTypography(.labelMedium)
        .foregroundStyle(Color.Text.muted)
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background(Color.Object.subtle)
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }

    private func weatherText(_ detail: RecordDetail) -> String {
        let condition = detail.weatherCondition.trimmingCharacters(in: .whitespaces)
        // API supplies a single temperature (weatherTemperature); Figma's range display is not backed yet (C-6).
        return condition.isEmpty ? "\(detail.weatherTemperature)°" : "\(condition)(\(detail.weatherTemperature)°)"
    }

    // MARK: 작업 정보

    private func infoSection(_ detail: RecordDetail) -> some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            sectionHeading("작업 정보")
            // Figma: title→line and line→details are both a literal `12` — no token between `sm` (8)
            // and `md` (16) matches.
            VStack(alignment: .leading, spacing: 12) {
                Text("\(detail.farmName) - \(detail.cropName)")
                    .appTypography(.titleMediumEmphasized)
                    .foregroundStyle(Color.Text.subtle)
                if !detail.infoRows.isEmpty {
                    Rectangle()
                        .fill(Color.Border.default)
                        .frame(height: 1)
                    VStack(alignment: .leading, spacing: Spacing.sm) {
                        ForEach(detail.infoRows) { row in
                            // No extra HStack spacing: the label's fixed 84pt column already
                            // reproduces the Figma gap to the value (label text width + slack).
                            HStack(alignment: .top, spacing: 0) {
                                Text(row.label)
                                    .appTypography(.labelMediumEmphasized)
                                    .foregroundStyle(Color.Text.muted)
                                    .frame(width: 84, alignment: .leading)
                                Text(row.value)
                                    .appTypography(.bodyMediumEmphasized)
                                    .foregroundStyle(Color.Text.subtle)
                                    .fixedSize(horizontal: false, vertical: true)
                                Spacer(minLength: 0)
                            }
                        }
                    }
                }
            }
            .padding(Spacing.md)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.Object.subtle)
            .clipShape(RoundedRectangle(cornerRadius: 16))
        }
        .padding(.horizontal, horizontalInset)
    }

    // MARK: 작업 사진

    private func imageSection(_ urls: [String]) -> some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            sectionHeading("작업 사진")
                .padding(.horizontal, horizontalInset)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: Spacing.md) {
                    ForEach(urls, id: \.self) { url in
                        RecordRemoteImage(url: url)
                            .frame(width: 144, height: 144)
                    }
                }
                .padding(.horizontal, horizontalInset)
            }
        }
        // No divider precedes this section (unlike info/coaching), so it carries its own top gap.
        .padding(.top, Spacing.xl)
    }

    // MARK: 참참참의 코칭

    /// Backed by `RecordFeedbackQueryService` (`GET /api/v1/farming-records/{id}/feedback`, backend commit
    /// `b943ba9e`). The view model polls while the feedback is generated; this renders the current state.
    private var coachingSection: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            sectionHeading("참참참의 코칭")
            switch viewModel.coachingState {
            case .loading, .preparing:
                coachingPreparingCard
            case let .ready(feedback):
                coachingReadyCard(feedback)
            case .unavailable:
                coachingUnavailableCard
            }
        }
        .padding(.horizontal, horizontalInset)
    }

    /// Coaching not generated yet (or still generating) — the view model keeps polling behind this.
    private var coachingPreparingCard: some View {
        HStack(spacing: Spacing.sm) {
            Image(systemName: "sparkles")
                .font(.system(size: 20))
                .foregroundStyle(Color.Icon.primary)
            Text("작업 기록을 바탕으로 맞춤 코칭을 준비하고 있어요.")
                .appTypography(.bodyMedium)
                .foregroundStyle(Color.Text.subtle)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }
        .padding(Spacing.lg)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.Object.secondarySubtle)
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    private func coachingReadyCard(_ feedback: CoachingFeedback) -> some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            // 잘한 점
            HStack(alignment: .top, spacing: Spacing.sm) {
                Image(systemName: "sparkles")
                    .font(.system(size: 20))
                    .foregroundStyle(Color.Icon.primary)
                Text(feedback.goodPoint)
                    .appTypography(.bodyMedium)
                    .foregroundStyle(Color.Text.subtle)
                    .fixedSize(horizontal: false, vertical: true)
                Spacer(minLength: 0)
            }
            if !feedback.nextActions.isEmpty {
                Rectangle()
                    .fill(Color.Border.default)
                    .frame(height: 1)
                VStack(alignment: .leading, spacing: Spacing.sm) {
                    Text("다음 할 일")
                        .appTypography(.labelMediumEmphasized)
                        .foregroundStyle(Color.Text.muted)
                    // nextActions have no server id (2~3 items, order-stable) — index key is fine.
                    ForEach(Array(feedback.nextActions.enumerated()), id: \.offset) { _, action in
                        HStack(alignment: .top, spacing: Spacing.sm) {
                            if let due = action.due.label {
                                dueChip(due)
                            }
                            Text(action.text)
                                .appTypography(.bodyMedium)
                                .foregroundStyle(Color.Text.subtle)
                                .fixedSize(horizontal: false, vertical: true)
                            Spacer(minLength: 0)
                        }
                    }
                }
            }
        }
        .padding(Spacing.lg)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.Object.secondarySubtle)
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    private func dueChip(_ label: String) -> some View {
        Text(label)
            .appTypography(.labelMedium)
            .foregroundStyle(Color.Text.muted)
            .padding(.horizontal, 10)
            .padding(.vertical, 4)
            .background(Color.Object.subtle)
            .clipShape(RoundedRectangle(cornerRadius: 8))
    }

    /// Generation failed. Quiet copy, no retry affordance (regenerate is out of scope for now).
    private var coachingUnavailableCard: some View {
        Text("코칭을 준비하지 못했어요.")
            .appTypography(.bodyMedium)
            .foregroundStyle(Color.Text.subtle)
            .fixedSize(horizontal: false, vertical: true)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(Spacing.lg)
            .background(Color.Object.secondarySubtle)
            .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    // MARK: - Shared

    private func sectionHeading(_ text: String) -> some View {
        Text(text)
            .appTypography(.titleLargeEmphasized)
            .foregroundStyle(Color.Text.subtle)
    }

    /// Figma: every divider carries a symmetric `Spacing.xl` (32) gap on both sides — the section
    /// before and after it never adds its own padding on the divider-facing edge.
    private var divider: some View {
        Rectangle()
            .fill(Color.Border.subtle)
            .frame(height: 2)
            .padding(.vertical, Spacing.xl)
    }

    private static let dateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ko_KR")
        formatter.dateFormat = "yyyy. MM. dd."
        return formatter
    }()
}

// MARK: - Previews

/// Preview-only stand-in for `RecordRepository`, mirroring `PreviewOnboardingDependencies`. Drives the same
/// `.task { await viewModel.loadCoaching() }` the real view uses, so these previews exercise the actual polling
/// path (not a hardcoded snapshot) against a scripted `fetchCoaching` result.
private struct PreviewCoachingRepository: RecordRepository {
    let coaching: RecordCoaching

    func fetchDetail(id: UUID) async throws -> RecordDetail {
        RecordDetail(
            id: id,
            workType: .watering,
            workedAt: Date(),
            weatherCondition: "맑음",
            weatherTemperature: 24,
            farmName: "행복농장",
            cropName: "인삼",
            memo: "오전에 물을 줬어요.",
            imageUrls: [],
            infoRows: [RecordInfoRow(label: "물의 양", value: "보통")]
        )
    }

    func fetchCoaching(id: UUID) async throws -> RecordCoaching { coaching }

    func fetchRecords(_ query: RecordQuery) async throws -> RecordPage { RecordPage(items: [], nextCursor: nil) }
    func deleteRecord(id: UUID) async throws {}
    func fetchActiveCrops() async throws -> [ActiveCrop] { [] }
    func fetchFarmCrops() async throws -> [FarmWithCrops] { [] }
    func fetchWeather(farmId: UUID) async throws -> CurrentWeather {
        CurrentWeather(temperature: 24, condition: "맑음")
    }
    func searchPesticides(keyword: String?) async throws -> [Pesticide] { [] }
    func fetchPests(pesticideId: UUID) async throws -> [Pest] { [] }
    func createRecord(_ request: SaveRecordRequestDTO) async throws -> UUID { UUID() }
}

#Preview("코칭 - 준비 중") {
    RecordDetailView(
        recordId: UUID(),
        repository: PreviewCoachingRepository(coaching: RecordCoaching(status: .pending, feedback: nil))
    )
}

#Preview("코칭 - 완료") {
    RecordDetailView(
        recordId: UUID(),
        repository: PreviewCoachingRepository(coaching: RecordCoaching(
            status: .ready,
            feedback: CoachingFeedback(
                goodPoint: "적절한 시기에 물을 주셨어요. 최근 강수량을 고려하면 좋은 판단이었어요.",
                nextActions: [
                    CoachingNextAction(text: "이틀 뒤 흙 상태를 확인해 보세요.", due: .thisWeek),
                    CoachingNextAction(text: "잎끝이 마르는지 관찰해 주세요.", due: .nextCheck)
                ]
            )
        ))
    )
}

#Preview("코칭 - 실패") {
    RecordDetailView(
        recordId: UUID(),
        repository: PreviewCoachingRepository(coaching: RecordCoaching(status: .failed, feedback: nil))
    )
}
