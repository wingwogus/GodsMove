//
//  KeychainTokenStorage.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import Foundation
import Security

struct KeychainTokenStorage: Sendable {
    private let service = "me.GodsMove.ChamChamCham.auth"

    func save(_ value: String, account: String) {
        let data = Data(value.utf8)
        let baseQuery = query(account: account)
        SecItemDelete(baseQuery as CFDictionary)

        var attributes = baseQuery
        attributes[kSecValueData as String] = data
        attributes[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock
        SecItemAdd(attributes as CFDictionary, nil)
    }

    func read(account: String) -> String? {
        var attributes = query(account: account)
        attributes[kSecReturnData as String] = true
        attributes[kSecMatchLimit as String] = kSecMatchLimitOne

        var result: AnyObject?
        let status = SecItemCopyMatching(attributes as CFDictionary, &result)
        guard status == errSecSuccess, let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    func delete(account: String) {
        SecItemDelete(query(account: account) as CFDictionary)
    }

    private func query(account: String) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
    }
}
