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
    var onSaved: () -> Void = {}

    init(
        repository: any MemberProfileRepository,
        mediaRepository: any MediaUploadRepository,
        onSaved: @escaping () -> Void = {}
    ) {
        _viewModel = State(
            initialValue: ProfileBasicInfoViewModel(repository: repository, mediaRepository: mediaRepository)
        )
        self.onSaved = onSaved
    }

    private static let managementTitles = ["개인 농업인", "농업경영체 법인", "비경영체"]

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
