//
//  ProfileBasicInfoViewModel.swift
//  ChamChamCham
//
//  Created by iyungui on 7/13/26.
//

import Foundation
import Observation

/// 프로필 수정 · 기본 정보 탭. Prefills from the member profile and saves via `PUT /members/me/profile`.
/// 이름은 수정 불가(표시 전용), 닉네임/연락처/생년월일은 선택. 필수 = 자격/귀농 연차.
@Observable
@MainActor
final class ProfileBasicInfoViewModel {
    // Form fields
    private(set) var name = ""            // 표시 전용(수정 불가)
    var nickname = ""                     // 선택
    var phone = ""
    var birthDate: Date?
    var experienceYears: Int?
    var managementType: ManagementType = .agriculturalIndividual

    private(set) var isLoading = false
    private(set) var loadErrorMessage: String?
    private(set) var isSubmitting = false
    var saveErrorMessage: String?
    var hasAttemptedSave = false

    // Avatar
    private(set) var profileImageUrl: String?
    private(set) var previewImageData: Data?
    private(set) var isUploadingImage = false
    var imageErrorMessage: String?
    /// Set only when a new photo is picked and uploaded this session. Left `nil` otherwise so `save()`
    /// keeps sending the previous behavior (the API has no "leave unchanged" signal for this field —
    /// see `save()`).
    @ObservationIgnored private var profileMediaId: UUID?

    @ObservationIgnored private let repository: any MemberProfileRepository
    @ObservationIgnored private let mediaRepository: any MediaUploadRepository
    @ObservationIgnored private let farmRepository: any FarmRepository

    init(
        repository: any MemberProfileRepository,
        mediaRepository: any MediaUploadRepository,
        farmRepository: any FarmRepository
    ) {
        self.repository = repository
        self.mediaRepository = mediaRepository
        self.farmRepository = farmRepository
    }

    func load() async {
        guard name.isEmpty else { return }
        isLoading = true
        loadErrorMessage = nil
        defer { isLoading = false }
        do {
            let profile = try await repository.fetchMyProfile()
            name = profile.name ?? ""
            nickname = profile.nickname ?? ""
            phone = profile.phone ?? ""
            birthDate = profile.birthDate.flatMap { Self.wireDateFormatter.date(from: $0) }
            experienceYears = profile.experienceLevel
            managementType = profile.managementType
                .flatMap(ManagementType.init(rawValue:)) ?? .agriculturalIndividual
            profileImageUrl = profile.profileImageUrl
        } catch {
            loadErrorMessage = "프로필을 불러오지 못했어요. 다시 시도해주세요."
        }
    }

    // MARK: - Avatar

    /// Uploads a newly picked photo and stashes its `mediaId` for the next `save()`. Shows the picked
    /// image immediately; reverts to the previous avatar if the upload fails.
    func pickImage(_ data: Data) async {
        previewImageData = data
        isUploadingImage = true
        imageErrorMessage = nil
        defer { isUploadingImage = false }
        do {
            let uploaded = try await mediaRepository.uploadProfileImage(data, originalFilename: nil)
            profileMediaId = uploaded.mediaId
        } catch {
            previewImageData = nil
            imageErrorMessage = "사진을 올리지 못했어요. 다시 시도해주세요."
        }
    }

    // MARK: - Validation

    /// Full (만) age as of today, or `nil` when `birthDate` hasn't been entered — it's optional now that
    /// Guideline 5.1.1(v) requires it not be required.
    var currentAge: Int? {
        guard let birthDate else { return nil }
        return Calendar.current.dateComponents([.year], from: birthDate, to: Date()).year
    }

    /// Upper bound for `experienceYears`: capped at the member's actual age when known, otherwise the server's
    /// generic 0...100 range (`experienceLevel` schema bound) since there's nothing else to check it against.
    var maxAllowedExperienceYears: Int {
        currentAge ?? 100
    }

    /// Sent to the backend as `isExperienceLevelWithinAge` — the server can't derive this itself since
    /// `birthDate` may be absent. `true` when there's no birth date to check against (nothing to contradict).
    var isExperienceLevelWithinAge: Bool {
        guard let experienceYears else { return true }
        guard let currentAge else { return true }
        return experienceYears <= currentAge
    }

    var experienceError: String? {
        guard hasAttemptedSave else { return nil }
        guard let experienceYears else { return "귀농 연차를 입력해주세요." }
        guard experienceYears <= maxAllowedExperienceYears else {
            return birthDate == nil ? "귀농 연차는 100년을 넘을 수 없어요." : "귀농 연차는 나이를 넘을 수 없어요."
        }
        return nil
    }

    var canSave: Bool {
        guard let experienceYears, experienceYears <= maxAllowedExperienceYears else { return false }
        return !isSubmitting && !isUploadingImage
    }

    // MARK: - Save

    func save() async -> Bool {
        hasAttemptedSave = true
        guard let experienceYears, canSave else { return false }
        isSubmitting = true
        saveErrorMessage = nil
        defer { isSubmitting = false }

        do {
            // The backend models the whole profile (including farms) as one atomic PUT with no partial-update
            // support, so a basic-info-only save still has to resend the member's current farms unchanged.
            let farms = try await farmRepository.listFarms().compactMap { $0.toUpdateMyProfileFarmRequest() }
            let request = UpdateMyProfileRequestDTO(
                name: name,
                phone: phone,
                birthDate: birthDate.map(Self.wireDateFormatter.string(from:)),
                nickname: nickname,
                experienceLevel: experienceYears,
                managementType: managementType.rawValue,
                profileMediaId: profileMediaId,
                farms: farms,
                isExperienceLevelWithinAge: isExperienceLevelWithinAge
            )
            _ = try await repository.updateMyProfile(request)
            return true
        } catch {
            saveErrorMessage = "저장에 실패했어요. 다시 시도해주세요."
            return false
        }
    }

    static let wireDateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(identifier: "Asia/Seoul")
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }()
}
