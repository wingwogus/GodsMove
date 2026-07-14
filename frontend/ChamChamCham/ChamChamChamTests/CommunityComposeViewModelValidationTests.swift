//
//  CommunityComposeViewModelValidationTests.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/9/26.
//

import Foundation
import Testing
@testable import ChamChamCham

@MainActor
@Suite("CommunityComposeViewModel validation")
struct CommunityComposeViewModelValidationTests {

    @Test("compose limits match the Figma community write spec")
    func figmaComposeLimits() {
        #expect(CommunityComposeViewModel.titleLimit == 30)
        #expect(CommunityComposeViewModel.bodyLimit == 500)
        #expect(CommunityComposeViewModel.maxImages == 5)
    }

    @Test("submit requires selected crop and title/body within their limits")
    func canSubmitRejectsOverLimitText() {
        let viewModel = makeViewModel()
        let cropId = UUID()
        viewModel.addBoards(from: [Crop(id: cropId, name: "딸기", category: "과채류")])

        viewModel.title = "제목 입력 완료"
        viewModel.body = "본문 입력 완료"
        #expect(viewModel.canSubmit)
        #expect(!viewModel.isTitleOverLimit)
        #expect(!viewModel.isBodyOverLimit)

        viewModel.title = String(repeating: "가", count: 31)
        #expect(viewModel.isTitleOverLimit)
        #expect(!viewModel.canSubmit)

        viewModel.title = String(repeating: "가", count: 30)
        viewModel.body = String(repeating: "나", count: 501)
        #expect(!viewModel.isTitleOverLimit)
        #expect(viewModel.isBodyOverLimit)
        #expect(!viewModel.canSubmit)
    }

    @Test("compose exposes the captured validation copy with title priority")
    func inputValidationMessage() {
        let viewModel = makeViewModel()
        #expect(viewModel.inputValidationMessage == nil)

        viewModel.title = String(repeating: "가", count: 31)
        #expect(viewModel.inputValidationMessage == "제목은 최대 30자까지 입력 가능합니다.")

        viewModel.body = String(repeating: "나", count: 501)
        #expect(viewModel.inputValidationMessage == "제목은 최대 30자까지 입력 가능합니다.")

        viewModel.title = String(repeating: "가", count: 30)
        #expect(viewModel.inputValidationMessage == "내용은 최대 500자까지 입력 가능합니다.")
    }

    @Test("compose accepts at most five image attachments")
    func imageAttachmentLimit() async {
        let viewModel = makeViewModel()

        for byte in UInt8(0)..<UInt8(6) {
            await viewModel.addImage(Data([byte]))
        }

        #expect(viewModel.attachments.count == 5)
        #expect(!viewModel.canAddImage)
    }

    private func makeViewModel() -> CommunityComposeViewModel {
        CommunityComposeViewModel(
            repository: FakeCommunityRepository(),
            cropCatalog: StubCropCatalogService(),
            mediaRepository: FakeMediaUploadRepository()
        )
    }
}

private actor FakeCommunityRepository: CommunityRepository {
    private(set) var createPostCallCount = 0
    private let createdPostId = UUID()

    func fetchBoards() async throws -> [CommunityBoard] { [] }

    func fetchPosts(_ query: CommunityPostQuery) async throws -> CommunityPostPage {
        CommunityPostPage(items: [], nextCursor: nil)
    }

    func fetchPostDetail(id: UUID) async throws -> CommunityPostDetail {
        throw FakeUploadError()
    }

    func createPost(
        cropId: UUID,
        postType: CommunityPostType,
        title: String,
        body: String,
        farmingRecordId: UUID?,
        mediaIds: [UUID]
    ) async throws -> UUID {
        createPostCallCount += 1
        return createdPostId
    }

    func updatePost(
        id: UUID,
        cropId: UUID,
        postType: CommunityPostType,
        title: String,
        body: String,
        farmingRecordId: UUID?,
        mediaIds: [UUID]
    ) async throws -> UUID {
        id
    }

    func deletePost(id: UUID) async throws {}

    func fetchComments(postId: UUID, cursor: String?, size: Int) async throws -> CommunityCommentPage {
        CommunityCommentPage(items: [], nextCursor: nil)
    }

    func createComment(postId: UUID, parentCommentId: UUID?, body: String, mediaId: UUID?) async throws -> UUID {
        UUID()
    }

    func deleteComment(id: UUID) async throws {}

    func toggleLike(postId: UUID) async throws -> LikeToggleResult {
        LikeToggleResult(liked: true, likeCount: 1)
    }
}
