//
//  OnboardingDraftStore.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import Foundation

struct OnboardingDraftSnapshot: Codable {
    var step: OnboardingViewModel.Step
    var draft: OnboardingDraft
}

final class OnboardingDraftStore {
    private let defaults: UserDefaults
    private let snapshotKey = "onboarding.draft.snapshot"
    private let imagesDirectory: URL

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        self.imagesDirectory = base.appendingPathComponent("OnboardingDraft", isDirectory: true)
        try? FileManager.default.createDirectory(at: imagesDirectory, withIntermediateDirectories: true)
    }

    func load() -> OnboardingDraftSnapshot? {
        guard let data = defaults.data(forKey: snapshotKey) else { return nil }
        return try? JSONDecoder().decode(OnboardingDraftSnapshot.self, from: data)
    }

    func save(_ snapshot: OnboardingDraftSnapshot) {
        guard let data = try? JSONEncoder().encode(snapshot) else { return }
        defaults.set(data, forKey: snapshotKey)
    }

    func clear() {
        defaults.removeObject(forKey: snapshotKey)
        try? FileManager.default.removeItem(at: imagesDirectory)
        try? FileManager.default.createDirectory(at: imagesDirectory, withIntermediateDirectories: true)
    }

    @discardableResult
    func saveProfileImage(_ data: Data) -> String {
        let fileName = "\(UUID().uuidString).jpg"
        let url = imagesDirectory.appendingPathComponent(fileName)
        try? data.write(to: url)
        return fileName
    }

    func loadProfileImage(fileName: String) -> Data? {
        try? Data(contentsOf: imagesDirectory.appendingPathComponent(fileName))
    }
}
