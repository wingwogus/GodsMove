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
    @State private var isQualificationTooltipVisible = false

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
                            isRequired: false,
                            helperText: "다른 이용자에게 표시되는 이름이에요. 입력하지 않아도 서비스 이용에 지장 없어요.",
                            errorMessage: error(for: .name)
                        )

                        AppTextField(
                            label: "닉네임",
                            placeholder: "닉네임을 입력해주세요.",
                            text: $viewModel.draft.nickname,
                            isRequired: false,
                            errorMessage: error(for: .nickname)
                        )

                        AppTextField(
                            label: "연락처",
                            placeholder: "000-0000-0000",
                            text: $viewModel.draft.phone,
                            isRequired: false,
                            helperText: "농지 관련 알림 발송 등에 사용돼요. 입력하지 않아도 서비스 이용에 지장 없어요.",
                            errorMessage: error(for: .phone),
                            keyboardType: .phonePad
                        )

                        AppDateField(
                            label: "생년월일",
                            selection: $viewModel.draft.birthDate,
                            isRequired: false,
                            helperText: "귀농 년차가 나이를 넘지 않는지 확인하는 데 참고돼요. 입력하지 않아도 서비스 이용에 지장 없어요.",
                            errorMessage: error(for: .birthDate)
                        )

                        AppTextField(
                            label: "귀농 년차",
                            placeholder: "귀농 년차를 입력해주세요.",
                            text: experienceYearsText,
                            isRequired: true,
                            helperText: "맞춤 농사 정보를 제공하기 위해 필요해요.",
                            errorMessage: error(for: .experienceYears),
                            keyboardType: .numberPad
                        )

                        qualificationSection
                    }
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 40)
            }
            .scrollDismissesKeyboard(.interactively)
            // 연락처(.phonePad)·귀농 년차(.numberPad)는 Return/완료 키가 없어 탭-바깥/스크롤로만
            // 닫혀 답답하다. 키보드 툴바에 "완료"를 달아 어떤 키보드든 닫을 수 있게 한다.
            .toolbar {
                ToolbarItemGroup(placement: .keyboard) {
                    Spacer()
                    Button("완료") {
                        UIApplication.shared.sendAction(
                            #selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil
                        )
                    }
                }
            }
        }
        .background(Color.Background.default)
        .dismissKeyboardOnTap()
        .safeAreaInset(edge: .bottom, spacing: 0) {
            bottomCTA
        }
        // 키보드가 하단 "다음" 버튼을 밀어 올리지 않도록 고정한다 (SearchView와 동일 패턴).
        .ignoresSafeArea(.keyboard, edges: .bottom)
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
        AppTopAppBar(
            title: "",
            isDetail: true,
            showBorder: false,
            leading: .init(.asset("arrow_back_ios_new")) { viewModel.goBack() }
        )
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
                    isQualificationTooltipVisible = true
                } label: {
                    AppIconView(source: .asset("info"), size: 24)
                        .foregroundStyle(Color.Icon.subtle)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("자격 안내 보기")
                .popover(isPresented: $isQualificationTooltipVisible, arrowEdge: .bottom) {
                    qualificationTooltip
                        .presentationCompactAdaptation(.popover)
                        .presentationBackground(alignment: .bottom) {
                            qualificationTooltipBackground
                        }
                }
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

    private var qualificationTooltip: some View {
        VStack(alignment: .leading, spacing: 20) {
            HStack(spacing: 0) {
                Text("자격 유형")
                    .appTypography(.bodyLargeEmphasized)
                    .foregroundStyle(Color.Text.inverse)
                Spacer()
                Button {
                    isQualificationTooltipVisible = false
                } label: {
                    AppIconView(source: .asset("close"), size: 20)
                        .foregroundStyle(Color.Text.inverse)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("닫기")
            }

            VStack(alignment: .leading, spacing: 16) {
                tooltipRow(title: "개인 농업인", body: "개인으로 농업경영체 등록을 마친 대상")
                tooltipRow(title: "농업경영 법인", body: "영농조합법인·농업회사법인으로 설립 후 농업경영체 등록을 완료한 대상")
                tooltipRow(title: "비경영체", body: "농업경영체로 등록하지 않은 대상")
            }
        }
        .padding(20)
        .frame(width: 300, alignment: .leading)
    }

    /// Opaque card + tail behind `qualificationTooltip`. Supplied via `presentationBackground(alignment:content:)`
    /// rather than `.presentationBackground(.clear)` — the latter renders the whole popover translucent instead
    /// of just clearing the system chrome, letting content behind it (e.g. the profile photo) show through.
    private var qualificationTooltipBackground: some View {
        RoundedRectangle(cornerRadius: 16)
            .fill(Color.Object.bold)
            .overlay(alignment: .bottom) {
                TooltipTailShape()
                    .fill(Color.Object.bold)
                    .frame(width: 20, height: 10)
                    .offset(y: 10)
            }
    }

    private func tooltipRow(title: String, body: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .appTypography(.bodyMediumEmphasized)
                .foregroundStyle(Color.Text.inverse)
            Text(body)
                .appTypography(.labelMedium)
                .foregroundStyle(Color.Text.inverse.opacity(0.64))
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
                AppIconView(source: .asset("person"), size: 48)
                    .foregroundStyle(Color.Icon.disabled)
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
                    AppIconView(source: .asset("photo_camera"), size: 18)
                        .foregroundStyle(Color.Icon.inverse)
                }
        }
    }

    private func error(for field: OnboardingViewModel.BasicProfileField) -> String? {
        guard hasAttemptedNext else { return nil }
        return viewModel.basicProfileValidationErrors[field]
    }

    /// Downward-pointing tail attached to the bottom of `qualificationTooltip`, giving the popover a
    /// speech-bubble shape pointing back at the info icon that opened it.
    private struct TooltipTailShape: Shape {
        func path(in rect: CGRect) -> Path {
            var path = Path()
            path.move(to: CGPoint(x: rect.minX, y: rect.minY))
            path.addLine(to: CGPoint(x: rect.maxX, y: rect.minY))
            path.addLine(to: CGPoint(x: rect.midX, y: rect.maxY))
            path.closeSubpath()
            return path
        }
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
