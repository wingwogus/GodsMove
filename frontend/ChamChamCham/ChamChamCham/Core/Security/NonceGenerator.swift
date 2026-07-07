//
//  NonceGenerator.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import Foundation
import CryptoKit

/// Shared by the Kakao and Apple OIDC login flows — both need a per-attempt random nonce to prevent ID-token replay.
enum NonceGenerator {
    private static let charset: [Character] = Array("0123456789ABCDEFGHIJKLMNOPQRSTUVXYZabcdefghijklmnopqrstuvwxyz-._")

    static func generate(length: Int = 32) -> String {
        var remaining = length
        var result = ""
        while remaining > 0 {
            var randomBytes = [UInt8](repeating: 0, count: 16)
            let status = SecRandomCopyBytes(kSecRandomDefault, randomBytes.count, &randomBytes)
            precondition(status == errSecSuccess, "Unable to generate secure random nonce bytes")
            for byte in randomBytes where remaining > 0 {
                if byte < charset.count {
                    result.append(charset[Int(byte)])
                    remaining -= 1
                }
            }
        }
        return result
    }

    /// Apple compares `SHA256(rawNonce)` (lowercase hex, no separators) against the ID token's `nonce` claim.
    static func sha256Hex(_ value: String) -> String {
        let digest = SHA256.hash(data: Data(value.utf8))
        return digest.map { String(format: "%02x", $0) }.joined()
    }
}
