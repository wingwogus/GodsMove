//
//  ProfileBasicInfoView.swift
//  ChamChamCham
//
//  Created by iyungui on 7/13/26.
//

import SwiftUI
import PhotosUI

/// 프로필 수정 · 기본 정보 탭 폼. 아바타(profileMediaId) / 이름(수정 불가) / 닉네임(선택) / 연락처 /
/// 생년월일 / 자격 / 귀농 연차 + 저장.
struct ProfileBasicInfoView: View {
    @State private var viewModel: ProfileBasicInfoViewModel
    @State private var pickerItem: PhotosPickerItem?
    @State private var isQualificationTooltipVisible = false
    var onSaved: () -> Void = {}

    init(
        repository: any MemberProfileRepository,
        mediaRepository: any MediaUploadRepository,
        farmRepository: any FarmRepository,
        onSaved: @escaping () -> Void = {}
    ) {
        _viewModel = State(
            initialValue: ProfileBasicInfoViewModel(
                repository: repository,
                mediaRepository: mediaRepository,
                farmRepository: farmRepository
            )
        )
        self.onSaved = onSaved
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.lg) {
                avatarSection

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
        .task(id: pickerItem) {
            guard let pickerItem,
                  let data = try? await pickerItem.loadTransferable(type: Data.self) else { return }
            await viewModel.pickImage(data)
        }
    }

    // MARK: - Avatar

    private var avatarSection: some View {
        VStack(spacing: Spacing.sm) {
            avatar

            if let message = viewModel.imageErrorMessage {
                Text(message)
                    .appTypography(.labelMedium)
                    .foregroundStyle(Color.Text.red)
            }
        }
        .frame(maxWidth: .infinity)
    }

    private var avatar: some View {
        PhotosPicker(selection: $pickerItem, matching: .images) {
            avatarContent
                .overlay {
                    if viewModel.isUploadingImage {
                        Circle().fill(Color.black.opacity(0.35))
                        ProgressView().tint(.white)
                    }
                }
                .overlay(alignment: .bottomTrailing) {
                    Circle()
                        .fill(Color.Object.bold)
                        .frame(width: 36, height: 36)
                        .overlay {
                            AppIconView(source: .asset("edit"), size: 16)
                                .foregroundStyle(Color.Icon.inverse)
                        }
                }
        }
        .disabled(viewModel.isUploadingImage)
        .accessibilityLabel("프로필 사진 수정")
    }

    @ViewBuilder private var avatarContent: some View {
        if let data = viewModel.previewImageData, let uiImage = UIImage(data: data) {
            AppAvatar(size: .large) {
                Image(uiImage: uiImage)
                    .resizable()
                    .scaledToFill()
            }
        } else if let urlString = viewModel.profileImageUrl, let url = URL(string: urlString) {
            AppAvatar(size: .large) {
                AsyncImage(url: url) { image in
                    image.resizable().scaledToFill()
                } placeholder: {
                    ProgressView()
                }
            }
        } else {
            AppAvatar(size: .large)
        }
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
        }
    }

    private func qualificationButton(_ option: QualificationOption) -> some View {
        AppSelectItem(
            title: option.title,
            isSelected: viewModel.managementType == option.managementType
        ) {
            viewModel.managementType = option.managementType
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
    /// of just clearing the system chrome, letting content behind it show through.
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

    private var experienceYearsText: Binding<String> {
        Binding {
            viewModel.experienceYears.map(String.init) ?? ""
        } set: { newValue in
            let digits = newValue.filter(\.isNumber)
            viewModel.experienceYears = digits.isEmpty ? nil : Int(digits)
        }
    }
}
