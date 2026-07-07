//
//  CommunityComposeView.swift
//  ChamChamCham
//
//  Created by iyungui on 7/7/26.
//

import PhotosUI
import SwiftUI

/// "게시물 작성하기" composer. Presented as a sheet; calls `onCreated` with the new post id after a successful
/// submit and dismisses itself.
struct CommunityComposeView: View {
    private let container: DIContainer
    private let onCreated: (UUID) -> Void

    @State private var viewModel: CommunityComposeViewModel
    @State private var pickerItems: [PhotosPickerItem] = []
    @State private var showCropPicker = false
    @Environment(\.dismiss) private var dismiss

    init(container: DIContainer, onCreated: @escaping (UUID) -> Void) {
        self.container = container
        self.onCreated = onCreated
        _viewModel = State(
            initialValue: CommunityComposeViewModel(
                repository: container.makeCommunityRepository(),
                cropCatalog: container.makeCropCatalogService(),
                mediaRepository: container.makeMediaUploadRepository()
            )
        )
    }

    var body: some View {
        VStack(spacing: 0) {
            header
            Divider()
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.lg) {
                    cropSection
                    titleField
                    AppTextEditor(
                        placeholder: "농사 관련 물어보고 싶은 이야기를 적어보세요.",
                        text: $viewModel.body
                    )
                    farmingRecordButton
                    imageSection
                    Divider()
                    questionToggle
                    if let errorMessage = viewModel.errorMessage {
                        Text(errorMessage)
                            .appTypography(.labelMedium)
                            .foregroundStyle(Color.Text.red)
                    }
                }
                .padding(Spacing.md)
            }
        }
        .task { await viewModel.loadBoards() }
        .sheet(isPresented: $showCropPicker) {
            CropPickerSheet(loadCrops: viewModel.catalogCrops) { crops in
                viewModel.addBoards(from: crops)
            }
        }
        .onChange(of: pickerItems) { _, items in
            guard !items.isEmpty else { return }
            Task { await attach(items) }
        }
    }

    // MARK: - Header

    private var header: some View {
        HStack {
            Button { dismiss() } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 20))
                    .foregroundStyle(Color.Icon.default)
                    .frame(width: 44, height: 44)
            }
            Spacer()
            Text("게시물 작성하기")
                .appTypography(.bodyLargeEmphasized)
                .foregroundStyle(Color.Text.default)
            Spacer()
            Button {
                Task {
                    if let id = await viewModel.submit() {
                        onCreated(id)
                        dismiss()
                    }
                }
            } label: {
                if viewModel.isSubmitting {
                    ProgressView()
                } else {
                    Text("등록")
                        .appTypography(.bodyLargeEmphasized)
                        .foregroundStyle(viewModel.canSubmit ? Color.Text.primary : Color.Text.disabled)
                }
            }
            .disabled(!viewModel.canSubmit)
            .frame(minWidth: 44, minHeight: 44)
        }
        .padding(.horizontal, Spacing.sm)
    }

    // MARK: - Crop board selection

    private var cropSection: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            HStack(spacing: 2) {
                Text("작물 게시판 선택")
                    .appTypography(.labelMediumEmphasized)
                    .foregroundStyle(Color.Text.default)
                Text("*")
                    .foregroundStyle(Color.Text.red)
            }
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: Spacing.sm) {
                    ForEach(viewModel.boards) { board in
                        AppChip(label: board.cropName, isSelected: viewModel.selectedCropId == board.cropId) {
                            viewModel.selectCrop(board.cropId)
                        }
                    }
                    Button {
                        showCropPicker = true
                    } label: {
                        Image(systemName: "plus")
                            .font(.system(size: 16))
                            .foregroundStyle(Color.Icon.subtle)
                            .frame(width: 32, height: 32)
                            .background(Circle().stroke(Color.Border.default, lineWidth: 1))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    private var titleField: some View {
        VStack(alignment: .trailing, spacing: Spacing.xs) {
            AppTextField(placeholder: "제목을 입력해 주세요", text: $viewModel.title)
                .onChange(of: viewModel.title) { _, newValue in
                    if newValue.count > CommunityComposeViewModel.titleLimit {
                        viewModel.title = String(newValue.prefix(CommunityComposeViewModel.titleLimit))
                    }
                }
            Text("\(viewModel.title.count)/\(CommunityComposeViewModel.titleLimit)")
                .appTypography(.labelMedium)
                .foregroundStyle(Color.Text.muted)
        }
    }

    // MARK: - 영농기록 (backend not available yet)

    private var farmingRecordButton: some View {
        HStack(spacing: Spacing.sm) {
            Image(systemName: "doc.text")
                .foregroundStyle(Color.Icon.disabled)
            Text("영농기록 가져오기")
                .appTypography(.bodyMedium)
                .foregroundStyle(Color.Text.disabled)
            Spacer()
        }
        .padding(Spacing.md)
        .background(RoundedRectangle(cornerRadius: 8).fill(Color.Object.muted))
        .opacity(0.6)
        // 영농기록(Farm) 도메인의 백엔드 엔드포인트가 아직 없어 비활성 플레이스홀더로 둔다.
        .allowsHitTesting(false)
    }

    // MARK: - Images

    private var imageSection: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Spacing.md) {
                if viewModel.canAddImage {
                    PhotosPicker(
                        selection: $pickerItems,
                        maxSelectionCount: CommunityComposeViewModel.maxImages - viewModel.attachments.count,
                        matching: .images
                    ) {
                        addSlotLabel
                    }
                    .disabled(viewModel.isUploadingImage)
                }
                ForEach(viewModel.attachments) { attachment in
                    AppImageUploadSlot(
                        onRemove: { viewModel.removeImage(id: attachment.id) }
                    ) {
                        thumbnail(for: attachment)
                    }
                }
            }
        }
    }

    private var addSlotLabel: some View {
        VStack(spacing: 6) {
            if viewModel.isUploadingImage {
                ProgressView()
            } else {
                Image(systemName: "camera.fill")
                    .font(.system(size: 24))
                    .foregroundStyle(Color.Icon.default)
            }
            Text("\(viewModel.attachments.count)/\(CommunityComposeViewModel.maxImages)")
                .appTypography(.labelMedium)
                .foregroundStyle(Color.Text.subtle)
        }
        .frame(width: 92, height: 92)
        .background(Color.Object.muted)
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }

    @ViewBuilder private func thumbnail(for attachment: CommunityComposeViewModel.Attachment) -> some View {
        if let uiImage = UIImage(data: attachment.previewData) {
            Image(uiImage: uiImage).resizable().scaledToFill()
        } else {
            Color.Object.muted
        }
    }

    // MARK: - Q&A toggle

    private var questionToggle: some View {
        Toggle(isOn: $viewModel.isQuestion) {
            VStack(alignment: .leading, spacing: Spacing.xs) {
                Label("Q&A로 받기", systemImage: "questionmark.circle")
                    .appTypography(.bodyMediumEmphasized)
                    .foregroundStyle(Color.Text.default)
                Text("다른 농부에게 Q&A를 요청합니다.")
                    .appTypography(.labelMedium)
                    .foregroundStyle(Color.Text.muted)
            }
        }
        .tint(Color.Object.primary)
    }

    private func attach(_ items: [PhotosPickerItem]) async {
        for item in items {
            guard let data = try? await item.loadTransferable(type: Data.self) else { continue }
            await viewModel.addImage(data)
        }
        pickerItems = []
    }
}
