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
/// Scope (2026-07-14): read-only. The ⋮ menu is rendered but inert — 수정/삭제 designs aren't captured yet. The
/// "참참참의 코칭" (AI) section is a placeholder: the deployed backend has no coaching data source (conflict C-18).
struct RecordDetailView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var viewModel: RecordDetailViewModel
    private let horizontalInset: CGFloat = 20

    init(recordId: UUID, repository: any RecordRepository) {
        _viewModel = State(initialValue: RecordDetailViewModel(recordId: recordId, repository: repository))
    }

    var body: some View {
        VStack(spacing: 0) {
            AppTopAppBar(
                title: "",
                isDetail: true,
                showBorder: false,
                leading: .init("chevron.backward") { dismiss() },
                trailing: [.init("ellipsis")] // 수정/삭제 — 캡처 후 연결(현재 inert)
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
    }

    // MARK: - States

    private var loadingState: some View {
        ProgressView()
            .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func failedState(_ message: String) -> some View {
        VStack(spacing: Spacing.md) {
            Image(systemName: "exclamationmark.triangle")
                .font(.system(size: 40))
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

    private func headerSection(_ detail: RecordDetail) -> some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
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
        .padding(.top, Spacing.md)
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
            VStack(alignment: .leading, spacing: Spacing.md) {
                Text("\(detail.farmName) - \(detail.cropName)")
                    .appTypography(.titleMediumEmphasized)
                    .foregroundStyle(Color.Text.subtle)
                if !detail.infoRows.isEmpty {
                    Rectangle()
                        .fill(Color.Border.default)
                        .frame(height: 1)
                    VStack(alignment: .leading, spacing: Spacing.sm) {
                        ForEach(detail.infoRows) { row in
                            HStack(alignment: .top, spacing: Spacing.md) {
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
        .padding(.vertical, Spacing.lg)
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
        .padding(.vertical, Spacing.lg)
    }

    // MARK: 참참참의 코칭 (placeholder — C-18)

    private var coachingSection: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            sectionHeading("참참참의 코칭")
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
        .padding(.horizontal, horizontalInset)
        .padding(.vertical, Spacing.lg)
    }

    // MARK: - Shared

    private func sectionHeading(_ text: String) -> some View {
        Text(text)
            .appTypography(.titleLargeEmphasized)
            .foregroundStyle(Color.Text.subtle)
    }

    private var divider: some View {
        Rectangle()
            .fill(Color.Border.subtle)
            .frame(height: 2)
    }

    private static let dateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ko_KR")
        formatter.dateFormat = "yyyy. MM. dd."
        return formatter
    }()
}
