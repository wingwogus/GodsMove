//
//  CommunityComposeView.swift
//  ChamChamCham
//
//  Created by iyungui on 7/7/26.
//

import PhotosUI
import SwiftUI

/// "게시물 작성하기" composer. Calls `onCreated` with the new post id after a successful submit and
/// dismisses itself.
struct CommunityComposeView: View {
    enum Layout {
        static let horizontalInset: CGFloat = 20
        static let textAreaContentInset: CGFloat = 20
        static let titleHeight: CGFloat = 38
        static let minimumBodyLines = 9
        static let maximumBodyLines = 21
        static let descriptionSpacing: CGFloat = 12
        static let imageSpacing: CGFloat = 12
        static let sectionTopInset: CGFloat = 16
        static let majorSectionGap: CGFloat = 24
    }

    private enum FocusedField {
        case title
        case body
    }

    private let container: DIContainer
    private let onCreated: (UUID) -> Void

    @State private var viewModel: CommunityComposeViewModel
    @State private var pickerItems: [PhotosPickerItem] = []
    @State private var showCropPicker = false
    @State private var showRecordPicker = false
    @FocusState private var focusedField: FocusedField?
    @Environment(\.dismiss) private var dismiss

    init(container: DIContainer, onCreated: @escaping (UUID) -> Void) {
        self.container = container
        self.onCreated = onCreated
        _viewModel = State(
            initialValue: CommunityComposeViewModel(
                repository: container.makeCommunityRepository(),
                cropCatalog: container.makeCropCatalogService(),
                mediaRepository: container.makeMediaUploadRepository(),
                recordRepository: container.makeRecordRepository()
            )
        )
    }

    var body: some View {
        VStack(spacing: 0) {
            AppTopAppBar(
                title: "게시물 작성하기",
                isDetail: true,
                showBorder: false,
                leading: .init(.asset("arrow_back_ios_new")) { dismiss() }
            )

            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    cropSection
                    composeTextArea
                    sectionDivider
                    farmingRecordSection
                    imageSection
                    sectionDivider
                    questionToggle
                    errorMessage
                }
            }
            .scrollDismissesKeyboard(.interactively)
        }
        .background(Color.Background.default)
        // 키보드가 하단 "완료" 바를 밀어 올리지 않도록 고정한다 (SearchView와 동일 패턴).
        .ignoresSafeArea(.keyboard, edges: .bottom)
        .dismissKeyboardOnTap()
        .safeAreaInset(edge: .bottom) {
            submitBar
        }
        .task { await viewModel.loadBoards() }
        .task { await viewModel.loadRecentRecords() }
        .fullScreenCover(isPresented: $showCropPicker) {
            CropPickerView(
                loadCrops: viewModel.catalogCrops,
                loadCategories: viewModel.catalogCategories,
                initialSelectedCropIDs: viewModel.boards.map(\.cropId)
            ) { crops in
                viewModel.addBoards(from: crops)
            }
        }
        .fullScreenCover(isPresented: $showRecordPicker) {
            FarmingRecordPickerView(
                repository: container.makeRecordRepository(),
                selectedRecord: Binding(
                    get: { viewModel.selectedFarmingRecord },
                    set: { viewModel.selectFarmingRecord($0) }
                )
            )
        }
        .onChange(of: pickerItems) { _, items in
            guard !items.isEmpty else { return }
            Task { await attach(items) }
        }
    }

    // MARK: - Crop board selection

    private var cropSection: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 2) {
                Text("작물 게시판")
                    .appTypography(.bodyMedium)
                    .foregroundStyle(Color.Text.default)
                Text("*")
                    .appTypography(.bodyMedium)
                    .foregroundStyle(Color.Text.red)
            }
            .frame(height: 24)
            .padding(.horizontal, Layout.horizontalInset)

            HStack(spacing: 0) {
                Button {
                    showCropPicker = true
                } label: {
                    AppIconView(source: .asset("add"), size: 32)
                        .foregroundStyle(Color.Icon.subtle)
                        .background(Color.Object.default)
                        .overlay(Circle().stroke(Color.Border.default, lineWidth: 1))
                        .clipShape(Circle())
                }
                .buttonStyle(.plain)
                .frame(width: 60, height: 48)
                .overlay(alignment: .trailing) {
                    Rectangle()
                        .fill(Color.Border.default)
                        .frame(width: 1, height: 24)
                }

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: Spacing.sm) {
                        ForEach(viewModel.boards) { board in
                            AppChip(
                                label: board.cropName,
                                isSelected: viewModel.selectedCropId == board.cropId,
                                style: .solid
                            ) {
                                viewModel.selectCrop(board.cropId)
                            }
                        }
                    }
                    .padding(.leading, Spacing.sm)
                    .padding(.trailing, Layout.horizontalInset)
                    .frame(height: 48)
                }
            }
            .frame(height: 48)
        }
        .padding(.top, Layout.sectionTopInset)
    }

    // MARK: - Text area

    private var composeTextArea: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            TextField(
                "",
                text: $viewModel.title,
                prompt: Text("제목을 입력해주세요.")
                    .foregroundStyle(Color.Text.muted)
            )
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
                    "",
                    text: $viewModel.body,
                    prompt: Text("농사와 관련해 이야기하고 싶은 내용을 자유롭게 작성해보세요.")
                        .foregroundStyle(Color.Text.muted),
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
        .padding(.bottom, Layout.majorSectionGap)
    }

    private var validationRow: some View {
        HStack(alignment: .top, spacing: Spacing.sm) {
            if let inputValidationMessage = viewModel.inputValidationMessage {
                Text(inputValidationMessage)
                    .appTypography(.labelMedium)
                    .foregroundStyle(Color.Text.red)
                    .lineLimit(2)
                    .fixedSize(horizontal: false, vertical: true)
            }

            Spacer(minLength: Spacing.sm)

            Text("\(viewModel.body.count)/\(CommunityComposeViewModel.bodyLimit)")
                .appTypography(.labelMedium)
                .foregroundStyle(viewModel.isBodyOverLimit ? Color.Text.red : Color.Text.muted)
                .lineLimit(1)
        }
    }

    // MARK: - Farming record

    private var farmingRecordSection: some View {
        VStack(alignment: .leading, spacing: Layout.imageSpacing) {
            Button {
                showRecordPicker = true
            } label: {
                HStack {
                    Text("영농 기록 첨부하기")
                        .appTypography(.bodyMedium)
                        .foregroundStyle(Color.Text.default)
                    Spacer()
                    AppIconView(source: .asset("chevron_forward"), size: 24)
                        .foregroundStyle(Color.Icon.subtle)
                }
                .frame(height: 24)
            }
            .buttonStyle(.plain)
            .padding(.horizontal, Layout.horizontalInset)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: Spacing.sm) {
                    ForEach(viewModel.recentRecords.prefix(3)) { record in
                        Button {
                            if viewModel.selectedFarmingRecord?.id == record.id {
                                viewModel.selectFarmingRecord(nil)
                            } else {
                                viewModel.selectFarmingRecord(record)
                            }
                        } label: {
                            AppCard(
                                size: .xsmall,
                                title: record.workType.label,
                                captions: [record.cropName, record.memoPreview],
                                dateText: FarmingRecordPickerView.dateText(for: record),
                                isSelected: viewModel.selectedFarmingRecord?.id == record.id
                            ) {
                                RecordRemoteImage(url: record.thumbnailUrl, workType: record.workType)
                            }
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, Layout.horizontalInset)
            }
        }
        .padding(.top, Layout.majorSectionGap)
        .padding(.bottom, Layout.majorSectionGap)
    }

    // MARK: - Images

    private var imageSection: some View {
        let attachmentCount = viewModel.attachments.count
        let remainingSelectionCount = CommunityComposeViewModel.maxImages - attachmentCount
        let uploadSlot = AppImageUploadSlot(
            label: "\(attachmentCount)/\(CommunityComposeViewModel.maxImages)",
            isInteractive: false
        )

        return VStack(alignment: .leading, spacing: Layout.imageSpacing) {
            Text("사진 첨부하기")
                .appTypography(.bodyMedium)
                .foregroundStyle(Color.Text.default)
                .padding(.horizontal, Layout.horizontalInset)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: Layout.imageSpacing) {
                    if viewModel.canAddImage {
                        PhotosPicker(
                            selection: $pickerItems,
                            maxSelectionCount: remainingSelectionCount,
                            matching: .images
                        ) {
                            uploadSlot
                        }
                    }

                    ForEach(viewModel.attachments) { attachment in
                        AppImageUploadSlot(
                            onRemove: { viewModel.removeImage(id: attachment.id) }
                        ) {
                            thumbnail(for: attachment)
                        }
                    }
                }
                .padding(.horizontal, Layout.horizontalInset)
            }
        }
        .padding(.bottom, Layout.majorSectionGap)
    }

    /// The picked image is shown immediately; while its upload is in flight a dimmed spinner overlays it.
    @ViewBuilder private func thumbnail(for attachment: CommunityComposeViewModel.Attachment) -> some View {
        ZStack {
            if let uiImage = UIImage(data: attachment.previewData) {
                Image(uiImage: uiImage).resizable().scaledToFill()
            } else {
                Color.Object.muted
            }
            if attachment.isUploading {
                Color.black.opacity(0.3)
                ProgressView()
                    .tint(Color.Icon.inverse)
            }
        }
    }

    // MARK: - Q&A toggle

    private var questionToggle: some View {
        HStack {
            Text("질문으로 올리기")
                .appTypography(.bodyMedium)
                .foregroundStyle(Color.Text.default)
            Spacer()
            AppToggle(isOn: $viewModel.isQuestion)
        }
        .padding(.horizontal, Layout.horizontalInset)
        .frame(height: 28)
        .padding(.top, Layout.majorSectionGap)
        .padding(.bottom, 36)
    }

    private var submitBar: some View {
        VStack(spacing: 0) {
            Rectangle()
                .fill(Color.Border.subtle)
                .frame(height: 1)

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
            .padding(.horizontal, Layout.horizontalInset)
            .padding(.top, 12)
            .padding(.bottom, 12)
        }
        .background(Color.Background.default)
    }

    @ViewBuilder private var errorMessage: some View {
        if let errorMessage = viewModel.errorMessage {
            Text(errorMessage)
                .appTypography(.labelMedium)
                .foregroundStyle(Color.Text.red)
                .padding(.horizontal, Layout.horizontalInset)
                .padding(.bottom, Spacing.md)
        }
    }

    private var sectionDivider: some View {
        AppDivider(size: .small)
    }

    private func submit() {
        Task {
            if let id = await viewModel.submit() {
                onCreated(id)
                dismiss()
            }
        }
    }

    private func attach(_ items: [PhotosPickerItem]) async {
        for item in items {
            guard let data = try? await item.loadTransferable(type: Data.self) else { continue }
            await viewModel.addImage(data)
        }
        pickerItems = []
    }
}
