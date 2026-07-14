//
//  ProfileBasicInfoView.swift
//  ChamChamCham
//
//  Created by iyungui on 7/13/26.
//

import SwiftUI

/// 프로필 수정 · 기본 정보 탭 폼. 이름(수정 불가) / 닉네임(선택) / 연락처 / 생년월일 / 자격 / 귀농 연차
/// + 저장. 아바타 편집(profileMediaId)은 후속.
struct ProfileBasicInfoView: View {
    @State private var viewModel: ProfileBasicInfoViewModel
    var onSaved: () -> Void = {}

    init(repository: any MemberProfileRepository, onSaved: @escaping () -> Void = {}) {
        _viewModel = State(initialValue: ProfileBasicInfoViewModel(repository: repository))
        self.onSaved = onSaved
    }

    private static let managementTitles = ["개인 농업인", "농업경영체 법인", "비경영체"]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.lg) {
                AppTextField(
                    label: "이름",
                    placeholder: "이름",
                    text: .constant(viewModel.name),
                    isRequired: true
                )
                .disabled(true)

                AppTextField(
                    label: "닉네임",
                    placeholder: "닉네임을 입력해주세요.",
                    text: $viewModel.nickname
                )

                AppTextField(
                    label: "연락처",
                    placeholder: "000-0000-0000",
                    text: $viewModel.phone,
                    isRequired: true,
                    errorMessage: viewModel.phoneError,
                    keyboardType: .phonePad
                )

                AppDateField(
                    label: "생년월일",
                    selection: $viewModel.birthDate,
                    isRequired: true,
                    errorMessage: viewModel.birthDateError
                )

                qualificationSection

                AppTextField(
                    label: "귀농 연차",
                    placeholder: "귀농 연차를 입력해주세요.",
                    text: experienceYearsText,
                    isRequired: true,
                    errorMessage: viewModel.experienceError,
                    keyboardType: .numberPad
                )

                if let message = viewModel.saveErrorMessage {
                    Text(message)
                        .appTypography(.labelMedium)
                        .foregroundStyle(Color.Text.red)
                }
            }
            .padding(.horizontal, Spacing.lg - Spacing.xs)
            .padding(.top, Spacing.md)
            .padding(.bottom, Spacing.xl)
        }
        .background(Color.Background.default)
        .safeAreaInset(edge: .bottom, spacing: 0) {
            AppButton("저장", variant: .secondary, size: .medium, fullWidth: true) {
                Task {
                    if await viewModel.save() { onSaved() }
                }
            }
            .disabled(!viewModel.canSave)
            .padding(.horizontal, Spacing.lg - Spacing.xs)
            .padding(.vertical, Spacing.md)
            .background(Color.Background.default)
        }
        .task { await viewModel.load() }
    }

    private var qualificationSection: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            HStack(spacing: 2) {
                Text("자격")
                    .appTypography(.bodyMedium)
                    .foregroundStyle(Color.Text.default)
                Text("*")
                    .appTypography(.bodyMedium)
                    .foregroundStyle(Color.Text.red)
            }
            AppSegmentedControl(titles: Self.managementTitles, selection: managementIndex)
        }
    }

    private var managementIndex: Binding<Int> {
        Binding {
            ManagementType.allCases.firstIndex(of: viewModel.managementType) ?? 0
        } set: { index in
            guard ManagementType.allCases.indices.contains(index) else { return }
            viewModel.managementType = ManagementType.allCases[index]
        }
    }

    private var experienceYearsText: Binding<String> {
        Binding {
            viewModel.experienceYears.map(String.init) ?? ""
        } set: { newValue in
            let digits = newValue.filter(\.isNumber)
            viewModel.experienceYears = digits.isEmpty ? nil : Int(digits)
        }
    }
}
