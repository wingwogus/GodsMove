//
//  CachedMemberProfile.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import Foundation
import SwiftData

/// A read-through cache of the last known server-side member/onboarding state, written only after a successful
/// network response (login or onboarding-complete) — never something with a "pending" state to reconcile.
/// Lets `RootView` route optimistically on cold launch before the network round-trip completes (offline-first),
/// at the cost of the cache going stale until the next full login — there is currently no `GET /api/v1/users/me`
/// to refresh it any other way.
@Model
final class CachedMemberProfile {
    @Attribute(.unique) var id: UUID
    var email: String?
    var name: String?
    var nickname: String?
    var phone: String?
    var birthDateRaw: String?
    var experienceLevel: Int?
    var managementTypeRaw: String?
    var profileImageUrl: String?
    var onboardingStatusRaw: String
    var missingFieldsRaw: [String]
    var updatedAt: Date

    init(
        id: UUID,
        email: String?,
        name: String?,
        nickname: String?,
        phone: String?,
        birthDateRaw: String?,
        experienceLevel: Int?,
        managementTypeRaw: String?,
        profileImageUrl: String?,
        onboardingStatusRaw: String,
        missingFieldsRaw: [String],
        updatedAt: Date
    ) {
        self.id = id
        self.email = email
        self.name = name
        self.nickname = nickname
        self.phone = phone
        self.birthDateRaw = birthDateRaw
        self.experienceLevel = experienceLevel
        self.managementTypeRaw = managementTypeRaw
        self.profileImageUrl = profileImageUrl
        self.onboardingStatusRaw = onboardingStatusRaw
        self.missingFieldsRaw = missingFieldsRaw
        self.updatedAt = updatedAt
    }

    var isOnboardingComplete: Bool {
        onboardingStatusRaw == OnboardingStatusDTO.complete.rawValue
    }

    /// This app only ever holds one signed-in member locally, so this replaces any existing row rather than
    /// keying a multi-row cache.
    @discardableResult
    static func upsert(
        member: MemberProfileResponseDTO,
        onboarding: OnboardingResponseDTO,
        in context: ModelContext
    ) -> CachedMemberProfile {
        let descriptor = FetchDescriptor<CachedMemberProfile>()
        let existing = (try? context.fetch(descriptor))?.first

        if let existing {
            existing.id = member.id
            existing.email = member.email
            existing.name = member.name
            existing.nickname = member.nickname
            existing.phone = member.phone
            existing.birthDateRaw = member.birthDate
            existing.experienceLevel = member.experienceLevel
            existing.managementTypeRaw = member.managementType
            existing.profileImageUrl = member.profileImageUrl
            existing.onboardingStatusRaw = onboarding.status.rawValue
            existing.missingFieldsRaw = onboarding.missingFields
            existing.updatedAt = Date()
            return existing
        }

        let created = CachedMemberProfile(
            id: member.id,
            email: member.email,
            name: member.name,
            nickname: member.nickname,
            phone: member.phone,
            birthDateRaw: member.birthDate,
            experienceLevel: member.experienceLevel,
            managementTypeRaw: member.managementType,
            profileImageUrl: member.profileImageUrl,
            onboardingStatusRaw: onboarding.status.rawValue,
            missingFieldsRaw: onboarding.missingFields,
            updatedAt: Date()
        )
        context.insert(created)
        return created
    }

    static func fetchCached(in context: ModelContext) -> CachedMemberProfile? {
        let descriptor = FetchDescriptor<CachedMemberProfile>()
        return (try? context.fetch(descriptor))?.first
    }
}
