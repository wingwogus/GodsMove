//
//  MemberProfileCache.swift
//  ChamChamCham
//
//  Created by iyungui on 7/6/26.
//

import SwiftData

/// Persists the post-login/post-onboarding member snapshot to `CachedMemberProfile`. View models depend on this
/// instead of touching `ModelContext`/`CachedMemberProfile` directly, keeping SwiftData out of the presentation
/// layer's dependency surface.
@MainActor
protocol MemberProfileCache {
    @discardableResult
    func save(member: MemberProfileResponseDTO, onboarding: OnboardingResponseDTO) -> CachedMemberProfile
    func fetchCurrent() -> CachedMemberProfile?
}

@MainActor
final class SwiftDataMemberProfileCache: MemberProfileCache {
    private let modelContext: ModelContext

    init(modelContext: ModelContext) {
        self.modelContext = modelContext
    }

    @discardableResult
    func save(member: MemberProfileResponseDTO, onboarding: OnboardingResponseDTO) -> CachedMemberProfile {
        CachedMemberProfile.upsert(member: member, onboarding: onboarding, in: modelContext)
    }

    func fetchCurrent() -> CachedMemberProfile? {
        CachedMemberProfile.fetchCached(in: modelContext)
    }
}
