//
//  AuthTokenStore.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import Foundation

actor AuthTokenStore {
    private static let accessTokenAccount = "accessToken"
    private static let refreshTokenAccount = "refreshToken"

    private let storage: KeychainTokenStorage
    private var cachedAccessToken: String?
    private var cachedRefreshToken: String?
    private var isLoaded = false
    private var sessionExpiredContinuations: [AsyncStream<Void>.Continuation] = []

    init(storage: KeychainTokenStorage = KeychainTokenStorage()) {
        self.storage = storage
    }

    func accessToken() -> String? {
        loadIfNeeded()
        return cachedAccessToken
    }

    func refreshToken() -> String? {
        loadIfNeeded()
        return cachedRefreshToken
    }

    /// Cold-launch-only synchronous check. Keychain reads are plain system calls, not truly asynchronous —
    /// this intentionally bypasses the actor so the app's very first `AppState` value (built in
    /// `ChamChamChamApp.init()`, before SwiftUI's first render) can already reflect an existing session,
    /// instead of `RootView` flashing the logged-out flow for a frame while `bootstrap()` awaits the actor.
    nonisolated static func hasStoredRefreshToken(storage: KeychainTokenStorage = KeychainTokenStorage()) -> Bool {
        storage.read(account: refreshTokenAccount) != nil
    }

    func save(accessToken: String, refreshToken: String) {
        storage.save(accessToken, account: Self.accessTokenAccount)
        storage.save(refreshToken, account: Self.refreshTokenAccount)
        cachedAccessToken = accessToken
        cachedRefreshToken = refreshToken
        isLoaded = true
    }

    /// Ends the local session (explicit logout or a failed silent refresh) and notifies `sessionExpiredEvents` subscribers.
    func clear() {
        storage.delete(account: Self.accessTokenAccount)
        storage.delete(account: Self.refreshTokenAccount)
        cachedAccessToken = nil
        cachedRefreshToken = nil
        isLoaded = true
        for continuation in sessionExpiredContinuations {
            continuation.yield(())
        }
    }

    /// The single coupling point between the networking layer and app-level auth state — `RootView` subscribes to
    /// this to fall back to the logged-out flow whenever `clear()` runs, without `Core/Networking` importing `AppState`.
    func sessionExpiredEvents() -> AsyncStream<Void> {
        AsyncStream { continuation in
            sessionExpiredContinuations.append(continuation)
        }
    }

    private func loadIfNeeded() {
        guard !isLoaded else { return }
        cachedAccessToken = storage.read(account: Self.accessTokenAccount)
        cachedRefreshToken = storage.read(account: Self.refreshTokenAccount)
        isLoaded = true
    }
}
