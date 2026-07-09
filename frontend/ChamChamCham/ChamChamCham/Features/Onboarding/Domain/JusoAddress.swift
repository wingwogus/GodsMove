//
//  JusoAddress.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import Foundation

struct JusoAddress: Identifiable, Hashable, Sendable {
    let id = UUID()
    let roadAddrPart1: String
    let jibunAddr: String
    let bdNm: String
}

extension JusoAddress: Decodable {
    private enum CodingKeys: String, CodingKey {
        case roadAddrPart1, jibunAddr, bdNm
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        roadAddrPart1 = try container.decodeIfPresent(String.self, forKey: .roadAddrPart1) ?? ""
        jibunAddr = try container.decodeIfPresent(String.self, forKey: .jibunAddr) ?? ""
        bdNm = try container.decodeIfPresent(String.self, forKey: .bdNm) ?? ""
    }
}
