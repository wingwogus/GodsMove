//
//  AuthTokenStore.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import Foundation

final class AuthTokenStore {
    func accessToken() -> String? { nil }
    func refreshToken() -> String? { nil }
    func save(accessToken: String, refreshToken: String) {}
    func clear() {}
}
