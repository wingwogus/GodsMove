//
//  CommunitySavePostRequestDTOTests.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/7/26.
//

import Foundation
import Testing
@testable import ChamChamCham

@Suite("Community request DTO encoding")
struct CommunitySavePostRequestDTOTests {

    private func encodedJSON(_ value: some Encodable) throws -> [String: Any] {
        let data = try JSONEncoder().encode(value)
        return try #require(JSONSerialization.jsonObject(with: data) as? [String: Any])
    }

    @Test("save post encodes to the confirmed wire keys")
    func savePostWireKeys() throws {
        let cropId = UUID()
        let recordId = UUID()
        let mediaId = UUID()
        let dto = SavePostRequestDTO(
            cropId: cropId,
            postType: CommunityPostType.question.rawValue,
            title: "제목",
            body: "본문",
            farmingRecordId: recordId,
            mediaIds: [mediaId]
        )

        let json = try encodedJSON(dto)
        #expect((json["cropId"] as? String).flatMap(UUID.init) == cropId)
        #expect(json["postType"] as? String == "QUESTION")
        #expect(json["title"] as? String == "제목")
        #expect(json["body"] as? String == "본문")
        #expect((json["farmingRecordId"] as? String).flatMap(UUID.init) == recordId)

        let mediaIds = try #require(json["mediaIds"] as? [String])
        #expect(mediaIds.compactMap(UUID.init) == [mediaId])
    }

    @Test("absent farmingRecordId is omitted; empty mediaIds encodes as []")
    func nullFarmingRecord() throws {
        let dto = SavePostRequestDTO(
            cropId: UUID(), postType: "GENERAL", title: "t", body: "b", farmingRecordId: nil, mediaIds: []
        )
        let json = try encodedJSON(dto)
        // Swift's JSONEncoder omits nil optionals; the backend's `UUID? = null` default treats the absent key
        // as null, so omission is the intended wire shape.
        #expect(json["farmingRecordId"] == nil)
        #expect((json["mediaIds"] as? [Any])?.isEmpty == true)
    }

    @Test("root comment omits parent, reply encodes parent id")
    func commentParent() throws {
        let rootJSON = try encodedJSON(
            CreateCommentRequestDTO(parentCommentId: nil, body: "루트", mediaId: nil)
        )
        #expect(rootJSON["parentCommentId"] == nil)
        #expect(rootJSON["mediaId"] == nil)
        #expect(rootJSON["body"] as? String == "루트")

        let parentId = UUID()
        let mediaId = UUID()
        let replyJSON = try encodedJSON(
            CreateCommentRequestDTO(parentCommentId: parentId, body: "답글", mediaId: mediaId)
        )
        #expect((replyJSON["parentCommentId"] as? String).flatMap(UUID.init) == parentId)
        #expect((replyJSON["mediaId"] as? String).flatMap(UUID.init) == mediaId)
    }

    @Test("farming record image usage matches Swagger enum")
    func farmingRecordImageUsage() throws {
        let dto = UploadImageRequestDTO(
            usageType: MediaImageUsage.farmingRecord.rawValue,
            base64Image: Data("image".utf8).base64EncodedString(),
            originalFilename: "record.jpg",
            contentType: "image/jpeg"
        )

        let json = try encodedJSON(dto)

        #expect(json["usageType"] as? String == "FARMING_RECORD")
        #expect(json["originalFilename"] as? String == "record.jpg")
    }
}
