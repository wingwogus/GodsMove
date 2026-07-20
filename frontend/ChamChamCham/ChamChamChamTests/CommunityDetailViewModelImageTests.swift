//
//  CommunityDetailViewModelImageTests.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/16/26.
//

import Foundation
import Testing
@testable import ChamChamCham

@MainActor
@Suite("CommunityDetailViewModel image attachment")
struct CommunityDetailViewModelImageTests {

    @Test("addImage fills the mediaId and clears the uploading flag on success")
    func addImageSuccess() async {
        let mediaId = UUID()
        let viewModel = makeViewModel(media: FakeMediaUploadRepository(successMediaId: mediaId))

        await viewModel.addImage(Data([0x1]))

        #expect(viewModel.attachment?.mediaId == mediaId)
        #expect(viewModel.attachment?.isUploading == false)
        #expect(viewModel.isUploadingImage == false)
        #expect(viewModel.errorMessage == nil)
    }

    @Test("addImage drops the attachment and surfaces an error when the upload fails")
    func addImageFailure() async {
        let viewModel = makeViewModel(media: FakeMediaUploadRepository(fails: true))

        await viewModel.addImage(Data([0x1]))

        #expect(viewModel.attachment == nil)
        #expect(viewModel.errorMessage == "사진을 올리지 못했어요. 다시 시도해주세요.")
    }

    @Test("submitComment sends the uploaded mediaId and clears the attachment")
    func submitSendsMediaId() async {
        let mediaId = UUID()
        let repository = RecordingCommunityRepository()
        let viewModel = makeViewModel(
            repository: repository,
            media: FakeMediaUploadRepository(successMediaId: mediaId)
        )
        await viewModel.load()

        await viewModel.addImage(Data([0x1]))
        viewModel.draftComment = "이미지 댓글"
        await viewModel.submitComment()

        #expect(await repository.lastCreatedCommentMediaId == mediaId)
        #expect(viewModel.attachment == nil)
        #expect(viewModel.draftComment.isEmpty)
    }

    @Test("submitComment sends a nil mediaId when no image is attached")
    func submitWithoutImageSendsNil() async {
        let repository = RecordingCommunityRepository()
        let viewModel = makeViewModel(repository: repository)
        await viewModel.load()

        viewModel.draftComment = "텍스트만"
        await viewModel.submitComment()

        #expect(await repository.createCommentCallCount == 1)
        #expect(await repository.lastCreatedCommentMediaId == nil)
    }

    @Test("submitComment is blocked while the attachment is still uploading")
    func submitBlockedWhileUploading() async {
        let repository = RecordingCommunityRepository()
        let gate = GatedMediaUploadRepository()
        let viewModel = makeViewModel(repository: repository, media: gate)
        await viewModel.load()

        // Kick off an upload that suspends inside the repository until we release it.
        let uploadTask = Task { await viewModel.addImage(Data([0x1])) }
        while await gate.isWaiting == false { await Task.yield() }

        viewModel.draftComment = "업로드 중 전송 시도"
        await viewModel.submitComment()
        #expect(viewModel.isUploadingImage)
        #expect(await repository.createCommentCallCount == 0)

        await gate.release()
        await uploadTask.value
        #expect(viewModel.isUploadingImage == false)
    }

    private func makeViewModel(
        repository: any CommunityRepository = RecordingCommunityRepository(),
        media: any MediaUploadRepository = FakeMediaUploadRepository()
    ) -> CommunityDetailViewModel {
        CommunityDetailViewModel(
            postId: UUID(),
            repository: repository,
            mediaRepository: media,
            recordRepository: StubRecordRepository()
        )
    }
}

/// Every fixture post in this file has `farmingRecordId == nil`, so `load()` never calls this repository —
/// every method is unused.
private struct StubRecordRepository: RecordRepository {
    private struct Unused: Error {}

    func fetchRecords(_ query: RecordQuery) async throws -> RecordPage { throw Unused() }
    func fetchDetail(id: UUID) async throws -> RecordDetail { throw Unused() }
    func fetchCoaching(id: UUID) async throws -> RecordCoaching { throw Unused() }
    func deleteRecord(id: UUID) async throws { throw Unused() }
    func fetchActiveCrops() async throws -> [ActiveCrop] { throw Unused() }
    func fetchFarmCrops() async throws -> [FarmWithCrops] { throw Unused() }
    func searchPesticides(keyword: String?) async throws -> [Pesticide] { throw Unused() }
    func fetchPests(pesticideId: UUID) async throws -> [Pest] { throw Unused() }
    func createRecord(_ request: SaveRecordRequestDTO) async throws -> UUID { throw Unused() }
    func fetchEditPrefill(id: UUID) async throws -> VoiceRecordPrefill { throw Unused() }
    func updateRecord(id: UUID, _ request: SaveRecordRequestDTO) async throws -> UUID { throw Unused() }
}

/// Records comment creation so tests can assert the `mediaId` the view model forwards. `fetchPostDetail`
/// returns a minimal post so `load()` succeeds before submitting.
private actor RecordingCommunityRepository: CommunityRepository {
    private(set) var createCommentCallCount = 0
    private(set) var lastCreatedCommentMediaId: UUID?

    func fetchBoards() async throws -> [CommunityBoard] { [] }

    func fetchPostCrops(memberId: UUID) async throws -> [CommunityBoard] { [] }

    func fetchPosts(_ query: CommunityPostQuery) async throws -> CommunityPostPage {
        CommunityPostPage(items: [], nextCursor: nil)
    }

    func fetchPostDetail(id: UUID) async throws -> CommunityPostDetail {
        CommunityPostDetail(
            id: id,
            cropId: UUID(),
            cropName: "딸기",
            postType: .general,
            title: "제목",
            body: "본문",
            imageUrls: [],
            farmingRecordId: nil,
            author: CommunityAuthor(memberId: UUID(), nickname: "닉네임", profileImageUrl: nil),
            commentCount: 0,
            likeCount: 0,
            likedByMe: false,
            createdAt: Date(timeIntervalSince1970: 0)
        )
    }

    func createPost(
        cropId: UUID,
        postType: CommunityPostType,
        title: String,
        body: String,
        farmingRecordId: UUID?,
        mediaIds: [UUID]
    ) async throws -> UUID { UUID() }

    func updatePost(
        id: UUID,
        cropId: UUID,
        postType: CommunityPostType,
        title: String,
        body: String,
        farmingRecordId: UUID?,
        mediaIds: [UUID]
    ) async throws -> UUID { id }

    func deletePost(id: UUID) async throws {}

    func fetchComments(postId: UUID, cursor: String?, size: Int) async throws -> CommunityCommentPage {
        CommunityCommentPage(items: [], nextCursor: nil)
    }

    func createComment(postId: UUID, parentCommentId: UUID?, body: String, mediaId: UUID?) async throws -> UUID {
        createCommentCallCount += 1
        lastCreatedCommentMediaId = mediaId
        return UUID()
    }

    func deleteComment(id: UUID) async throws {}

    func toggleLike(postId: UUID) async throws -> LikeToggleResult {
        LikeToggleResult(liked: true, likeCount: 1)
    }
}

/// A media repository whose upload suspends until `release()` is called, so a test can observe the
/// view model's `isUploadingImage` guard while an upload is genuinely in flight.
private actor GatedMediaUploadRepository: MediaUploadRepository {
    private(set) var isWaiting = false
    private var continuation: CheckedContinuation<Void, Never>?
    private let mediaId = UUID()

    func release() {
        continuation?.resume()
        continuation = nil
    }

    private func waitForRelease() async {
        await withCheckedContinuation { continuation in
            self.continuation = continuation
            self.isWaiting = true
        }
        isWaiting = false
    }

    func uploadProfileImage(_ imageData: Data, originalFilename: String?) async throws -> UploadedImageResponseDTO {
        await waitForRelease()
        return OnboardingTestFactory.uploadedImage(mediaId: mediaId)
    }

    func uploadCommunityImage(_ imageData: Data, originalFilename: String?) async throws -> UploadedImageResponseDTO {
        await waitForRelease()
        return OnboardingTestFactory.uploadedImage(mediaId: mediaId)
    }

    func uploadFarmingRecordImage(_ imageData: Data, originalFilename: String?) async throws -> UploadedImageResponseDTO {
        await waitForRelease()
        return OnboardingTestFactory.uploadedImage(mediaId: mediaId)
    }
}
