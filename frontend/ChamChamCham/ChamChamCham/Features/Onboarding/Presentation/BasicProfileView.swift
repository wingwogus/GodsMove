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

    private let store = OnboardingDraftStore()

    private var isValid: Bool {
        !viewModel.draft.name.trimmingCharacters(in: .whitespaces).isEmpty
            && !viewModel.draft.contact.trimmingCharacters(in: .whitespaces).isEmpty
            && viewModel.draft.managementType != nil
    }

    var body: some View {
        @Bindable var viewModel = viewModel

        VStack(alignment: .leading, spacing: Spacing.md) {
            HStack {
                Button {
                    viewModel.goBack()
                } label: {
                    Image(systemName: "chevron.left")
                        .foregroundStyle(Color.appTextPrimary)
                }
                Spacer()
            }

            OnboardingProgressBar(currentStep: viewModel.currentStep)

            VStack(alignment: .leading, spacing: Spacing.xs) {
                Text("기본 정보 · 프로필")
                    .font(.appCaption)
                    .foregroundStyle(Color.appTextSecondary)
                Text("기본 정보와 프로필을\n입력해주세요")
                    .font(.appTitle)
            }

            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.lg) {
                    VStack(alignment: .leading, spacing: Spacing.md) {
                        HStack {
                            Spacer()
                            PhotosPicker(selection: $pickerItem, matching: .images) {
                                profileImageThumbnail
                            }
                            Spacer()
                        }

                        AppTextField(label: "*이름", placeholder: "이름(실명)을 입력하세요", text: $viewModel.draft.name)
                        AppTextField(label: "닉네임", placeholder: "예) 이랑이", text: $viewModel.draft.nickname)
                        AppTextField(label: "*연락처", placeholder: "010-0000-0000", text: $viewModel.draft.contact, keyboardType: .phonePad)
                        
                        VStack(alignment: .leading, spacing: Spacing.sm) {
                            Text("*자격")
                                .font(.appCaption)
                                .foregroundStyle(Color.appTextSecondary)

                            Picker("자격", selection: $viewModel.draft.managementType) {
                                Text("일반").tag(ManagementType?.some(.general))
                                Text("법인").tag(ManagementType?.some(.corporation))
                                Text("미가입").tag(ManagementType?.some(.none))
                            }
                            .pickerStyle(.segmented)

                            guideBanner
                        }
                    }
                }
                .padding(.vertical, Spacing.sm)
            }

            PrimaryButton(title: "다음") {
                viewModel.goNext()
            }
            .disabled(!isValid)
            .opacity(isValid ? 1 : 0.5)
        }
        .padding(Spacing.lg)
        .task(id: pickerItem) {
            guard let pickerItem,
                  let data = try? await pickerItem.loadTransferable(type: Data.self) else { return }
            let fileName = store.saveProfileImage(data)
            viewModel.draft.profileImageFileName = fileName
            if let uiImage = UIImage(data: data) {
                profileImage = Image(uiImage: uiImage)
            }
        }
        .onAppear {
            guard profileImage == nil,
                  let fileName = viewModel.draft.profileImageFileName,
                  let data = store.loadProfileImage(fileName: fileName),
                  let uiImage = UIImage(data: data) else { return }
            profileImage = Image(uiImage: uiImage)
        }
    }

    @ViewBuilder
    private var guideBanner: some View {
        if let guideText {
            HStack(alignment: .top, spacing: Spacing.sm) {
                Image(systemName: "info.circle.fill")
                    .foregroundStyle(Color.appPrimary)
                Text(guideText)
                    .font(.appCaption)
                    .foregroundStyle(Color.appTextPrimary)
            }
            .padding(Spacing.sm)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.appPrimary.opacity(0.1))
            .clipShape(RoundedRectangle(cornerRadius: 8))
        }
    }

    private var guideText: String? {
        guard let managementType = viewModel.draft.managementType else { return nil }
        switch managementType {
        case .general:
            return "가장 일반적인 경우예요. 개인 자격의 농업경영체(농업인)라면 선택하세요."
        case .corporation:
            return "영농조합법인, 농업회사법인 등 법인 형태의 농업경영체라면 선택하세요."
        case .none:
            return "아직 농업경영체로 등록하지 않았다면 선택하세요. 등록 후 마이페이지에서 언제든 수정할 수 있어요."
        }
    }

    @ViewBuilder
    private var profileImageThumbnail: some View {
        if let profileImage {
            profileImage
                .resizable()
                .scaledToFill()
                .frame(width: 96, height: 96)
                .clipShape(Circle())
        } else {
            Circle()
                .fill(Color(.secondarySystemBackground))
                .frame(width: 96, height: 96)
                .overlay {
                    Image(systemName: "camera.fill")
                        .foregroundStyle(Color.appTextSecondary)
                }
        }
    }
}

#Preview {
    BasicProfileView()
        .environment(OnboardingViewModel())
}
