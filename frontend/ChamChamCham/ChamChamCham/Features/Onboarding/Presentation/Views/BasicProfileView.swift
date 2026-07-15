//
//  BasicProfileView.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import SwiftUI
import PhotosUI

struct BasicProfileView: View {
    @Environment(OnboardingViewModel.self) private var viewModel
    @State private var pickerItem: PhotosPickerItem?
    @State private var profileImage: Image?
    @State private var hasAttemptedNext = false
    @State private var isQualificationGuideVisible = false

    private let store = OnboardingDraftStore()

    private var experienceYearsText: Binding<String> {
        Binding(
            get: {
                guard let years = viewModel.draft.experienceYears else { return "" }
                return String(years)
            },
            set: { newValue in
                let digitsOnly = newValue.filter(\.isNumber)
                viewModel.draft.experienceYears = digitsOnly.isEmpty ? nil : Int(digitsOnly)
            }
        )
    }

    var body: some View {
        @Bindable var viewModel = viewModel

        VStack(spacing: 0) {
            topAppBar

            OnboardingProgressBar(currentStep: viewModel.currentStep)
                .padding(.horizontal, 20)
                .padding(.top, 8)
                .padding(.bottom, 36)

            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    header

                    PhotosPicker(selection: $pickerItem, matching: .images) {
                        profileImageThumbnail
                    }
                    .frame(maxWidth: .infinity)

                    VStack(alignment: .leading, spacing: 24) {
                        AppTextField(
                            label: "이름",
                            placeholder: "이름을 입력해주세요.",
                            text: $viewModel.draft.name,
                            isRequired: true,
                            errorMessage: error(for: .name)
                        )

                        AppTextField(
                            label: "닉네임",
                            placeholder: "닉네임을 입력해주세요.",
                            text: $viewModel.draft.nickname,
                            isRequired: true,
                            errorMessage: error(for: .nickname)
                        )

                        AppTextField(
                            label: "연락처",
                            placeholder: "000-0000-0000",
                            text: $viewModel.draft.phone,
                            isRequired: true,
                            errorMessage: error(for: .phone),
                            keyboardType: .phonePad
                        )

                        AppDateField(
                            label: "생년월일",
                            selection: $viewModel.draft.birthDate,
                            isRequired: true,
                            errorMessage: error(for: .birthDate)
                        )

                        AppTextField(
                            label: "귀농 년차",
                            placeholder: "귀농 년차를 입력해주세요.",
                            text: experienceYearsText,
                            isRequired: true,
                            errorMessage: error(for: .experienceYears),
                            keyboardType: .numberPad
                        )

                        qualificationSection
                    }
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 40)
            }
        }
        .background(Color.Background.default)
        .safeAreaInset(edge: .bottom, spacing: 0) {
            bottomCTA
        }
        .sheet(isPresented: $isQualificationGuideVisible) {
            qualificationGuideSheet
        }
        .task(id: pickerItem) {
            guard let pickerItem,
                  let data = try? await pickerItem.loadTransferable(type: Data.self) else { return }
            let fileName = store.saveProfileImage(data)
            viewModel.draft.profileImageFileName = fileName
            // A freshly picked photo invalidates any media already uploaded for a previous pick, so the submit
            // flow re-uploads this one instead of attaching the stale `profileMediaId`.
            viewModel.draft.profileMediaId = nil
            if let uiImage = UIImage(data: data) {
                profileImage = Image(uiImage: uiImage)
            }
        }
        .onAppear {
            viewModel.draft.managementType = viewModel.draft.managementType ?? .agriculturalIndividual
            guard profileImage == nil,
                  let fileName = viewModel.draft.profileImageFileName,
                  let data = store.loadProfileImage(fileName: fileName),
                  let uiImage = UIImage(data: data) else { return }
            profileImage = Image(uiImage: uiImage)
        }
    }

    private var topAppBar: some View {
        HStack {
            Button {
                viewModel.goBack()
            } label: {
                Image(systemName: "chevron.left")
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(Color.Icon.default)
                    .frame(width: 48, height: 48)
            }
            .buttonStyle(.plain)
            Spacer()
        }
        .frame(height: 60)
        .padding(.horizontal, 4)
        .background(Color.Background.default)
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("기본 정보 설정하기")
                .appTypography(.headlineMedium)
                .foregroundStyle(Color.Text.default)

            Text("프로필 정보를 입력해주세요.")
                .appTypography(.bodyLarge)
                .foregroundStyle(Color.Text.muted)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var qualificationSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 2) {
                Text("자격")
                    .appTypography(.bodyMedium)
                    .foregroundStyle(Color.Text.default)
                Text("*")
                    .appTypography(.bodyMedium)
                    .foregroundStyle(Color.Text.red)

                Spacer()

                Button {
                    isQualificationGuideVisible = true
                } label: {
                    Image(systemName: "info.circle.fill")
                        .font(.system(size: 22, weight: .semibold))
                        .foregroundStyle(Color.Icon.subtle)
                        .frame(width: 24, height: 24)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("자격 안내 보기")
            }

            HStack(spacing: 8) {
                ForEach(QualificationOption.allCases) { option in
                    qualificationButton(option)
                }
            }

            if let message = error(for: .managementType) {
                Text(message)
                    .appTypography(.labelMedium)
                    .foregroundStyle(Color.Text.red)
            }
        }
    }

    private func qualificationButton(_ option: QualificationOption) -> some View {
        AppSelectItem(
            title: option.title,
            isSelected: viewModel.draft.managementType == option.managementType
        ) {
            viewModel.draft.managementType = option.managementType
        }
    }

    private var bottomCTA: some View {
        VStack(spacing: 0) {
            Divider()
                .background(Color.Border.subtle)
            OnboardingCTAButton(title: "다음", isVisuallyEnabled: viewModel.canProceedFromBasicProfile) {
                hasAttemptedNext = true
                guard viewModel.canProceedFromBasicProfile else { return }
                viewModel.goNext()
            }
            .padding(.horizontal, 20)
            .padding(.top, 16)
            .padding(.bottom, 28)
            .background(Color.Background.default)
        }
    }

    private var qualificationGuideSheet: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 20) {
                guideRow(title: "농업경영체(농업인)", body: "일반 — 개인 자격으로 농업경영체에 등록된 농업인")
                guideRow(title: "농업경영체(법인)", body: "법인 — 영농조합법인·농업회사법인 등")
                guideRow(title: "농업인(비경영체)", body: "미가입 — 농업경영체에 등록되지 않은 농업인")
                Spacer(minLength: 0)
                OnboardingCTAButton(title: "확인", isVisuallyEnabled: true) {
                    isQualificationGuideVisible = false
                }
            }
            .padding(20)
            .navigationTitle("농업경영체 자격 안내")
            .navigationBarTitleDisplayMode(.inline)
        }
        .presentationDetents([.medium])
    }

    private func guideRow(title: String, body: String) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .appTypography(.bodyMediumEmphasized)
                .foregroundStyle(Color.Text.default)
            Text(body)
                .appTypography(.labelMedium)
                .foregroundStyle(Color.Text.subtle)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    @ViewBuilder
    private var profileImageThumbnail: some View {
        if let profileImage {
            avatarCircle {
                profileImage
                    .resizable()
                    .scaledToFill()
            }
        } else {
            avatarCircle {
                Image(systemName: "person.fill")
                    .resizable()
                    .scaledToFit()
                    .foregroundStyle(Color.Icon.disabled)
                    .padding(24)
            }
        }
    }

    private func avatarCircle<Content: View>(@ViewBuilder content: () -> Content) -> some View {
        ZStack(alignment: .bottomTrailing) {
            Circle()
                .fill(Color.Object.muted)
                .frame(width: 96, height: 96)
                .overlay {
                    content()
                        .frame(width: 96, height: 96)
                        .clipShape(Circle())
                }
                .overlay {
                    Circle()
                        .stroke(Color.Border.default, lineWidth: 1)
                }

            Circle()
                .fill(Color.Object.bold)
                .frame(width: 36, height: 36)
                .overlay {
                    Image(systemName: "camera.fill")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundStyle(Color.Icon.inverse)
                }
        }
    }

    private func error(for field: OnboardingViewModel.BasicProfileField) -> String? {
        guard hasAttemptedNext else { return nil }
        return viewModel.basicProfileValidationErrors[field]
    }

    private struct QualificationOption: Identifiable, CaseIterable {
        let id: ManagementType
        let title: String
        let managementType: ManagementType

        static let allCases: [QualificationOption] = [
            .init(id: .agriculturalIndividual, title: "개인 농업인", managementType: .agriculturalIndividual),
            .init(id: .agriculturalCorporation, title: "농업경영체 법인", managementType: .agriculturalCorporation),
            .init(id: .nonRegisteredFarmer, title: "비경영체", managementType: .nonRegisteredFarmer)
        ]
    }
}

#if DEBUG
#Preview {
    BasicProfileView()
        .environment(OnboardingViewModel.preview())
}
#endif
