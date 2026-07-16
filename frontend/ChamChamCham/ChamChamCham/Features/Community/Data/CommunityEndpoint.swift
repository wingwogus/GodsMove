//
//  CommunityEndpoint.swift
//  ChamChamCham
//
//  Created by iyungui on 7/7/26.
//

import Foundation

/// Search/paging parameters for `GET /api/v1/community/posts`. Mirrors the backend `CommunityPostSearchCondition`.
/// Defaults match the server (`sort = .latest`, `size = 20`) so the common "first page, latest" call is just
/// `CommunityPostQuery()`.
struct CommunityPostQuery: Sendable, Equatable {
    var cropId: UUID?
    var postType: CommunityPostType?
    var keyword: String?
    var likedOnly: Bool
    var mineOnly: Bool
    /// Filters to posts authored by this member, independent of the authenticated caller
    /// (which still drives `likedOnly`/`mineOnly`/`likedByMe`). Backend param name: `memberId`.
    var memberId: UUID?
    var sort: CommunityPostSort
    var cursor: String?
    var size: Int

    init(
        cropId: UUID? = nil,
        postType: CommunityPostType? = nil,
        keyword: String? = nil,
        likedOnly: Bool = false,
        mineOnly: Bool = false,
        memberId: UUID? = nil,
        sort: CommunityPostSort = .latest,
        cursor: String? = nil,
        size: Int = 20
    ) {
        self.cropId = cropId
        self.postType = postType
        self.keyword = keyword
        self.likedOnly = likedOnly
        self.mineOnly = mineOnly
        self.memberId = memberId
        self.sort = sort
        self.cursor = cursor
        self.size = size
    }
}

/// Requires auth — the whole `/api/v1/community/**` tree sits behind authentication.
enum CommunityEndpoint: Endpoint {
    case listBoards
    case listPosts(CommunityPostQuery)
    case createPost(SavePostRequestDTO)
    case getPost(UUID)
    case updatePost(UUID, SavePostRequestDTO)
    case deletePost(UUID)
    case listComments(postId: UUID, cursor: String?, size: Int)
    case createComment(postId: UUID, CreateCommentRequestDTO)
    case deleteComment(UUID)
    case toggleLike(postId: UUID)

    private static let base = "api/v1/community"

    var path: String {
        switch self {
        case .listBoards:
            "\(Self.base)/boards"
        case .listPosts, .createPost:
            "\(Self.base)/posts"
        case let .getPost(id), let .updatePost(id, _), let .deletePost(id):
            "\(Self.base)/posts/\(id.uuidString)"
        case let .listComments(postId, _, _), let .createComment(postId, _):
            "\(Self.base)/posts/\(postId.uuidString)/comments"
        case let .deleteComment(id):
            "\(Self.base)/comments/\(id.uuidString)"
        case let .toggleLike(postId):
            "\(Self.base)/posts/\(postId.uuidString)/like-toggle"
        }
    }

    var method: HTTPMethod {
        switch self {
        case .listBoards, .listPosts, .getPost, .listComments:
            .get
        case .createPost, .createComment, .toggleLike:
            .post
        case .updatePost:
            .patch
        case .deletePost, .deleteComment:
            .delete
        }
    }

    var body: (any Encodable & Sendable)? {
        switch self {
        case let .createPost(dto), let .updatePost(_, dto):
            dto
        case let .createComment(_, dto):
            dto
        default:
            nil
        }
    }

    var requiresAuth: Bool { true }

    var queryItems: [URLQueryItem] {
        switch self {
        case let .listPosts(query):
            var items: [URLQueryItem] = [
                URLQueryItem(name: "sort", value: query.sort.rawValue),
                URLQueryItem(name: "size", value: String(query.size))
            ]
            if let cropId = query.cropId {
                items.append(URLQueryItem(name: "cropId", value: cropId.uuidString))
            }
            if let postType = query.postType {
                items.append(URLQueryItem(name: "postType", value: postType.rawValue))
            }
            if let keyword = query.keyword, !keyword.trimmingCharacters(in: .whitespaces).isEmpty {
                items.append(URLQueryItem(name: "keyword", value: keyword))
            }
            if query.likedOnly {
                items.append(URLQueryItem(name: "likedOnly", value: "true"))
            }
            if query.mineOnly {
                items.append(URLQueryItem(name: "mineOnly", value: "true"))
            }
            if let memberId = query.memberId {
                items.append(URLQueryItem(name: "memberId", value: memberId.uuidString))
            }
            if let cursor = query.cursor {
                items.append(URLQueryItem(name: "cursor", value: cursor))
            }
            return items
        case let .listComments(_, cursor, size):
            var items = [URLQueryItem(name: "size", value: String(size))]
            if let cursor {
                items.append(URLQueryItem(name: "cursor", value: cursor))
            }
            return items
        default:
            return []
        }
    }
}
